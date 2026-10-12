package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
		assertEquals(R.string.failed, env.notificationTextIds.last())
	}

	@Test
	fun invalidImportUsesExistingFailureTranslation() {
		val result = PackInstallTestEnv.Recorder()
		env.finish(env.import("not a ZIP archive".toByteArray(), result))

		assertNotNull(result.error)
		assertEquals(R.string.failed, env.notificationTextIds.last())
	}
}
