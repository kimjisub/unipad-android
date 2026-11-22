package com.kimjisub.launchpad.midi

internal data class VirtualMidiPort(val number: Int, val name: String)
internal data class VirtualMidiDevice(
	val id: Int,
	val name: String,
	val product: String,
	val provider: String,
	val performancePorts: List<VirtualMidiPort>,
	val lightPorts: List<VirtualMidiPort>,
) {
	val recommendsMatrix: Boolean get() = listOf(name, product).any {
		it.contains("mystrix", ignoreCase = true) || it.contains("matrix", ignoreCase = true)
	}
}
internal data class VirtualMidiSelection(val device: VirtualMidiDevice, val performancePort: Int, val lightPort: Int?)

/** All blocking calls run on the connection's IO dispatcher. No driver or pad layout lives here. */
internal interface VirtualMidiBackend {
	suspend fun devices(): List<VirtualMidiDevice>
	suspend fun open(id: Int): Device?
	fun watchRemoval(callback: (Int) -> Unit): AutoCloseable
	interface Device : AutoCloseable {
		/** False once the device has closed; null while that is unknown. */
		suspend fun isAlive(): Boolean? = true
		fun openPerformancePort(number: Int, receiver: (ByteArray, Int, Int) -> Unit): Port?
		fun openLightPort(number: Int): Port?
	}
	interface Port : AutoCloseable {
		fun send(bytes: ByteArray)
	}
}
