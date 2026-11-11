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

    // A short duration still needs growth. Start on the existing doubling grid rather
    // than an arbitrary size, so old + new arrays never exceed the previous growth peak.
    val frames = (durationUs * sampleRate + 999_999) / 1_000_000
    val estimate = (frames * channels).toInt()
    return maxOf(MIN_PCM_CAPACITY, Integer.highestOneBit(estimate))
}
