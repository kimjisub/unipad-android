package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.midi.Mk2Layout
import com.kimjisub.launchpad.midi.PadChange
import com.kimjisub.launchpad.midi.PeripheralDevice
import com.kimjisub.launchpad.midi.UsbPeripheralConnection.Failure
import com.kimjisub.launchpad.midi.UsbPeripheralConnection.State
import com.kimjisub.launchpad.ui.compose.PadGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerPadRulesTest {
	private val port = PeripheralDevice.Port(0, "port")
	private val device = PeripheralDevice(1, "phone", listOf(port), listOf(port))
	private val base = ComputerPadRules.Inputs(
		midiSupported = true, state = State.Idle, devices = listOf(device), discoveryFailure = null,
		portsClosing = false, lightsRequested = true, withoutLights = false, paused = false,
		dismissedAttempt = null, lastLightNanos = null, nowNanos = 10_000_000_000L,
	)
	private fun phase(change: ComputerPadRules.Inputs.() -> ComputerPadRules.Inputs = { this }) = ComputerPadRules.phase(base.change())

	@Test fun withoutMidiOnlyTheReasonIsShownWhateverTheConnectionSays() {
		assertEquals(ComputerPadPhase.Unsupported, phase { copy(midiSupported = false, state = State.Open(true, 3)) })
	}

	@Test fun nothingFoundShowsTheSearchStateAndAListingFailureKeepsItsReason() {
		assertEquals(ComputerPadPhase.NotFound(null), phase { copy(devices = emptyList()) })
		assertEquals(ComputerPadPhase.NotFound(Failure.DISCOVERY), phase { copy(devices = emptyList(), discoveryFailure = Failure.DISCOVERY) })
		assertEquals(ComputerPadPhase.Ready(listOf(device), false), phase())
	}

	@Test fun connectingAndClosingBlockOtherChoices() {
		assertEquals(ComputerPadPhase.Connecting, phase { copy(state = State.Connecting, paused = true) })
		assertEquals(ComputerPadPhase.Closing, phase { copy(state = State.Closing) })
		assertEquals(ComputerPadPhase.Ready(listOf(device), true), phase { copy(portsClosing = true) })
	}

	@Test fun anOpenPortIsNotReportedAsReceivedUntilAValidLightArrivesAndOnlyForThreeSeconds() {
		val now = base.nowNanos
		assertEquals(ComputerPadPhase.Open(4, ComputerPadPhase.Lights.NO_SIGNAL_YET), phase { copy(state = State.Open(true, 4)) })
		assertEquals(ComputerPadPhase.Open(4, ComputerPadPhase.Lights.RECENT),
			phase { copy(state = State.Open(true, 4), lastLightNanos = now - 2_999_000_000L) })
		// A quiet computer is not a lost one: the port stays open, only the recent label goes.
		assertEquals(ComputerPadPhase.Open(4, ComputerPadPhase.Lights.NO_SIGNAL_YET),
			phase { copy(state = State.Open(true, 4), lastLightNanos = now - 3_000_000_000L) })
	}

	@Test fun onlyTheLightsPortFailingAsksBeforeUseAndCanContinueWithoutLights() {
		assertEquals(ComputerPadPhase.LightsUnavailable, phase { copy(state = State.Open(false, 5)) })
		assertEquals(ComputerPadPhase.Open(5, ComputerPadPhase.Lights.NOT_RECEIVING),
			phase { copy(state = State.Open(false, 5), withoutLights = true) })
		assertEquals(ComputerPadPhase.Open(5, ComputerPadPhase.Lights.NOT_RECEIVING),
			phase { copy(state = State.Open(false, 5), lightsRequested = false) })
	}

	@Test fun aFailureStaysUntilTheUserActsAndARepeatedOneShowsAgain() {
		assertEquals(ComputerPadPhase.Failed(Failure.TIMEOUT, 7), phase { copy(state = State.Failed(Failure.TIMEOUT, 7)) })
		assertEquals(ComputerPadPhase.Ready(listOf(device), false), phase { copy(state = State.Failed(Failure.TIMEOUT, 7), dismissedAttempt = 7) })
		assertEquals(ComputerPadPhase.Failed(Failure.TIMEOUT, 8), phase { copy(state = State.Failed(Failure.TIMEOUT, 8), dismissedAttempt = 7) })
	}

	@Test fun returningAfterLeavingShowsTheStoppedStateInsteadOfReconnecting() {
		assertEquals(ComputerPadPhase.Paused, phase { copy(paused = true) })
		assertEquals(ComputerPadPhase.Paused, phase { copy(paused = true, state = State.Failed(Failure.RELEASE, 2)) })
		assertTrue(ComputerPadRules.pausesOnLeave(ComputerPadPhase.Open(1, ComputerPadPhase.Lights.RECENT)))
		assertTrue(ComputerPadRules.pausesOnLeave(ComputerPadPhase.Connecting))
		assertFalse(ComputerPadRules.pausesOnLeave(ComputerPadPhase.NotFound(null)))
	}

	@Test fun everyFailureHasItsOwnSentenceAndConnectionsThatMayHaveSoundedAskToStopTheSound() {
		assertEquals(Failure.entries.size, Failure.entries.map(::failureText).toSet().size)
		val disconnected = Failure.entries.filter { ComputerPadRules.kind(it) == ComputerPadFailureKind.DISCONNECTED }.toSet()
		assertEquals(setOf(Failure.SEND, Failure.RELEASE, Failure.CLOSE, Failure.OVERFLOW, Failure.REMOVED), disconnected)
		assertEquals(ComputerPadFailureKind.OTHER_CONNECTION, ComputerPadRules.kind(Failure.BUSY))
	}

	@Test fun gridCellsMatchTheLayoutOrderAndTheCornerIsEmpty() {
		val side = 900f
		val pitch = side / 9
		assertNull(PadGridGeometry.indexAt(8.5f * pitch, 0.5f * pitch, side))
		assertEquals(64, PadGridGeometry.indexAt(0.5f * pitch, 0.5f * pitch, side))
		assertEquals(0, PadGridGeometry.indexAt(0.5f * pitch, 1.5f * pitch, side))
		assertEquals(63, PadGridGeometry.indexAt(7.5f * pitch, 8.5f * pitch, side))
		assertEquals(72, PadGridGeometry.indexAt(8.5f * pitch, 1.5f * pitch, side))
		assertNull(PadGridGeometry.indexAt(-1f, 10f, side))
		assertNull(PadGridGeometry.indexAt(10f, side, side))
		for (index in 0 until Mk2Layout.SIZE) {
			val (row, column) = PadGridGeometry.position(index)
			assertEquals(index, PadGridGeometry.index(row, column))
		}
		assertEquals(81, Mk2Layout.address(0)!!.number)
		assertEquals(18, Mk2Layout.address(63)!!.number)
		assertEquals(89, Mk2Layout.address(72)!!.number)
		assertEquals(104, Mk2Layout.address(64)!!.number)
	}
}

class ComputerPadInputTest {
	private val sent = mutableListOf<Pair<PadChange, Long>>()
	private val held = BooleanArray(Mk2Layout.SIZE)
	private val input = ComputerPadInput({ change, generation -> sent.add(change to generation); true }) { i, down -> held[i] = down }

	@Test fun nothingIsSentWhileInputIsLocked() {
		input.move(0, 5)
		input.click(5)
		assertTrue(sent.isEmpty())
	}

	@Test fun twoFingersOnOnePadPressOnceAndReleaseWithTheLastFinger() {
		input.accept(1)
		input.move(0, 5)
		input.move(1, 5)
		input.move(0, null)
		assertEquals(listOf(PadChange(5, true) to 1L), sent)
		assertTrue(held[5])
		input.move(1, null)
		assertEquals(PadChange(5, false) to 1L, sent.last())
		assertFalse(held[5])
	}

	@Test fun draggingReleasesThePreviousPadBeforePressingTheNextAndLeavingTheGridReleases() {
		input.accept(1)
		input.move(0, 5)
		input.move(1, 9)
		input.move(0, 6)
		input.move(0, null)
		assertEquals(listOf(PadChange(5, true), PadChange(9, true), PadChange(5, false), PadChange(6, true), PadChange(6, false)), sent.map { it.first })
		assertTrue(held[9])
	}

	@Test fun aCancelReleasesEveryHeldPadAndNeedsNewTouches() {
		input.accept(1)
		input.move(0, 5)
		input.move(1, 70)
		input.cancel()
		assertEquals(setOf(PadChange(5, false), PadChange(70, false)), sent.drop(2).map { it.first }.toSet())
		assertTrue(held.none { it })
		input.move(0, 5)
		assertEquals(PadChange(5, true), sent.last().first)
	}

	@Test fun aReleaseKeepsTheGenerationItWasPressedInAndANewSessionReleasesFirst() {
		input.accept(1)
		input.move(0, 5)
		input.accept(2)
		assertEquals(PadChange(5, false) to 1L, sent.last())
		input.move(0, 5)
		assertEquals(PadChange(5, true) to 2L, sent.last())
		input.accept(null)
		assertEquals(PadChange(5, false) to 2L, sent.last())
		input.move(0, 6)
		assertEquals(4, sent.size)
	}

	@Test fun aScreenReaderClickIsOnePressReleasePairSharedWithFingers() {
		input.accept(3)
		input.click(10)
		assertEquals(listOf(PadChange(10, true) to 3L, PadChange(10, false) to 3L), sent)
		input.move(0, 10)
		input.click(10)
		assertEquals(3, sent.size)
		assertTrue(held[10])
	}
}
