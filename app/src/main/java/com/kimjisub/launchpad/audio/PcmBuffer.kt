package com.kimjisub.launchpad.audio

import java.nio.ShortBuffer

/** Interleaved PCM storage. Observations name arrays that coexist during an allocation. */
internal class PcmBuffer(private val initialCapacity: Int, private val onAllocation: ((Int, Int) -> Unit)? = null) {
    var pcm: ShortArray? = null
        private set
    var sampleCount = 0
        private set

    fun append(source: ShortBuffer) {
        val count = source.remaining()
        if (count == 0) return
        val needed = sampleCount.toLong() + count
        if (needed > Int.MAX_VALUE - 8L) throw OutOfMemoryError("Decoded PCM exceeds array capacity")
        var target = pcm ?: run {
            onAllocation?.invoke(0, initialCapacity)
            ShortArray(initialCapacity)
        }
        if (needed > target.size) {
            val capacity = maxOf(target.size.toLong() * 2, needed).coerceAtMost(Int.MAX_VALUE - 8L).toInt()
            onAllocation?.invoke(target.size, capacity)
            target = target.copyOf(capacity)
        }
        source.get(target, sampleCount, count)
        pcm = target
        sampleCount += count
    }
}
