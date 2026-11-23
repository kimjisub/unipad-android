package com.kimjisub.launchpad.midi

data class PadChange(val index: Int, val pressed: Boolean)

/** Screen coordinates are resolved by the caller. Null means outside the pad. */
class PadPointers {
    private val pointers = mutableMapOf<Int, Int>()
    private val owners = IntArray(Mk2Layout.SIZE)
    val pointerCount: Int get() = pointers.size

    fun move(pointer: Int, index: Int?): List<PadChange> {
        if (pointer < 0) return emptyList()
        val next = index?.takeIf { it in owners.indices }
        val old = pointers[pointer]
        if (old == next) return emptyList()
        if (old == null && next != null && pointers.size >= 32) return emptyList()
        return buildList {
            if (old != null) {
                pointers.remove(pointer)
                if (--owners[old] == 0) add(PadChange(old, false))
            }
            if (next != null) {
                pointers[pointer] = next
                if (owners[next]++ == 0) add(PadChange(next, true))
            }
        }
    }

    fun cancel(): List<PadChange> {
        val releases = owners.indices.filter { owners[it] > 0 }.map { PadChange(it, false) }
        pointers.clear()
        owners.fill(0)
        return releases
    }
}
