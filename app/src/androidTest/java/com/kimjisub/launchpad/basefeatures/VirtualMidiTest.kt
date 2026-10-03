package com.kimjisub.launchpad.basefeatures

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class VirtualMidiTest : PlaybackScreenTest() {
    @Test fun virtualLaunchpadIsDiscoveredPadInputPlaysAndKeyLedSendsPackets() {
        screen.openPlay()
        val packets = CopyOnWriteArrayList<List<Int>>()
        val deviceDriver = LaunchpadS()
        val output = object : DriverRef.OnSendSignalListener {
            override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
                packets += listOf(cmd, sig, note, velocity).map { it.toInt() and 0xff }
            }
            override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
        }
        val connection = screen.onMain {
            MidiConnection.initConnection(Intent(), screen.context.getSystemService(Context.USB_SERVICE) as UsbManager, screen.context)
            MidiConnection.attachTransport("Virtual Launchpad", deviceDriver, output)
        }
        try {
            assertEquals("Virtual Launchpad", MidiConnection.connectedDevice!!.name)
            screen.node(By.text("Virtual Launchpad"))
            packets.clear()
            screen.onMain { deviceDriver.getSignal(9, -112, 0, 127) }
            screen.await("Virtual pad did not request sound") { audio.plays.size == 1 }
            assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.single())
            // Launchpad S encodes keyLED velocity 5 as 2; this checks bytes leaving the app's driver.
            screen.await("keyLED output packet missing") { packets.contains(listOf(9, 144, 0, 2)) }
            screen.onMain { deviceDriver.getSignal(9, -112, 0, 0) }
            screen.await("LED off output missing") { packets.contains(listOf(9, 144, 0, 0)) }
            screen.onMain { deviceDriver.getSignal(9, -112, 24, 127) }
            assertEquals("MIDI chain input did not select chain 2", 1, screen.onMain { screen.vm().chain.value })
            screen.onMain { deviceDriver.getSignal(9, -112, 1, 127) }
            assertEquals(audio.loaded.getValue("silence2.wav"), audio.plays.last())
            screen.onMain { deviceDriver.getSignal(9, -112, 1, 0) }
            screen.capture("virtual-midi")
        } finally {
            screen.onMain { connection.close() }
        }
        assertNull(MidiConnection.connectedDevice)
        screen.node(By.text(screen.text(R.string.midi_disconnected)))
        val before = audio.plays.size
        screen.onMain { deviceDriver.getSignal(9, -112, 0, 127) }
        assertEquals("Detached device still delivers pad input", before, audio.plays.size)
    }
}
