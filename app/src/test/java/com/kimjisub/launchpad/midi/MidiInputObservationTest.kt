package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.MidiConnectionStatus.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiInputObservationTest {
    @Test fun idleRemainsNeutralAndFirstPadLatchesUntilReconnect() {
        val input = MidiInputObservation(100)
        assertEquals(Phase.WAITING, input.phase(20_099))
        assertEquals(Phase.WAITING_LONG, input.phase(20_100))
        input.pad(3, 4)
        assertEquals(Phase.INPUT, input.phase(90_000))
        assertEquals(Phase.WAITING, MidiInputObservation(90_000).phase(90_001))
    }

    @Test fun outOfGridInputsDoNotConfirmAPad() {
        val input = MidiInputObservation(0)
        input.pad(-1, 3)
        input.pad(8, 3)
        input.pad(3, 8)
        assertEquals(Phase.WAITING, input.phase(5))
    }

    @Test fun devicesDoNotShareInputAndHelpUsesObservedFirmware() {
        val first = MidiInputObservation(0)
        val second = MidiInputObservation(0)
        first.pad(0, 0)
        assertEquals(Phase.INPUT, first.phase(2))
        assertEquals(Phase.WAITING, second.phase(2))
        assertEquals(0, MidiConnectionStatus().initialHelp)
        assertEquals(0, MidiConnectionStatus(phase = Phase.PERMISSION).initialHelp)
        assertEquals(0, MidiConnectionStatus(phase = Phase.DISCONNECTED).initialHelp)
        assertEquals(1, MidiConnectionStatus(phase = Phase.WAITING, manuallyChosen = true).initialHelp)
        assertEquals(2, MidiConnectionStatus(phase = Phase.INPUT).initialHelp)
        assertEquals(5, MidiConnectionStatus(phase = Phase.INPUT, firmware = "CoreFW").initialHelp)
    }
}
