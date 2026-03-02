package com.kimjisub.launchpad.activity

import android.app.Activity
import android.app.Application
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kimjisub.launchpad.R.anim
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.Log
import org.koin.android.ext.android.inject
import splitties.activities.start

open class BaseActivity : AppCompatActivity() {

	companion object {
		private val activities = mutableListOf<BaseActivity>()

		private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
				if (activity is BaseActivity) {
					activities.add(activity)
					printActivityLog("${activity.getActivityName()} start")
				}
			}

			override fun onActivityDestroyed(activity: Activity) {
				if (activity is BaseActivity) {
					val removed = activities.remove(activity)
					printActivityLog("${activity.getActivityName()} finish${if (removed) "" else " error"}")
				}
			}

			override fun onActivityStarted(activity: Activity) {}
			override fun onActivityResumed(activity: Activity) {}
			override fun onActivityPaused(activity: Activity) {}
			override fun onActivityStopped(activity: Activity) {}
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
		}

		private var callbacksRegistered = false

		internal fun registerCallbacks(application: Application) {
			if (!callbacksRegistered) {
				application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
				callbacksRegistered = true
			}
		}

		private fun restartApp(activity: BaseActivity) {
			for (a in activities.asReversed()) {
				a.finish()
			}
			activities.clear()
			activity.start<MainActivity>()
			printActivityLog("${activity.getActivityName()} requestRestart")
			Process.killProcess(Process.myPid())
		}

		private fun printActivityLog(log: String) {
			val stack = activities.joinToString(", ") { it.getActivityName() }
			Log.activity("ACTIVITY STACK - $log[$stack]")
		}

		fun requestRestart(activity: BaseActivity) {
			AlertDialog.Builder(activity)
				.setTitle(activity.getString(string.requireRestart))
				.setMessage(activity.getString(string.doYouWantToRestartApp))
				.setPositiveButton(activity.getString(string.restart)) { dialog: DialogInterface, _: Int ->
					restartApp(activity)
					dialog.dismiss()
				}
				.setNegativeButton(activity.getString(string.cancel)) { dialog: DialogInterface, _: Int ->
					dialog.dismiss()
					activity.finish()
				}
				.show()
		}
	}

	val p: PreferenceManager by inject()
	val ws: WorkspaceManager by inject()
	val unipackRepo: UnipackRepository by inject()

	fun getActivityName(): String {
		return localClassName.split('.').last()
	}

	override fun startActivity(intent: Intent) {
		super.startActivity(intent)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, anim.activity_in, anim.activity_out)
		} else {
			@Suppress("DEPRECATION")
			overridePendingTransition(anim.activity_in, anim.activity_out)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		Log.activity("onCreate ${getActivityName()}")
		super.onCreate(savedInstanceState)

		registerCallbacks(application)
	}

	override fun onStart() {
		Log.activity("onStart ${getActivityName()}")
		super.onStart()
	}

	override fun onResume() {
		Log.activity("onResume ${getActivityName()}")
		super.onResume()
		volumeControlStream = AudioManager.STREAM_MUSIC
	}

	override fun onPause() {
		Log.activity("onPause ${getActivityName()}")
		super.onPause()
	}

	override fun onStop() {
		Log.activity("onStop ${getActivityName()}")
		super.onStop()
	}

	override fun onRestart() {
		Log.activity("onRestart ${getActivityName()}")
		super.onRestart()
	}

	override fun onDestroy() {
		Log.activity("onDestroy ${getActivityName()}")
		super.onDestroy()
	}
}