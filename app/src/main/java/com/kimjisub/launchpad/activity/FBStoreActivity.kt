package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kimjisub.design.PackView
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.StoreAdapter
import com.kimjisub.launchpad.adapter.StoreAdapter.EventListener
import com.kimjisub.launchpad.adapter.StoreItem
import com.kimjisub.launchpad.manager.PreferenceManager.PrevStoreCount
import com.kimjisub.launchpad.manager.Unipack
import com.kimjisub.launchpad.network.Networks
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.network.UnipackInstaller
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.android.synthetic.main.activity_store.*
import org.jetbrains.anko.toast
import java.io.File
import java.util.*

class FBStoreActivity : BaseActivity() {
	internal var firebase_store: FirebaseManager? = null
	internal var firebase_storeCount: FirebaseManager? = null
	internal var list: ArrayList<StoreItem>? = null
	internal var adapter: StoreAdapter? = null
	internal fun initVar(onFirst: Boolean) {
		if (onFirst) {
			firebase_store = FirebaseManager("store")
			firebase_storeCount = FirebaseManager("storeCount")
			list = ArrayList()
			adapter = StoreAdapter(list!!, object : EventListener {
				override fun onViewClick(item: StoreItem, v: PackView) {
					togglePlay(item)
				}

				override fun onViewLongClick(item: StoreItem, v: PackView) {}
				override fun onPlayClick(item: StoreItem, v: PackView) {
					if (!item.downloaded && !item.downloading) startDownload(getPackItemByCode(item.storeVO.code!!)!!)
				}
			})
			adapter!!.registerAdapterDataObserver(object : AdapterDataObserver() {
				override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
					super.onItemRangeInserted(positionStart, itemCount)
					LL_errItem.visibility = if (list!!.size == 0) View.VISIBLE else View.GONE
				}

				override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
					super.onItemRangeRemoved(positionStart, itemCount)
					LL_errItem.visibility = if (list!!.size == 0) View.VISIBLE else View.GONE
				}
			})
			val divider = DividerItemDecoration(this@FBStoreActivity, DividerItemDecoration.VERTICAL)
			divider.setDrawable(resources.getDrawable(drawable.border_divider))
			RV_recyclerView.addItemDecoration(divider)
			RV_recyclerView.setHasFixedSize(false)
			RV_recyclerView.layoutManager = LinearLayoutManager(this@FBStoreActivity)
			//b.recyclerView.setItemAnimator(null);


			RV_recyclerView.adapter = adapter
		}
	}

	// =============================================================================================

	val asdf: Array<File> by lazy { uniPackDirList }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_store)
		initVar(true)

		P_total.b.customLogo.setImageResource(drawable.custom_logo)
		P_total.b.version.text = BuildConfig.VERSION_NAME
		P_total.b.storeCount.text = list!!.size.toString()
		firebase_store!!.setEventListener(object : ChildEventListener {
			override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
				Log.test("onChildAdded: $s")
				try {
					val d: StoreVO = dataSnapshot.getValue(StoreVO::class.java)!!
					var isDownloaded = false
					for (dir in asdf) {
						if (d.code == dir.name) {
							isDownloaded = true
							break
						}
					}
					list!!.add(0, StoreItem(d, isDownloaded))
					adapter!!.notifyItemInserted(0)
					updatePanelMain()
				} catch (e: Exception) {
					e.printStackTrace()
				}
				Log.test("onChildAdded: ")
			}

			override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {
				Log.test("onChildChanged: $s")
				try {
					val d: StoreVO = dataSnapshot.getValue(StoreVO::class.java)!!
					val item = getPackItemByCode(d.code!!)
					item!!.storeVO = d
					adapter!!.notifyItemChanged(list!!.indexOf(item), "update")
					val selectedIndex = selectedIndex
					if (selectedIndex != -1) {
						val changeCode = item.storeVO.code
						val selectedCode = list!![selectedIndex].storeVO.code
						if (changeCode == selectedCode) updatePanelPack(item)
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}

			override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
			override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
			override fun onCancelled(databaseError: DatabaseError) {}
		})
		firebase_storeCount!!.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long = dataSnapshot.getValue(Long::class.java)!!
				PrevStoreCount.save(this@FBStoreActivity, data)
			}

			override fun onCancelled(databaseError: DatabaseError) {}
		})
	}

	// ============================================================================================= List Manage


	internal fun togglePlay(target: StoreItem?) {
		try {
			for (item in list!!) {
				val packView = item.packView
				if (target != null && item.storeVO.code == target.storeVO.code) item.isToggle = !item.isToggle else item.isToggle = false
				packView?.toggle(item.isToggle)
			}
			updatePanel()
		} catch (e: ConcurrentModificationException) {
			e.printStackTrace()
		}
	}

	internal val selectedIndex: Int
		internal get() {
			var index = -1
			var i = 0
			for (item in list!!) {
				if (item.isToggle) {
					index = i
					break
				}
				i++
			}
			return index
		}

	internal fun getPackItemByCode(code: String): StoreItem? {
		var ret: StoreItem? = null
		for (item in list!!)
			if (item.storeVO.code == code) {
				ret = item
				break
			}
		return ret
	}

	internal val downloadingCount: Int
		internal get() {
			var count = 0
			for (item in list!!) {
				if (item.downloading) count++
			}
			return count
		}

	internal val downloadedCount: Int
		internal get() {
			var count = 0
			for (item in list!!) {
				if (item.downloaded) count++
			}
			return count
		}

	@SuppressLint("StaticFieldLeak")
	internal fun startDownload(item: StoreItem) {
		val (code, title, producerName, isAutoPlay, isLED, downloadCount, URL) = item.storeVO

		val packView = item.packView!!
		val F_UniPackZip: File = FileManager.makeNextPath(F_UniPackRootExt, code!!, ".zip")
		val F_UniPack = File(F_UniPackRootExt, code)
		packView.toggleColor = colors.gray1
		packView.untoggleColor = colors.gray1
		packView.setPlayText("0%")

		item.downloading = true


		// todo thread
		Networks.sendGet("https://us-central1-unipad-e41ab.cloudfunctions.net/increaseDownloadCount/$code")


		UnipackInstaller(
				url = item.storeVO.URL!!,
				workspace = F_UniPackRootExt,
				folderName = item.storeVO.code!!,
				listener = object : UnipackInstaller.Listener {

					override fun onInstallStart() {
					}

					override fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {
						val percent = 0
						val downloadedMB: String = FileManager.byteToMB(0)
						val fileSizeMB: String = FileManager.byteToMB(fileSize)

						packView.setPlayText("${percent}%\n${downloadedMB} / $fileSizeMB MB")
					}

					override fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {
						val downloadedMB: String = FileManager.byteToMB(downloadedSize)
						val fileSizeMB: String = FileManager.byteToMB(fileSize)

						packView.setPlayText("${percent}%\n${downloadedMB} / $fileSizeMB MB")
					}

					override fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {
					}

					override fun onAnalyzeStart(zip: File) {
						packView.setPlayText(lang(string.analyzing))
						packView.toggleColor = colors.orange
						packView.untoggleColor = colors.orange
					}

					override fun onInstallComplete(folder: File, unipack: Unipack) {
						packView.setPlayText(lang(string.downloaded))
						packView.toggleColor = colors.green
						packView.untoggleColor = colors.green
						item.downloaded = true
						updatePanel()
					}

					override fun onException(throwable: Throwable) {
						packView.setPlayText(lang(string.failed))
						packView.toggleColor = colors.red
						packView.untoggleColor = colors.red
					}

				})
	}

	// ============================================================================================= panel


	internal fun updatePanel() {
		Log.test("panel")
		val playIndex = selectedIndex
		val animation: Animation = AnimationUtils.loadAnimation(this@FBStoreActivity, if (playIndex != -1) anim.panel_in else anim.panel_out)
		animation.setAnimationListener(object : AnimationListener {
			override fun onAnimationStart(animation: Animation?) {
				P_pack.visibility = View.VISIBLE
				P_pack.alpha = 1f
			}

			override fun onAnimationEnd(animation: Animation?) {
				P_pack.visibility = if (playIndex != -1) View.VISIBLE else View.INVISIBLE
				P_pack.alpha = (if (playIndex != -1) 1.toFloat() else 0.toFloat())
			}

			override fun onAnimationRepeat(animation: Animation?) {}
		})
		if (playIndex == -1) updatePanelMain() else updatePanelPack(list!![playIndex])
		val visibility = P_pack.visibility
		if (visibility == View.VISIBLE && playIndex == -1
				|| visibility == View.INVISIBLE && playIndex != -1) P_pack.startAnimation(animation)
	}

	internal fun updatePanelMain() {
		Log.test("panel main")
		P_total.b.downloadedCount.text = downloadedCount.toString()
	}

	internal fun updatePanelPack(item: StoreItem?) {
		Log.test("panel pack")
		val storeVO = item!!.storeVO
		P_pack.updateTitle(storeVO.title)
		P_pack.updateSubtitle(storeVO.producerName)
		P_pack.updateDownloadCount(storeVO.downloadCount.toLong())
	}

	// ============================================================================================= Activity


	override fun onBackPressed() {
		if (selectedIndex != -1) togglePlay(null) else {
			if (downloadingCount > 0) toast(string.canNotQuitWhileDownloading) else super.onBackPressed()
		}
	}

	override fun onResume() {
		super.onResume()
		initVar(false)
		list!!.clear()
		adapter!!.notifyDataSetChanged()
		togglePlay(null)
		updatePanel()
		firebase_store!!.attachEventListener(true)
		firebase_storeCount!!.attachEventListener(true)
	}

	override fun onPause() {
		super.onPause()
		firebase_store!!.attachEventListener(false)
		firebase_storeCount!!.attachEventListener(false)
	}
}