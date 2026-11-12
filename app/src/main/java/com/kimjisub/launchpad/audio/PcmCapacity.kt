package com.kimjisub.launchpad.audio

private const val FALLBACK_PCM_CAPACITY = 16_384

/** PCM capacity from admitted structure, including container timing and whole AAC packet bounds. */
internal fun initialPcmCapacity(sampleRate: Int, channels: Int, inspection: AudioInspection, fullPacketFrames: Long? = null): Int {
    val facts = (inspection as? AudioInspection.Readable)?.facts ?: return FALLBACK_PCM_CAPACITY
    if (facts.frames <= 0 || facts.sampleRate != sampleRate || facts.channels != channels ||
        AudioSafety.cost(facts).exclusion != null) return FALLBACK_PCM_CAPACITY

    // Ogg Opus granules trim the last packet; some decoders emit its untrimmed samples.
    // Preserve the old allocation sequence instead of growing an almost-full large array.
    if (facts.format == "opus") return FALLBACK_PCM_CAPACITY

    // AAC's admission timeline can trim the last packet; old decoders keep it whole.
    // Counts come from validated packet extents and codec configuration, not player duration.
    val frames = maxOf(facts.frames, fullPacketFrames ?: facts.frames)
    if (frames > AudioSafety.SINGLE_LIMIT / channels) return FALLBACK_PCM_CAPACITY
    return (frames * channels).toInt()
}
