package com.kimjisub.launchpad.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The session state machine that decides which of `pack_load`, `play_start` and `play_end` are sent, and how often. */
class PlaySessionTrackerTest {

	private val sink = RecordingUsageSink()
	private var nanos = 5_000_000_000L
	private val session = sink.analytics.newPlaySession { nanos }

	private fun advanceSeconds(seconds: Double) {
		nanos += (seconds * 1_000_000_000L).toLong()
	}

	private fun packLoad(vararg parameters: Pair<String, String>) = sink.pack(UsageEvent.PACK_LOAD, *parameters)
	private fun playStart(trigger: String) = sink.pack(UsageEvent.PLAY_START, UsageParam.TRIGGER to trigger)
	private fun playEnd(bucket: String) = sink.pack(UsageEvent.PLAY_END, UsageParam.DURATION_BUCKET to bucket)

	@Test
	fun fullSessionSendsLoadStartAndEndOnce() {
		session.loadStarted()
		advanceSeconds(2.0)
		session.loadSucceeded()
		session.playTriggered(PlayTrigger.PAD)
		advanceSeconds(45.0)
		session.ended()

		assertEquals(
			listOf(
				packLoad(UsageParam.RESULT to "success", UsageParam.DURATION_BUCKET to "1s_3s"),
				playStart("pad"),
				playEnd("30s_2m"),
			),
			sink.events,
		)
	}

	@Test
	fun repeatedCallbacksAreCountedOnce() {
		session.loadStarted()
		session.loadStarted()
		session.loadSucceeded()
		session.loadSucceeded()
		session.loadFailed(UsageErrorType.SOUND_ENGINE)
		session.playTriggered(PlayTrigger.AUTOPLAY)
		session.playTriggered(PlayTrigger.PAD)
		session.playTriggered(PlayTrigger.AUTOPLAY)
		session.ended()
		session.ended()

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_END), sink.events.map { it.name })
		assertEquals(playStart("autoplay"), sink.events[1])
	}

	@Test
	fun leavingBeforeThePackIsReadyIsACancelledLoadAndNothingElse() {
		session.loadStarted()
		session.ended()
		session.playTriggered(PlayTrigger.PAD)
		session.loadSucceeded()

		assertEquals(listOf(packLoad(UsageParam.RESULT to "cancelled")), sink.events)
	}

	@Test
	fun leavingAfterLoadButBeforeTheFirstSoundSendsNeitherStartNorEnd() {
		session.loadStarted()
		session.loadSucceeded()
		session.ended()

		assertEquals(listOf(UsageEvent.PACK_LOAD), sink.events.map { it.name })
		assertEquals("success", sink.events.single().parameters[UsageParam.RESULT])
	}

	@Test
	fun aFailedLoadIsFinalAndLaterCallbacksSendNothing() {
		session.loadStarted()
		session.loadFailed(UsageErrorType.INVALID_PACK)
		session.loadSucceeded()
		session.playTriggered(PlayTrigger.PAD)
		session.ended()

		assertEquals(
			listOf(packLoad(UsageParam.RESULT to "failure", UsageParam.ERROR_TYPE to "invalid_pack")),
			sink.events,
		)
	}

	@Test
	fun aPressBeforeTheLoadFinishedIsNotAPlayStart() {
		session.playTriggered(PlayTrigger.PAD)
		session.loadStarted()
		session.playTriggered(PlayTrigger.PAD)
		session.loadSucceeded()
		session.ended()

		assertEquals(listOf(UsageEvent.PACK_LOAD), sink.events.map { it.name })
	}

	@Test
	fun anUntouchedSessionSendsNothing() {
		session.ended()
		assertTrue(sink.events.isEmpty())
	}

	@Test
	fun loadDurationBucketsFollowTheDocumentedBounds() {
		val expected = listOf(
			0.0 to "lt_1s", 0.999 to "lt_1s", 1.0 to "1s_3s", 2.999 to "1s_3s", 3.0 to "3s_10s", 9.999 to "3s_10s",
			10.0 to "10s_30s", 29.999 to "10s_30s", 30.0 to "30s_2m", 119.999 to "30s_2m", 120.0 to "2m_10m",
			599.999 to "2m_10m", 600.0 to "10m_30m", 1799.999 to "10m_30m", 1800.0 to "30m_plus", 86_400.0 to "30m_plus",
		)
		for ((seconds, bucket) in expected) {
			val recorder = RecordingUsageSink()
			var now = 1L
			val tracker = recorder.analytics.newPlaySession { now }
			tracker.loadStarted()
			now += (seconds * 1_000_000_000L).toLong()
			tracker.loadSucceeded()
			assertEquals("load of $seconds s", bucket, recorder.events.single().parameters[UsageParam.DURATION_BUCKET])
		}
	}

	@Test
	fun playDurationUsesTheClockTheTrackerWasGiven() {
		session.loadStarted()
		session.loadSucceeded()
		advanceSeconds(1000.0)
		session.playTriggered(PlayTrigger.PAD)
		advanceSeconds(0.5)
		session.ended()

		assertEquals(playEnd("lt_1s"), sink.events.last())
	}
}
