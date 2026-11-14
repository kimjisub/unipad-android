package com.kimjisub.launchpad.tool

import android.content.Context
import com.kimjisub.launchpad.diagnostics.SystemExitReporter
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A unit test process is a launch in which Firebase never started: no content provider ran and
 * nothing called `FirebaseApp.initializeApp`. What the app calls while starting and on every
 * screen change must then do nothing rather than throw.
 */
class FirebaseNotStartedTest {

	@Test
	fun crashContextIsDroppedWithoutFirebase() {
		FirebaseCrashContext.setKey("install_splits", "none")
		FirebaseCrashContext.log("MainActivity resumed")
	}

	@Test
	fun storeCountIsSkippedWithoutFirebase() {
		FirebaseManager("storeCount").attachEventListener(true).attachEventListener(false)
	}

	@Test
	fun systemExitsWaitForALaunchWithFirebase() {
		// A strict mock fails on any call, so the history and its checkpoint stay untouched.
		SystemExitReporter.collect(mockk<Context>(), sdkInt = 35)
	}

	@Test
	fun firebaseIsReachedOnlyThroughTheGate() {
		val direct = Regex("""\b(FirebaseCrashlytics|FirebaseDatabase)\.getInstance\(""")
		val callers = File("src/main/java").walkTopDown()
			.filter { it.extension == "kt" && it.name != "FirebaseServices.kt" }
			.filter { direct.containsMatchIn(it.readText()) }
			.map { it.name }
			.sorted()
			.toList()
		assertEquals("Reach Crashlytics and the database through FirebaseServices", emptyList<String>(), callers)
	}
}
