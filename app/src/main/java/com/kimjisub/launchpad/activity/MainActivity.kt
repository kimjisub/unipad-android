package com.kimjisub.launchpad.activity

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.Animation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog.Builder
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
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
import com.kimjisub.design.extra.getVirtualIndexFormSorted
import com.kimjisub.design.view.PackView
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.UniPackAdapter
import com.kimjisub.launchpad.adapter.UniPackAdapter.EventListener
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.databinding.ActivityMainBinding
import com.kimjisub.launchpad.db.AppDataBase
import com.kimjisub.launchpad.db.ent.UniPackOpenENT
import com.kimjisub.launchpad.db.util.observeRealChange
import com.kimjisub.launchpad.fragment.MainPackPanelFragment
import com.kimjisub.launchpad.fragment.MainTotalPanelFragment
import com.kimjisub.launchpad.fragment.MainTotalPanelViewModel
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
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import kotlin.collections.ArrayList

class MainActivity : BaseActivity(), MainTotalPanelFragment.Callbacks {
	private lateinit var b: ActivityMainBinding

	private var isPro = false
		set(value) {
			field = value
			b.totalPanel.data.premium.set(field)
		}

	private val db: AppDataBase by lazy { AppDataBase.getInstance(this)!! }

	private var adsPlayStart: InterstitialAd? = null

	// List Management
	private var unipackList: ArrayList<UniPackItem> = ArrayList()
	private var lastPlayIndex = -1
	private var listRefreshing = false
	private val adapter: UniPackAdapter by lazy {
		val adapter = UniPackAdapter(unipackList, object : EventListener {
			override fun onViewClick(item: UniPackItem, v: PackView) {
				togglePlay(item)
			}

			override fun onViewLongClick(item: UniPackItem, v: PackView) {}

			override fun onPlayClick(item: UniPackItem, v: PackView) {
				pressPlay(item)
			}
		})
		adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
			override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
				super.onItemRangeInserted(positionStart, itemCount)
				b.errItem.visibility = if (unipackList.size == 0) View.VISIBLE else View.GONE
			}

			override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
				super.onItemRangeRemoved(positionStart, itemCount)
				b.errItem.visibility = if (unipackList.size == 0) View.VISIBLE else View.GONE
			}
		})

		adapter
	}

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
				if (f == 0 && upDown) {
					if (havePrev()) {
						togglePlay(lastPlayIndex - 1)
						b.recyclerView.smoothScrollToPosition(lastPlayIndex)
					} else showSelectLPUI()
				} else if (f == 1 && upDown) {
					if (haveNext()) {
						togglePlay(lastPlayIndex + 1)
						b.recyclerView.smoothScrollToPosition(lastPlayIndex)
					} else showSelectLPUI()
				} else if (f == 2 && upDown) {
					if (haveNow()) unipackList[lastPlayIndex].playClick?.invoke()
				}
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {}

			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) {
				if ((cmd == 7) && (sig == 46) && (note == 0) && (velocity == -9)) updateLP()
			}
		}
	}

	private val settingsActivityResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			update()
		}
	private val storeActivityResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			update()
		}

	////////////////////////////////////////////////////////////////////////////////////////////////

	private val mainTotalPanelFragment by lazy { MainTotalPanelFragment() }


	override fun sortChangeListener(sort: Pair<MainTotalPanelViewModel.SortMethod, Boolean>) {
		update()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityMainBinding.inflate(layoutInflater)
		setContentView(b.root)

		bm.load()

		supportFragmentManager
			.beginTransaction()
			.replace(b.panelFragment.id, mainTotalPanelFragment)
			.commit()

		val divider = DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL)
		val borderDivider = ResourcesCompat.getDrawable(resources, drawable.border_divider, null)!!
		divider.setDrawable(borderDivider)
		b.recyclerView.addItemDecoration(divider)
		b.recyclerView.setHasFixedSize(false)
		b.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
		b.recyclerView.adapter = adapter


		initPanel()

		b.swipeRefreshLayout.setOnRefreshListener { this.update() }
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
		b.errItem.setOnClickListener { start<FBStoreActivity>() }
		checkThings()
	}

	override fun onProStatusUpdated(isPro: Boolean) {
		this.isPro = isPro
	}


	private fun checkThings() {
		versionCheck()
	}

	@SuppressLint("StaticFieldLeak")
	private fun update(animateNew: Boolean = true) {
		Log.test("update")

		lastPlayIndex = -1
		if (listRefreshing) return
		b.swipeRefreshLayout.isRefreshing = true
		listRefreshing = true

		mainTotalPanelFragment.update()

		CoroutineScope(Dispatchers.IO).launch {
			var I_list = ArrayList<UniPackItem>()
			val I_added = ArrayList<UniPackItem>()
			val I_removed = ArrayList(unipackList)


			try {

				ws.getUnipacks().forEach {
					if (!it.isDirectory) return@forEach

					val unipack = UniPackFolder(it).load()
					val unipackENT = db.unipackDAO()!!.getOrCreate(unipack.id)

					val packItem = UniPackItem(unipack, unipackENT, animateNew)
					I_list.add(packItem)
				}

				val sort = mainTotalPanelFragment.sort

				if (sort != null) {
					val comparator = Comparator<UniPackItem> { a, b ->
						sort.first.comparator.compare(a, b) * if (p.sortOrder) 1 else -1
					}

					I_list = ArrayList(I_list.sortedWith(comparator))
				}

				for (item: UniPackItem in I_list) {
					var index = -1
					for ((i, item2: UniPackItem) in I_removed.withIndex()) {
						if ((item2.unipack == item.unipack)) {
							index = i
							break
						}
					}
					if (index != -1)
						I_removed.removeAt(index)
					else
						I_added.add(0, item)
				}

			} catch (e: Exception) {
				e.printStackTrace()
			}

			withContext(Dispatchers.Main) {
				for (added: UniPackItem in I_added) {
					val i = unipackList.getVirtualIndexFormSorted(Comparator { a, b ->
						a.unipack.lastModified().compareTo(
							b.unipack.lastModified()
						)
					}, added)

					unipackList.add(i, added)
					adapter.notifyItemInserted(i)

					added.unipackENTObserver =
						added.unipackENT.observeRealChange(this@MainActivity, Observer {
							val index = unipackList.indexOf(added)
							adapter.notifyItemChanged(index)

							/* todo unipackEnt가 변경되었을 때 Fragment 내부에서 알아서 감지하는지
							if (selectedIndex == index)
								updatePanel(false)*/
						}) { it.clone() }
				}
				for (removed: UniPackItem in I_removed) {
					for ((i, item: UniPackItem) in unipackList.withIndex()) {
						if ((item.unipack == removed.unipack)) {
							unipackList.removeAt(i)
							adapter.notifyItemRemoved(i)
							removed.unipackENT.removeObserver(removed.unipackENTObserver!!)
							b.totalPanel.data.unipackCount.set(unipackList.size.toString())
							break
						}
					}
				}

				var changed = false
				for ((to, target: UniPackItem) in I_list.withIndex()) {
					var from = -1
					for ((i, item) in adapter.list.withIndex())
						if (target.unipack == item.unipack)
							from = i
					if (from != -1 && from != to) {
						Collections.swap(adapter.list, from, to)
						changed = true
					}
				}
				if (changed)
					adapter.notifyDataSetChanged()

				if (I_added.size > 0) b.recyclerView.smoothScrollToPosition(0)
				b.swipeRefreshLayout.isRefreshing = false
				listRefreshing = false
			}
		}
	}

	// UniPack /////////////////////////////////////////////////////////////////////////////////////////

	private fun deleteUniPack(unipack: UniPack) {

	}

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
					when {
						unipack.errorDetail == null -> {
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

	// ListManage /////////////////////////////////////////////////////////////////////////////////////////

	private fun togglePlay(i: Int) {
		togglePlay(unipackList[i])
	}

	@SuppressLint("SetTextI18n")
	fun togglePlay(target: UniPackItem?) {
		try {
			for ((i, item: UniPackItem) in unipackList.withIndex()) {
				//val packView = item.packView
				if (target != null && (item.unipack == target.unipack)) {

					item.toggle = !item.toggle
					lastPlayIndex = i
					item.togglea?.invoke(item.toggle)
				} else if (item.toggle) {
					item.toggle = false
					item.togglea?.invoke(item.toggle)
				}
			}
			showSelectLPUI()
			updatePanel()
		} catch (e: ConcurrentModificationException) {
			e.printStackTrace()
		}
	}

	fun pressPlay(item: UniPackItem) {
		Thread {
			db.unipackOpenDAO()!!.insert(UniPackOpenENT(item.unipack.id, Date()))
		}.start()
		start<PlayActivity> {
			putExtra("path", item.unipack.getPathString())
		}
		ads.showAdsWithCooltime(adsPlayStart) {
			val playStartUnitId = resources.getString(string.admob_play_start)
			ads.loadAds(playStartUnitId) {
				adsPlayStart = it
			}
		}
		removeController((midiController))
	}

	private val selectedIndex: Int
		get() {
			var index = -1
			var i = 0
			for (item: UniPackItem in unipackList) {
				if (item.toggle) {
					index = i
					break
				}
				i++
			}
			return index
		}

	private val selected: UniPackItem?
		get() {
			var ret: UniPackItem? = null
			val playIndex = selectedIndex
			if (playIndex != -1) ret = unipackList[playIndex]
			return ret
		}

	// Panel /////////////////////////////////////////////////////////////////////////////////////////

	@SuppressLint("SetTextI18n")
	private fun initPanel() {

	}

	private fun updatePanel() {
		if (selectedIndex == -1) { // Total
			// Pack 이 켜져있으면 닫기
			if (supportFragmentManager.backStackEntryCount >= 1)
				supportFragmentManager.popBackStack()
		} else { // Pack
			if (supportFragmentManager.backStackEntryCount >= 1)
				supportFragmentManager.popBackStack()

			supportFragmentManager.beginTransaction().apply {
				if (supportFragmentManager.backStackEntryCount == 0)
					setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)

				replace(b.panelFragment.id, MainPackPanelFragment(unipackList[selectedIndex]))
				addToBackStack("pack")
				commit()
			}
		}
	}

	// Check /////////////////////////////////////////////////////////////////////////////////////////

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
					"${getString(string.newVersionFound)}"
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

	// Controller /////////////////////////////////////////////////////////////////////////////////////////

	private fun updateLP() {
		showWatermark()
		showSelectLPUI()
	}

	private fun haveNow(): Boolean {
		return 0 <= lastPlayIndex && lastPlayIndex <= unipackList.size - 1
	}

	private fun haveNext(): Boolean {
		return lastPlayIndex < unipackList.size - 1
	}

	private fun havePrev(): Boolean {
		return 0 < lastPlayIndex
	}

	private fun showSelectLPUI() {
		if (havePrev()) driver.sendFunctionkeyLed(0, 63) else driver.sendFunctionkeyLed(0, 5)
		if (haveNow()) driver.sendFunctionkeyLed(2, 61) else driver.sendFunctionkeyLed(2, 0)
		if (haveNext()) driver.sendFunctionkeyLed(1, 63) else driver.sendFunctionkeyLed(1, 5)
	}

	private fun showWatermark() {
		driver.sendPadLed(3, 3, 61)
		driver.sendPadLed(3, 4, 40)
		driver.sendPadLed(4, 3, 40)
		driver.sendPadLed(4, 4, 61)
	}

	// Activity /////////////////////////////////////////////////////////////////////////////////////////

	override fun onBackPressed() {
		if (selectedIndex != -1) togglePlay(null) else super.onBackPressed()
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