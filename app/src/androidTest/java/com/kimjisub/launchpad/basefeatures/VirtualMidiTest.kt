package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.midi.MidiConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualMidiTest : PlaybackScreenTest() {
    @Test fun virtualLaunchpadIsDiscoveredPadInputPlaysAndKeyLedSendsPackets() {
        screen.openPlay()
        val launchpad = VirtualLaunchpad(screen)
        val packets = launchpad.packets
        try {
            assertEquals("Virtual Launchpad", MidiConnection.connectedDevice!!.name)
            screen.node(By.text("Virtual Launchpad"))
            packets.clear()
            screen.onMain { launchpad.driver.getSignal(9, -112, 0, 127) }
            screen.await("Virtual pad did not request sound") { audio.plays.size == 1 }
            assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.single())
            // Launchpad S encodes keyLED velocity 5 as 2; this checks bytes leaving the app's driver.
            screen.await("keyLED output packet missing") { packets.contains(listOf(9, 144, 0, 2)) }
            screen.onMain { launchpad.driver.getSignal(9, -112, 0, 0) }
            screen.await("LED off output missing") { packets.contains(listOf(9, 144, 0, 0)) }
            screen.onMain { launchpad.driver.getSignal(9, -112, 24, 127) }
            assertEquals("MIDI chain input did not select chain 2", 1, screen.onMain { screen.vm().chain.value })
            screen.onMain { launchpad.driver.getSignal(9, -112, 1, 127) }
            assertEquals(audio.loaded.getValue("silence2.wav"), audio.plays.last())
            screen.onMain { launchpad.driver.getSignal(9, -112, 1, 0) }
            screen.capture("virtual-midi")
        } finally {
            launchpad.close()
        }
        assertNull(MidiConnection.connectedDevice)
        screen.node(By.text(screen.text(R.string.midi_disconnected)))
        val before = audio.plays.size
        screen.onMain { launchpad.driver.getSignal(9, -112, 0, 127) }
        assertEquals("Detached device still delivers pad input", before, audio.plays.size)
    }
}
