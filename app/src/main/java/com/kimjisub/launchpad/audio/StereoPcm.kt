package com.kimjisub.launchpad.audio

import android.media.AudioFormat
import android.media.MediaFormat
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val supportedOutputSpeakers = AudioFormat.CHANNEL_OUT_FRONT_LEFT or
    AudioFormat.CHANNEL_OUT_FRONT_RIGHT or AudioFormat.CHANNEL_OUT_FRONT_CENTER or
    AudioFormat.CHANNEL_OUT_LOW_FREQUENCY or AudioFormat.CHANNEL_OUT_BACK_LEFT or
    AudioFormat.CHANNEL_OUT_BACK_RIGHT or AudioFormat.CHANNEL_OUT_BACK_CENTER or
    AudioFormat.CHANNEL_OUT_SIDE_LEFT or AudioFormat.CHANNEL_OUT_SIDE_RIGHT

/** Codec2 AAC uses Java output masks; some PCM decoders expose native speaker bits. */
internal fun decoderChannelMask(channels: Int, channelMask: Int): Int {
    val candidates = if (channelMask and 3 == 0) intArrayOf(channelMask, channelMask shl 2)
        else intArrayOf(channelMask shl 2, channelMask)
    return candidates.firstOrNull { mask ->
        Integer.bitCount(mask) == channels && mask and supportedOutputSpeakers.inv() == 0 &&
            mask and AudioFormat.CHANNEL_OUT_STEREO == AudioFormat.CHANNEL_OUT_STEREO
    } ?: 0 // A missing or invalid position mask uses the channel count's canonical layout.
}

/**
 * Converts interleaved decoder output before the native mono/stereo mixer reads it.
 * Quad and 5.1 use the Web Audio speaker down-mix, with saturation for 16-bit PCM.
 * Missing position masks use Android's canonical layouts for three to eight channels.
 * Unrecognized layouts use Web Audio's discrete interpretation (the first two channels).
 *
 * https://www.w3.org/TR/webaudio-1.0/#down-mixing
 * https://developer.android.com/reference/android/media/AudioFormat#channelMask
 */
internal fun stereoPcm(pcm: ShortArray, channels: Int, channelMask: Int = 0, sampleCount: Int = pcm.size): ShortArray {
    require(channels > 0 && sampleCount in 0..pcm.size)
    if (channels <= 2) return pcm
    require(sampleCount % channels == 0)
    val fl = AudioFormat.CHANNEL_OUT_FRONT_LEFT
    val fr = AudioFormat.CHANNEL_OUT_FRONT_RIGHT
    val fc = AudioFormat.CHANNEL_OUT_FRONT_CENTER
    val bl = AudioFormat.CHANNEL_OUT_BACK_LEFT
    val br = AudioFormat.CHANNEL_OUT_BACK_RIGHT
    val bc = AudioFormat.CHANNEL_OUT_BACK_CENTER
    val sl = AudioFormat.CHANNEL_OUT_SIDE_LEFT
    val sr = AudioFormat.CHANNEL_OUT_SIDE_RIGHT
    val mask = if (channelMask != 0) channelMask else when (channels) {
        3 -> fl or fr or fc
        4 -> AudioFormat.CHANNEL_OUT_QUAD
        5 -> AudioFormat.CHANNEL_OUT_QUAD or fc
        6 -> AudioFormat.CHANNEL_OUT_5POINT1
        7 -> AudioFormat.CHANNEL_OUT_5POINT1 or bc
        8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        else -> 0
    }
    val speakers = (0 until Int.SIZE_BITS).map { 1 shl it }.filter { mask and it != 0 }
    val positioned = speakers.size == channels && mask and supportedOutputSpeakers.inv() == 0
    val quad = positioned && (mask == AudioFormat.CHANNEL_OUT_QUAD || mask == (fl or fr or sl or sr))
    val left = DoubleArray(channels)
    val right = DoubleArray(channels)
    val surround = sqrt(0.5)
    if (positioned) {
        for ((i, speaker) in speakers.withIndex()) {
            when (speaker) {
                fl -> left[i] = if (quad) 0.5 else 1.0
                fr -> right[i] = if (quad) 0.5 else 1.0
                fc -> { left[i] = surround; right[i] = surround }
                bl, sl -> left[i] = if (quad) 0.5 else surround
                br, sr -> right[i] = if (quad) 0.5 else surround
                bc -> { left[i] = 0.5; right[i] = 0.5 }
                // LFE is omitted from the stereo speaker down-mix.
            }
        }
    } else {
        left[0] = 1.0
        right[1] = 1.0
    }
    return ShortArray(sampleCount / channels * 2).also { output ->
        for (frame in 0 until sampleCount / channels) {
            var l = 0.0
            var r = 0.0
            for (channel in 0 until channels) {
                val sample = pcm[frame * channels + channel]
                l += sample * left[channel]
                r += sample * right[channel]
            }
            output[frame * 2] = l.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            output[frame * 2 + 1] = r.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

/**
 * Opus streams of three to eight channels (mapping family 1) carry the Vorbis speaker order
 * (RFC 7845 section 5.1.1.2): left, center, right, ..., LFE last. Google's software decoders
 * (Codec2 `c2.android.opus.decoder`, OMX `OMX.google.opus.decoder`) output that order unchanged.
 * Any channel mask on their output is only copied from the container: Android 10's Ogg extractor
 * declares Android's 5.1 mask for Vorbis-ordered PCM. A vendor decoder may already reorder to
 * Android's layout, with or without a mask, so its output is left alone.
 */
internal fun decoderEmitsVorbisOrder(mime: String, decoderName: String, channels: Int): Boolean =
    mime == MediaFormat.MIMETYPE_AUDIO_OPUS && channels in 3..8 &&
        (decoderName.startsWith("c2.android.") || decoderName.startsWith("OMX.google."))

private val vorbisSpeakers = run {
    val fl = AudioFormat.CHANNEL_OUT_FRONT_LEFT
    val fr = AudioFormat.CHANNEL_OUT_FRONT_RIGHT
    val fc = AudioFormat.CHANNEL_OUT_FRONT_CENTER
    val lfe = AudioFormat.CHANNEL_OUT_LOW_FREQUENCY
    val bl = AudioFormat.CHANNEL_OUT_BACK_LEFT
    val br = AudioFormat.CHANNEL_OUT_BACK_RIGHT
    val bc = AudioFormat.CHANNEL_OUT_BACK_CENTER
    val sl = AudioFormat.CHANNEL_OUT_SIDE_LEFT
    val sr = AudioFormat.CHANNEL_OUT_SIDE_RIGHT
    arrayOf(
        intArrayOf(fl, fc, fr),
        intArrayOf(fl, fr, bl, br),
        intArrayOf(fl, fc, fr, bl, br),
        intArrayOf(fl, fc, fr, bl, br, lfe),
        intArrayOf(fl, fc, fr, sl, sr, bc, lfe),
        intArrayOf(fl, fc, fr, sl, sr, bl, br, lfe),
    )
}

/**
 * Reorders Vorbis-order frames in place into ascending speaker-bit order, the order [stereoPcm]
 * reads a channel mask in, and returns that mask.
 */
internal fun vorbisOrderToChannelMask(pcm: ShortArray, channels: Int, sampleCount: Int = pcm.size): Int {
    require(channels in 3..8 && sampleCount in 0..pcm.size && sampleCount % channels == 0)
    val speakers = vorbisSpeakers[channels - 3]
    val source = speakers.indices.sortedBy { speakers[it] }
    val frame = ShortArray(channels)
    for (start in 0 until sampleCount step channels) {
        pcm.copyInto(frame, 0, start, start + channels)
        for (k in 0 until channels) pcm[start + k] = frame[source[k]]
    }
    return speakers.fold(0, Int::or)
}
