package com.kimjisub.launchpad.midi

/**
 * Reserves the connection before any port is opened, including pending asynchronous opens.
 * USB host controllers may share it with each other; a virtual or computer (USB peripheral)
 * connection holds it alone. A held reservation is never taken away: the later request is refused.
 * A computer connection that fails hands its reservation back before its ports finish closing, so
 * a port stuck on a computer that is not reading can still be busy for the next connection.
 */
internal class MidiTransportGate {
	private enum class Exclusive { VIRTUAL, PERIPHERAL }

	private var usbCount = 0
	private var exclusive: Exclusive? = null

	@Synchronized fun acquireUsb(): AutoCloseable? {
		if (exclusive != null) return null
		usbCount++
		return lease { usbCount-- }
	}

	@Synchronized fun acquireVirtual(): AutoCloseable? = acquireExclusive(Exclusive.VIRTUAL)

	@Synchronized fun acquirePeripheral(): AutoCloseable? = acquireExclusive(Exclusive.PERIPHERAL)

	private fun acquireExclusive(kind: Exclusive): AutoCloseable? {
		if (exclusive != null || usbCount != 0) return null
		exclusive = kind
		return lease { exclusive = null }
	}

	private fun lease(release: () -> Unit): AutoCloseable {
		var closed = false
		return AutoCloseable { synchronized(this) { if (!closed) { closed = true; release() } } }
	}
}
