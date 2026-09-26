package com.kimjisub.launchpad

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A small UniPack written straight into the app's default workspace so UI tests always have a
 * pack to select, play and delete, whatever the emulator already holds.
 *
 * 8x8 square buttons, two chains, LED and autoPlay present (so every play option is shown),
 * and every sound is silence so a run never plays audio through the host speaker.
 */
object TestUniPack {
    const val FOLDER_NAME = "zz_ui_test_pack"
    const val TITLE = "UI Test Pack"
    const val PRODUCER = "UniPad UI Test"
    const val CHAINS = 2

    fun folder(context: Context): File =
        File(File(context.getExternalFilesDir(null), "UniPack"), FOLDER_NAME)

    fun exists(context: Context): Boolean = File(folder(context), "info").isFile

    fun install(context: Context): File {
        val root = folder(context)
        root.deleteRecursively()
        File(root, "sounds").mkdirs()
        File(root, "keyLED").mkdirs()

        File(root, "info").writeText(
            "title=$TITLE\nproducerName=$PRODUCER\nbuttonX=8\nbuttonY=8\nchain=$CHAINS\nsquareButton=true\n"
        )
        File(root, "sounds/silence.wav").writeBytes(silentWav())

        val keySound = StringBuilder()
        for (c in 1..CHAINS) for (x in 1..8) for (y in 1..8) {
            keySound.append("$c $x $y silence.wav\n")
        }
        File(root, "keySound").writeText(keySound.toString())

        File(root, "keyLED/1 1 1 1").writeText("o 1 1 a 5\nd 100\nf 1 1\n")

        File(root, "autoPlay").writeText(autoPlayScript())
        return root
    }

    fun remove(context: Context) {
        folder(context).deleteRecursively()
    }

    /** About 60 s of presses across both chains, long enough for transport checks. */
    private fun autoPlayScript(): String = buildString {
        for (step in 0 until AUTO_PLAY_STEPS) {
            if (step == AUTO_PLAY_STEPS / 2) append("c 2\n")
            val x = step % 8 + 1
            val y = step / 8 % 8 + 1
            append("o $x $y\nd $AUTO_PLAY_DELAY_MS\nf $x $y\nd $AUTO_PLAY_DELAY_MS\n")
        }
    }

    private const val AUTO_PLAY_STEPS = 100
    private const val AUTO_PLAY_DELAY_MS = 300

    /** Length of the pack's AutoPlay: every step presses and releases a pad. */
    const val AUTO_PLAY_MS = AUTO_PLAY_STEPS * AUTO_PLAY_DELAY_MS * 2L

    /** 0.1 s of 8 kHz mono 16-bit silence. */
    private fun silentWav(): ByteArray {
        val sampleRate = 8000
        val dataSize = sampleRate / 10 * 2
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataSize)
        }.array()
    }
}
