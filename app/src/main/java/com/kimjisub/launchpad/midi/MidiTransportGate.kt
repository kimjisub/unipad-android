package com.kimjisub.launchpad.midi

/** Reserves the connection before any port is opened, including pending asynchronous opens. */
internal class MidiTransportGate {
	private var usbCount = 0
	private var virtual = false

	@Synchronized fun acquireUsb(): AutoCloseable? {
		if (virtual) return null
		usbCount++
		return lease { usbCount-- }
	}

	@Synchronized fun acquireVirtual(): AutoCloseable? {
		if (virtual || usbCount != 0) return null
		virtual = true
		return lease { virtual = false }
	}

	private fun lease(release: () -> Unit): AutoCloseable {
		var closed = false
		return AutoCloseable { synchronized(this) { if (!closed) { closed = true; release() } } }
	}
}
