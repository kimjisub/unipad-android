package com.kimjisub.launchpad.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.graphics.Color
import android.os.Build
import com.kimjisub.launchpad.R.string

object NotificationManager {
	fun createChannel(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

		val manager = getManager(context)

		enumValues<Group>().forEach {
			val group = NotificationChannelGroup(it.name, context.getString(it.titleId))
			manager.createNotificationChannelGroup(group)
		}

		enumValues<Channel>().forEach {
			val channel =
				NotificationChannel(it.name, context.getString(it.titleId), IMPORTANCE_LOW)
			channel.apply {
				description = context.getString(it.titleId) + " disc"
				group = it.group.name
				lightColor = it.colorId
				lockscreenVisibility = Notification.VISIBILITY_PUBLIC
			}
			manager.createNotificationChannel(channel)
		}
	}

	/**
	 * Removes the progress notifications of downloads and imports that a killed process was running:
	 * nothing is left to update or remove them, so they stayed on screen as if still in progress.
	 * Call when the process starts, before any screen can start a download or an import. Finished
	 * downloads and other channels' notifications are kept.
	 */
	fun cancelLeftoverDownloadProgress(context: Context) {
		val manager = getManager(context)
		manager.activeNotifications
			.filter { isDownloadProgress(it.notification) }
			.forEach { manager.cancel(it.tag, it.id) }
	}

	private fun isDownloadProgress(notification: Notification): Boolean {
		if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
		// Before channels, downloads and imports are the only notifications the app keeps ongoing.
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notification.channelId == Channel.Download.name
	}

	fun getManager(context: Context): NotificationManager {
		return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
	}

	enum class Group(val titleId: Int) {
		General(string.general),
		Notice(string.notice)
	}

	enum class Channel(val titleId: Int, val group: Group, val colorId: Int) {
		Download(string.download, Group.General, Color.GREEN),
		Notice(string.notice, Group.Notice, Color.BLUE),
		NewPack(string.newUniPack, Group.Notice, Color.BLUE)
	}
}
