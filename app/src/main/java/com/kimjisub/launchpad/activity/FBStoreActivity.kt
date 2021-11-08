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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kimjisub.design.view.PackView
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.StoreAdapter
import com.kimjisub.launchpad.adapter.StoreAdapter.EventListener
import com.kimjisub.launchpad.adapter.StoreItem
import com.kimjisub.launchpad.databinding.ActivityStoreBinding
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import splitties.toast.toast
import java.io.File
import java.util.*


class FBStoreActivity : BaseActivity() {
	private lateinit var b: ActivityStoreBinding
	private var mRewardedAd: RewardedAd? = null
	private var isPro = false

	private val firebase_store: FirebaseManager by lazy { FirebaseManager("store") }
	private val firebase_storeCount: FirebaseManager by lazy { FirebaseManager("storeCount") }
	private val list: ArrayList<StoreItem> = ArrayList()
	private var adapter: StoreAdapter? = null
	private lateinit var downloadList: Array<File>

	private fun initVar(onFirst: Boolean) {
		if (onFirst) {
			CoroutineScope(Dispatchers.IO).launch {
				downloadList = ws.getUnipacks()
			}

			adapter = StoreAdapter(list, object : EventListener {
				override fun onViewClick(item: StoreItem, v: PackView) {
					togglePlay(item)
				}

				override fun onViewLongClick(item: StoreItem, v: PackView) {}
				override fun onPlayClick(item: StoreItem, v: PackView) {
					if (!item.downloaded && !item.downloading) {
						if (p.downloadCouponCount > 0 || isPro) {
							if (!isPro)
								p.downloadCouponCount--

							startDownload(getPackItemByCode(item.storeVO.code!!)!!)
						} else {
							showRewardedAd {
								p.downloadCouponCount--
								startDownload(getPackItemByCode(item.storeVO.code!!)!!)
							}
						}
					}
				}
			})
			adapter!!.registerAdapterDataObserver(object : AdapterDataObserver() {
				override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
					super.onItemRangeInserted(positionStart, itemCount)
					b.errItem.visibility = if (list.size == 0) View.VISIBLE else View.GONE
				}

				override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
					super.onItemRangeRemoved(positionStart, itemCount)
					b.errItem.visibility = if (list.size == 0) View.VISIBLE else View.GONE
				}
			})
			val divider =
				DividerItemDecoration(this@FBStoreActivity, DividerItemDecoration.VERTICAL)
			divider.setDrawable(resources.getDrawable(drawable.border_divider))
			b.recyclerView.addItemDecoration(divider)
			b.recyclerView.setHasFixedSize(false)
			b.recyclerView.layoutManager = LinearLayoutManager(this@FBStoreActivity)
			//b.recyclerView.setItemAnimator(null);


			b.recyclerView.adapter = adapter
		}
	}


	private fun initRewardedAd() {
		val storeDownloadCouponUnitId = resources.getString(string.admob_download_coupon)
		ads.loadRewardedAd(storeDownloadCouponUnitId) {
			mRewardedAd = it
		}
	}

	fun showRewardedAd(callback: (() -> Unit)? = null) {
		if (mRewardedAd != null) {
			ads.showRewardedAd(mRewardedAd!!) { _, _ ->
				initRewardedAd()
				callback?.invoke()
			}

		} else {
			// TODO ad not ready
		}
	}

	// =============================================================================================

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityStoreBinding.inflate(layoutInflater)
		bm.load()
		setContentView(b.root)
		initVar(true)

		b.totalPanel.b.logo.setImageResource(drawable.custom_logo)
		b.totalPanel.b.version.text = BuildConfig.VERSION_NAME
		b.totalPanel.b.storeCount.text = list.size.toString()
		firebase_store.setEventListener(object : ChildEventListener {
			override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
				try {
					val d: StoreVO = dataSnapshot.getValue(StoreVO::class.java)!!
					var isDownloaded = false
					for (dir in downloadList) {
						if (d.code == dir.name) {
							isDownloaded = true
							break
						}
					}
					list.add(0, StoreItem(d, isDownloaded))
					adapter!!.notifyItemInserted(0)
					updatePanelMain()
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}

			override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {
				try {
					val d: StoreVO = dataSnapshot.getValue(StoreVO::class.java)!!
					val item = getPackItemByCode(d.code!!)
					item!!.storeVO = d
					adapter!!.notifyItemChanged(list.indexOf(item), "update")
					val selectedIndex = selectedIndex
					if (selectedIndex != -1) {
						val changeCode = item.storeVO.code
						val selectedCode = list[selectedIndex].storeVO.code
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
		firebase_storeCount.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long = dataSnapshot.getValue(Long::class.java)!!
				p.prevStoreCount = data
			}

			override fun onCancelled(databaseError: DatabaseError) {}
		})


		firebase_store.attachEventListener(true)
		firebase_storeCount.attachEventListener(true)
	}

	override fun onProStatusUpdated(isPro: Boolean) {
		this.isPro = isPro
	}

	// ============================================================================================= List Manage


	internal fun togglePlay(target: StoreItem?) {
		try {
			for (item in list) {
				val packView = item.packView
				if (target != null && item.storeVO.code == target.storeVO.code)
					item.isToggle = !item.isToggle
				else
					item.isToggle = false
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
			for (item in list) {
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
		for (item in list)
			if (item.storeVO.code == code) {
				ret = item
				break
			}
		return ret
	}

	internal val downloadingCount: Int
		internal get() {
			var count = 0
			for (item in list) {
				if (item.downloading) count++
			}
			return count
		}

	internal val downloadedCount: Int
		internal get() {
			var count = 0
			for (item in list) {
				if (item.downloaded) count++
			}
			return count
		}

	@SuppressLint("StaticFieldLeak")
	internal fun startDownload(item: StoreItem) {
		val (code, title, producerName, isAutoPlay, isLED, downloadCount, URL) = item.storeVO

		val packView = item.packView!!
		val F_UniPackZip: File =
			FileManager.makeNextPath(ws.mainWorkspace.file, code!!, ".zip")
		val F_UniPack = File(ws.mainWorkspace.file, code)
		packView.toggleColor = colors.gray1
		packView.untoggleColor = colors.gray1
		packView.setPlayText("0%")

		item.downloading = true

		UniPackDownloader(
			context = this,
			title = item.storeVO.title!!,
			url = "https://us-central1-unipad-e41ab.cloudfunctions.net/downloadUniPackLegacy?code=$code",
			workspace = ws.mainWorkspace.file,
			folderName = item.storeVO.code!!,
			listener = object : UniPackDownloader.Listener {
				override fun onInstallStart() {
				}

				override fun onGetFileSize(
					fileSize: Long,
					contentLength: Long,
					preKnownFileSize: Long,
				) {
					val percent = 0
					val downloadedMB: String = FileManager.byteToMB(0)
					val fileSizeMB: String = FileManager.byteToMB(fileSize)

					packView.setPlayText("${percent}%\n${downloadedMB} / $fileSizeMB MB")
				}

				override fun onDownloadProgress(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long,
				) {
					val downloadedMB: String = FileManager.byteToMB(downloadedSize)
					val fileSizeMB: String = FileManager.byteToMB(fileSize)

					packView.setPlayText("${percent}%\n${downloadedMB} / $fileSizeMB MB")
				}

				override fun onDownloadProgressPercent(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long,
				) {
				}

				override fun onImportStart(zip: File) {
					packView.setPlayText(getString(string.importing))
					packView.toggleColor = colors.orange
					packView.untoggleColor = colors.orange
				}

				override fun onInstallComplete(folder: File, unipack: UniPack) {
					packView.setPlayText(getString(string.downloaded))
					packView.toggleColor = colors.green
					packView.untoggleColor = colors.green
					item.downloading = false
					item.downloaded = true
					updatePanel()
				}

				override fun onException(throwable: Throwable) {
					throwable.printStackTrace()
					packView.setPlayText(getString(string.failed))
					packView.toggleColor = colors.red
					packView.untoggleColor = colors.red
					item.downloading = false
				}
			})
	}

	// ============================================================================================= panel


	internal fun updatePanel() {
		val playIndex = selectedIndex
		val animation: Animation = AnimationUtils.loadAnimation(
			this@FBStoreActivity,
			if (playIndex != -1) anim.panel_in else anim.panel_out
		)
		animation.setAnimationListener(object : AnimationListener {
			override fun onAnimationStart(animation: Animation?) {
				b.packPanel.visibility = View.VISIBLE
				b.packPanel.alpha = 1f
			}

			override fun onAnimationEnd(animation: Animation?) {
				b.packPanel.visibility = if (playIndex != -1) View.VISIBLE else View.INVISIBLE
				b.packPanel.alpha = (if (playIndex != -1) 1.toFloat() else 0.toFloat())
			}

			override fun onAnimationRepeat(animation: Animation?) {}
		})
		if (playIndex == -1) updatePanelMain() else updatePanelPack(list[playIndex])
		val visibility = b.packPanel.visibility
		if (visibility == View.VISIBLE && playIndex == -1
			|| visibility == View.INVISIBLE && playIndex != -1
		) b.packPanel.startAnimation(animation)
	}

	internal fun updatePanelMain() {
		b.totalPanel.b.downloadedCount.text = downloadedCount.toString()
	}

	internal fun updatePanelPack(item: StoreItem?) {
		val storeVO = item!!.storeVO
		b.packPanel.updateTitle(storeVO.title!!)
		b.packPanel.updateSubtitle(storeVO.producerName!!)
		b.packPanel.updateDownloadCount(storeVO.downloadCount.toLong())
	}

	// ============================================================================================= Activity


	override fun onBackPressed() {
		if (selectedIndex != -1) togglePlay(null) else {
			if (downloadingCount > 0) toast(string.canNotQuitWhileDownloading) else super.onBackPressed()
		}
	}

	override fun onResume() {
		super.onResume()
		//initVar(false)
		//list.clear()
		//adapter!!.notifyDataSetChanged()
		//togglePlay(null)
		//updatePanel()

		initRewardedAd()
	}

	override fun onPause() {
		super.onPause()
		//firebase_store.attachEventListener(false)
		//firebase_storeCount.attachEventListener(false)
	}
}