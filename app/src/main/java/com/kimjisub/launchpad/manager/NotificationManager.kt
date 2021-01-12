package com.kimjisub.launchpad.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.NotificationManager.*
import android.content.Context
import android.graphics.Color
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.kimjisub.launchpad.R.string

object NotificationManager {
	fun createChannel(context: Context) {
		if (VERSION.SDK_INT >= VERSION_CODES.O) {
			val manager = getManager(context)

			enumValues<Group>().forEach {
				val group = NotificationChannelGroup(it.name, context.getString(it.titleId))
				manager.createNotificationChannelGroup(group)
			}

			enumValues<Channel>().forEach {
				val channel =
					NotificationChannel(it.name, context.getString(it.titleId), IMPORTANCE_HIGH)
				channel.apply {
					description = context.getString(it.titleId) + " disc"
					group = it.group.name
					lightColor = it.colorId
					lockscreenVisibility = Notification.VISIBILITY_PUBLIC
				}
				manager.createNotificationChannel(channel)
			}
		}
	}

	fun getManager(context: Context): NotificationManager {
		return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
	}

	fun deleteChannel(context: Context, channel: Channel) {
		if (VERSION.SDK_INT >= VERSION_CODES.O)
			getManager(context).deleteNotificationChannel(channel.name)
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