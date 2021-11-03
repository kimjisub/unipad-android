package com.kimjisub.launchpad.activity

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog.Builder
import androidx.core.content.res.ResourcesCompat
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
import com.kimjisub.design.extra.addOnPropertyChanged
import com.kimjisub.design.extra.getVirtualIndexFormSorted
import com.kimjisub.design.panel.MainPackPanel
import com.kimjisub.design.view.PackView
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.ThemeItem
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.launchpad.adapter.UniPackAdapter
import com.kimjisub.launchpad.adapter.UniPackAdapter.EventListener
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.databinding.ActivityMainBinding
import com.kimjisub.launchpad.db.AppDataBase
import com.kimjisub.launchpad.db.ent.UniPackENT
import com.kimjisub.launchpad.db.ent.UniPackOpenENT
import com.kimjisub.launchpad.db.util.observeOnce
import com.kimjisub.launchpad.db.util.observeRealChange
import com.kimjisub.launchpad.manager.FileManager
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

class MainActivity : BaseActivity() {
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

	////////////////////////////////////////////////////////////////////////////////////////////////

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityMainBinding.inflate(layoutInflater)
		setContentView(b.root)

		bm.load()

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
			startActivityForResult(intent, REQUEST_FB_STORE)
		}
		b.store.setOnLongClickListener { false }
		b.setting.setOnClickListener { start<SettingsActivity>() }
		b.setting.setOnLongClickListener {
			start<SettingLegacyActivity>()
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
		update(false)

		updatePanel(true)
	}

	override fun onProStatusUpdated(isPro: Boolean) {
		this.isPro = isPro
	}


	private fun checkThings() {
		versionCheck()
	}

	@SuppressLint("StaticFieldLeak")
	private fun update(animateNew: Boolean = true) {
		lastPlayIndex = -1
		if (listRefreshing) return
		b.swipeRefreshLayout.isRefreshing = true
		listRefreshing = true

		val sortMethods: Array<Comparator<UniPackItem>> = arrayOf(
			Comparator { a, b -> -a.unipack.title.compareTo(b.unipack.title) },
			Comparator { a, b -> -a.unipack.producerName.compareTo(b.unipack.producerName) },
			Comparator { a, b ->
				val aCount = db.unipackOpenDAO()!!.getCountSync(a.unipack.id)
				val bCount = db.unipackOpenDAO()!!.getCountSync(b.unipack.id)
				-aCount.compareTo(bCount)
			},
			Comparator { a, b ->
				val aDate = db.unipackOpenDAO()!!
					.getLastOpenedDateSync(a.unipack.id)?.created_at ?: Date(0)
				val bDate = db.unipackOpenDAO()!!
					.getLastOpenedDateSync(b.unipack.id)?.created_at ?: Date(0)
				-aDate.compareTo(bDate)
			},
			Comparator { a, b ->
				val aDate = a.unipack.lastModified()
				val bDate = b.unipack.lastModified()
				-aDate.compareTo(bDate)
			}
		)

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

				I_list = ArrayList(I_list.sortedWith(sortMethods[3]))
				I_list =
					ArrayList(I_list.sortedWith(Comparator { a, b ->
						sortMethods[p.sortMethod].compare(
							a,
							b
						) * if (p.sortType) -1 else 1
					}))

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

							if (selectedIndex == index)
								updatePanel(false)
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

				updatePanel(true)
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
			updatePanel(false)
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
		b.totalPanel.data.sortMethod.set(p.sortMethod)
		b.totalPanel.data.sortType.set(p.sortType)
		b.totalPanel.data.logo.set(resources.getDrawable(drawable.custom_logo))
		b.totalPanel.data.version.set(BuildConfig.VERSION_NAME)
		b.totalPanel.data.sort.addOnPropertyChanged {
			val sort = it.get()!!
			p.sortMethod = sort / 2
			p.sortType = sort % 2 == 1
			update()
		}
		b.totalPanel.data.selectedTheme.addOnPropertyChanged {
			val selectedThemeIndex = it.get()!!
			p.selectedTheme = themeItemList!![selectedThemeIndex].package_name
		}

		b.packPanel.onEventListener = object : MainPackPanel.OnEventListener {
			override fun onBookmarkClick(v: View) {
				val item = selected
				if (item != null) {
					val unipackENT = item.unipackENT
					unipackENT.observe(this@MainActivity, object : Observer<UniPackENT> {
						override fun onChanged(it: UniPackENT?) {
							unipackENT.removeObserver(this)
							CoroutineScope(Dispatchers.IO).launch {
								it!!.bookmark = !it.bookmark
								db.unipackDAO()!!.update(it)
							}
						}
					})
				}
			}

			override fun onEditClick(v: View) {}

			override fun onYoutubeClick(v: View) {
				Intent()
				Intent(Intent.ACTION_VIEW)

				val item = selected
				if (item != null)
					browse("https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title + "+" + item.unipack.producerName)
			}

			override fun onWebsiteClick(v: View) {
				val item = selected
				if (item != null) {
					val website: String? = item.unipack.website
					if (website != null)
						browse(website)
				}
			}

			override fun onFuncClick(v: View) {
				val item = selected
				if (item != null) Builder(this@MainActivity)
					.setTitle(getString(string.warning))
					.setMessage(getString(string.doYouWantToRemapUniPack))
					.setPositiveButton(getString(string.accept)) { _: DialogInterface?, _: Int ->
						autoMapping(
							item.unipack
						)
					}.setNegativeButton(
						getString(string.cancel),
						null
					)
					.show()
			}

			override fun onDeleteClick(v: View) {
				val item = selected
				if (item != null) Builder(this@MainActivity)
					.setTitle(getString(string.warning))
					.setMessage(getString(string.doYouWantToDeleteUniPack))
					.setPositiveButton(getString(string.accept)) { _: DialogInterface?, _: Int ->
						item.unipack.delete()
						update()
					}.setNegativeButton(
						getString(string.cancel),
						null
					)
					.show()
			}
		}
	}

	private fun updatePanel(hardWork: Boolean) {
		val selectedIndex = selectedIndex
		val animation: Animation = AnimationUtils.loadAnimation(
			this@MainActivity,
			if (selectedIndex != -1) anim.panel_in else anim.panel_out
		)
		animation.setAnimationListener(object : AnimationListener {
			override fun onAnimationStart(animation: Animation?) {
				b.packPanel.visibility = View.VISIBLE
				b.packPanel.alpha = 1f
			}

			override fun onAnimationEnd(animation: Animation?) {
				b.packPanel.visibility = if (selectedIndex != -1) View.VISIBLE else View.INVISIBLE
				b.packPanel.alpha = if (selectedIndex != -1) 1.toFloat() else 0.toFloat()
			}

			override fun onAnimationRepeat(animation: Animation?) {}
		})
		if (selectedIndex == -1) updatePanelMain(hardWork) else updatePanelPack(unipackList[selectedIndex])
		val visibility = b.packPanel.visibility
		if (((visibility == View.VISIBLE && selectedIndex == -1)
					|| (visibility == View.INVISIBLE && selectedIndex != -1))
		) b.packPanel.startAnimation(animation)
	}

	var themeItemList: ArrayList<ThemeItem>? = null
	var themeNameList: ArrayList<String>? = null
	private fun updateThemeList() {
		themeItemList = ThemeTool.getThemePackList(applicationContext)
		themeNameList = ArrayList()
		for (item: ThemeItem in themeItemList!!)
			themeNameList!!.add(item.name)
	}

	@SuppressLint("StaticFieldLeak")
	private fun updatePanelMain(hardWork: Boolean) {
		b.totalPanel.data.unipackCount.set(unipackList.size.toString())
		db.unipackOpenDAO()!!.count.observe(
			this,
			{ integer: Int? -> b.totalPanel.data.openCount.set(integer.toString()) })
		updateThemeList()

		b.totalPanel.data.themeList.set(themeNameList)

		try {
			val index = themeItemList!!.indexOfFirst { it.package_name == p.selectedTheme }
			b.totalPanel.data.selectedTheme.set(index)
		} catch (e: Exception) {
			b.totalPanel.data.selectedTheme.set(0)
		}
		if (hardWork)
			CoroutineScope(Dispatchers.IO).launch {
				val size = FileManager.getFolderSize(
					ws.mainWorkspace.file// todo 여러 workspace의 용량 계산, WorkspaceManager로 이동하기
				)

				withContext(Dispatchers.Main) {
					b.totalPanel.data.unipackCapacity.set(FileManager.byteToMB(size, "%.0f"))
				}
			}
	}

	@SuppressLint("StaticFieldLeak", "SetTextI18n")
	private fun updatePanelPack(item: UniPackItem) {
		val unipack = item.unipack
		val flagColor: Int = if (unipack.criticalError) colors.red else colors.skyblue
		item.flagColor = flagColor

		b.packPanel.data.apply {
			title.set(unipack.title)
			subtitle.set(unipack.producerName)
			padSize.set("${unipack.buttonX} × ${unipack.buttonY}")
			chainCount.set(unipack.chain.toString())
			websiteExist.set(unipack.website != null)
			path.set(item.unipack.getPathString())
			downloadedDate.set(Date(item.unipack.lastModified()))


			soundCount.set(getString(string.measuring))
			ledCount.set(getString(string.measuring))
			fileSize.set(getString(string.measuring))

			item.unipackENT.observeOnce(Observer {
				bookmark.set(it.bookmark)
			})
			db.unipackOpenDAO()!!.getCount(item.unipack.id)
				.observe(this@MainActivity, Observer {
					playCount.set(it.toString())
				})
			db.unipackOpenDAO()!!.getLastOpenedDate(item.unipack.id)
				.observe(this@MainActivity, Observer {
					lastPlayed.set(it?.created_at)
				})

			CoroutineScope(Dispatchers.IO).launch {
				val fileSizeString =
					FileManager.byteToMB(unipack.getByteSize()) + " MB"
				withContext(Dispatchers.Main) {
					//if ((path.get() == item.unipack)) // todo 다른 방식으로 활성 판넬 인식
					fileSize.set(fileSizeString)
				}
			}

			CoroutineScope(Dispatchers.IO).launch {
				item.unipack.loadDetail()
				withContext(Dispatchers.Main) {
					//if ((path.get() == item.unipack.F_project.path)) {// todo 다른 방식으로 활성 판넬 인식
					soundCount.set(item.unipack.soundCount.toString())
					ledCount.set(item.unipack.ledTableCount.toString())
					//}
				}
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
		Handler().postDelayed({ update() }, 1000)
		controller = midiController
		fbStoreCount.attachEventListener(true)
		bm.restorePurchase()

		val playStartUnitId = resources.getString(string.admob_play_start)
		ads.loadAds(playStartUnitId) {
			adsPlayStart = it
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)
		when (requestCode) {
			REQUEST_FB_STORE -> checkThings()
			else -> {
			}
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


	companion object {
		private const val REQUEST_FB_STORE = 100
	}
}