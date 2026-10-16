package com.kimjisub.launchpad.midi.driver

/**
 * Any Launchpad running CoreFW (launchpad-core-firmware). It boots into its own Performance mode,
 * which plays the drum-rack layout on channel 1 of its second port and drops everything sent to the
 * first. It ignores Novation's programmer-mode command, and a 9-byte Novation mode SysEx switches it
 * back to Performance mode, so nothing is sent on connect.
 *
 * [ledColour] turns a pack LED code into what the model shows: the code itself on RGB models, the
 * Launchpad S red/green code on the S and the first Mini.
 */
class LaunchpadCoreFW(private val ledColour: (Int) -> Int = { it }) : DrumRackDriver(cable = 1, channel = 0) {

	override fun isButtonPacket(cmd: Int): Boolean = cmd == CABLE_1_NOTE_OFF || cmd == CABLE_1_NOTE_ON

	override fun ledVelocity(velocity: Int): Int = ledColour(velocity)

	companion object {
		/** CoreFW writes this prefix into the USB serial number; product id and name stay the official ones. */
		const val SERIAL_PREFIX = "COREFW-"

		fun isCoreFwSerial(serialNumber: String?): Boolean = serialNumber?.startsWith(SERIAL_PREFIX) == true

		private const val CABLE_1_NOTE_OFF = 0x18
		private const val CABLE_1_NOTE_ON = 0x19
	}
}
