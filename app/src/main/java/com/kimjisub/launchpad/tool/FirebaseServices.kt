package com.kimjisub.launchpad.tool

import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase

/**
 * The app's only way into Crashlytics and the Realtime Database.
 *
 * Firebase starts itself from a content provider that Android runs before `Application.onCreate`.
 * When a launch skips content providers, every Firebase SDK throws on first use, and a call made
 * while starting or on a screen change stops the app before its first screen. Through here, such a
 * launch only goes without crash context and store data.
 */
object FirebaseServices {
	fun crashlytics(): FirebaseCrashlytics? = if (isStarted()) FirebaseCrashlytics.getInstance() else null

	fun database(): FirebaseDatabase? = if (isStarted()) FirebaseDatabase.getInstance() else null

	// FirebaseApp offers no check without a Context, and throws this only when the default app is missing.
	private fun isStarted(): Boolean = try {
		FirebaseApp.getInstance()
		true
	} catch (_: IllegalStateException) {
		false
	}
}
