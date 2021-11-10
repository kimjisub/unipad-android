package com.kimjisub.launchpad.activity

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.animation.Animation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.FragmentTransaction
import com.github.clans.fab.FloatingActionMenu.OnMenuToggleListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.kimjisub.design.dialog.FileExplorerDialog
import com.kimjisub.design.dialog.FileExplorerDialog.OnEventListener
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.databinding.ActivityMainBinding
import com.kimjisub.launchpad.fragment.MainListFragment
import com.kimjisub.launchpad.fragment.MainPackPanelFragment
import com.kimjisub.launchpad.fragment.MainTotalPanelFragment
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.UniPackAutoMapper
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel
import net.lingala.zip4j.progress.ProgressMonitor
import splitties.activities.start
import splitties.alertdialog.alertDialog
import splitties.alertdialog.message
import splitties.alertdialog.okButton
import splitties.alertdialog.title
import splitties.snackbar.action
import splitties.snackbar.longSnack
import java.io.File
import java.util.*

class MainActivity : BaseActivity(),
	MainListFragment.Callbacks,
	MainTotalPanelFragment.Callbacks,
	MainPackPanelFragment.Callbacks {
	private lateinit var b: ActivityMainBinding

	private var isPro = false


	private val storeAnimator: ValueAnimator by lazy {
		val animator = ObjectAnimator.ofObject(ArgbEvaluator(), colors.orange, colors.red)
		animator.duration = 300
		animator.repeatCount = Animation.INFINITE
		animator.repeatMode = ValueAnimator.REVERSE
		animator.addUpdateListener {
			val color = it.animatedValue as Int
			b.floatingMenu.menuButtonColorNormal = color
			b.floatingMenu.menuButtonColorPressed = color
			b.store.colorNormal = color
			b.store.colorPressed = color
		}
		animator
	}

	private val fbStoreCount: FirebaseManager by lazy {
		val firebaseManager = FirebaseManager("storeCount")
		firebaseManager.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long? = dataSnapshot.getValue(Long::class.java)
				val prev = p.prevStoreCount
				runOnUiThread { blink(data != prev) }
			}

			override fun onCancelled(databaseError: DatabaseError) {}
		})
		firebaseManager
	}

	private val midiController: MidiController by lazy {
		object : MidiController() {
			override fun onAttach() {
				Log.driverCycle("MainActivity onConnected()")
				updateLP()
			}

			override fun onDetach() {
				Log.driverCycle("MainActivity onDisconnected()")
			}

			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
				if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
					if (upDown) driver.sendPadLed(
						x,
						y,
						intArrayOf(40, 61)[(Math.random() * 2).toInt()]
					) else driver.sendPadLed(x, y, 0)
				}
			}

			override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
				if (f == 0 && upDown)
					listFragment.prev()
				else if (f == 1 && upDown)
					listFragment.next()
				else if (f == 2 && upDown)
					listFragment.currentClick()

			}

			override fun onChainTouch(c: Int, upDown: Boolean) {}

			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) {
				if ((cmd == 7) && (sig == 46) && (note == 0) && (velocity == -9)) updateLP()
			}
		}
	}

	private val settingsActivityResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			listFragment.update()
		}
	private val storeActivityResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			listFragment.update()
		}

	////////////////////////////////////////////////////////////////////////////////////////////////

	private val mainTotalPanelFragment by lazy { MainTotalPanelFragment() }
	private val listFragment by lazy { MainListFragment() }


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityMainBinding.inflate(layoutInflater)
		setContentView(b.root)

		bm.load()

		supportFragmentManager
			.beginTransaction()
			.replace(b.fragmentPanel.id, mainTotalPanelFragment)
			.commit()

		supportFragmentManager
			.beginTransaction()
			.replace(b.fragmentList.id, listFragment)
			.commit()



		b.reconnectLaunchpad.setOnClickListener {
			start<MidiSelectActivity>()
		}
		b.loadUniPack.setOnClickListener {
			FileExplorerDialog(this@MainActivity, p.fileExplorerPath,
				object : OnEventListener {
					override fun onFileSelected(filePath: String) {
						importUniPack(File(filePath))
					}

					override fun onPathChanged(folderPath: String) {
						p.fileExplorerPath = folderPath
					}
				})
				.show()
		}
		b.store.setOnClickListener {
			val intent = Intent(applicationContext, FBStoreActivity::class.java)
			storeActivityResultLauncher.launch(intent)
		}
		b.store.setOnLongClickListener { false }
		b.setting.setOnClickListener {
			val intent = Intent(applicationContext, SettingsActivity::class.java)
			settingsActivityResultLauncher.launch(intent)
		}
		b.setting.setOnLongClickListener {
			val intent = Intent(applicationContext, SettingLegacyActivity::class.java)
			settingsActivityResultLauncher.launch(intent)
			false
		}
		b.floatingMenu.setOnMenuToggleListener(object : OnMenuToggleListener {
			var handler = Handler()
			var runnable: Runnable = Runnable { b.floatingMenu.close(true) }

			override fun onMenuToggle(opened: Boolean) {
				if (opened) handler.postDelayed(runnable, 5000) else handler.removeCallbacks(
					runnable
				)
			}
		})
		checkThings()
	}

	override fun onProStatusUpdated(isPro: Boolean) {
		this.isPro = isPro
	}


	private fun checkThings() {
		versionCheck()
	}


	// UniPack

	@SuppressLint("StaticFieldLeak")
	private fun autoMapping(unipack: UniPack) {
		UniPackAutoMapper(unipack, object : UniPackAutoMapper.Listener {
			val progressDialog: ProgressDialog = ProgressDialog(this@MainActivity)
			override fun onStart() {
				progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
				progressDialog.setTitle(getString(string.importing))
				progressDialog.setMessage(getString(string.wait_a_sec))
				progressDialog.setCancelable(false)
				progressDialog.show()
			}

			override fun onGetWorkSize(size: Int) {
				progressDialog.max = size
			}

			override fun onProgress(progress: Int) {
				progressDialog.progress = progress
			}

			override fun onDone() {
				if (progressDialog.isShowing) progressDialog.dismiss()

				Builder(this@MainActivity)
					.setTitle(getString(string.success))
					.setMessage(getString(string.remapDone))
					.setPositiveButton(getString(string.accept), null)
					.show()
			}

			override fun onException(throwable: Throwable) {
				if (progressDialog.isShowing) progressDialog.dismiss()


				Builder(this@MainActivity)
					.setTitle(getString(string.failed))
					.setMessage(getString(string.remapFail))
					.setPositiveButton(getString(string.accept), null)
					.show()
			}
		})
	}

	@SuppressLint("StaticFieldLeak")
	private fun importUniPack(unipackFile: File) {
		lateinit var progressDialog: ProgressDialog

		UniPackImporter(
			context = this,
			unipackFile = unipackFile,
			ws.mainWorkspace.file,
			object :
				UniPackImporter.OnEventListener {
				override fun onImportStart(zip: File) {
					progressDialog = ProgressDialog(this@MainActivity)
					progressDialog.setTitle(getString(string.importing))
					progressDialog.setMessage(getString(string.wait_a_sec))
					progressDialog.setCancelable(false)
					progressDialog.show()
				}

				override fun onImportProgress(processMonitor: ProgressMonitor) {
					progressDialog.progress = processMonitor.percentDone
				}

				override fun onImportComplete(folder: File, unipack: UniPack) {
					when (unipack.errorDetail) {
						null -> {
							alertDialog {
								title = getString(string.importComplete)
								message = unipack.infoToString(this@MainActivity)
								okButton()
							}.show()
						}
						else -> {
							alertDialog {
								title = getString(string.warning)
								message = unipack.errorDetail!!
								okButton()
							}.show()
						}
					}
				}

				override fun onException(throwable: Throwable) {
					alertDialog {
						title = getString(string.importFailed)
						message = throwable.toString()
						okButton()
					}.show()
				}

			})
	}

	// ListManage


	// Panel

	private fun updatePanel() {
		val selectedUniPackItem = listFragment.selected
		if (selectedUniPackItem == null) { // Total
			// Pack 이 켜져있으면 닫기
			if (supportFragmentManager.backStackEntryCount >= 1)
				supportFragmentManager.popBackStack()
		} else { // Pack
			if (supportFragmentManager.backStackEntryCount >= 1)
				supportFragmentManager.popBackStack()

			supportFragmentManager.beginTransaction().apply {
				if (supportFragmentManager.backStackEntryCount == 0)
					setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)

				replace(b.fragmentPanel.id, MainPackPanelFragment(selectedUniPackItem))
				addToBackStack("pack")
				commit()
			}
		}
	}

	// Check

	private fun versionCheck() {
		val thisVersion = BuildConfig.VERSION_NAME
		if (thisVersion.contains('b'))
			return
		val currVersionJson = FirebaseRemoteConfig.getInstance().getString("android_version")
		if (currVersionJson.isNotEmpty()) {
			val gson: Gson = GsonBuilder().create()
			val currVersionList: List<String> =
				gson.fromJson(currVersionJson, object : TypeToken<List<String?>?>() {}.type)
			if (!currVersionList.contains(thisVersion))
				b.root.longSnack(
					getString(string.newVersionFound)
				) {
					action(getString(string.update)) {
						browse("https://play.google.com/store/apps/details?id=$packageName")
					}
				}
		}
	}

	private fun blink(bool: Boolean) {
		if (bool) storeAnimator.start() else storeAnimator.end()
	}

	// Controller

	private fun updateLP() {
		showWatermark()
		showSelectLPUI()
	}


	private fun showSelectLPUI() {
		if (listFragment.havePrev()) driver.sendFunctionkeyLed(0,
			63) else driver.sendFunctionkeyLed(0, 5)
		if (listFragment.haveNow()) driver.sendFunctionkeyLed(2, 61) else driver.sendFunctionkeyLed(
			2,
			0)
		if (listFragment.haveNext()) driver.sendFunctionkeyLed(1,
			63) else driver.sendFunctionkeyLed(1, 5)
	}

	private fun showWatermark() {
		driver.sendPadLed(3, 3, 61)
		driver.sendPadLed(3, 4, 40)
		driver.sendPadLed(4, 3, 40)
		driver.sendPadLed(4, 4, 61)
	}

	// ListFragment Callbacks

	override fun onListSelectedChange(
		index: Int,
	) {
		showSelectLPUI()
		updatePanel()
	}

	override fun onListUpdated() {
		mainTotalPanelFragment.update()
	}

	private var adsPlayStart: InterstitialAd? = null
	override fun onRequestAds() {
		ads.showAdsWithCooltime(adsPlayStart) {
			val playStartUnitId = resources.getString(string.admob_play_start)
			ads.loadAds(playStartUnitId) {
				adsPlayStart = it
			}
		}
	}

	// TotalPanelFragment Callbacks

	override fun onSortChangeListener(sort: Pair<MainTotalPanelViewModel.SortMethod, Boolean>) {
		listFragment.sort = sort
		listFragment.update()
	}

	// PackPanelFragment Callbacks

	override fun onDelete() {
		listFragment.update()
	}

	// Activity

	override fun onBackPressed() {
		if (!listFragment.deselect()) super.onBackPressed()
	}

	override fun onResume() {
		super.onResume()
		checkThings()
		controller = midiController
		fbStoreCount.attachEventListener(true)
		bm.restorePurchase()

		val playStartUnitId = resources.getString(string.admob_play_start)
		ads.loadAds(playStartUnitId) {
			adsPlayStart = it
		}
	}

	override fun onPause() {
		super.onPause()
		controller = midiController
		fbStoreCount.attachEventListener(false)
	}

	override fun onDestroy() {
		super.onDestroy()
		removeController(midiController)
	}
}