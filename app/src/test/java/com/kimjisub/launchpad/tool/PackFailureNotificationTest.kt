package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.text
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Shown
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PackFailureNotificationTest {
	private val env = PackInstallTestEnv()

	@Before
	fun setUp() = env.setUp()

	@After
	fun tearDown() = env.tearDown()

	@Test
	fun missingDownloadUsesExistingFailureTranslation() {
		val result = PackInstallTestEnv.Recorder()
		env.gate("missing").failWithStatus(404)
		env.finish(env.download("missing", result))

		assertNotNull(result.error)
		assertEquals(listOf(Shown(text(R.string.failed))), env.shownNow())
		assertResultPostedApartFromProgress()
	}

	@Test
	fun invalidImportUsesExistingFailureTranslation() {
		val result = PackInstallTestEnv.Recorder()
		env.finish(env.import("not a ZIP archive".toByteArray(), result))

		assertNotNull(result.error)
		assertEquals(listOf(Shown(text(R.string.failed))), env.shownNow())
		assertResultPostedApartFromProgress()
	}

	/**
	 * Android drops updates to an unfinished progress notification during a burst of updates,
	 * but never a new notification, so the result must not reuse a progress notification's id.
	 */
	private fun assertResultPostedApartFromProgress() {
		val (progress, results) = env.notifyLog.partition { (_, shown) -> shown.ongoing }
		assertTrue("no progress notification was shown", progress.isNotEmpty())
		val resultId = results.single().first
		assertFalse("result reused the progress notification", progress.any { (id, _) -> id == resultId })
	}
}
