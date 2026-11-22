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
    private val gate: MidiTransportGate,
    private val newRules: () -> MidiSessionRules,
) : AutoCloseable {
    private val lock = Any()
    private val io = Executors.newFixedThreadPool(2)
    private val timer = Executors.newSingleThreadScheduledExecutor()
    // Closing a port waits for a send stuck on a computer that is not reading; it must not hold io.
    private val closer = Executors.newCachedThreadPool()
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
    private var refreshAgain = false

    private class Session(val generation: Long, val id: Int, val lease: AutoCloseable, val rules: MidiSessionRules) {
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

    /**
     * Enumeration errors are separate from an already open session. A change reported while a read
     * is running may not be in that read, so it is followed by one more read instead of being dropped.
     */
    fun refresh() {
        synchronized(lock) {
            if (closed) return
            if (refreshing) { refreshAgain = true; return }
            refreshing = true
            io.execute {
                do {
                    synchronized(lock) { refreshAgain = false }
                    try {
                        val found = access.devices()
                        synchronized(lock) { if (!closed) { mutableDevices.value = found; mutableDiscoveryFailure.value = null } }
                    } catch (_: Exception) {
                        synchronized(lock) { if (!closed) { mutableDevices.value = emptyList(); mutableDiscoveryFailure.value = Failure.DISCOVERY } }
                    }
                } while (synchronized(lock) { (refreshAgain && !closed).also { again -> if (!again) refreshing = false } })
            }
        }
    }

    fun connect(device: PeripheralDevice, inputPort: Int, outputPort: Int?) {
        synchronized(lock) {
            if (closed || current != null) return
            if (device.inputs.none { it.number == inputPort }) { mutableState.value = State.Failed(Failure.INPUT_PORT); return }
            if (outputPort != null && device.outputs.none { it.number == outputPort }) {
                mutableState.value = State.Failed(Failure.OUTPUT_PORT)
                return
            }
            val lease = gate.acquirePeripheral()
            if (lease == null) { mutableState.value = State.Failed(Failure.BUSY); return }
            val session = Session(++generation, device.id, lease, newRules())
            current = session
            session.rules.clearReceived()
            mutableState.value = State.Connecting
            // cancel() cannot stop a timer that already started, so it checks whether the open finished first.
            session.timeout = timer.schedule({
                synchronized(lock) { if (session.device == null) end(session, State.Failed(Failure.TIMEOUT)) }
            }, 10, TimeUnit.SECONDS)
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
            session.timeout = timer.schedule({ end(session, State.Failed(Failure.RELEASE)) }, 1, TimeUnit.SECONDS)
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

    /**
     * A failure is reported and the transport handed back at once: closing the ports can wait for
     * as long as a stuck send does. A normal release has no send in progress, so it closes first and
     * reports a port that did not close cleanly.
     */
    private fun end(session: Session, result: State) {
        synchronized(lock) {
            if (current !== session || session.ending) return
            session.ending = true
            session.accepting = false
            ++generation
            session.timeout?.cancel(false)
            session.writer.shutdownNow()
            session.rules.clearReceived()
            val waitForClose = result == State.Idle
            if (waitForClose) mutableState.value = State.Closing else finish(session, result)
            closer.execute {
                val closedCleanly = synchronized(session.resourcesLock) {
                    val outputClosed = safelyClose(session.output)
                    val inputClosed = safelyClose(session.input)
                    val deviceClosed = safelyClose(session.device)
                    outputClosed && inputClosed && deviceClosed
                }
                if (waitForClose) synchronized(lock) {
                    finish(session, if (closedCleanly) result else State.Failed(Failure.CLOSE))
                    if (closed) shutdown()
                }
            }
            if (closed && !waitForClose) shutdown()
        }
    }

    private fun finish(session: Session, result: State) {
        session.lease.close()
        if (current === session) { current = null; mutableState.value = result }
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

    private fun shutdown() { timer.shutdownNow(); io.shutdown(); closer.shutdown() }
    private fun safelyClose(resource: AutoCloseable?): Boolean = try { resource?.close(); true } catch (_: Exception) { false }
}
