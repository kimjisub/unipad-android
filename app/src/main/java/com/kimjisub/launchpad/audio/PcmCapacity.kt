package com.kimjisub.launchpad.audio

private const val MIN_PCM_CAPACITY = 16_384

/**
 * Initial interleaved 16-bit PCM capacity, in samples. Advertised duration can only
 * reserve samples backed by structural inspection and admitted by the audio budget.
 */
internal fun initialPcmCapacity(durationUs: Long, sampleRate: Int, channels: Int,
    inspection: AudioInspection): Int {
    val facts = (inspection as? AudioInspection.Readable)?.facts ?: return MIN_PCM_CAPACITY
    if (durationUs <= 0 || facts.frames <= 0 || facts.sampleRate != sampleRate ||
        facts.channels != channels || AudioSafety.cost(facts).exclusion != null) return MIN_PCM_CAPACITY

    // Admission bounds frames, rate and channels before any multiplication. Compare first
    // so even Long.MAX_VALUE in the duration field never overflows or enlarges this bound.
    val physicalSamples = (facts.frames * channels).toInt()
    if (durationUs >= facts.frames * 1_000_000 / sampleRate) return physicalSamples

    // An understated duration still needs growth. Keep the exact old starting size:
    // even power-of-two estimates can increase the peak when a decoder emits large packets.
    return MIN_PCM_CAPACITY
}
