package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.kimjisub.launchpad.db.AppDataBase
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class MainPackPanelViewModel(val app: Application) : AndroidViewModel(app) {
	val title = MutableLiveData<String>()
	val producerName = MutableLiveData<String>()
	val padSize = MutableLiveData<String>()
	val chainCount = MutableLiveData<Int>()
	val website = MutableLiveData<String?>()
	val path = MutableLiveData<String>()
	val downloadedDate = MutableLiveData<Date>()
	val soundCount = MutableLiveData<Int?>()
	val ledCount = MutableLiveData<Int?>()
	val fileSize = MutableLiveData<String?>()
	val bookmark = MutableLiveData<Boolean>()
	val playCount = MutableLiveData<Int>()
	val lastPlayed = MutableLiveData<Date?>()

	private val db: AppDataBase by lazy { AppDataBase.getInstance(app.baseContext)!! }

	init {
	}

	fun setUniPack(unipack: UniPack) {
		title.value = unipack.title
		producerName.value = unipack.producerName
		padSize.value = "${unipack.buttonX} × ${unipack.buttonY}"
		chainCount.value = unipack.chain
		website.value = unipack.website
		path.value = unipack.getPathString()
		downloadedDate.value = Date(unipack.lastModified())


		val unipackOpenDao = db.unipackOpenDAO()!!
		val unipackDao = db.unipackDAO()!!
		val unipackEnt = unipackDao.find(unipack.getPathString())

		unipackEnt.observeForever {
			bookmark.value = it.bookmark
		}

		unipackOpenDao.getCount(unipack.id).observeForever {
			playCount.value = it
		}
		unipackOpenDao.getLastOpenedDate(unipack.id).observeForever {
			lastPlayed.value = it?.created_at
		}

		fileSize.value = null
		CoroutineScope(Dispatchers.IO).launch {
			val fileSizeString =
				FileManager.byteToMB(unipack.getByteSize()) + " MB"
			withContext(Dispatchers.Main) {
				fileSize.value = (fileSizeString)
			}
		}

		if (unipack.detailLoaded) {
			soundCount.value = unipack.soundCount
			ledCount.value = unipack.ledTableCount
		} else {
			soundCount.value = null
			ledCount.value = null
			CoroutineScope(Dispatchers.IO).launch {
				unipack.loadDetail()
				withContext(Dispatchers.Main) {
					soundCount.value = unipack.soundCount
					ledCount.value = unipack.ledTableCount
				}
			}
		}
	}

	fun websiteClick() {
		website.value?.let { app.browse(it) }
	}

	fun youtubeClick() {
		// todo fix
		app.browse("https://www.youtube.com/results?search_query=UniPad+${title.value}+${producerName.value}")
	}

	/*b.packPanel.onEventListener = object : MainPackPanel.OnEventListener {
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

		// done
		override fun onYoutubeClick(v: View) {
			Intent()
			Intent(Intent.ACTION_VIEW)

			val item = selected
			if (item != null)
				browse("https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title + "+" + item.unipack.producerName)
		}

		// done
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
			if (item != null) AlertDialog.Builder(this@MainActivity)
				.setTitle(getString(R.string.warning))
				.setMessage(getString(R.string.doYouWantToRemapUniPack))
				.setPositiveButton(getString(R.string.accept)) { _: DialogInterface?, _: Int ->
					autoMapping(
						item.unipack
					)
				}.setNegativeButton(
					getString(R.string.cancel),
					null
				)
				.show()
		}

		override fun onDeleteClick(v: View) {
			val item = selected
			if (item != null) AlertDialog.Builder(this@MainActivity)
				.setTitle(getString(R.string.warning))
				.setMessage(getString(R.string.doYouWantToDeleteUniPack))
				.setPositiveButton(getString(R.string.accept)) { _: DialogInterface?, _: Int ->
					item.unipack.delete()
					update()
				}.setNegativeButton(
					getString(R.string.cancel),
					null
				)
				.show()
		}
	}*/
}