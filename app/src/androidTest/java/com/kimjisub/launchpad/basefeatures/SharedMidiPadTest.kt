package com.kimjisub.launchpad.basefeatures

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real screen dispatch and virtual Launchpad packets share the same looping pad. */
@RunWith(AndroidJUnit4::class)
class SharedMidiPadTest : HeldPadTest() {
    @Test fun controllerReleaseKeepsScreenHoldUntilTheFingerLifts() = sharedPad(controllerFirst = true)

    @Test fun screenReleaseKeepsControllerHoldUntilItsPadReleases() = sharedPad(controllerFirst = false)

    private fun sharedPad(controllerFirst: Boolean) {
        startHeld()
        val driver = LaunchpadS()
        val packets = java.util.concurrent.CopyOnWriteArrayList<List<Int>>()
        val output = object : DriverRef.OnSendSignalListener {
            override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
                packets += listOf(cmd, sig, note, velocity).map { it.toInt() and 0xff }
            }
            override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
        }
        val connection = screen.onMain {
            MidiConnection.initConnection(Intent(), screen.context.getSystemService(Context.USB_SERVICE) as UsbManager, screen.context)
            MidiConnection.attachTransport("Virtual Launchpad", driver, output)
        }
        val pad = Cell(2, 2)
        // Launchpad S note: row * 16 + column (the driver converts it to the grid coordinate).
        fun midi(down: Boolean) = screen.onMain { driver.getSignal(9, -112, 34, if (down) 127 else 0) }
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"
        val order = if (controllerFirst) "controller-first" else "screen-first"
        fun capture(stage: String) = screen.capture("shared-midi-$label-$order-$stage")
        try {
            val finger = fingers.down(center(pad))
            awaitPlays("Screen press did not play", 1)
            midi(true)
            awaitPlays("Virtual Launchpad press did not play", 2)
            awaitHeld("Both inputs must share one loop", setOf(pad))
            capture("both-held")
            packets.clear()
            if (controllerFirst) midi(false) else fingers.up(finger)
            settle()
            capture("one-released")
            assertHeldStill("Releasing one input must preserve the other", listOf(pad, pad), setOf(pad))
            assertEquals("Partial release must send no LED-off packet for this pad", false,
                packets.contains(listOf(9, 144, 34, 0)))
            if (controllerFirst) fingers.up(finger) else midi(false)
            awaitHeld("Last release must stop the loop and press light", emptySet())
            assertHeldStill("Both inputs released", listOf(pad, pad), emptySet())
            screen.await("Last release did not send the controller LED-off packet") {
                packets.contains(listOf(9, 144, 34, 0))
            }
            capture("both-released")
        } finally {
            fingers.cancelRemaining()
            screen.onMain { connection.close() }
        }
    }
}
