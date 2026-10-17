package com.kimjisub.launchpad.ledtiming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a command the driver never got from one still on its way when the run gave up. */
class LedTimingMeasurementTest {

	private val pad = LedTarget(0, 0)
	private val round = LedTarget(-1, 0)

	/** Two LEDs flashing in turn, 10 ms apart. */
	private val intended = (0 until 6).flatMap { i ->
		listOf(IntendedCommand(i * 20L, pad, if (i % 2 == 0) 5 else 0), IntendedCommand(i * 20L + 10, round, if (i % 2 == 0) 9 else 0))
	}

	private fun measure(arrived: List<IntendedCommand>, gaveUpAfterReleaseMs: Long?): LedTimingMeasurement {
		val sent = arrived.map { SentCommand(it.ms + 3, it.target, it.code) }
		val recording = Recording(sent, sent, emptyList(), releaseRepaints = 0, covered = 0, gaveUpAfterReleaseMs = gaveUpAfterReleaseMs)
		return LedTimingMeasurement("record", IntendedTimeline(intended, emptyList(), intended.last().t0, 0), recording)
	}

	@Test
	fun everyCommandArrived_passes() {
		assertNull(measure(intended, gaveUpAfterReleaseMs = null).packToDriverFailure())
	}

	@Test
	fun aCommandLostInTheMiddle_isMissingEvenWhenTheRunGaveUp() {
		val measurement = measure(intended - intended[4], gaveUpAfterReleaseMs = 10_000)

		assertEquals(1, measurement.packToDriver.missing)
		assertEquals(0, measurement.packToDriver.unreached)
		assertTrue(measurement.packToDriverFailure()!!.startsWith("commands lost: reordered 0, missing 1, extra 0\n"))
	}

	@Test
	fun theLastCommandsCutOffByTheDeadline_areLagNotLoss() {
		val measurement = measure(intended.dropLast(3), gaveUpAfterReleaseMs = 10_000)

		assertEquals(0, measurement.packToDriver.missing)
		assertEquals(3, measurement.packToDriver.unreached)
		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"the last 3 commands did not reach the driver within 10 s of the final release: processing lag, nothing missing\n"
			)
		)
	}

	@Test
	fun aLossInTheMiddleWithTheEndCutOff_namesTheLossFirst() {
		val measurement = measure(intended.dropLast(3) - intended[4], gaveUpAfterReleaseMs = 10_000)

		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"commands lost: reordered 0, missing 1, extra 0, and the last 3 did not arrive\n"
			)
		)
	}

	/** Nothing was still playing, so nothing more was coming: the end that did not arrive was lost. */
	@Test
	fun theLastCommandsMissingAfterEveryAnimationEnded_areLoss() {
		val measurement = measure(intended.dropLast(3), gaveUpAfterReleaseMs = null)

		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"commands lost: the last 3 never reached the driver, though every animation ended\n"
			)
		)
	}
}
