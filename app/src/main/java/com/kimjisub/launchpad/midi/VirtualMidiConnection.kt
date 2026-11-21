package com.kimjisub.launchpad.midi

import androidx.annotation.MainThread
import com.kimjisub.launchpad.midi.driver.DriverRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** One explicitly selected virtual device. Lifecycle and state callbacks belong to the main thread. */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
internal class VirtualMidiConnection(
	private val backend: VirtualMidiBackend,
	private val scope: CoroutineScope,
	private val main: CoroutineDispatcher,
	private val io: CoroutineDispatcher,
	private val gate: MidiTransportGate,
	private val attach: (String, DriverRef, DriverRef.OnSendSignalListener) -> AutoCloseable,
) {
	sealed interface State {
		data object Idle : State
		data object Connecting : State
		data object LightUnavailable : State
		data class Connected(val lights: Boolean) : State
		data class Failed(val reason: Failure) : State
	}
	enum class Failure { USB_BUSY, INVALID_PORT, UNAVAILABLE, INPUT_PORT, TIMEOUT, DISCONNECTED, IO, OVERFLOW }
	var state: State = State.Idle
		private set
	var onStateChanged: ((State) -> Unit)? = null
	val canConnect: Boolean get() = attempt == null
	private var attempt: Attempt? = null

	private class Attempt(val selection: VirtualMidiSelection, val driver: DriverRef, val lease: AutoCloseable) {
		val receiving = AtomicBoolean(false)
		val overflowing = AtomicBoolean(false)
		val incoming = Channel<IntArray>(256)
		val outgoing = Channel<ByteArray>(256)
		var job: Job? = null
		var timer: Job? = null
		var watch: AutoCloseable? = null
		var attachment: AutoCloseable? = null
		var writer: Job? = null
		var light: VirtualMidiBackend.Port? = null
	}

	/** A one-shot read; callers may cancel their coroutine. It never changes a connection. */
	suspend fun devices(): Result<List<VirtualMidiDevice>> = try {
		Result.success(withContext(io) { backend.devices() })
	} catch (e: CancellationException) { throw e } catch (_: Exception) { Result.failure(IllegalStateException("MIDI devices unavailable")) }

	@MainThread
	fun connect(selection: VirtualMidiSelection, driver: DriverRef): Boolean {
		if (attempt != null) return false
		if (selection.device.performancePorts.none { it.number == selection.performancePort } ||
			(selection.lightPort != null && selection.device.lightPorts.none { it.number == selection.lightPort })) {
			publish(State.Failed(Failure.INVALID_PORT)); return false
		}
		val lease = gate.acquireVirtual() ?: run { publish(State.Failed(Failure.USB_BUSY)); return false }
		val current = Attempt(selection, driver, lease)
		attempt = current
		current.timer = scope.launch(main) {
			delay(10_000)
			if (attempt === current) stop(State.Failed(Failure.TIMEOUT))
		}
		// Enter the try/finally even if cancellation arrives before the IO dispatcher starts.
		current.job = scope.launch(io, start = CoroutineStart.ATOMIC) { run(current) }
		publish(State.Connecting)
		return true
	}

	private suspend fun run(current: Attempt) {
		var device: VirtualMidiBackend.Device? = null
		var input: VirtualMidiBackend.Port? = null
		var failure = Failure.IO
		try {
			current.watch = backend.watchRemoval { id ->
				scope.launch(main) {
					if (attempt === current && id == current.selection.device.id) stop(State.Failed(Failure.DISCONNECTED))
				}
			}
			failure = Failure.UNAVAILABLE
			device = backend.open(current.selection.device.id) ?: error("Device unavailable")
			currentCoroutineContext().ensureActive()
			val decoder = MidiStreamDecoder { status, note, value ->
				if (current.receiving.get() && !current.incoming.trySend(intArrayOf(status shr 4, status.toByte().toInt(), note, value)).isSuccess) overflow(current)
			}
			failure = Failure.INPUT_PORT
			input = device.openPerformancePort(current.selection.performancePort, decoder::accept) ?: error("Input port unavailable")
			currentCoroutineContext().ensureActive()
			current.light = current.selection.lightPort?.let { number ->
				try { device.openLightPort(number) } catch (_: Exception) { null }
			}
			currentCoroutineContext().ensureActive()
			withContext(main) {
				if (attempt === current) {
					current.timer?.cancel()
					if (current.selection.lightPort != null && current.light == null) publish(State.LightUnavailable)
					else activate(current)
				}
			}
			failure = Failure.IO
			while (true) {
				delay(1_000)
				if (withTimeoutOrNull(2_000) { device.isAlive() } != true) {
					withContext(main) { if (attempt === current) stop(State.Failed(Failure.DISCONNECTED)) }
					break
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			withContext(main) { if (attempt === current) stop(State.Failed(failure)) }
		} finally {
			// Keep cancellation masked across dispatcher returns, until every resource is released.
			withContext(NonCancellable) {
				withContext(main) {
					current.receiving.set(false)
					current.timer?.cancel()
					// Discard old animation writes before detaching queues the driver's all-off messages.
					while (current.outgoing.tryReceive().isSuccess) { }
					closeSafely(current.attachment)
					current.attachment = null
				}
				withContext(io) {
					current.outgoing.close()
					withTimeoutOrNull(1_000) { current.writer?.join() }
					closeSafely(current.watch)
					closeSafely(input)
					closeSafely(current.light)
					closeSafely(device)
					current.incoming.cancel()
					current.outgoing.cancel()
					current.writer?.cancel()
				}
				withContext(main) {
					current.lease.close()
					if (attempt === current) {
						attempt = null
						publish(if (state is State.Connected || state == State.Connecting) State.Failed(Failure.DISCONNECTED) else state)
					}
				}
			}
		}
	}

	@MainThread
	fun continueWithoutLights() {
		val current = attempt ?: return
		if (state == State.LightUnavailable) activate(current)
	}

	private fun activate(current: Attempt) {
		val sender = object : DriverRef.OnSendSignalListener {
			private fun send(bytes: ByteArray?) {
				if (current.light != null && bytes != null && !current.outgoing.trySend(bytes).isSuccess) overflow(current)
			}
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) = send(MidiStreamEncoder.packet(cmd, sig, note, velocity))
			override fun onSendPackets(packets: ByteArray) = send(MidiStreamEncoder.packets(packets))
			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
				for (message in messages) if (message.size <= 4096) send(message.copyOf())
			}
		}
		try {
			current.attachment = attach(current.selection.device.name, current.driver, sender)
			current.receiving.set(true)
			publish(State.Connected(current.light != null))
			scope.launch(main) {
				try {
					for (event in current.incoming) {
						if (current.receiving.get()) current.driver.getSignal(event[0], event[1], event[2], event[3])
					}
				} catch (e: CancellationException) { throw e }
				catch (_: Exception) { if (attempt === current) stop(State.Failed(Failure.IO)) }
			}
			current.writer = scope.launch(io) {
				try { for (bytes in current.outgoing) current.light?.send(bytes) }
				catch (e: CancellationException) { throw e }
				catch (_: Exception) { withContext(main) { if (attempt === current) stop(State.Failed(Failure.IO)) } }
			}
		} catch (_: Exception) { stop(State.Failed(Failure.IO)) }
	}

	private fun overflow(current: Attempt) {
		if (current.overflowing.compareAndSet(false, true)) scope.launch(main) {
			if (attempt === current) stop(State.Failed(Failure.OVERFLOW))
		}
	}

	@MainThread
	fun disconnect() = stop(State.Idle)

	private fun stop(next: State) {
		val current = attempt
		current?.receiving?.set(false)
		current?.timer?.cancel()
		current?.job?.cancel()
		publish(next)
	}

	private fun publish(value: State) {
		state = value
		try { onStateChanged?.invoke(value) } catch (_: Exception) { /* State remains readable after a failed observer. */ }
	}
	private fun closeSafely(value: AutoCloseable?) { try { value?.close() } catch (_: Exception) { /* Continue releasing the other resources. */ } }
}
