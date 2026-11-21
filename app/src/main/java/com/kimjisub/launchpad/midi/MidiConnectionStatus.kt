package com.kimjisub.launchpad.midi

import android.os.SystemClock

/** Observed facts for one connection. These never select a driver or send MIDI. */
data class MidiConnectionStatus(
	val sessionId: Int? = null,
	val deviceName: String = "",
	val phase: Phase = Phase.NONE,
	val manuallyChosen: Boolean = false,
	val firmware: String? = null,
	val modelUnknown: Boolean = false,
) {
	enum class Phase { NONE, PERMISSION, WAITING, WAITING_LONG, INPUT, DISCONNECTED }

	val connected: Boolean
		get() = phase == Phase.WAITING || phase == Phase.WAITING_LONG || phase == Phase.INPUT

	val initialHelp: Int
		get() = when {
			!connected -> 0
			firmware != null -> 5
			phase == Phase.INPUT -> 2
			else -> 1
		}
}

/** The input path writes only the first accepted pad timestamp; the screen samples it. */
internal class MidiInputObservation(private val connectedAt: Long) {
	@Volatile private var firstPadAt: Long? = null

	fun pad(x: Int, y: Int) {
		if (firstPadAt == null && x in 0..7 && y in 0..7) firstPadAt = SystemClock.elapsedRealtime()
	}

	fun phase(now: Long): MidiConnectionStatus.Phase = when {
		firstPadAt != null -> MidiConnectionStatus.Phase.INPUT
		now - connectedAt >= 20_000 -> MidiConnectionStatus.Phase.WAITING_LONG
		else -> MidiConnectionStatus.Phase.WAITING
	}
}
