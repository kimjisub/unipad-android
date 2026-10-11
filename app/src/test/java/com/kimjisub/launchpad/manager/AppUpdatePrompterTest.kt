package com.kimjisub.launchpad.manager

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.kimjisub.launchpad.manager.AppUpdatePrompter.Card
import com.kimjisub.launchpad.manager.AppUpdatePrompter.Dialog
import com.kimjisub.launchpad.manager.AppUpdateSchedule.Companion.CHECK_INTERVAL_MS
import com.kimjisub.launchpad.manager.AppUpdateSchedule.Companion.DECLINE_REST_MS
import com.kimjisub.launchpad.manager.AppUpdateSchedule.Companion.INSTALL_REST_MS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdatePrompterTest {
	private val store = MemoryAppUpdateStore()
	private val source = FakeSource()
	private var now = START
	private var safe = true
	private var storeOpens = 0
	private var storeExists = true

	private fun TestScope.prompter() = AppUpdatePrompter(
		source = source,
		schedule = AppUpdateSchedule(store) { now },
		scope = this,
		isSafe = { safe },
		openStore = { storeOpens++; storeExists },
	)

	private val AppUpdatePrompter.card get() = state.value.card
	private val AppUpdatePrompter.dialog get() = state.value.dialog

	@Test
	fun newVersionIsOfferedOncePerDay() = runTest {
		source.answer = UpdateStatus.Available
		val prompter = prompter()
		prompter.onListReady()
		advanceUntilIdle()
		assertEquals(Card.Offer, prompter.card)
		assertNull(prompter.dialog)

		prompter.onListReady()
		advanceUntilIdle()
		assertEquals(1, source.queries)

		now += CHECK_INTERVAL_MS
		prompter.onListReady()
		advanceUntilIdle()
		assertEquals(2, source.queries)
	}

	@Test
	fun slowStoreGivesUpAfter10SecondsWithoutAnyNotice() = runTest {
		source.hang = true
		val prompter = prompter()
		prompter.onListReady()
		advanceTimeBy(AppUpdatePrompter.TIMEOUT_MS - 1)
		assertTrue(source.waiting)
		advanceTimeBy(2)
		assertFalse("the query is abandoned at 10 s", source.waiting)
		assertNull(prompter.card)
		assertNull(prompter.dialog)

		source.hang = false
		source.answer = UpdateStatus.Available
		prompter.onListReady()
		advanceUntilIdle()
		assertEquals("a failed check also waits a day", 1, source.queries)
	}

	@Test
	fun failedAutoCheckShowsNothing() = runTest {
		source.error = IOException("offline")
		val prompter = prompter()
		prompter.onListReady()
		advanceUntilIdle()
		assertNull(prompter.card)
		assertNull(prompter.dialog)
	}

	@Test
	fun unsupportedOrNoUpdateIsSilentOnItsOwn() = runTest {
		for (answer in listOf(UpdateStatus.Unsupported, UpdateStatus.None)) {
			source.answer = answer
			val prompter = prompter()
			prompter.onListReady()
			advanceUntilIdle()
			assertNull(prompter.card)
			assertNull(prompter.dialog)
			now += CHECK_INTERVAL_MS
		}
	}

	@Test
	fun laterRestsSevenDaysAcrossRestartAndNewerBuilds() = runTest {
		source.answer = UpdateStatus.Available
		prompter().apply {
			onListReady()
			advanceUntilIdle()
			later()
			assertNull(card)
		}

		// Restarted the next week minus a moment; a newer build is up meanwhile.
		now += DECLINE_REST_MS - 1
		val restarted = prompter()
		restarted.onListReady()
		advanceUntilIdle()
		assertNull(restarted.card)
		assertEquals("resting skips the store entirely", 1, source.queries)

		now += 1
		restarted.onListReady()
		advanceUntilIdle()
		assertEquals(Card.Offer, restarted.card)
	}

	@Test
	fun checkingByHandWorksDuringTheRest() = runTest {
		store.declinedAt = now
		source.answer = UpdateStatus.Available
		val prompter = prompter()
		prompter.checkByHand()
		assertTrue(prompter.state.value.checking)
		advanceUntilIdle()
		assertFalse(prompter.state.value.checking)
		assertEquals(Card.Offer, prompter.card)
	}

	@Test
	fun checkingByHandNeverCallsAFailureUpToDate() = runTest {
		val prompter = prompter()
		source.error = IOException("offline")
		prompter.checkByHand()
		advanceUntilIdle()
		assertEquals(Dialog.CheckFailed, prompter.dialog)

		prompter.dismissDialog()
		source.error = null
		source.answer = UpdateStatus.Unsupported
		prompter.checkByHand()
		advanceUntilIdle()
		assertEquals(Dialog.Unsupported, prompter.dialog)

		prompter.dismissDialog()
		source.answer = UpdateStatus.None
		prompter.checkByHand()
		advanceUntilIdle()
		assertEquals(Dialog.NoUpdate, prompter.dialog)
	}

	@Test
	fun lateAnswerAfterLeavingTheListOpensNoDialog() = runTest {
		val answer = CompletableDeferred<UpdateStatus>()
		source.pending = answer
		val prompter = prompter()
		prompter.checkByHand()
		runCurrent()
		safe = false
		answer.complete(UpdateStatus.None)
		advanceUntilIdle()
		assertNull(prompter.dialog)
		assertFalse(prompter.state.value.checking)
	}

	@Test
	fun downloadOnlyStartsWhileTheListIsSafe() = runTest {
		source.answer = UpdateStatus.Available
		val prompter = prompter()
		prompter.onListReady()
		advanceUntilIdle()

		safe = false
		prompter.download()
		advanceUntilIdle()
		assertEquals(0, source.downloads)

		// The list stops being safe while the store is asked again.
		safe = true
		val answer = CompletableDeferred<UpdateStatus>()
		source.pending = answer
		prompter.download()
		runCurrent()
		safe = false
		answer.complete(UpdateStatus.Available)
		advanceUntilIdle()
		assertEquals(0, source.downloads)

		safe = true
		source.pending = null
		prompter.download()
		advanceUntilIdle()
		assertEquals(1, source.downloads)
	}

	@Test
	fun consentResultsMapToRestDownloadOrFailure() = runTest {
		val prompter = prompter()
		prompter.onConsentResult(Activity.RESULT_CANCELED)
		assertNull(prompter.card)
		assertEquals(now, store.declinedAt)

		prompter.onConsentResult(ActivityResult.RESULT_IN_APP_UPDATE_FAILED)
		assertEquals(Card.Failed, prompter.card)

		prompter.onConsentResult(Activity.RESULT_OK)
		assertEquals(Card.Downloading(null), prompter.card)
		assertTrue(store.downloadRequested)
	}

	@Test
	fun finishedDownloadWaitsForTheInstallButton() = runTest {
		val prompter = prompter()
		prompter.onConsentResult(Activity.RESULT_OK)
		safe = false
		source.push(UpdateStatus.Downloading(0.5f))
		assertEquals(Card.Downloading(0.5f), prompter.card)
		source.push(UpdateStatus.Downloaded)
		advanceUntilIdle()
		assertEquals(Card.Ready, prompter.card)
		assertEquals(0, source.installs)

		source.answer = UpdateStatus.Downloaded
		prompter.install()
		advanceUntilIdle()
		assertEquals("no install while playing", 0, source.installs)

		safe = true
		prompter.install()
		advanceUntilIdle()
		assertEquals(1, source.installs)
	}

	@Test
	fun installIsNotTriedWhenTheListIsLeftDuringTheCheck() = runTest {
		val prompter = prompter()
		val answer = CompletableDeferred<UpdateStatus>()
		source.pending = answer
		prompter.install()
		runCurrent()
		safe = false
		answer.complete(UpdateStatus.Downloaded)
		advanceUntilIdle()
		assertEquals(0, source.installs)
	}

	@Test
	fun installFailureKeepsTheAppAndOffersRetry() = runTest {
		source.answer = UpdateStatus.Downloaded
		source.installError = IllegalStateException("not enough space")
		val prompter = prompter()
		prompter.install()
		advanceUntilIdle()
		assertEquals(Card.Failed, prompter.card)

		source.answer = UpdateStatus.Available
		prompter.retry()
		advanceUntilIdle()
		assertEquals(1, source.downloads)
	}

	@Test
	fun downloadInProgressIsFollowedOnEveryReturnAfterRestart() = runTest {
		store.downloadRequested = true
		store.lastAutoCheckAt = now
		source.answer = UpdateStatus.Downloading(0.2f)
		val prompter = prompter()
		prompter.onListReady()
		advanceUntilIdle()
		assertEquals(Card.Downloading(0.2f), prompter.card)

		source.answer = UpdateStatus.Downloaded
		prompter.onListReady()
		advanceUntilIdle()
		assertEquals(Card.Ready, prompter.card)
		assertEquals(0, source.installs)
	}

	@Test
	fun laterOnTheReadyCardRestsItsOwnNotice24Hours() = runTest {
		store.downloadRequested = true
		source.answer = UpdateStatus.Downloaded
		val prompter = prompter()
		prompter.onListReady()
		advanceUntilIdle()
		prompter.later()
		assertNull(prompter.card)

		now += INSTALL_REST_MS - 1
		prompter.onListReady()
		advanceUntilIdle()
		assertNull(prompter.card)

		now += 1
		prompter.onListReady()
		advanceUntilIdle()
		assertEquals(Card.Ready, prompter.card)
		assertEquals(0, source.installs)
	}

	@Test
	fun canceledDownloadRests() = runTest {
		val prompter = prompter()
		prompter.onConsentResult(Activity.RESULT_OK)
		source.push(UpdateStatus.Canceled)
		assertNull(prompter.card)
		assertFalse(store.downloadRequested)
		assertEquals(now, store.declinedAt)
	}

	@Test
	fun storeLinkCountsAsDeclineAndReportsAMissingStore() = runTest {
		val prompter = prompter()
		prompter.askStore()
		assertEquals(Dialog.StoreNotice, prompter.dialog)
		storeExists = false
		prompter.openStore()
		assertEquals(1, storeOpens)
		assertEquals(Dialog.StoreUnavailable, prompter.dialog)
		assertEquals(now, store.declinedAt)

		safe = false
		prompter.askStore()
		prompter.openStore()
		assertEquals(1, storeOpens)
	}

	private class FakeSource : AppUpdateSource {
		var answer: UpdateStatus = UpdateStatus.None
		var error: Exception? = null
		var hang = false
		var pending: CompletableDeferred<UpdateStatus>? = null
		var installError: Exception? = null
		var queries = 0
		var downloads = 0
		var installs = 0
		var waiting = false
		private var listener: ((UpdateStatus) -> Unit)? = null

		override suspend fun status(): UpdateStatus {
			queries++
			waiting = true
			try {
				if (hang) awaitCancellation()
				pending?.let { return it.await() }
				error?.let { throw it }
				return answer
			} finally {
				waiting = false
			}
		}

		override fun startDownload(): Boolean {
			downloads++
			return true
		}

		override suspend fun install() {
			installs++
			installError?.let { throw it }
		}

		override fun observe(onChange: (UpdateStatus) -> Unit) {
			listener = onChange
		}

		override fun close() {
			listener = null
		}

		fun push(status: UpdateStatus) = listener!!.invoke(status)
	}

	private companion object {
		const val START = 1_790_000_000_000L
	}
}
