package com.kimjisub.launchpad.manager

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Only the process that posted a download's progress notification updates or removes it. When the
 * system killed the app during a download, the notification stayed on screen as if the download
 * were still running, until the person swiped it away.
 */
@RunWith(AndroidJUnit4::class)
class LeftoverDownloadNotificationTest {
	private val instrumentation = InstrumentationRegistry.getInstrumentation()
	private val context = instrumentation.targetContext
	private val manager = NotificationManager.getManager(context)
	private val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

	@Test
	fun startupRemovesOnlyUnfinishedDownloadProgress() {
		if (needsPermission) instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
		try {
			post(STALE_PROGRESS, NotificationManager.Channel.Download, ongoing = true)
			post(FINISHED_DOWNLOAD, NotificationManager.Channel.Download, ongoing = false)
			post(ONGOING_NOTICE, NotificationManager.Channel.Notice, ongoing = true)
			assertShown(setOf(STALE_PROGRESS, FINISHED_DOWNLOAD, ONGOING_NOTICE))

			NotificationManager.cancelLeftoverDownloadProgress(context)

			// Before channels, downloads and imports are the only notifications the app keeps ongoing.
			val kept = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setOf(FINISHED_DOWNLOAD, ONGOING_NOTICE) else setOf(FINISHED_DOWNLOAD)
			assertShown(kept)
		} finally {
			listOf(STALE_PROGRESS, FINISHED_DOWNLOAD, ONGOING_NOTICE).forEach { manager.cancel(TAG, it) }
			if (needsPermission) instrumentation.uiAutomation.dropShellPermissionIdentity()
		}
	}

	private fun post(id: Int, channel: NotificationManager.Channel, ongoing: Boolean) {
		val notification = NotificationCompat.Builder(context, channel.name)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentTitle("Leftover notification check $id")
			.setProgress(PROGRESS_MAX, PROGRESS_MAX / 3, false)
			.setOngoing(ongoing)
			.build()
		manager.notify(TAG, id, notification)
	}

	/** Posting and cancelling reach the system asynchronously. */
	private fun assertShown(expected: Set<Int>) {
		val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
		var shown = shown()
		while (shown != expected && SystemClock.uptimeMillis() < deadline) {
			SystemClock.sleep(POLL_MS)
			shown = shown()
		}
		assertEquals(expected, shown)
	}

	private fun shown(): Set<Int> = manager.activeNotifications.filter { it.tag == TAG }.map { it.id }.toSet()

	private companion object {
		const val TAG = "leftover-download-notification-test"
		const val STALE_PROGRESS = 1
		const val FINISHED_DOWNLOAD = 2
		const val ONGOING_NOTICE = 3
		const val PROGRESS_MAX = 100
		const val TIMEOUT_MS = 5_000L
		const val POLL_MS = 100L
	}
}
