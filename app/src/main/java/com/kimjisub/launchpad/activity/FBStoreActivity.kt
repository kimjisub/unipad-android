package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.ui.compose.StorePackPanelContent
import com.kimjisub.launchpad.ui.compose.StoreTotalPanelContent
import com.kimjisub.launchpad.ui.compose.UnipackListItem
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.ui.theme.Green
import com.kimjisub.launchpad.ui.theme.Orange
import com.kimjisub.launchpad.ui.theme.Red
import com.kimjisub.launchpad.ui.theme.SkyBlue
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.NumberFormat

class StoreItemState(
	storeVO: StoreVO,
	downloaded: Boolean = false,
) {
	var storeVO by mutableStateOf(storeVO)
	var downloaded by mutableStateOf(downloaded)
	var downloading by mutableStateOf(false)
	var isToggle by mutableStateOf(false)
	var playText by mutableStateOf("")
	var flagColorOverride by mutableStateOf<Color?>(null)
}

class FBStoreActivity : BaseActivity() {
	companion object {
		private const val DOWNLOAD_BASE_URL = "https://us-central1-unipad-e41ab.cloudfunctions.net/downloadUniPackLegacy"
	}

	private val firebaseStore: FirebaseManager by lazy { FirebaseManager("store") }
	private val firebaseStoreCount: FirebaseManager by lazy { FirebaseManager("storeCount") }
	private val storeItems = mutableStateListOf<StoreItemState>()
	private var downloadList: List<UniPackItem> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		lifecycleScope.launch(Dispatchers.IO) {
			downloadList = ws.getUnipacks()
		}

		setContent {
			UniPadTheme {
				val selectedItem = storeItems.firstOrNull { it.isToggle }
				val hasDownloading = storeItems.any { it.downloading }

				BackHandler {
					if (selectedItem != null) {
						togglePlay(null)
					} else if (hasDownloading) {
						Snackbar.make(findViewById(android.R.id.content), R.string.canNotQuitWhileDownloading, Snackbar.LENGTH_SHORT).show()
					} else {
						finish()
					}
				}

				StoreScreen(
					storeItems = storeItems,
					selectedItem = selectedItem,
					onBackClick = { finish() },
					storeCount = storeItems.size,
					downloadedCount = storeItems.count { it.downloaded },
					onItemClick = { item -> togglePlay(item) },
					onPlayClick = { item ->
						if (!item.downloaded && !item.downloading) {
							startDownload(item)
						}
					},
					onDownloadClick = { item ->
						if (!item.downloaded && !item.downloading) {
							startDownload(item)
						}
					},
					onYoutubeClick = {
						val item = storeItems.firstOrNull { it.isToggle }
						if (item != null) {
							browse("https://www.youtube.com/results?search_query=UniPad+${item.storeVO.title}+${item.storeVO.producerName}")
						}
					},
					onWebsiteClick = {},
				)
			}
		}

		firebaseStore.setEventListener(object : ChildEventListener {
			override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
				try {
					val d: StoreVO = dataSnapshot.getValue(StoreVO::class.java) ?: return
					val isDownloaded = downloadList.any { it.unipack.id == d.code }
					storeItems.add(0, StoreItemState(d, isDownloaded))
				} catch (e: RuntimeException) {
					Log.err("onChildAdded failed", e)
				}
			}

			override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {
				try {
					val d: StoreVO = dataSnapshot.getValue(StoreVO::class.java) ?: return
					val code = d.code ?: return
					val item = storeItems.firstOrNull { it.storeVO.code == code } ?: return
					item.storeVO = d
				} catch (e: RuntimeException) {
					Log.err("onChildChanged failed", e)
				}
			}

			override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
			override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
			override fun onCancelled(databaseError: DatabaseError) {}
		})

		firebaseStoreCount.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long = dataSnapshot.getValue(Long::class.java) ?: return
				p.prevStoreCount = data
			}

			override fun onCancelled(databaseError: DatabaseError) {}
		})

		firebaseStore.attachEventListener(true)
		firebaseStoreCount.attachEventListener(true)
	}

	private fun togglePlay(target: StoreItemState?) {
		for (item in storeItems) {
			if (target != null && item.storeVO.code == target.storeVO.code)
				item.isToggle = !item.isToggle
			else
				item.isToggle = false
		}
	}

	private fun startDownload(item: StoreItemState) {
		val itemCode = item.storeVO.code ?: return
		item.flagColorOverride = Gray1
		item.playText = "0%"
		item.downloading = true

		UniPackDownloader(
			context = this,
			title = item.storeVO.title ?: "",
			url = "$DOWNLOAD_BASE_URL?code=$itemCode",
			workspace = ws.downloadWorkspace.file,
			folderName = item.storeVO.code ?: "",
			listener = object : UniPackDownloader.Listener {
				override fun onInstallStart() {}

				override fun onGetFileSize(
					fileSize: Long,
					contentLength: Long,
					preKnownFileSize: Long,
				) {
					val downloadedMB: String = FileManager.byteToMB(0)
					val fileSizeMB: String = FileManager.byteToMB(fileSize)
					item.playText = getString(R.string.download_progress_format, 0, downloadedMB, fileSizeMB)
				}

				override fun onDownloadProgress(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long,
				) {
					val downloadedMB: String = FileManager.byteToMB(downloadedSize)
					val fileSizeMB: String = FileManager.byteToMB(fileSize)
					item.playText = getString(R.string.download_progress_format, percent, downloadedMB, fileSizeMB)
				}

				override fun onDownloadProgressPercent(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long,
				) {}

				override fun onImportStart(zip: File) {
					item.playText = getString(R.string.importing)
					item.flagColorOverride = Orange
				}

				override fun onInstallComplete(folder: File, unipack: UniPack) {
					item.playText = getString(R.string.downloaded)
					item.flagColorOverride = Green
					item.downloading = false
					item.downloaded = true
				}

				override fun onException(throwable: Throwable) {
					Log.err("Store download failed", throwable)
					item.playText = getString(R.string.failed)
					item.flagColorOverride = Red
					item.downloading = false
				}
			},
			scope = lifecycleScope,
		)
	}

	override fun onDestroy() {
		firebaseStore.attachEventListener(false)
		firebaseStoreCount.attachEventListener(false)
		super.onDestroy()
	}
}


private fun parseDownloadPercent(playText: String): Float {
	val match = Regex("""^(\d+)%""").find(playText)
	return (match?.groupValues?.get(1)?.toFloatOrNull() ?: 0f) / 100f
}

private const val FLAG_ANIMATION_MS = 500

@Composable
private fun StoreScreen(
	storeItems: List<StoreItemState>,
	selectedItem: StoreItemState?,
	onBackClick: () -> Unit,
	storeCount: Int,
	downloadedCount: Int,
	onItemClick: (StoreItemState) -> Unit,
	onPlayClick: (StoreItemState) -> Unit,
	onDownloadClick: (StoreItemState) -> Unit,
	onYoutubeClick: () -> Unit,
	onWebsiteClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// Left panel (weight 2)
		Box(
			modifier = Modifier
				.weight(2f)
				.fillMaxHeight()
				.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
		) {
			Crossfade(
				targetState = selectedItem != null,
				label = "panel",
			) { showPack ->
				if (showPack) {
					val item = storeItems.firstOrNull { it.isToggle }
					if (item != null) {
						StorePackPanelContent(
							title = item.storeVO.title ?: "",
							subtitle = item.storeVO.producerName ?: "",
							downloadCount = NumberFormat.getInstance()
								.format(item.storeVO.downloadCount.toLong()),
							isLED = item.storeVO.isLED,
							isAutoPlay = item.storeVO.isAutoPlay,
							isDownloaded = item.downloaded,
							isDownloading = item.downloading,
							downloadProgress = parseDownloadPercent(item.playText),
							downloadStatusText = item.playText,
							onDownloadClick = { onDownloadClick(item) },
							onYoutubeClick = onYoutubeClick,
							onWebsiteClick = onWebsiteClick,
						)
					}
				} else {
					StoreTotalPanelContent(
						version = BuildConfig.VERSION_NAME,
						storeCount = storeCount,
						downloadedCount = downloadedCount,
					)
				}
			}
		}

		// Right panel (weight 3)
		Column(
			modifier = Modifier
				.weight(3f)
				.fillMaxHeight(),
		) {
			// Store header
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onBackClick) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = null,
						tint = Color.White,
					)
				}
				Icon(
					painter = painterResource(R.drawable.baseline_shopping_basket_white_24),
					contentDescription = null,
					modifier = Modifier.size(20.dp),
					tint = SkyBlue,
				)
				Spacer(Modifier.width(8.dp))
				Text(
					text = stringResource(R.string.store),
					fontSize = 16.sp,
					fontWeight = FontWeight.Bold,
					color = Color.White,
				)
				Spacer(Modifier.width(8.dp))
				Text(
					text = storeItems.size.toString(),
					fontSize = 12.sp,
					color = Gray1,
				)
			}

			if (storeItems.isEmpty()) {
				Column(
					modifier = Modifier
						.weight(1f)
						.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.Center,
				) {
					Icon(
						painter = painterResource(R.drawable.ic_error),
						contentDescription = null,
						modifier = Modifier.size(80.dp),
						tint = Gray1,
					)
					Spacer(Modifier.height(8.dp))
					Text(
						text = stringResource(R.string.UnableToAccessServer),
						color = Gray1,
						fontSize = 13.sp,
					)
				}
			} else {
				LazyColumn(
					modifier = Modifier.weight(1f),
					contentPadding = PaddingValues(top = 8.dp, bottom = 6.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					itemsIndexed(
						storeItems,
						key = { index, item -> item.storeVO.code ?: index },
					) { _, item ->
						StorePackListItem(
							item = item,
							onItemClick = { onItemClick(item) },
							onPlayClick = { onPlayClick(item) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun StorePackListItem(
	item: StoreItemState,
	onItemClick: () -> Unit,
	onPlayClick: () -> Unit,
) {
	val defaultColor = if (item.downloaded) Green else Red
	val flagWidth by animateDpAsState(
		targetValue = if (item.isToggle) 100.dp else 10.dp,
		animationSpec = tween(FLAG_ANIMATION_MS),
		label = "flagWidth",
	)
	val animatedFlagColor by animateColorAsState(
		targetValue = item.flagColorOverride ?: defaultColor,
		animationSpec = tween(FLAG_ANIMATION_MS),
		label = "flagColor",
	)

	UnipackListItem(
		title = item.storeVO.title ?: "",
		subtitle = item.storeVO.producerName ?: "",
		hasLed = item.storeVO.isLED,
		hasAutoPlay = item.storeVO.isAutoPlay,
		indicatorFontSize = 12.sp,
		flagColor = animatedFlagColor,
		flagWidth = flagWidth,
		contentStartPadding = flagWidth,
		flagClickable = item.isToggle,
		onFlagClick = onPlayClick,
		flagContent = {
			if (item.isToggle) {
				Text(
					text = item.playText.ifEmpty {
						stringResource(if (item.downloaded) R.string.downloaded else R.string.download)
					},
					fontSize = 12.sp,
					textAlign = TextAlign.Center,
					maxLines = 1,
				)
			}
		},
		onClick = onItemClick,
	)
}
