package com.kimjisub.launchpad.midi

/** USB hosts may share one mode (two controllers), but different modes never steal a lease. */
internal object MidiModeOwnership {
    enum class Mode { USB_HOST, VIRTUAL, USB_PERIPHERAL }
    private val leases = mutableSetOf<Lease>()
    @Synchronized fun acquire(mode: Mode): Lease? {
        if (leases.isNotEmpty() && (mode != Mode.USB_HOST || leases.first().mode != mode)) return null
        return Lease(mode).also { leases.add(it) }
    }
    class Lease internal constructor(val mode: Mode) : AutoCloseable {
        override fun close() { synchronized(MidiModeOwnership) { leases.remove(this) } }
    }
}
