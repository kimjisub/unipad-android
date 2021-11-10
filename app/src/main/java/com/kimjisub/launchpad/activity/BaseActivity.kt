package com.kimjisub.launchpad.activity

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.kimjisub.launchpad.R.anim
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.AdmobManager
import com.kimjisub.launchpad.manager.ColorManager
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.manager.billing.BillingModule
import com.kimjisub.launchpad.manager.billing.Sku
import com.kimjisub.launchpad.tool.Log
import org.koin.android.ext.android.inject
import splitties.activities.start

open class BaseActivity : AppCompatActivity(), BillingModule.Callback {

	companion object {
		private var activityList = ArrayList<BaseActivity>()
		internal fun onStartActivity(activity: BaseActivity) {
			activityList.add(activity)
			printActivityLog(activity.getActivityName() + " start")
		}

		internal fun onFinishActivity(activity: BaseActivity) {
			var exist = false
			val size = activityList.size
			for (i in 0 until size) {
				if (activityList[i] === activity) {
					activityList[i].finish()
					activityList.removeAt(i)
					exist = true
					break
				}
			}
			printActivityLog(activity.getActivityName() + " finish" + if (exist) "" else " error")
		}

		private fun restartApp(activity: BaseActivity) {
			val size = activityList.size
			for (i in size - 1 downTo 0) {
				activityList[i].finish()
				activityList.removeAt(i)
			}
			activity.start<MainActivity>()
			printActivityLog(activity.getActivityName() + " requestRestart")
			Process.killProcess(Process.myPid())
		}

		private fun printActivityLog(log: String) {
			val str = StringBuilder("ACTIVITY STACK - $log[")
			val size = activityList.size
			for (i in 0 until size) {
				val activity = activityList[i]
				str.append(", ").append(activity.getActivityName())
			}
			Log.activity("$str]")
		}

		fun requestRestart(context: Context) {
			AlertDialog.Builder(context)
				.setTitle(context.getString(string.requireRestart))
				.setMessage(context.getString(string.doYouWantToRestartApp))
				.setPositiveButton(context.getString(string.restart)) { dialog: DialogInterface, _: Int ->
					restartApp(context as BaseActivity)
					dialog.dismiss()
				}
				.setNegativeButton(context.getString(string.cancel)) { dialog: DialogInterface, _: Int ->
					dialog.dismiss()
					(context as BaseActivity).finish()
				}
				.show()
		}
	}

	val p: PreferenceManager by inject()
	val ws: WorkspaceManager by inject()
	val ads by lazy { AdmobManager(this) }
	val bm by lazy { BillingModule(this, lifecycleScope, this) }


	override fun onBillingPurchaseUpdate(skuDetails: SkuDetails, purchased: Boolean) {
		Log.billing("onPurchaseUpdate: ${skuDetails.sku} - $purchased")
		if (skuDetails.type == BillingClient.SkuType.SUBS)
			when (skuDetails.sku) {
				Sku.PRO -> {
					onProStatusUpdated(purchased)
				}
			}
	}

	open fun onProStatusUpdated(isPro: Boolean) {

	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	fun getActivityName(): String {
		return this.localClassName.split('.').last()
	}

	// ============================================================================================= Show Things, Get Resources


	val colors by lazy { ColorManager(this) }


	// ============================================================================================= Activity Cycle


	override fun startActivity(intent: Intent) {
		super.startActivity(intent)
		overridePendingTransition(anim.activity_in, anim.activity_out)
	}

	public override fun onCreate(savedInstanceState: Bundle?) {
		Log.activity("onCreate " + getActivityName())
		super.onCreate(savedInstanceState)

		/*Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
				paramThrowable.printStackTrace();
			}
		});*/

		onStartActivity(this)
	}

	public override fun onStart() {
		Log.activity("onStart " + getActivityName())
		super.onStart()
	}

	public override fun onResume() {
		Log.activity("onResume " + getActivityName())
		super.onResume()
		this.volumeControlStream = AudioManager.STREAM_MUSIC
	}

	public override fun onPause() {
		Log.activity("onPause " + getActivityName())
		super.onPause()
	}

	public override fun onStop() {
		Log.activity("onStop " + getActivityName())
		super.onStop()
	}

	public override fun onRestart() {
		Log.activity("onRestart " + getActivityName())
		super.onRestart()
	}

	public override fun onDestroy() {
		Log.activity("onDestroy " + getActivityName())
		super.onDestroy()
		onFinishActivity(this)
	}
}