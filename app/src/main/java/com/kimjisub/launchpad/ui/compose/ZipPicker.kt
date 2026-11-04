package com.kimjisub.launchpad.ui.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.tool.FolderZips
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.ZipPickRoute
import com.kimjisub.launchpad.tool.tryLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks one zip archive. Where the system picker returns the tapped zip this is that picker; on
 * Android 7.0 and 7.1 ([ZipPickRoute.FOLDER]) a folder is picked and [ZipFolderDialog] lists its zips.
 */
class ZipPicker internal constructor(
	private val route: ZipPickRoute,
	private val launchDocument: () -> Boolean,
	private val launchFolder: () -> Boolean,
	private val context: Context,
	internal val folder: MutableState<Uri?>,
	internal val onPicked: (Uri) -> Unit,
) {
	/** Opens the system picker; false when no app on the device can pick files or folders. */
	fun launch(): Boolean = when (route) {
		ZipPickRoute.DOCUMENT -> launchDocument()
		ZipPickRoute.FOLDER -> pickFolder()
	}

	internal fun pickFolder(): Boolean {
		val launched = launchFolder()
		if (launched) Toast.makeText(context, R.string.zip_folder_pick_hint, Toast.LENGTH_LONG).show()
		return launched
	}
}

/**
 * Remembers a [ZipPicker] whose document route uses [documentContract] with [documentInput], so
 * each screen keeps the picker it has always opened on Android 8 and later. Show [ZipFolderDialog]
 * with it on the same screen.
 */
@Composable
fun <I> rememberZipPicker(
	documentContract: ActivityResultContract<I, Uri?>,
	documentInput: I,
	onPicked: (Uri) -> Unit,
	route: ZipPickRoute = ZipPickRoute.forSdk(Build.VERSION.SDK_INT),
): ZipPicker {
	val context = LocalContext.current
	val currentOnPicked by rememberUpdatedState(onPicked)
	val folder = rememberSaveable { mutableStateOf<Uri?>(null) }
	val documentLauncher = rememberLauncherForActivityResult(documentContract) { uri ->
		if (uri != null) currentOnPicked(uri)
	}
	val folderLauncher = rememberLauncherForActivityResult(OpenFolderShowingDeviceStorage()) { uri ->
		if (uri != null) folder.value = uri
	}
	return remember(route, documentInput) {
		ZipPicker(
			route = route,
			launchDocument = { documentLauncher.tryLaunch(documentInput) },
			launchFolder = { folderLauncher.tryLaunch(null) },
			context = context,
			folder = folder,
			onPicked = { uri -> currentOnPicked(uri) },
		)
	}
}

private sealed interface FolderListing {
	data object Loading : FolderListing
	data object Failed : FolderListing
	data class Read(val zips: List<FolderZips.Pickable>) : FolderListing
}

/** The zips of the folder [picker] was given, for Android 7.0 and 7.1; shows nothing otherwise. */
@Composable
fun ZipFolderDialog(picker: ZipPicker) {
	val folder = picker.folder.value ?: return
	val context = LocalContext.current
	var listing by remember(folder) { mutableStateOf<FolderListing>(FolderListing.Loading) }
	LaunchedEffect(folder) {
		listing = withContext(Dispatchers.IO) {
			try {
				FolderListing.Read(FolderZips.read(context.contentResolver, folder))
			} catch (e: Exception) {
				// The folder's grant ends with the process, and a provider may refuse to list it.
				Log.err("listing the picked folder failed", e)
				FolderListing.Failed
			}
		}
	}
	val close = { picker.folder.value = null }
	AppAlertDialog(
		onDismissRequest = close,
		title = { Text(stringResource(R.string.zip_folder_title)) },
		text = {
			when (val state = listing) {
				FolderListing.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
				FolderListing.Failed -> Text(stringResource(R.string.zip_folder_failed))
				is FolderListing.Read -> if (state.zips.isEmpty()) {
					Text(stringResource(R.string.zip_folder_empty))
				} else {
					LazyColumn(Modifier.heightIn(max = 360.dp)) {
						items(state.zips, key = { it.uri.toString() }) { zip ->
							Text(
								text = zip.name,
								modifier = Modifier
									.fillMaxWidth()
									.clickable {
										close()
										picker.onPicked(zip.uri)
									}
									.padding(vertical = 12.dp),
							)
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = {
				close()
				if (!picker.pickFolder()) Toast.makeText(context, R.string.picker_unavailable, Toast.LENGTH_LONG).show()
			}) {
				Text(stringResource(R.string.zip_folder_other))
			}
		},
		dismissButton = {
			TextButton(onClick = close) {
				Text(stringResource(R.string.cancel))
			}
		},
	)
}

/** Android 7.x hides the device's own storage in the picker unless asked; its Download folder lives there. */
private class OpenFolderShowingDeviceStorage : ActivityResultContracts.OpenDocumentTree() {
	override fun createIntent(context: Context, input: Uri?): Intent =
		super.createIntent(context, input).putExtra(EXTRA_SHOW_ADVANCED, true)

	private companion object {
		/** `DocumentsContract.EXTRA_SHOW_ADVANCED`, which is hidden from the SDK. */
		const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"
	}
}
