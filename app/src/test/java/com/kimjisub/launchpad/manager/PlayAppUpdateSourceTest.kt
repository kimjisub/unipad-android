package com.kimjisub.launchpad.manager

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayAppUpdateSourceTest {
	private val manager = mockk<AppUpdateManager>(relaxed = true)
	private val launcher = mockk<ActivityResultLauncher<IntentSenderRequest>>()
	private val source = PlayAppUpdateSource(manager, launcher)

	@Test
	fun canceledQueryUsesAvailabilityButNotificationReportsCancellation() = runTest {
		configureInfo(InstallStatus.CANCELED)
		assertEquals(UpdateStatus.Available, source.status())
		configureInfo(InstallStatus.CANCELED, allowed = false)
		assertEquals(UpdateStatus.Unsupported, source.status())
		configureInfo(InstallStatus.CANCELED, availability = UpdateAvailability.UPDATE_NOT_AVAILABLE)
		assertEquals(UpdateStatus.None, source.status())

		val listener = slot<InstallStateUpdatedListener>()
		every { manager.registerListener(capture(listener)) } returns Unit
		var observed: UpdateStatus? = null
		source.observe { observed = it }
		listener.captured.onStateUpdate(installState(InstallStatus.CANCELED, 0, 0))
		assertEquals(UpdateStatus.Canceled, observed)
	}

	@Test
	fun pendingDownloadHasNoProgressOnQueryAndNotification() = runTest {
		assertBoth(InstallStatus.PENDING, UpdateStatus.Downloading(null))
	}

	@Test
	fun progressIsTheSameOnQueryAndNotification() = runTest {
		assertBoth(InstallStatus.DOWNLOADING, UpdateStatus.Downloading(0.4f), 400, 1000)
		assertBoth(InstallStatus.DOWNLOADING, UpdateStatus.Downloading(null), 400, 0)
		assertBoth(InstallStatus.DOWNLOADING, UpdateStatus.Downloading(1f), 1500, 1000)
		assertBoth(InstallStatus.DOWNLOADING, UpdateStatus.Downloading(0f), -100, 1000)
	}

	@Test
	fun finishedAndFailedDownloadsMatchOnQueryAndNotification() = runTest {
		assertBoth(InstallStatus.DOWNLOADED, UpdateStatus.Downloaded)
		assertBoth(InstallStatus.FAILED, UpdateStatus.Failed)
	}

	@Test
	fun idleQueryUsesAvailabilityAndFlexibleSupport() = runTest {
		configureInfo(InstallStatus.UNKNOWN)
		assertEquals(UpdateStatus.Available, source.status())
		configureInfo(InstallStatus.UNKNOWN, allowed = false)
		assertEquals(UpdateStatus.Unsupported, source.status())
		configureInfo(InstallStatus.UNKNOWN, availability = UpdateAvailability.UPDATE_NOT_AVAILABLE)
		assertEquals(UpdateStatus.None, source.status())
	}

	@Test
	fun unrelatedInstallNotificationsAreIgnored() {
		val listener = slot<InstallStateUpdatedListener>()
		every { manager.registerListener(capture(listener)) } returns Unit
		var observed: UpdateStatus? = null
		source.observe { observed = it }
		for (status in listOf(InstallStatus.UNKNOWN, InstallStatus.INSTALLING, InstallStatus.INSTALLED)) {
			listener.captured.onStateUpdate(installState(status, 0, 0))
			assertNull(observed)
		}
	}

	private suspend fun assertBoth(status: Int, expected: UpdateStatus, done: Long = 0, total: Long = 0) {
		configureInfo(status, done, total)
		assertEquals(expected, source.status())
		val listener = slot<InstallStateUpdatedListener>()
		every { manager.registerListener(capture(listener)) } returns Unit
		var observed: UpdateStatus? = null
		source.observe { observed = it }
		listener.captured.onStateUpdate(installState(status, done, total))
		assertEquals(expected, observed)
	}

	private fun installState(status: Int, done: Long, total: Long): InstallState = mockk {
		every { installStatus() } returns status
		every { bytesDownloaded() } returns done
		every { totalBytesToDownload() } returns total
	}

	private fun configureInfo(
		status: Int,
		done: Long = 0,
		total: Long = 0,
		availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
		allowed: Boolean = true,
	) {
		val info = mockk<AppUpdateInfo>()
		every { info.installStatus() } returns status
		every { info.bytesDownloaded() } returns done
		every { info.totalBytesToDownload() } returns total
		every { info.updateAvailability() } returns availability
		every { info.isUpdateTypeAllowed(any<AppUpdateOptions>()) } returns allowed
		// Complete synchronously without Android's main-thread task executor.
		val task = mockk<Task<AppUpdateInfo>>()
		every { task.exception } returns null
		every { task.isCanceled } returns false
		every { task.result } returns info
		every { task.addOnCompleteListener(any<OnCompleteListener<AppUpdateInfo>>()) } answers {
			firstArg<OnCompleteListener<AppUpdateInfo>>().onComplete(task)
			task
		}
		every { manager.appUpdateInfo } returns task
	}
}
