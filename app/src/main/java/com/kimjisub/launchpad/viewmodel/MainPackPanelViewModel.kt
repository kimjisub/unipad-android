package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kimjisub.launchpad.db.repository.UniPackRepository
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.util.*


class MainPackPanelViewModel(
	private val app: Application,
	private val repo: UniPackRepository,
	val unipack: UniPack,
) : AndroidViewModel(app) {
	//val downloadedDate = Date(unipack.lastModified())
	val soundCount = MutableLiveData<Int?>()
	val ledCount = MutableLiveData<Int?>()
	val fileSize = MutableLiveData<String?>()

	//val bookmark = MutableLiveData<Boolean>()
	val playCount by lazy { repo.openCount(unipack.id) }
	val lastPlayed by lazy { repo.getLastOpenedDate(unipack.id) }
	val unipackEnt by lazy { repo.find(unipack.id) }

	init {
		CoroutineScope(Dispatchers.IO).launch {
			repo.getOrCreate(unipack.id)
		}

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

	fun bookmarkToggle() {
		CoroutineScope(Dispatchers.IO).launch {
			repo.toggleBookmark(unipack.id)
		}
	}

	fun websiteClick() {
		unipack.website?.let { app.browse(it) }
	}

	fun youtubeClick() {
		app.browse("https://www.youtube.com/results?search_query=UniPad+${unipack.title}+${unipack.producerName}")
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

	class Factory(
		private val app: Application,
		private val repo: UniPackRepository,
		private val unipack: UniPack,
	) : ViewModelProvider.NewInstanceFactory() {

		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			if (ViewModel::class.java.isAssignableFrom(modelClass)) {
				try {
					return modelClass.getConstructor(
						Application::class.java,
						UniPackRepository::class.java,
						UniPack::class.java)
						.newInstance(app, repo, unipack)
				} catch (e: NoSuchMethodException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				} catch (e: IllegalAccessException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				} catch (e: InstantiationException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				} catch (e: InvocationTargetException) {
					throw RuntimeException("Cannot create an instance of $modelClass", e)
				}
			}
			return super.create(modelClass)
		}
	}
}