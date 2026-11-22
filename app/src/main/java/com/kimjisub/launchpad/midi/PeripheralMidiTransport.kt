package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.UsbPeripheralConnection.Failure
import com.kimjisub.launchpad.midi.UsbPeripheralConnection.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Lifecycle and ordered raw bytes; no layout, palette, pointers or byte-stream parser. */
internal class PeripheralMidiTransport(
    private val access: PeripheralMidiAccess,
    private val newRules: () -> MidiSessionRules,
) : AutoCloseable {
    private val lock = Any()
    private val io = Executors.newFixedThreadPool(2)
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state = mutableState.asStateFlow()
    private val mutableDevices = MutableStateFlow<List<PeripheralDevice>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val mutableDiscoveryFailure = MutableStateFlow<Failure?>(null)
    val discoveryFailure = mutableDiscoveryFailure.asStateFlow()
    private var generation = 0L
    private var current: Session? = null
    private var closed = false
    private var watcher: AutoCloseable? = null
    private var refreshing = false

    private class Session(val generation: Long, val id: Int, val lease: MidiModeOwnership.Lease, val rules: MidiSessionRules) {
        val resourcesLock = Any()
        var device: PeripheralMidiAccess.Device? = null
        var input: PeripheralMidiAccess.Sender? = null
        var output: AutoCloseable? = null
        val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1024))
        var timeout: ScheduledFuture<*>? = null
        var accepting = false
        var ending = false
    }

    init {
        io.execute {
            try {
                val registered = access.watch(::refresh) { id ->
                    synchronized(lock) { current?.takeIf { it.id == id } }?.let { end(it, State.Failed(Failure.REMOVED)) }
                }
                synchronized(lock) { if (closed) safelyClose(registered) else watcher = registered }
                refresh()
            } catch (_: Exception) { mutableDiscoveryFailure.value = Failure.UNAVAILABLE }
        }
    }

    /** Enumeration errors are separate from an already open session. */
    fun refresh() {
        synchronized(lock) {
            if (closed || refreshing) return
            refreshing = true
            io.execute {
                try {
                    val found = access.devices()
                    synchronized(lock) { if (!closed) { mutableDevices.value = found; mutableDiscoveryFailure.value = null } }
                } catch (_: Exception) {
                    synchronized(lock) { if (!closed) { mutableDevices.value = emptyList(); mutableDiscoveryFailure.value = Failure.DISCOVERY } }
                } finally { synchronized(lock) { refreshing = false } }
            }
        }
    }

    fun connect(device: PeripheralDevice, inputPort: Int, outputPort: Int?) {
        synchronized(lock) {
            if (closed || current != null) return
            if (device.inputs.none { it.number == inputPort } ||
                (outputPort != null && device.outputs.none { it.number == outputPort })) {
                mutableState.value = State.Failed(Failure.INPUT_PORT)
                return
            }
            val lease = MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_PERIPHERAL)
            if (lease == null) { mutableState.value = State.Failed(Failure.BUSY); return }
            val session = Session(++generation, device.id, lease, newRules())
            current = session
            session.rules.clearReceived()
            mutableState.value = State.Connecting
            session.timeout = timer.schedule({ end(session, State.Failed(Failure.TIMEOUT)) }, 10, TimeUnit.SECONDS)
            io.execute {
                try {
                    access.open(device.id) { opened ->
                        // This callback can arrive after close(), when the pool is already shut down.
                        dispatchOrClose(opened) { finishOpen(session, opened, inputPort, outputPort) }
                    }
                } catch (_: Exception) { end(session, State.Failed(Failure.OPEN)) }
            }
        }
    }

    private fun finishOpen(session: Session, opened: PeripheralMidiAccess.Device?, inputPort: Int, outputPort: Int?) {
        synchronized(session.resourcesLock) {
            val valid = synchronized(lock) { current === session && !session.ending && session.generation == generation }
            if (!valid) { safelyClose(opened); return }
            if (opened == null) { end(session, State.Failed(Failure.OPEN)); return }
            var input: PeripheralMidiAccess.Sender? = null
            var output: AutoCloseable? = null
            try {
                input = opened.input(inputPort)
                if (input == null) { safelyClose(opened); end(session, State.Failed(Failure.INPUT_PORT)); return }
                if (outputPort != null) {
                    try {
                        output = opened.output(outputPort) { bytes, offset, count ->
                            synchronized(lock) {
                                if (current === session && session.generation == generation && !session.ending)
                                    session.rules.receive(bytes, offset, count)
                            }
                        }
                    } catch (_: Exception) { /* The required outgoing port remains usable. */ }
                }
                synchronized(lock) {
                    if (current === session && !session.ending && session.generation == generation) {
                        session.device = opened
                        session.input = input
                        session.output = output
                        session.accepting = true
                        session.timeout?.cancel(false)
                        mutableState.value = State.Open(output != null, session.generation)
                        return
                    }
                }
            } catch (_: Exception) { end(session, State.Failed(Failure.INPUT_PORT)) }
            safelyClose(output); safelyClose(input); safelyClose(opened)
        }
    }

    /** Queued in order off the caller thread. A full queue fails the whole session. */
    fun send(bytes: ByteArray, generation: Long): Boolean {
        synchronized(lock) {
            val session = current?.takeIf { it.accepting && !it.ending && it.generation == generation } ?: return false
            try {
                session.writer.execute {
                    try {
                        session.input!!.send(bytes)
                        session.rules.sent(bytes)
                    } catch (_: Exception) { end(session, State.Failed(Failure.SEND)) }
                }
                return true
            } catch (_: RejectedExecutionException) { overflow(session); return false }
        }
    }

    /** Stops new input immediately; gives preceding writes and final releases one second. */
    fun disconnect() {
        synchronized(lock) {
            val session = current ?: return
            if (session.ending || mutableState.value == State.Closing) return
            if (!session.accepting) { end(session, State.Idle); return }
            session.accepting = false
            ++generation // Invalidate incoming bytes immediately.
            session.rules.clearReceived()
            mutableState.value = State.Closing
            session.timeout = timer.schedule({ end(session, State.Failed(Failure.SEND)) }, 1, TimeUnit.SECONDS)
            try {
                session.writer.execute {
                    try {
                        for (release in session.rules.releases()) session.input!!.send(release)
                        end(session, State.Idle)
                    } catch (_: Exception) { end(session, State.Failed(Failure.SEND)) }
                }
            } catch (_: RejectedExecutionException) { overflow(session) }
        }
    }

    private fun overflow(session: Session) {
        synchronized(lock) {
            if (current !== session || session.ending) return
            session.accepting = false
            ++generation
            session.writer.queue.clear()
            session.rules.clearReceived()
            mutableState.value = State.Closing
            session.timeout?.cancel(false)
            session.timeout = timer.schedule({ end(session, State.Failed(Failure.OVERFLOW)) }, 1, TimeUnit.SECONDS)
            try {
                session.writer.execute {
                    try {
                        for (release in session.rules.releases()) session.input!!.send(release)
                    } catch (_: Exception) { }
                    end(session, State.Failed(Failure.OVERFLOW))
                }
            } catch (_: RejectedExecutionException) { end(session, State.Failed(Failure.OVERFLOW)) }
        }
    }

    private fun end(session: Session, result: State) {
        synchronized(lock) {
            if (current !== session || session.ending) return
            session.ending = true
            session.accepting = false
            ++generation
            session.timeout?.cancel(false)
            session.writer.shutdownNow()
            session.rules.clearReceived()
            mutableState.value = State.Closing
            io.execute {
                val closedCleanly = synchronized(session.resourcesLock) {
                    val outputClosed = safelyClose(session.output)
                    val inputClosed = safelyClose(session.input)
                    val deviceClosed = safelyClose(session.device)
                    session.lease.close()
                    outputClosed && inputClosed && deviceClosed
                }
                val finalResult = if (result == State.Idle && !closedCleanly) State.Failed(Failure.SEND) else result
                synchronized(lock) {
                    if (current === session) { current = null; mutableState.value = finalResult }
                    if (closed) shutdown()
                }
            }
        }
    }

    private fun dispatchOrClose(device: PeripheralMidiAccess.Device?, block: () -> Unit) {
        try { io.execute(block) } catch (_: RejectedExecutionException) { safelyClose(device) }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            val oldWatcher = watcher
            watcher = null
            io.execute { safelyClose(oldWatcher) }
            if (current == null) shutdown() else disconnect()
        }
    }

    private fun shutdown() { timer.shutdownNow(); io.shutdown() }
    private fun safelyClose(resource: AutoCloseable?): Boolean = try { resource?.close(); true } catch (_: Exception) { false }
}
