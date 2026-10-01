package com.kimjisub.launchpad.analytics

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.kimjisub.launchpad.BuildConfig
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Which sink each build uses. A release build must keep handing events to Firebase; a debug build
 * must keep them on the device unless a server check was asked for. The Firebase SDK is a mock here,
 * so every call the app would make to it is visible and none leaves the JVM.
 */
class UsageEventRoutingTest {

	private val firebaseSdk = mockk<FirebaseAnalytics>(relaxed = true)

	@Before
	fun setUp() {
		mockkStatic(FirebaseAnalytics::class)
		every { FirebaseAnalytics.getInstance(any()) } returns firebaseSdk
	}

	@After
	fun tearDown() = unmockkStatic(FirebaseAnalytics::class)

	private fun firebaseSink(): UsageEventSink = FirebaseUsageEventSink(mockk<Context>())

	/** One of every event, through the same door the app uses. */
	private fun sendEveryEvent(sink: UsageEventSink) {
		val analytics = UsageAnalytics(sink)
		analytics.packImport(PackImportSource.STORE).succeeded()
		analytics.newPlaySession().run {
			loadStarted()
			loadSucceeded()
			playTriggered(PlayTrigger.PAD)
			ended()
		}
	}

	private fun verifyEveryEventReachedFirebaseOnce() {
		verifySequence {
			firebaseSdk.logEvent(UsageEvent.PACK_IMPORT, any())
			firebaseSdk.logEvent(UsageEvent.PACK_LOAD, any())
			firebaseSdk.logEvent(UsageEvent.PLAY_START, any())
			firebaseSdk.logEvent(UsageEvent.PLAY_END, any())
		}
	}

	private fun verifyFirebaseWasNeverTouched() {
		verify(exactly = 0) { FirebaseAnalytics.getInstance(any()) }
		confirmVerified(firebaseSdk)
	}

	@Test
	fun aReleaseBuildHandsEveryEventToFirebaseWithoutReadingTheDebugSwitch() {
		val sink = UsageEventRouting.sinkFor(
			debugBuild = false,
			serverCheckRequested = { fail("a release build has no local mode to switch"); true },
			firebase = ::firebaseSink,
		)

		sendEveryEvent(sink)

		verifyEveryEventReachedFirebaseOnce()
	}

	@Test
	fun aDebugBuildKeepsEveryEventOnTheDeviceAndNeverTouchesFirebase() {
		val sink = UsageEventRouting.sinkFor(debugBuild = true, serverCheckRequested = { false }, firebase = ::firebaseSink)

		sendEveryEvent(sink)

		assertTrue("$sink", sink is LocalUsageEventSink)
		verifyFirebaseWasNeverTouched()
	}

	@Test
	fun aDebugBuildAskedForAServerCheckHandsEveryEventToFirebase() {
		val sink = UsageEventRouting.sinkFor(debugBuild = true, serverCheckRequested = { true }, firebase = ::firebaseSink)

		sendEveryEvent(sink)

		verifyEveryEventReachedFirebaseOnce()
	}

	/** Collection on or off, consent and user identifiers are SDK settings; the sink calls none of them. */
	@Test
	fun theFirebaseSinkOnlyLogsEventsAndLeavesTheSdkSettingsAlone() {
		sendEveryEvent(firebaseSink())

		verifyEveryEventReachedFirebaseOnce()
		confirmVerified(firebaseSdk)
	}

	@Test
	fun aFirebaseSdkThatCannotStartIsNotPassedOnToWhatReports() {
		every { FirebaseAnalytics.getInstance(any()) } throws IllegalStateException("Default FirebaseApp is not initialized")

		sendEveryEvent(firebaseSink())

		verify(exactly = 0) { firebaseSdk.logEvent(any(), any()) }
	}

	/** Holds in whichever build type runs it: the debug unit tests and the release unit tests. */
	@Test
	fun theBuildTypeUnderTestIsRoutedAsItsConfigurationSays() {
		val sink = UsageEventRouting.sinkForThisBuild(::firebaseSink)

		sendEveryEvent(sink)

		if (BuildConfig.DEBUG) {
			assertTrue("$sink", sink is LocalUsageEventSink)
			verifyFirebaseWasNeverTouched()
		} else {
			verifyEveryEventReachedFirebaseOnce()
		}
	}

	@Test
	fun theServerCheckSwitchFitsTheLogTagLimitOfTheOldestSupportedAndroid() {
		assertTrue(UsageEventRouting.SERVER_CHECK_LOG_TAG.length <= 23)
	}
}
