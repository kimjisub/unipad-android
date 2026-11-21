package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.manager.LaunchpadColor

/** At most one latest color and one dirty flag per position; no output or touch callbacks. */
class PeripheralLights {
    private val colors = IntArray(Mk2Layout.SIZE)
    private val dirty = BooleanArray(Mk2Layout.SIZE)
    @Volatile var lastSignalNanos: Long? = null
        private set

    @Synchronized fun accept(status: Int, number: Int, value: Int): Boolean {
        if (value !in 0..127) return false
        val index = Mk2Layout.index(status, number) ?: return false
        colors[index] = if (status == 0x80) 0 else value
        dirty[index] = true
        lastSignalNanos = System.nanoTime()
        return true
    }

    @Synchronized fun snapshot(): IntArray = colors.copyOf()
    @Synchronized fun argb(index: Int): Long = LaunchpadColor.ARGB[colors[index]]
    @Synchronized fun drainChanges(): Map<Int, Int> = buildMap {
        for (index in dirty.indices) if (dirty[index]) {
            put(index, colors[index])
            dirty[index] = false
        }
    }
    @Synchronized fun clear() {
        colors.fill(0)
        dirty.fill(true)
        lastSignalNanos = null
    }
}
