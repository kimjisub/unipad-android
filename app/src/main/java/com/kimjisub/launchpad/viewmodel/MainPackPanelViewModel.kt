@file:Suppress("EmptyMethod") // private set on mutableStateOf generates empty setter bytecode

package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject


class MainPackPanelViewModel(
	private val app: Application,
	val unipack: UniPack,
) : AndroidViewModel(app) {
	private val repo: UnipackRepository by app.inject()

	var soundCount by mutableStateOf<Int?>(null)
		private set
	var ledCount by mutableStateOf<Int?>(null)
		private set
	var fileSize by mutableStateOf<String?>(null)
		private set

	val unipackEnt = repo.find(unipack.id)

	var deleteRequested by mutableStateOf(false)
		private set

	init {
		viewModelScope.launch(Dispatchers.IO) {
			repo.getOrCreate(unipack.id)
		}

		viewModelScope.launch(Dispatchers.IO) {
			val fileSizeString =
				FileManager.byteToMB(unipack.getByteSize()) + " MB"
			withContext(Dispatchers.Main) {
				fileSize = fileSizeString
			}
		}

		if (unipack.detailLoaded) {
			soundCount = unipack.soundCount
			ledCount = unipack.ledTableCount
		} else {
			soundCount = null
			ledCount = null
			viewModelScope.launch(Dispatchers.IO) {
				unipack.loadDetail()
				withContext(Dispatchers.Main) {
					soundCount = unipack.soundCount
					ledCount = unipack.ledTableCount
				}
			}
		}
	}

	fun bookmarkToggle() {
		viewModelScope.launch(Dispatchers.IO) {
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
		deleteRequested = true
	}

	fun clearDeleteRequest() {
		deleteRequested = false
	}

	class Factory(
		private val app: Application,
		private val unipack: UniPack,
	) : ViewModelProvider.Factory {

		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MainPackPanelViewModel(app, unipack) as T
		}
	}
}
