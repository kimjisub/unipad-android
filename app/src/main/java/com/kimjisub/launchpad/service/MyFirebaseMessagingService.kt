package com.kimjisub.launchpad.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kimjisub.launchpad.tool.Log

class MyFirebaseMessagingService : FirebaseMessagingService() {

	override fun onMessageReceived(remoteMessage: RemoteMessage) {
		Log.fbmsg("From: ${remoteMessage.from}")

		if (remoteMessage.data.isNotEmpty()) {
			Log.fbmsg("Message data payload: ${remoteMessage.data}")
		}

		remoteMessage.notification?.let {
			Log.fbmsg("Message Notification Body: ${it.body}")
		}
	}

	override fun onNewToken(token: String) {
		Log.fbmsg("Refreshed token: $token")
	}
}
