package com.kimjisub.launchpad.audio

/**
 * MediaCodec can retain the final Ogg Opus packet's padding after applying pre-skip.
 * Only trust the inspected granule at its native 48 kHz rate and within one maximum
 * Opus packet (120 ms). Other rates and large disagreements keep the decoder output.
 * The inspector uses "opus" only for Ogg; Opus in MP4 has format "m4a".
 */
internal fun oggOpusEndFrames(decodedFrames: Int, sampleRate: Int, inspection: AudioInspection,
    onExcessPadding: (Long) -> Unit = {}): Int {
    val facts = (inspection as? AudioInspection.Readable)?.facts ?: return decodedFrames
    if (facts.format != "opus" || facts.sampleRate != 48_000 || sampleRate != 48_000 || facts.frames < 0) {
        return decodedFrames
    }
    val padding = decodedFrames.toLong() - facts.frames
    if (padding <= 0) return decodedFrames
    if (padding > 5_760) {
        onExcessPadding(padding)
        return decodedFrames
    }
    // facts.frames is nonnegative and smaller than the Int-sized decoded output here.
    return facts.frames.toInt()
}
