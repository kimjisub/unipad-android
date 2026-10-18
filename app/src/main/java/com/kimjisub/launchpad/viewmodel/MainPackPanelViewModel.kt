@file:Suppress("EmptyMethod") // private set on mutableStateOf generates empty setter bytecode

package com.kimjisub.launchpad.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CancellationException
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

	/** Reading the pack to count its sounds and LEDs failed, so the counts will not arrive. */
	var countsUnknown by mutableStateOf(false)
		private set

	val unipackEnt = repo.find(unipack.id)

	var deleteRequested by mutableStateOf(false)
		private set

	init {
		viewModelScope.launch(Dispatchers.IO) {
			repo.getOrCreate(unipack.id)
		}

		viewModelScope.launch(Dispatchers.IO) {
			val byteSize = readOrNull("UniPack size read failed") { unipack.getByteSize() } ?: return@launch
			val fileSizeString = FileManager.byteToMB(byteSize) + " MB"
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
				val loaded = readOrNull("UniPack detail read failed") { unipack.loadDetail() } != null
				withContext(Dispatchers.Main) {
					if (loaded) {
						soundCount = unipack.soundCount
						ledCount = unipack.ledTableCount
					} else {
						countsUnknown = true
					}
				}
			}
		}
	}

	/**
	 * [read] or null when it fails. The play screen catches the same failures and closes; here the
	 * pack is only being shown, so a pack too large or unreadable must not take the app down.
	 */
	private inline fun <T : Any> readOrNull(what: String, read: () -> T): T? = try {
		read()
	} catch (e: CancellationException) {
		throw e
	} catch (e: OutOfMemoryError) {
		Log.err("$what (OOM): ${unipack.getPathString()}", e)
		null
	} catch (e: Exception) {
		Log.err("$what: ${unipack.getPathString()}", e)
		null
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
		app.browse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode("UniPad ${unipack.title} ${unipack.producerName}"))
	}

	fun delete() {
		deleteRequested = true
	}

	fun clearDeleteRequest() {
		deleteRequested = false
	}

	companion object {
		/**
		 * The panel for [unipack], kept in [owner] while the pack is unchanged. A pack deleted and
		 * installed again under the same name keeps its path, so the folder's time is part of the key.
		 */
		fun of(owner: ViewModelStoreOwner, app: Application, unipack: UniPack): MainPackPanelViewModel =
			ViewModelProvider(owner, Factory(app, unipack))[key(unipack), MainPackPanelViewModel::class.java]

		fun key(unipack: UniPack) = "pack_${unipack.getPathString()}_${unipack.folderModified}"
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
