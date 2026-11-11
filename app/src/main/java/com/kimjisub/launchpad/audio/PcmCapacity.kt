package com.kimjisub.launchpad.audio

private const val FALLBACK_PCM_CAPACITY = 16_384

/** Samples backed by structural inspection and admitted by the audio budget, never player duration. */
internal fun initialPcmCapacity(sampleRate: Int, channels: Int, inspection: AudioInspection): Int {
    val facts = (inspection as? AudioInspection.Readable)?.facts ?: return FALLBACK_PCM_CAPACITY
    if (facts.frames <= 0 || facts.sampleRate != sampleRate || facts.channels != channels ||
        AudioSafety.cost(facts).exclusion != null) return FALLBACK_PCM_CAPACITY

    // Ogg Opus granules trim the last packet; some decoders emit its untrimmed samples.
    // Preserve the old allocation sequence instead of growing an almost-full large array.
    if (facts.format == "opus") return FALLBACK_PCM_CAPACITY

    // Physical MPEG frames include padding the decoder can omit. Using this bound also
    // handles short or missing duration hints without repeated copies of a long MP3.
    // Admission bounds every operand before multiplication and the conversion to Int.
    return (facts.frames * channels).toInt()
}
