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
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.anjlab.android.iab.v3.TransactionDetails
import com.github.clans.fab.FloatingActionMenu.OnMenuToggleListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.kimjisub.design.PackView
import com.kimjisub.design.dialog.FileExplorerDialog
import com.kimjisub.design.dialog.FileExplorerDialog.OnEventListener
import com.kimjisub.design.panel.MainPackPanel
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.ThemeItem
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.launchpad.adapter.UniPackAdapter
import com.kimjisub.launchpad.adapter.UniPackAdapter.EventListener
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.db.AppDataBase
import com.kimjisub.launchpad.db.ent.UniPackENT
import com.kimjisub.launchpad.db.ent.UniPackOpenENT
import com.kimjisub.launchpad.db.util.observeOnce
import com.kimjisub.launchpad.db.util.observeRealChange
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.BillingManager.BillingEventListener
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.tool.UniPackAutoMapper
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.FileManager.getInnerFileLastModified
import com.kimjisub.manager.Log
import com.kimjisub.manager.extra.addOnPropertyChanged
import com.kimjisub.manager.extra.getVirtualIndexFormSorted
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.alert
import org.jetbrains.anko.browse
import org.jetbrains.anko.design.snackbar
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.startActivityForResult
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : BaseActivity() {

	private val db: AppDataBase by lazy { AppDataBase.getInstance(this)!! }
	private var billingManager: BillingManager? = null
	private val preferenceManager: PreferenceManager by lazy {PreferenceManager(this)}

	// List Management
	private var unipackList: ArrayList<UniPackItem> = ArrayList()
	private var lastPlayIndex = -1
	private var listRefreshing = false
	private val adapter: UniPackAdapter by lazy {
		val adapter = UniPackAdapter(unipackList, object : EventListener {
			override fun onViewClick(item: UniPackItem, v: PackView) {
				if (!item.moving) togglePlay(item)
			}

			override fun onViewLongClick(item: UniPackItem, v: PackView) {}

			override fun onPlayClick(item: UniPackItem, v: PackView) {
				if (!item.moving) pressPlay(item)
			}
		})
		adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
			override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
				super.onItemRangeInserted(positionStart, itemCount)
				LL_errItem.visibility = if (unipackList.size == 0) View.VISIBLE else View.GONE
			}

			override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
				super.onItemRangeRemoved(positionStart, itemCount)
				LL_errItem.visibility = if (unipackList.size == 0) View.VISIBLE else View.GONE
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
			FAM_floatingMenu.menuButtonColorNormal = color
			FAM_floatingMenu.menuButtonColorPressed = color
			FAB_store.colorNormal = color
			FAB_store.colorPressed = color
		}
		animator
	}

	private val fbStoreCount: FirebaseManager by lazy {
		val firebaseManager = FirebaseManager("storeCount")
		firebaseManager.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long? = dataSnapshot.getValue(Long::class.java)
				val prev = preference.prevStoreCount
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

			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int) {
				if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
					if (upDown) driver.sendPadLed(x, y, intArrayOf(40, 61)[(Math.random() * 2).toInt()]) else driver.sendPadLed(x, y, 0)
				}
			}

			override fun onFunctionkeyTouch(f: Int, upDown: Boolean) {
				if (f == 0 && upDown) {
					if (havePrev()) {
						togglePlay(lastPlayIndex - 1)
						RV_recyclerView.smoothScrollToPosition(lastPlayIndex)
					} else showSelectLPUI()
				} else if (f == 1 && upDown) {
					if (haveNext()) {
						togglePlay(lastPlayIndex + 1)
						RV_recyclerView.smoothScrollToPosition(lastPlayIndex)
					} else showSelectLPUI()
				} else if (f == 2 && upDown) {
					if (haveNow()) unipackList[lastPlayIndex].playClick?.invoke()
				}
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {}

			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velo: Int) {
				if ((cmd == 7) && (sig == 46) && (note == 0) && (velo == -9)) updateLP()
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_main)

		val divider = DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL)
		divider.setDrawable(resources.getDrawable(drawable.border_divider))
		RV_recyclerView.addItemDecoration(divider)
		RV_recyclerView.setHasFixedSize(false)
		RV_recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
		RV_recyclerView.adapter = adapter


		initPanel()
		loadAdmob()
		billingManager = BillingManager(this@MainActivity, object : BillingEventListener {
			override fun onProductPurchased(productId: String, details: TransactionDetails?) {}
			override fun onPurchaseHistoryRestored() {}
			override fun onBillingError(errorCode: Int, error: Throwable?) {}
			override fun onBillingInitialized() {}
			override fun onRefresh() {
				P_total.data.premium.set(billingManager!!.purchaseRemoveAds || billingManager!!.purchaseProTools)
				if (billingManager!!.showAds) {
					if (checkAdsCooltime()) {
						updateAdsCooltime()
						showAdmob()
					}
					/*todo ad
					AdRequest adRequest = new AdRequest.Builder().build();
					b.adView.loadAd(adRequest);*/
				}
				/*todo ad
				 else
					b.adView.setVisibility(View.GONE);*/
			}
		})

		SRL_swipeRefreshLayout.setOnRefreshListener { this.update() }
		FAB_reconnectLaunchpad.setOnClickListener {
			startActivity<MidiSelectActivity>()
		}
		FAB_loadUniPack.setOnClickListener {
			FileExplorerDialog(this@MainActivity, preference.fileExplorerPath,
				object : OnEventListener {
					override fun onFileSelected(filePath: String) {
						importUniPack(File(filePath))
					}

					override fun onPathChanged(folderPath: String) {
						preference.fileExplorerPath = folderPath
					}
				})
				.show()
		}
		FAB_store.setOnClickListener { startActivityForResult<FBStoreActivity>(0) }
		FAB_store.setOnLongClickListener { false }
		FAB_setting.setOnClickListener { startActivity<SettingActivity>() }
		FAM_floatingMenu.setOnMenuToggleListener(object : OnMenuToggleListener {
			var handler = Handler()
			var runnable: Runnable = Runnable { FAM_floatingMenu.close(true) }

			override fun onMenuToggle(opened: Boolean) {
				if (opened) handler.postDelayed(runnable, 5000) else handler.removeCallbacks(runnable)
			}
		})
		LL_errItem.setOnClickListener { startActivity<FBStoreActivity>() }
		checkThings()
		update(false)

		updatePanel(true)
	}

	private fun checkThings() {
		versionCheck()
	}

	@SuppressLint("StaticFieldLeak")
	private fun update(animateNew: Boolean = true) {
		lastPlayIndex = -1
		if (listRefreshing) return
		SRL_swipeRefreshLayout.isRefreshing = true
		listRefreshing = true

		val sortMethods: Array<Comparator<UniPackItem>> = arrayOf(
			Comparator { a, b -> -getInnerFileLastModified(a.unipack.F_project).compareTo(getInnerFileLastModified(b.unipack.F_project)) },
			Comparator { a, b -> getInnerFileLastModified(a.unipack.F_project).compareTo(getInnerFileLastModified(b.unipack.F_project)) },
			Comparator { a, b ->
				db.unipackOpenDAO()!!.getCountSync(a.unipack.F_project.name).compareTo(db.unipackOpenDAO()!!.getCountSync(b.unipack.F_project.name))
			},
			Comparator { a, b -> -db.unipackOpenDAO()!!.getCountSync(a.unipack.F_project.name).compareTo(db.unipackOpenDAO()!!.getCountSync(b.unipack.F_project.name)) },
			Comparator { a, b ->
				(db.unipackOpenDAO()!!.getLastOpenedDateSync(a.unipack.F_project.name)?.created_at
					?: Date(0)).compareTo(db.unipackOpenDAO()!!.getLastOpenedDateSync(b.unipack.F_project.name)?.created_at ?: Date(0))
			},
			Comparator { a, b ->
				-(db.unipackOpenDAO()!!.getLastOpenedDateSync(a.unipack.F_project.name)?.created_at
					?: Date(0)).compareTo(db.unipackOpenDAO()!!.getLastOpenedDateSync(b.unipack.F_project.name)?.created_at ?: Date(0))
			},
			Comparator { a, b -> a.unipack.title!!.compareTo(b.unipack.title!!) },
			Comparator { a, b -> -a.unipack.title!!.compareTo(b.unipack.title!!) },
			Comparator { a, b -> a.unipack.producerName!!.compareTo(b.unipack.producerName!!) },
			Comparator { a, b -> -a.unipack.producerName!!.compareTo(b.unipack.producerName!!) }
		)

		CoroutineScope(Dispatchers.IO).launch {
			var I_list = ArrayList<UniPackItem>()
			val I_added = ArrayList<UniPackItem>()
			val I_removed = ArrayList(unipackList)


			try {
				for (file: File in getUniPackDirList()) {
					if (!file.isDirectory) continue
					val unipack = UniPack(file, false)
					val unipackENT = db.unipackDAO()!!.getOrCreate(unipack.F_project.name)

					val packItem = UniPackItem(unipack, unipackENT, animateNew)
					I_list.add(packItem)
				}

				I_list = ArrayList(I_list.sortedWith(sortMethods[6]))
				I_list = ArrayList(I_list.sortedWith(sortMethods[preferenceManager.defaultSort]))

				for (item: UniPackItem in I_list) {
					var index = -1
					for ((i, item2: UniPackItem) in I_removed.withIndex()) {
						if ((item2.unipack.F_project.path == item.unipack.F_project.path)) {
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
						getInnerFileLastModified(a.unipack.F_project).compareTo(getInnerFileLastModified(b.unipack.F_project))
					}, added)

					unipackList.add(i, added)
					adapter.notifyItemInserted(i)

					added.unipackENTObserver = added.unipackENT.observeRealChange(this@MainActivity, Observer {
						val index = unipackList.indexOf(added)
						adapter.notifyItemChanged(index)

						if (selectedIndex == index)
							updatePanel(false)
					}) { it.clone() }
				}
				for (removed: UniPackItem in I_removed) {
					for ((i, item: UniPackItem) in unipackList.withIndex()) {
						if ((item.unipack.F_project.path == removed.unipack.F_project.path)) {
							Log.test("remove: #$i ")
							unipackList.removeAt(i)
							adapter.notifyItemRemoved(i)
							removed.unipackENT.removeObserver(removed.unipackENTObserver!!)
							P_total.data.unipackCount.set(unipackList.size.toString())
							break
						}
					}
				}

				var changed = false
				for ((to, target: UniPackItem) in I_list.withIndex()) {
					var from = -1
					for ((i, item) in adapter.list.withIndex())
						if (target.unipack.F_project.path == item.unipack.F_project.path)
							from = i
					if (from != -1 && from != to) {
						Collections.swap(adapter.list, from, to)
						Log.test("swap: $from -> $to")
						changed = true
					}
				}
				if (changed)
					adapter.notifyDataSetChanged()

				if (I_added.size > 0) RV_recyclerView.smoothScrollToPosition(0)
				SRL_swipeRefreshLayout.isRefreshing = false
				listRefreshing = false

				updatePanel(true)
			}
		}
	}

	// UniPack /////////////////////////////////////////////////////////////////////////////////////////

	private fun deleteUniPack(unipack: UniPack) {
		FileManager.deleteDirectory(unipack.F_project)
		update()
	}

	@SuppressLint("StaticFieldLeak")
	private fun autoMapping(unipack: UniPack) {
		UniPackAutoMapper(unipack, object : UniPackAutoMapper.Listener {
			val progressDialog: ProgressDialog = ProgressDialog(this@MainActivity)
			override fun onStart() {
				progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
				progressDialog.setTitle(getString(string.analyzing))
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
	private fun importUniPack(F_UniPackZip: File) {
		CoroutineScope(Dispatchers.IO).launch {

			var progressDialog = ProgressDialog(this@MainActivity)
			var msg1: String?
			var msg2: String?

			withContext(Dispatchers.Main) {
				progressDialog.setTitle(getString(string.analyzing))
				progressDialog.setMessage(getString(string.wait_a_sec))
				progressDialog.setCancelable(false)
				progressDialog.show()
			}

			val name: String = F_UniPackZip.name
			val name_ = name.substring(0, name.lastIndexOf("."))
			val F_UniPack: File = FileManager.makeNextPath(F_UniPackRootExt, name_, "/")
			try {
				FileManager.unZipFile(F_UniPackZip.path, F_UniPack.path)
				val unipack = UniPack(F_UniPack, true)
				when {
					unipack.errorDetail == null -> {
						msg1 = getString(string.analyzeComplete)
						msg2 = unipack.toString(this@MainActivity)
					}
					unipack.criticalError -> {
						msg1 = getString(string.analyzeFailed)
						msg2 = unipack.errorDetail
						FileManager.deleteDirectory(F_UniPack)
					}
					else -> {
						msg1 = getString(string.warning)
						msg2 = unipack.errorDetail
					}
				}
			} catch (e: Exception) {
				msg1 = getString(string.analyzeFailed)
				msg2 = e.toString()
				FileManager.deleteDirectory(F_UniPack)
			}

			withContext(Dispatchers.Main) {
				update()
				alert(msg1!!, msg2!!) {
					positiveButton(string.accept) { it.dismiss() }
				}.show()
				progressDialog.dismiss()
			}
		}
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
				if (target != null && (item.unipack.F_project.path == target.unipack.F_project.path)) {

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
		Thread(Runnable { db.unipackOpenDAO()!!.insert(UniPackOpenENT(item.unipack.F_project.name, Date())) }).start()
		startActivity<PlayActivity>("path" to item.unipack.F_project.path)
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

	// Pannel /////////////////////////////////////////////////////////////////////////////////////////

	@SuppressLint("SetTextI18n")
	private fun initPanel() {
		P_total.data.sortingMethod.set(preferenceManager.defaultSort)
		P_total.data.logo.set(resources.getDrawable(drawable.custom_logo))
		P_total.data.version.set(BuildConfig.VERSION_NAME)
		P_total.data.sortingMethod.addOnPropertyChanged {
			preferenceManager.defaultSort = it.get()!!
			update()
		}
		P_total.data.selectedTheme.addOnPropertyChanged {

		}

		P_pack.onEventListener = object : MainPackPanel.OnEventListener {
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

			override fun onStorageClick(v: View) {
				val item = selected
				if (item != null) {
					val source = File(item.unipack.F_project.path)
					val isInternal = FileManager.isInternalFile(this@MainActivity, source)
					val target = File(if (isInternal) F_UniPackRootExt else F_UniPackRootInt, source.name)
					CoroutineScope(Dispatchers.IO).launch {
						withContext(Dispatchers.Main) {
							item.moving = true
							P_pack.data.moving.set(item.moving)
						}
						FileManager.moveDirectory(source, target)
						withContext(Dispatchers.Main) {
							item.moving = false
							P_pack.data.moving.set(item.moving)
							update()
						}
					}
				}
			}

			override fun onYoutubeClick(v: View) {
				val item = selected
				if (item != null)
					browse("https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title)
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
					.setMessage(getString(string.doYouWantToRemapProject))
					.setPositiveButton(getString(string.accept)) { _: DialogInterface?, _: Int -> autoMapping(item.unipack) }.setNegativeButton(
						getString(string.cancel),
						null
					)
					.show()
			}

			override fun onDeleteClick(v: View) {
				val item = selected
				if (item != null) Builder(this@MainActivity)
					.setTitle(getString(string.warning))
					.setMessage(getString(string.doYouWantToDeleteProject))
					.setPositiveButton(getString(string.accept)) { _: DialogInterface?, _: Int -> deleteUniPack(item.unipack) }.setNegativeButton(
						getString(string.cancel),
						null
					)
					.show()
			}
		}
	}

	private fun updatePanel(hardWork: Boolean) {
		val selectedIndex = selectedIndex
		val animation: Animation = AnimationUtils.loadAnimation(this@MainActivity, if (selectedIndex != -1) anim.panel_in else anim.panel_out)
		animation.setAnimationListener(object : AnimationListener {
			override fun onAnimationStart(animation: Animation?) {
				P_pack.visibility = View.VISIBLE
				P_pack.alpha = 1f
			}

			override fun onAnimationEnd(animation: Animation?) {
				P_pack.visibility = if (selectedIndex != -1) View.VISIBLE else View.INVISIBLE
				P_pack.alpha = if (selectedIndex != -1) 1.toFloat() else 0.toFloat()
			}

			override fun onAnimationRepeat(animation: Animation?) {}
		})
		if (selectedIndex == -1) updatePanelMain(hardWork) else updatePanelPack(unipackList[selectedIndex])
		val visibility = P_pack.visibility
		if (((visibility == View.VISIBLE && selectedIndex == -1)
					|| (visibility == View.INVISIBLE && selectedIndex != -1))
		) P_pack.startAnimation(animation)
	}

	@SuppressLint("StaticFieldLeak")
	private fun updatePanelMain(hardWork: Boolean) {
		P_total.data.unipackCount.set(unipackList.size.toString())
		db.unipackOpenDAO()!!.count.observe(this, Observer { integer: Int? -> P_total.data.openCount.set(integer.toString()) })

		val themeItemList = ThemeTool.getThemePackList(applicationContext)
		val themeNameList = ArrayList<String>()
		for(item:ThemeItem in themeItemList)
			themeNameList.add(item.name)
		P_total.data.themeList.set(themeNameList)

		try {
			val index = themeItemList.indexOfFirst { it.package_name == preference.selectedTheme }
			P_total.data.selectedTheme.set(index)
		} catch (e: Exception) {
			P_total.data.selectedTheme.set(0)
		}
		if (hardWork)
			CoroutineScope(Dispatchers.IO).launch {
				val fileSize = FileManager.byteToMB(FileManager.getFolderSize(F_UniPackRootExt), "%.0f")
				withContext(Dispatchers.Main) {
					P_total.data.unipackCapacity.set(fileSize)
				}
			}
	}

	@SuppressLint("StaticFieldLeak", "SetTextI18n")
	private fun updatePanelPack(item: UniPackItem) {
		val unipack = item.unipack
		val flagColor: Int = if (unipack.criticalError) colors.red else colors.skyblue
		item.flagColor = flagColor

		P_pack.data.apply {

			storage.set(!FileManager.isInternalFile(this@MainActivity, unipack.F_project))
			title.set(unipack.title)
			subtitle.set(unipack.producerName)
			padSize.set("${unipack.buttonX} × ${unipack.buttonY}")
			chainCount.set(unipack.chain.toString())
			websiteExist.set(unipack.website != null)
			path.set(item.unipack.F_project.path)
			downloadedDate.set(Date(getInnerFileLastModified(item.unipack.F_project)))


			soundCount.set(getString(string.measuring))
			ledCount.set(getString(string.measuring))
			fileSize.set(getString(string.measuring))

			item.unipackENT.observeOnce(Observer {
				bookmark.set(it.bookmark)
			})
			db.unipackOpenDAO()!!.getCount(item.unipack.F_project.name).observe(this@MainActivity, Observer {
				playCount.set(it.toString())
			})
			db.unipackOpenDAO()!!.getLastOpenedDate(item.unipack.F_project.name).observe(this@MainActivity, Observer {
				lastPlayed.set(it?.created_at)
			})

			CoroutineScope(Dispatchers.IO).launch {
				val fileSizeString = FileManager.byteToMB(FileManager.getFolderSize(unipack.F_project)) + " MB"
				withContext(Dispatchers.Main) {
					if ((path.get() == item.unipack.F_project.path))
						fileSize.set(fileSizeString)
				}
			}

			CoroutineScope(Dispatchers.IO).launch {
				item.unipack = UniPack(item.unipack.F_project, true)
				withContext(Dispatchers.Main) {
					if ((path.get() == item.unipack.F_project.path)) {
						soundCount.set(item.unipack.soundCount.toString())
						ledCount.set(item.unipack.ledTableCount.toString())
					}
				}
			}
		}
	}

	// Check /////////////////////////////////////////////////////////////////////////////////////////

	private fun versionCheck() {
		val thisVersion = BuildConfig.VERSION_NAME
		val currVersionJson = FirebaseRemoteConfig.getInstance().getString("android_version")
		if (currVersionJson.isNotEmpty()) {
			val gson: Gson = GsonBuilder().create()
			val currVersionList: List<String> = gson.fromJson(currVersionJson, object : TypeToken<List<String?>?>() {}.type)
			if (!currVersionList.contains(thisVersion))
				CL_root.snackbar("${getString(string.newVersionFound)}\n$thisVersion → ${currVersionList[0]}", getString(string.update)) {
					browse("https://play.google.com/store/apps/details?id=$packageName")
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
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)
		when (requestCode) {
			0 -> checkThings()
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
}