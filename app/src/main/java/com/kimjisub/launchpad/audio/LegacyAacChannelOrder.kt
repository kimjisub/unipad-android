package com.kimjisub.launchpad.audio

import android.media.AudioFormat
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Android 7/8's FDK decoder applies the standard four-channel table {2, 0, 1, 3}
 * even to a nonstandard quad PCE. Its PCM is FR, BL, FL, BR, without a position mask.
 * Android 9 changed the PCE mapping; Codec2 and vendor decoders are left alone.
 * Only AAC-LC with one front pair and one back pair is recognized here.
 *
 * https://android.googlesource.com/platform/external/aac/+/android-8.1.0_r1/libAACdec/src/aacdecoder.cpp#1676
 * https://android.googlesource.com/platform/external/aac/+/android-9.0.0_r1/libAACdec/src/aacdecoder.cpp#3098
 */
internal fun decoderEmitsLegacyQuadAacOrder(
    mime: String, decoderName: String, channels: Int, sdkInt: Int, config: ByteBuffer?,
): Boolean = mime == MediaFormat.MIMETYPE_AUDIO_AAC &&
    decoderName == "OMX.google.aac.decoder" && channels == 4 && sdkInt in 24..27 &&
    hasQuadAacPce(config)

private fun hasQuadAacPce(config: ByteBuffer?): Boolean {
    if (config == null) return false
    val start = config.position()
    val bits = AudioBits(config.remaining().toLong() * 8, false) { config.get(start + it.toInt()).toInt() and 255 }
    return try {
        if (bits.read(5) != 2) return false // AAC-LC only, not HE-AAC or an extended object type.
        val frequency = bits.read(4)
        if (frequency == 15) bits.skip(24) else if (frequency >= AAC_SAMPLE_RATES.size) return false
        if (bits.read(4) != 0) return false // Standard channel configurations do not have this order.
        bits.skip(1) // frameLengthFlag
        if (bits.read(1) != 0) bits.skip(14) // dependsOnCoreCoder, coreCoderDelay
        bits.skip(1) // extensionFlag
        bits.skip(4) // PCE element_instance_tag
        if (bits.read(2) != 1) return false // PCE also declares AAC-LC.
        if (bits.read(4) >= AAC_SAMPLE_RATES.size) return false
        if (bits.read(4) != 1 || bits.read(4) != 0 || bits.read(4) != 1 ||
            bits.read(2) != 0 || bits.read(3) != 0 || bits.read(4) != 0) return false
        // Optional down-mix metadata precedes the front and back channel elements.
        if (bits.read(1) != 0) bits.skip(4)
        if (bits.read(1) != 0) bits.skip(4)
        if (bits.read(1) != 0) bits.skip(3)
        if (bits.read(1) != 1) return false
        val frontTag = bits.read(4)
        if (bits.read(1) != 1 || bits.read(4) == frontTag) return false
        bits.skip((8 - bits.position % 8) % 8)
        bits.skip(bits.read(8).toLong() * 8) // Require the whole PCE, including its comment.
        true
    } catch (_: InvalidAudio) {
        false // Missing or truncated configuration never opts a decoder into reordering.
    }
}

/** Undo the old FDK four-channel mapping in place, keeping any unused buffer tail intact. */
internal fun legacyQuadAacOrderToChannelMask(pcm: ShortArray, sampleCount: Int = pcm.size): Int {
    require(sampleCount in 0..pcm.size && sampleCount % 4 == 0)
    for (start in 0 until sampleCount step 4) {
        val right = pcm[start]
        val rearLeft = pcm[start + 1]
        pcm[start] = pcm[start + 2]
        pcm[start + 1] = right
        pcm[start + 2] = rearLeft
    }
    return AudioFormat.CHANNEL_OUT_QUAD
}
