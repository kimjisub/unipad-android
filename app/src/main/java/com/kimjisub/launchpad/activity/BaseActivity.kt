package com.kimjisub.launchpad.activity

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Environment
import android.os.Process
import androidx.ads.identifier.AdvertisingIdClient.isAdvertisingIdProviderAvailable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.kimjisub.launchpad.R.anim
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.ColorManager
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import splitties.activities.start
import java.io.File

open class BaseActivity : AppCompatActivity() {

	companion object {
		var activityList = ArrayList<BaseActivity>()
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

		internal fun restartApp(activity: BaseActivity) {
			val size = activityList.size
			for (i in size - 1 downTo 0) {
				activityList[i].finish()
				activityList.removeAt(i)
			}
			activity.start<MainActivity>()
			printActivityLog(activity.getActivityName() + " requestRestart")
			Process.killProcess(Process.myPid())
		}

		internal fun printActivityLog(log: String) {
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

	lateinit var p: PreferenceManager

	////////////////////////////////////////////////////////////////////////////////////////////////

	val uniPackWorkspace: File
		get() {
			if (p.storageIndex == -1) {
				val legacyWorkspace = File(Environment.getExternalStorageDirectory(), "Unipad")

				//todo thread
				FileManager.makeDirWhenNotExist(legacyWorkspace)
				FileManager.makeNomedia(legacyWorkspace)
				return legacyWorkspace
			}
			val filesDirs = ContextCompat.getExternalFilesDirs(applicationContext, "UniPack")
			if (filesDirs.size <= p.storageIndex)
				p.storageIndex = 0

			return ContextCompat.getExternalFilesDirs(
				applicationContext,
				"UniPack"
			)[p.storageIndex]
		}


	data class UniPackWorkspace(
		val name: String,
		val file: File,
		val index: Int
	) {
		override fun toString(): String {
			return "UniPackWorkspace(name='$name', file=$file, index=$index)"
		}
	}

	fun getUniPackWorkspaces(): Array<UniPackWorkspace> {
		val uniPackWorkspaces = ArrayList<UniPackWorkspace>()

		uniPackWorkspaces.add(
			UniPackWorkspace(
				"Legacy",
				File(Environment.getExternalStorageDirectory(), "Unipad"),
				-1
			)
		)

		val dirs = ContextCompat.getExternalFilesDirs(this, "UniPack")

		dirs.forEachIndexed { index, file ->

			val name = when {
				file.absolutePath.contains("/storage/emulated/0") -> "내부 저장소" // todo string.xml
				file.absolutePath.contains("/storage/0000-0000") -> "SD 카드"
				else -> "SD 카드 $index"
			}

			uniPackWorkspaces.add(
				UniPackWorkspace(name, file, index)
			)
		}

		return uniPackWorkspaces.toTypedArray()
	}

	fun getUniPackDirList(): Array<File> {
		return uniPackWorkspace.listFiles()!!
	}

	fun getActivityName(): String {
		return this.localClassName.split('.').last()
	}

	// Ads /////////////////////////////////////////////////////////////////////////////////////////

	private fun adsCooltime(callback: () -> Unit) {
		if (checkAdsCooltime()) {
			updateAdsCooltime()
			callback
		}else
			Log.admob("ads skip: cooltime")
	}

	private fun checkAdsCooltime(): Boolean {
		val prevTime = p.prevAdsShowTime
		val currTime = System.currentTimeMillis()
		return currTime < prevTime || currTime - prevTime >= Constant.ADSCOOLTIME
	}

	private fun updateAdsCooltime() {
		val currTime = System.currentTimeMillis()
		p.prevAdsShowTime = currTime
	}

	fun loadAds(unitId: String, callback: ((interstitialAd: InterstitialAd?) -> Unit)) {
		val adRequest = AdRequest.Builder().build()
		InterstitialAd.load(this, unitId, adRequest, object : InterstitialAdLoadCallback() {
			override fun onAdFailedToLoad(adError: LoadAdError) {
				Log.admob("Ads load fail: ${adError.message}")
				callback(null)
			}

			override fun onAdLoaded(interstitialAd: InterstitialAd) {
				Log.admob("Ads loaded.")
				callback(interstitialAd)
			}
		})
	}

	fun showAds(interstitialAd: InterstitialAd?, callback: (()->Unit)? = null) {
		Log.admob("Ads show: ${getActivityName()}")
		if (interstitialAd != null) {
			adsCooltime {
				Log.admob("Ads showed")
				interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
					override fun onAdShowedFullScreenContent() {
						super.onAdShowedFullScreenContent()
						Log.admob("onAdShowedFullScreenContent")
					}

					override fun onAdDismissedFullScreenContent() {
						super.onAdDismissedFullScreenContent()
						Log.admob("onAdDismissedFullScreenContent")
					}

					override fun onAdFailedToShowFullScreenContent(p0: AdError) {
						super.onAdFailedToShowFullScreenContent(p0)
						Log.admob("onAdFailedToShowFullScreenContent: ${p0.message}")
					}
				}
				interstitialAd.show(this@BaseActivity)
				callback?.invoke()
			}
		}else
			Log.admob("Ads not loaded")
	}

	fun immediatelyAds(unitId: String){
		loadAds(unitId){
			showAds(it)
		}
	}

	fun logAdsId(){
		if(isAdvertisingIdProviderAvailable(this)) {
			val adsId = AdvertisingIdClient.getAdvertisingIdInfo(this).id
			Log.admob("AdvertisingId: $adsId")
		}else{
			Log.admob("AdvertisingId: not available")
		}
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
		p = PreferenceManager(applicationContext)

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