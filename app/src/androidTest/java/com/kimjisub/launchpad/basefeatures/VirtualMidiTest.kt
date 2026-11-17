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
        val ledOn = listOf(9, 144, 0, 2)
        val ledOff = listOf(9, 144, 0, 0)
        screen.openPlay()
        val activity = screen.resumed()
        val vm = screen.vm()
        val first = VirtualLaunchpad(screen)
        try {
            screen.node(By.text("Virtual Launchpad"))
            screen.onMain {
                first.packets.clear()
                first.driver.getSignal(9, -112, 0, 127)
            }
            screen.await("First connection did not request sound") { audio.plays.size == 1 }
            screen.await("First connection keyLED on packet missing") { first.packets.contains(ledOn) }
            val firstOn = first.packets.indexOf(ledOn)
            screen.onMain { first.driver.getSignal(9, -112, 0, 0) }
            // Finish this animation before attaching another output, so its delayed off cannot
            // satisfy the reconnected pad's off check.
            screen.await("First connection keyLED off after on missing") { first.packets.drop(firstOn + 1).contains(ledOff) }
            println("First connection keyLED cycle before detach: ${first.packets}")
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
            screen.onMain {
                second.packets.clear()
                second.driver.getSignal(9, -112, 0, 127)
            }
            screen.await("Reconnected pad did not request sound") { audio.plays.size == before + 1 }
            assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.last())
            screen.await("Reconnected keyLED on packet missing") { second.packets.contains(ledOn) }
            val onIndex = second.packets.indexOf(ledOn)
            screen.onMain { second.driver.getSignal(9, -112, 0, 0) }
            screen.await("Reconnected keyLED off after on missing") { second.packets.drop(onIndex + 1).contains(ledOff) }
            println("Reconnected keyLED on at $onIndex, later packets: ${second.packets.drop(onIndex + 1)}")
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
