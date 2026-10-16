package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.SplashActivity
import com.kimjisub.launchpad.manager.NotificationManager
import kotlin.random.Random

/**
 * The notification of one pack download or import: an ongoing progress notification while it
 * runs, then a dismissible one saying how it ended.
 *
 * The result is posted under its own id once the progress notification is removed. Android drops
 * updates to an unfinished progress notification while an app updates notifications more than a
 * few times a second, as a fast download does, so updating it in place could leave a progress bar
 * that never ends and cannot be dismissed.
 */
class PackInstallNotification(private val context: Context) {
	private val manager = NotificationManager.getManager(context)
	private val progressId = Random.nextInt(Int.MAX_VALUE)
	private val builder: NotificationCompat.Builder by lazy {
		val intent = Intent(context, SplashActivity::class.java).apply {
			action = Intent.ACTION_MAIN
			addCategory(Intent.CATEGORY_LAUNCHER)
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		NotificationCompat.Builder(context, NotificationManager.Channel.Download.name).apply {
			setAutoCancel(true)
			setSmallIcon(R.mipmap.ic_launcher)
			setContentIntent(
				PendingIntent.getActivity(
					context,
					1,
					intent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)
			)
		}
	}

	fun showProgress(title: String?, text: String, progress: Int, indeterminate: Boolean) {
		builder.apply {
			setContentTitle(title)
			setContentText(text)
			setProgress(PROGRESS_MAX, progress, indeterminate)
			setOngoing(true)
		}
		manager.notify(progressId, builder.build())
	}

	fun showProgress(title: String?, @StringRes text: Int, progress: Int, indeterminate: Boolean) =
		showProgress(title, context.getString(text), progress, indeterminate)

	fun showResult(title: String?, @StringRes text: Int) {
		cancelProgress()
		builder.apply {
			setContentTitle(title)
			setContentText(context.getString(text))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		manager.notify(Random.nextInt(Int.MAX_VALUE), builder.build())
	}

	fun cancelProgress() = manager.cancel(progressId)

	private companion object {
		const val PROGRESS_MAX = 100
	}
}
