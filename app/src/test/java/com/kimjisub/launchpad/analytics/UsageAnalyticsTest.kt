package com.kimjisub.launchpad.analytics

import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.tool.UniPackImporter
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.ZipException
import net.lingala.zip4j.exception.ZipException as Zip4jException
import javax.net.ssl.SSLHandshakeException

class UsageAnalyticsTest {

	private val sink = RecordingUsageSink()

	private fun importEvent(source: String, result: String, error: String? = null) = sink.pack(
		UsageEvent.PACK_IMPORT,
		*listOfNotNull(
			UsageParam.RESULT to result,
			UsageParam.IMPORT_SOURCE to source,
			error?.let { UsageParam.ERROR_TYPE to it },
		).toTypedArray(),
	)

	@Test
	fun successIsSentOnceWithItsSource() {
		val report = sink.analytics.packImport(PackImportSource.STORE)
		report.succeeded()
		assertEquals(listOf(importEvent("store", "success")), sink.events)
	}

	@Test
	fun onlyTheFirstOutcomeOfOneImportIsSent() {
		val report = sink.analytics.packImport(PackImportSource.CODE)
		report.succeeded()
		report.cancelled()
		report.failed(UsageErrorType.NETWORK)
		report.failed(IOException("late"))
		report.succeeded()
		assertEquals(listOf(importEvent("code", "success")), sink.events)

		val other = sink.analytics.packImport(PackImportSource.CODE)
		other.cancelled()
		other.failed(IOException("after cancel"))
		assertEquals(importEvent("code", "cancelled"), sink.events.last())
		assertEquals(2, sink.events.size)
	}

	@Test
	fun aCancelledCoroutineIsACancellationNotAFailure() {
		sink.analytics.packImport(PackImportSource.FILE).failed(CancellationException("scope closed"))
		assertEquals(listOf(importEvent("file", "cancelled")), sink.events)
	}

	@Test
	fun eachImportAttemptReportsIndependently() {
		sink.analytics.packImport(PackImportSource.FILE).succeeded()
		sink.analytics.packImport(PackImportSource.FILE).succeeded()
		assertEquals(2, sink.named(UsageEvent.PACK_IMPORT).size)
	}

	@Test
	fun failuresAreClassifiedIntoTheFixedCategories() {
		val cases = listOf(
			UniPackImporter.UniPackCriticalErrorException("info missing") to UsageErrorType.INVALID_PACK,
			UniPackDownloader.UniPackCriticalErrorException("info missing") to UsageErrorType.INVALID_PACK,
			ZipException("invalid entry size") to UsageErrorType.CORRUPT_ARCHIVE,
			Zip4jException("Zip headers not found. Probably not a zip file") to UsageErrorType.CORRUPT_ARCHIVE,
			UniPackDownloader.HttpStatusException(404, "HTTP 404") to UsageErrorType.NOT_FOUND,
			UniPackDownloader.HttpStatusException(500, "HTTP 500") to UsageErrorType.SERVER,
			UniPackDownloader.HttpStatusException(403, "Empty response body (HTTP 403)") to UsageErrorType.SERVER,
			UnknownHostException("api.unipad.io") to UsageErrorType.NETWORK,
			ConnectException("refused") to UsageErrorType.NETWORK,
			SocketTimeoutException("timeout") to UsageErrorType.NETWORK,
			SSLHandshakeException("handshake") to UsageErrorType.NETWORK,
			FileNotFoundException("/x/y (Permission denied)") to UsageErrorType.FILE_ACCESS,
			SecurityException("no permission") to UsageErrorType.FILE_ACCESS,
			IOException("Could not open /x for writing") to UsageErrorType.FILE_ACCESS,
			IOException("write failed: ENOSPC (No space left on device)") to UsageErrorType.STORAGE,
			IOException("copy failed", IOException("write failed: ENOSPC (No space left on device)")) to UsageErrorType.STORAGE,
			IllegalStateException("boom") to UsageErrorType.UNKNOWN,
		)
		for ((error, expected) in cases) {
			assertEquals("$error", expected, UsageErrorType.classify(error))
		}
	}

	@Test
	fun failureEventsCarryTheCategoryAndNeverTheErrorText() {
		sink.analytics.packImport(PackImportSource.FILE)
			.failed(IOException("/storage/emulated/0/Download/Secret Pack.zip: token=abc123"))

		val event = sink.events.single()
		assertEquals(importEvent("file", "failure", "file_access"), event)
		assertTrue(event.toString(), "Secret" !in event.toString() && "abc123" !in event.toString())
	}

	@Test
	fun everyEventParameterIsFromTheAllowedVocabulary() {
		val values = mutableSetOf<String>()
		UsageResult.entries.mapTo(values) { it.value }
		PackImportSource.entries.mapTo(values) { it.value }
		PlayTrigger.entries.mapTo(values) { it.value }
		UsageErrorType.entries.mapTo(values) { it.value }

		sink.analytics.packImport(PackImportSource.CODE).failed(UsageErrorType.NOT_FOUND)
		val session = sink.analytics.newPlaySession()
		session.loadStarted(); session.loadSucceeded(); session.playTriggered(PlayTrigger.PAD); session.ended()

		for (event in sink.events) {
			assertTrue("$event", event.parameters.keys.all { it in UsageParam.allowed })
			event.parameters.filterKeys { it != UsageParam.DURATION_BUCKET }.values.forEach { assertTrue("$it in $event", it in values) }
		}
		assertEquals(setOf("result", "import_source", "error_type", "duration_bucket", "trigger"), UsageParam.allowed)
	}

	@Test
	fun aFailingSinkDoesNotBreakTheFeatureThatReports() {
		val calls = mutableListOf<String>()
		val analytics = UsageAnalytics { name, _ ->
			calls += name
			throw IllegalStateException("Firebase is not initialised")
		}

		analytics.packImport(PackImportSource.FILE).succeeded()
		val session = analytics.newPlaySession()
		session.loadStarted()
		session.loadSucceeded()
		session.playTriggered(PlayTrigger.PAD)
		session.ended()

		assertEquals(listOf(UsageEvent.PACK_IMPORT, UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), calls)
	}

	@Test
	fun eventNamesMatchTheIosApp() {
		assertEquals(listOf("pack_import", "pack_load", "play_start", "play_end"),
			listOf(UsageEvent.PACK_IMPORT, UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_END))
	}
}
