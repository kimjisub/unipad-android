package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.midi.Mk2Layout
import com.kimjisub.launchpad.midi.PadChange
import com.kimjisub.launchpad.midi.PadPointers
import com.kimjisub.launchpad.midi.PeripheralDevice
import com.kimjisub.launchpad.midi.UsbPeripheralConnection.Failure
import com.kimjisub.launchpad.midi.UsbPeripheralConnection.State

/** What the computer pad screen shows. Derived from the connection; no Android types. */
internal sealed interface ComputerPadPhase {
	/** The phone has no MIDI feature or service; only the reason and a way back are offered. */
	data object Unsupported : ComputerPadPhase
	/** [failure] is a listing failure (DISCOVERY or UNAVAILABLE), or null when nothing was found. */
	data class NotFound(val failure: Failure?) : ComputerPadPhase
	data class Ready(val devices: List<PeripheralDevice>, val previousClosing: Boolean) : ComputerPadPhase
	data object Connecting : ComputerPadPhase
	/** The required press port opened but the chosen lights port did not: use without lights or cancel. */
	data object LightsUnavailable : ComputerPadPhase
	/** The ports are open; this says nothing about whether the computer reads them. */
	data class Open(val generation: Long, val lights: Lights) : ComputerPadPhase
	data object Closing : ComputerPadPhase
	data class Failed(val failure: Failure, val attempt: Long) : ComputerPadPhase
	/** The screen was left while connected; nothing resumes until the user connects again. */
	data object Paused : ComputerPadPhase

	enum class Lights { NOT_RECEIVING, NO_SIGNAL_YET, RECENT }
}

/** How a connection failure is explained: a connection that may have carried presses needs the stop-sound advice. */
internal enum class ComputerPadFailureKind { NOT_CONNECTED, OTHER_CONNECTION, DISCONNECTED }

internal object ComputerPadRules {
	/** A light signal counts as recent this long after the last valid one; a quiet computer is not a lost one. */
	const val RECENT_LIGHT_NANOS = 3_000_000_000L

	data class Inputs(
		val midiSupported: Boolean,
		val state: State,
		val devices: List<PeripheralDevice>,
		val discoveryFailure: Failure?,
		val portsClosing: Boolean,
		val lightsRequested: Boolean,
		val withoutLights: Boolean,
		val paused: Boolean,
		val dismissedAttempt: Long?,
		/** A valid light arrived within [RECENT_LIGHT_NANOS]; see [recent]. */
		val lightsRecent: Boolean,
	)

	fun phase(inputs: Inputs): ComputerPadPhase = with(inputs) {
		if (!midiSupported) return ComputerPadPhase.Unsupported
		when (state) {
			State.Connecting -> return ComputerPadPhase.Connecting
			State.Closing -> return ComputerPadPhase.Closing
			is State.Open -> return when {
				!state.lightsAvailable && lightsRequested && !withoutLights -> ComputerPadPhase.LightsUnavailable
				else -> ComputerPadPhase.Open(state.generation, lights(state.lightsAvailable, lightsRecent))
			}
			else -> Unit
		}
		if (paused) return ComputerPadPhase.Paused
		if (state is State.Failed && state.attempt != dismissedAttempt) return ComputerPadPhase.Failed(state.reason, state.attempt)
		if (devices.isEmpty()) return ComputerPadPhase.NotFound(discoveryFailure)
		return ComputerPadPhase.Ready(devices, portsClosing)
	}

	fun recent(lastLightNanos: Long?, nowNanos: Long): Boolean =
		lastLightNanos != null && nowNanos - lastLightNanos < RECENT_LIGHT_NANOS

	fun lights(available: Boolean, recent: Boolean): ComputerPadPhase.Lights = when {
		!available -> ComputerPadPhase.Lights.NOT_RECEIVING
		recent -> ComputerPadPhase.Lights.RECENT
		else -> ComputerPadPhase.Lights.NO_SIGNAL_YET
	}

	fun kind(failure: Failure): ComputerPadFailureKind = when (failure) {
		Failure.BUSY -> ComputerPadFailureKind.OTHER_CONNECTION
		Failure.SEND, Failure.RELEASE, Failure.CLOSE, Failure.OVERFLOW, Failure.REMOVED -> ComputerPadFailureKind.DISCONNECTED
		Failure.UNAVAILABLE, Failure.DISCOVERY, Failure.OPEN, Failure.INPUT_PORT, Failure.OUTPUT_PORT, Failure.TIMEOUT ->
			ComputerPadFailureKind.NOT_CONNECTED
	}

	/** Leaving the screen stops a connection that was open or being opened; the user resumes it explicitly. */
	fun pausesOnLeave(phase: ComputerPadPhase): Boolean =
		phase is ComputerPadPhase.Open || phase == ComputerPadPhase.Connecting || phase == ComputerPadPhase.LightsUnavailable
}

/**
 * Finger ownership to MIDI messages. Each pad keeps the session generation it was pressed in, so its
 * release goes to that session or nowhere. Input is accepted only while a generation is set.
 */
internal class ComputerPadInput(
	private val send: (PadChange, Long) -> Boolean,
	private val pressed: (index: Int, pressed: Boolean) -> Unit,
) {
	private companion object {
		/** Screen reader clicks: a press and release pair that shares counting with real fingers. */
		const val CLICK_POINTER = Int.MAX_VALUE
	}

	private val pointers = PadPointers()
	private val pressedIn = LongArray(Mk2Layout.SIZE)
	private var generation: Long? = null

	/** Releases everything held when input stops or a different session takes over. */
	fun accept(generation: Long?) {
		if (this.generation == generation) return
		cancel()
		this.generation = generation
	}

	fun move(pointer: Int, index: Int?) {
		val current = generation ?: return
		apply(pointers.move(pointer, index), current)
	}

	fun click(index: Int) {
		move(CLICK_POINTER, index)
		move(CLICK_POINTER, null)
	}

	/** For system cancels, lifecycle changes and coordinate changes; new presses need new touches. */
	fun cancel() = apply(pointers.cancel(), generation)

	private fun apply(changes: List<PadChange>, current: Long?) {
		for (change in changes) {
			val sessionGeneration = if (change.pressed) current!!.also { pressedIn[change.index] = it } else pressedIn[change.index]
			send(change, sessionGeneration)
			pressed(change.index, change.pressed)
		}
	}
}
