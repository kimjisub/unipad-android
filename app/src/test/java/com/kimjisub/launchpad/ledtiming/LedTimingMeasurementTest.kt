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

	/**
	 * A run in which [inTime] reached the driver before the run stopped counting, and [late] after it
	 * while it went on; [gaveUpAfterReleaseMs] is null when every animation ended first.
	 */
	private fun measure(
		inTime: List<IntendedCommand>,
		gaveUpAfterReleaseMs: Long?,
		late: List<IntendedCommand> = emptyList(),
	): LedTimingMeasurement {
		fun sent(commands: List<IntendedCommand>) = commands.map { SentCommand(it.ms + 3, it.target, it.code) }
		val recording = Recording(
			sent(inTime), sent(inTime), emptyList(), releaseRepaints = 0, covered = 0,
			gaveUpAfterReleaseMs = gaveUpAfterReleaseMs, toDriverLate = sent(late), lateForMs = if (gaveUpAfterReleaseMs == null) 0 else 5_000,
		)
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
	fun theLastCommandsCutOffByTheDeadlineAndArrivingLate_areLagNotLoss() {
		val measurement = measure(intended.dropLast(3), gaveUpAfterReleaseMs = 10_000, late = intended.takeLast(3))

		assertEquals(0, measurement.packToDriver.missing)
		assertEquals(3, measurement.packToDriver.unreached)
		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"the last 3 commands did not reach the driver within 10000 ms of the final release " +
					"but all arrived in the next 5000 ms: processing lag, nothing missing\n"
			)
		)
	}

	@Test
	fun aLossInTheMiddleWithTheEndCutOff_namesTheLossFirst() {
		val measurement = measure(intended.dropLast(3) - intended[4], gaveUpAfterReleaseMs = 10_000, late = intended.takeLast(3))

		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"commands lost: reordered 0, missing 1, extra 0, and the last 3 did not arrive in time\n"
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

	/**
	 * An endless animation the release did not stop keeps the run animating until it gives up, while its
	 * off never comes: one LED lacks its last command though the other LED got later ones.
	 */
	@Test
	fun oneLedsLastCommandNeverArriving_isLossThoughTheRunGaveUp() {
		val padOff = intended.last { it.target == pad }
		val measurement = measure(intended - padOff, gaveUpAfterReleaseMs = 10_000)

		assertEquals(1, measurement.packToDriver.unreached)
		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"commands lost: the last 1 did not reach the driver within 10000 ms of the final release, " +
					"and 5000 ms later still reordered 0, missing 0, never arrived 1\n"
			)
		)
	}

	/** Only part of the cut-off end arriving late leaves a hole: lost, not lagging. */
	@Test
	fun theCutOffEndArrivingOnlyInPart_isLoss() {
		val measurement = measure(intended.dropLast(3), gaveUpAfterReleaseMs = 10_000, late = listOf(intended[10], intended[11]))

		assertTrue(
			measurement.packToDriverFailure()!!.startsWith(
				"commands lost: the last 3 did not reach the driver within 10000 ms of the final release, " +
					"and 5000 ms later still reordered 0, missing 1, never arrived 0\n"
			)
		)
	}
}
