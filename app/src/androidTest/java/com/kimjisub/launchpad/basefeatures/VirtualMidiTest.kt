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
    @Test fun virtualLaunchpadReconnectsWhilePlayingAndRestoresInputAndLights() {
        screen.openPlay()
        val activity = screen.resumed()
        val vm = screen.vm()
        val first = VirtualLaunchpad(screen)
        try {
            screen.node(By.text("Virtual Launchpad"))
            screen.onMain { first.driver.getSignal(9, -112, 0, 127) }
            screen.await("First connection did not request sound") { audio.plays.size == 1 }
            screen.onMain { first.driver.getSignal(9, -112, 0, 0) }
        } finally {
            first.close()
        }
        assertNull(MidiConnection.connectedDevice)
        screen.node(By.text(screen.text(R.string.midi_disconnected)))
        screen.capture("virtual-midi-detached-while-playing")
        val before = audio.plays.size
        screen.onMain { first.driver.getSignal(9, -112, 0, 127) }
        assertEquals("Detached device still delivers input", before, audio.plays.size)
        val second = VirtualLaunchpad(screen)
        try {
            screen.node(By.text("Virtual Launchpad"))
            assertSame("Reconnecting replaced the play activity", activity, screen.resumed())
            assertSame("Reconnecting replaced the loaded pack", vm, screen.vm())
            second.packets.clear()
            screen.onMain { second.driver.getSignal(9, -112, 0, 127) }
            screen.await("Reconnected pad did not request sound") { audio.plays.size == before + 1 }
            assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.last())
            screen.await("Reconnected keyLED on packet missing") { second.packets.contains(listOf(9, 144, 0, 2)) }
            screen.onMain { second.driver.getSignal(9, -112, 0, 0) }
            screen.await("Reconnected keyLED off packet missing") { second.packets.contains(listOf(9, 144, 0, 0)) }
            val after = audio.plays.size
            screen.onMain { first.driver.getSignal(9, -112, 0, 127) }
            assertEquals("Old device delivers input after reconnection", after, audio.plays.size)
            screen.capture("virtual-midi-reconnected-while-playing")
        } finally {
            second.close()
        }
        assertNull(MidiConnection.connectedDevice)
        screen.node(By.text(screen.text(R.string.midi_disconnected)))
    }

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
