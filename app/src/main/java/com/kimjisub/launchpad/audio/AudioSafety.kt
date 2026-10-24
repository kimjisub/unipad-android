package com.kimjisub.launchpad.audio


data class AudioFileFacts(val sampleRate: Int, val channels: Int, val frames: Long, val format: String = "")
enum class AudioExclusion { INVALID_FORMAT, READ_FAILURE, SAMPLE_RATE, CHANNELS, DURATION, SINGLE_LIMIT, PACK_LIMIT }
sealed interface AudioInspection {
    data class Readable(val facts: AudioFileFacts) : AudioInspection
    data class Rejected(val reason: AudioExclusion) : AudioInspection
}
data class AudioCost(val value: Long = 0, val exclusion: AudioExclusion? = null)
object AudioSafety {
    const val SINGLE_LIMIT = 67_108_864L
    const val PACK_LIMIT = 134_217_728L
    fun cost(facts: AudioFileFacts): AudioCost {
        val (rate, channels, frames) = facts
        val reason = when {
            rate !in 8_000..384_000 -> AudioExclusion.SAMPLE_RATE
            channels !in 1..8 -> AudioExclusion.CHANNELS
            frames < 0 -> AudioExclusion.INVALID_FORMAT
            frames > 600L * rate -> AudioExclusion.DURATION
            else -> null
        }
        if (reason != null) return AudioCost(exclusion = reason)
        // Range checks above bound every operand before multiplication. Round without n + r - 1.
        val scaled = if (rate >= 48_000) frames else {
            val numerator = audioMultiply(frames, 48_000)
            numerator / rate + if (numerator % rate == 0L) 0 else 1
        }
        val cost = audioMultiply(scaled, maxOf(channels, 2).toLong())
        return if (cost <= SINGLE_LIMIT) AudioCost(cost) else AudioCost(exclusion = AudioExclusion.SINGLE_LIMIT)
    }
}
/** Coordinates are zero-based; identity is the resolved stored path, not a keySound spelling. */
data class AudioReference(val chain: Int, val x: Int, val y: Int, val line: Int, val identity: String)
data class AudioPackBounds(val chains: Int, val width: Int, val height: Int)
data class AudioSelection(val identity: String, val cost: Long, val exclusion: AudioExclusion?)
/** A reservation is final: a later decoder failure cannot alter this plan. */
data class AudioSelectionPlan(val files: List<AudioSelection>, val reservedCost: Long)
object AudioSelector {
    fun select(references: List<AudioReference>, bounds: AudioPackBounds,
        inspect: (String) -> AudioInspection): AudioSelectionPlan {
        require(bounds.chains >= 0 && bounds.width >= 0 && bounds.height >= 0)
        val ordered = references.asSequence().filter {
            it.chain in 0 until bounds.chains && it.x in 0 until bounds.width && it.y in 0 until bounds.height
        }.sortedWith(compareBy(AudioReference::chain, AudioReference::x, AudioReference::y, AudioReference::line))
        val decisions = linkedMapOf<String, AudioSelection>()
        var reserved = 0L
        for (reference in ordered) {
            val id = reference.identity
            if (id in decisions) continue
            val cost = when (val result = inspect(id)) {
                is AudioInspection.Readable -> AudioSafety.cost(result.facts)
                is AudioInspection.Rejected -> AudioCost(exclusion = result.reason)
            }
            val exclusion = cost.exclusion ?: if (cost.value > AudioSafety.PACK_LIMIT - reserved) AudioExclusion.PACK_LIMIT else null
            val admitted = if (exclusion == null) cost.value else 0
            reserved += admitted // admitted <= remaining budget, checked before addition.
            decisions[id] = AudioSelection(id, admitted, exclusion)
        }
        return AudioSelectionPlan(decisions.values.toList(), reserved)
    }
}
/** Checked nonnegative arithmetic shared by structural readers; never wrap malformed sizes. */
internal fun audioMultiply(a: Long, b: Long): Long {
    audioRequire(a >= 0 && b >= 0 && (b == 0L || a <= Long.MAX_VALUE / b))
    return a * b
}
internal fun audioAdd(a: Long, b: Long): Long {
    audioRequire(a >= 0 && b >= 0 && a <= Long.MAX_VALUE - b)
    return a + b
}
internal class InvalidAudio : Exception()
internal fun audioRequire(condition: Boolean) { if (!condition) throw InvalidAudio() }
