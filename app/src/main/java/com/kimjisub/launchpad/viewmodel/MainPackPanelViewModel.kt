package com.kimjisub.launchpad.viewmodel

import android.app.Application
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.Event
import com.kimjisub.launchpad.tool.emit
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.lang.reflect.InvocationTargetException


class MainPackPanelViewModel(
	private val app: Application,
	val unipack: UniPack,
) : AndroidViewModel(app) {
	private val repo: UnipackRepository by app.inject()

	//val downloadedDate = Date(unipack.lastModified())
	val soundCount = MutableLiveData<Int?>()
	val ledCount = MutableLiveData<Int?>()
	val fileSize = MutableLiveData<String?>()

	//val bookmark = MutableLiveData<Boolean>()
	val unipackEnt = repo.find(unipack.id)

	val eventDelete = MutableLiveData<Event<Unit>>()

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

	fun delete() {
		eventDelete.emit()
	}

	/*b.packPanel.onEventListener = object : MainPackPanel.OnEventListener {

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

		}
	}*/

	class Factory(
		private val app: Application,
		private val unipack: UniPack,
	) : ViewModelProvider.NewInstanceFactory() {

		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			if (ViewModel::class.java.isAssignableFrom(modelClass)) {
				try {
					return modelClass.getConstructor(
						Application::class.java,
						UniPack::class.java)
						.newInstance(app, unipack)
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