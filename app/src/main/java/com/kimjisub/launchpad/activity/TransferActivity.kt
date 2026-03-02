package com.kimjisub.launchpad.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.SafMigrationHelper
import com.kimjisub.launchpad.ui.theme.Green
import com.kimjisub.launchpad.ui.theme.Orange
import com.kimjisub.launchpad.ui.theme.Red
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File

class TransferActivity : AppCompatActivity() {

	companion object {
		const val EXTRA_SOURCE_TYPE = "source_type"
		const val EXTRA_SOURCE_PATH = "source_path"
		const val EXTRA_TARGET_TYPE = "target_type"
		const val EXTRA_TARGET_PATH = "target_path"
		const val EXTRA_MODE = "transfer_mode"
		const val EXTRA_TITLE = "title"
	}

	private val workspaceManager: WorkspaceManager by inject()
	private val prefManager: PreferenceManager by inject()

	private var screenState by mutableStateOf<TransferState>(TransferState.Loading)
	private var customTitle by mutableStateOf<String?>(null)

	private val safPickerLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri != null) {
			contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
			)
			prefManager.backupSafUri = uri.toString()
			// Re-scan to pick up the new SAF uri
			loadConfiguration()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			UniPadTheme {
				TransferScreen(
					state = screenState,
					customTitle = customTitle,
					onItemToggle = { index ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						current.items[index] = current.items[index].copy(selected = !current.items[index].selected)
					},
					onSelectAll = { selectAll ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						current.items.forEachIndexed { i, item ->
							current.items[i] = item.copy(selected = selectAll)
						}
					},
					onSelectFiltered = { filteredIndices, selectAll ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						filteredIndices.forEach { i ->
							current.items[i] = current.items[i].copy(selected = selectAll)
						}
					},
					onTargetSelect = { targetId ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						screenState = current.copy(
							selectedTargetId = targetId,
							showFormatSelector = targetId == "backup",
						)
					},
					onModeSelect = { mode ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						screenState = current.copy(mode = mode)
					},
					onFormatSelect = { format ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						screenState = current.copy(format = format)
					},
					onSourceSelect = { sourceId ->
						val current = screenState as? TransferState.Configuration ?: return@TransferScreen
						if (current.sourceSelectable) {
							selectSource(sourceId)
						}
					},
					onGrantBackupAccess = { launchSafPicker() },
					onStartTransfer = { startTransfer() },
					onCancel = { finish() },
					onDone = { finish() },
				)
			}
		}

		loadConfiguration()
	}

	private fun launchSafPicker() {
		val initialUri = DocumentsContract.buildDocumentUri(
			"com.android.externalstorage.documents",
			"primary:Documents"
		)
		safPickerLauncher.launch(initialUri)
	}

	private fun loadConfiguration() {
		screenState = TransferState.Loading
		customTitle = intent.getStringExtra(EXTRA_TITLE)
		lifecycleScope.launch {
			val sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE)
			val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)
			val targetType = intent.getStringExtra(EXTRA_TARGET_TYPE)
			val targetPath = intent.getStringExtra(EXTRA_TARGET_PATH)
			val modeStr = intent.getStringExtra(EXTRA_MODE)
			val mode = when (modeStr) {
				"move" -> TransferMode.MOVE
				else -> TransferMode.COPY
			}

			val workspaces = workspaceManager.availableWorkspaces.toList()
			val backupSafUri = prefManager.backupSafUri

			// Build targets
			val targets = mutableListOf<TransferTarget>()
			workspaces.forEach { ws ->
				targets.add(
					TransferTarget(
						id = "ws:${ws.file.path}",
						name = ws.name,
						description = getWorkspaceDescription(ws),
						file = ws.file,
					)
				)
			}

			if (backupSafUri != null) {
				targets.add(
					TransferTarget(
						id = "backup",
						name = getString(R.string.workspace_backup_documents),
						description = getString(R.string.backup_backup_description),
						safUri = backupSafUri,
					)
				)
			} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				targets.add(
					TransferTarget(
						id = "backup",
						name = getString(R.string.workspace_backup_documents),
						description = getString(R.string.backup_backup_description),
						needsSaf = true,
					)
				)
			}

			// Determine source and items
			when {
				sourceType == "workspace" && sourcePath != null -> {
					val items = withContext(Dispatchers.IO) { scanWorkspaceItems(File(sourcePath)) }
					val ws = workspaces.firstOrNull { it.file.path == sourcePath }
					val selectedTarget = when {
						targetType == "backup" -> "backup"
						targetPath != null -> targets.firstOrNull { it.file?.path == targetPath }?.id
						else -> null
					}
					screenState = TransferState.Configuration(
						sourceName = ws?.name ?: File(sourcePath).name,
						sourceId = "ws:$sourcePath",
						sourceSelectable = false,
						items = mutableStateListOf(*items.toTypedArray()),
						targets = targets.filter { it.id != "ws:$sourcePath" },
						selectedTargetId = selectedTarget,
						mode = mode,
						showFormatSelector = selectedTarget == "backup",
					)
				}

				sourceType == "backup" -> {
					if (backupSafUri != null) {
						val items = withContext(Dispatchers.IO) { scanBackupItems(backupSafUri) }
						screenState = TransferState.Configuration(
							sourceName = "Documents/Unipad",
							sourceId = "backup",
							sourceSelectable = false,
							items = mutableStateListOf(*items.toTypedArray()),
							targets = targets.filter { it.id != "backup" },
							selectedTargetId = targetPath?.let { p -> targets.firstOrNull { it.file?.path == p }?.id },
							mode = mode,
						)
					} else {
						// Need SAF permission first
						screenState = TransferState.Configuration(
							sourceName = "Documents/Unipad",
							sourceId = "backup",
							sourceSelectable = false,
							items = mutableStateListOf(),
							targets = targets.filter { it.id != "backup" },
							selectedTargetId = null,
							mode = mode,
							needsSourceSaf = true,
						)
					}
				}

				targetType == "backup" -> {
					// Source not specified, let user pick from workspaces
					// Target is fixed to backup folder
					val backupTarget = targets.firstOrNull { it.id == "backup" }
					screenState = TransferState.Configuration(
						sourceName = "",
						sourceId = null,
						sourceSelectable = true,
						items = mutableStateListOf(),
						targets = if (backupTarget != null) listOf(backupTarget) else emptyList(),
						selectedTargetId = "backup",
						mode = mode,
						format = TransferFormat.ZIP,
						showFormatSelector = true,
						sourceOptions = targets.filter { it.id != "backup" && it.file != null },
						fixedTargetName = backupTarget?.name ?: getString(R.string.workspace_backup_documents),
					)
				}

				else -> {
					// Generic: let user pick source workspace
					screenState = TransferState.Configuration(
						sourceName = "",
						sourceId = null,
						sourceSelectable = true,
						items = mutableStateListOf(),
						targets = targets,
						selectedTargetId = null,
						mode = mode,
						sourceOptions = targets.filter { it.file != null },
					)
				}
			}
		}
	}

	private fun selectSource(sourceId: String) {
		lifecycleScope.launch {
			val current = screenState as? TransferState.Configuration ?: return@launch
			val sourceTarget = (current.sourceOptions ?: emptyList()).firstOrNull { it.id == sourceId }
				?: return@launch

			val items = if (sourceTarget.file != null) {
				withContext(Dispatchers.IO) { scanWorkspaceItems(sourceTarget.file) }
			} else if (sourceTarget.safUri != null) {
				withContext(Dispatchers.IO) { scanBackupItems(sourceTarget.safUri) }
			} else emptyList()

			val newTargets = if (current.fixedTargetName != null) {
				// Target is fixed (e.g. backup mode) - keep existing targets
				current.targets
			} else {
				buildAllTargets().filter { it.id != sourceId }
			}
			screenState = current.copy(
				sourceName = sourceTarget.name,
				sourceId = sourceId,
				items = mutableStateListOf(*items.toTypedArray()),
				targets = newTargets,
			)
		}
	}

	private fun buildAllTargets(): List<TransferTarget> {
		val targets = mutableListOf<TransferTarget>()
		workspaceManager.availableWorkspaces.forEach { ws ->
			targets.add(
				TransferTarget(
					id = "ws:${ws.file.path}",
					name = ws.name,
					description = getWorkspaceDescription(ws),
					file = ws.file,
				)
			)
		}
		val backupSafUri = prefManager.backupSafUri
		if (backupSafUri != null) {
			targets.add(
				TransferTarget(
					id = "backup",
					name = getString(R.string.workspace_backup_documents),
					description = getString(R.string.backup_backup_description),
					safUri = backupSafUri,
				)
			)
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			targets.add(
				TransferTarget(
					id = "backup",
					name = getString(R.string.workspace_backup_documents),
					description = getString(R.string.backup_backup_description),
					needsSaf = true,
				)
			)
		}
		return targets
	}

	private fun scanWorkspaceItems(dir: File): List<TransferItem> {
		if (!dir.exists() || !dir.canRead()) return emptyList()
		return dir.listFiles()
			?.filter { it.isDirectory && it.name != ".nomedia" }
			?.map { TransferItem(name = it.name, file = it) }
			?: emptyList()
	}

	private fun scanBackupItems(safUri: String): List<TransferItem> {
		val treeDoc = DocumentFile.fromTreeUri(this, safUri.toUri()) ?: return emptyList()
		val items = mutableListOf<TransferItem>()
		treeDoc.listFiles().forEach { doc ->
			val name = doc.name ?: return@forEach
			when {
				doc.isDirectory -> items.add(TransferItem(name = name, documentFile = doc))
				name.endsWith(".zip", ignoreCase = true) ->
					items.add(TransferItem(name = name.removeSuffix(".zip"), documentFile = doc, isZip = true))
			}
		}
		return items
	}

	private fun startTransfer() {
		val config = screenState as? TransferState.Configuration ?: return
		val selectedItems = config.items.filter { it.selected }
		if (selectedItems.isEmpty()) return
		val targetId = config.selectedTargetId ?: return
		val target = config.targets.firstOrNull { it.id == targetId } ?: return
		val deleteSource = config.mode == TransferMode.MOVE

		screenState = TransferState.Executing(0, selectedItems.size, "")

		lifecycleScope.launch {
			val helper = SafMigrationHelper(this@TransferActivity)
			val result: SafMigrationHelper.TransferResult

			val sourceFiles = selectedItems.mapNotNull { it.file }

			result = when {
				// File → File
				sourceFiles.isNotEmpty() && target.file != null -> {
					helper.transferFileToFile(sourceFiles, target.file, deleteSource) { cur, total, name ->
						screenState = TransferState.Executing(cur, total, name)
					}
				}
				// File → SAF (ZIP format)
				sourceFiles.isNotEmpty() && target.safUri != null && config.format == TransferFormat.ZIP -> {
					val treeDoc = DocumentFile.fromTreeUri(this@TransferActivity, target.safUri.toUri())
					if (treeDoc != null) {
						helper.transferFileToSafZip(sourceFiles, treeDoc, deleteSource) { cur, total, name ->
							screenState = TransferState.Executing(cur, total, name)
						}
					} else {
						SafMigrationHelper.TransferResult(selectedItems.size, 0, 0, selectedItems.size, listOf("Cannot access backup folder"))
					}
				}
				// File → SAF (folder format)
				sourceFiles.isNotEmpty() && target.safUri != null -> {
					val treeDoc = DocumentFile.fromTreeUri(this@TransferActivity, target.safUri.toUri())
					if (treeDoc != null) {
						helper.transferFileToSaf(sourceFiles, treeDoc, deleteSource) { cur, total, name ->
							screenState = TransferState.Executing(cur, total, name)
						}
					} else {
						SafMigrationHelper.TransferResult(selectedItems.size, 0, 0, selectedItems.size, listOf("Cannot access backup folder"))
					}
				}
				// SAF → File (mixed: folders + ZIPs)
				target.file != null -> {
					val folderItems = selectedItems.filter { !it.isZip && it.documentFile != null }
					val zipItems = selectedItems.filter { it.isZip && it.documentFile != null }

					var totalTransferred = 0
					var totalSkipped = 0
					var totalFailed = 0
					val totalErrors = mutableListOf<String>()
					var progressOffset = 0

					if (folderItems.isNotEmpty()) {
						val folderResult = helper.transferSafToFile(
							folderItems.map { it.documentFile!! },
							target.file,
							deleteSource,
						) { cur, total, name ->
							screenState = TransferState.Executing(cur, selectedItems.size, name)
						}
						totalTransferred += folderResult.transferred
						totalSkipped += folderResult.skipped
						totalFailed += folderResult.failed
						totalErrors.addAll(folderResult.errors)
						progressOffset = folderItems.size
					}

					if (zipItems.isNotEmpty()) {
						val zipResult = helper.transferSafZipToFile(
							zipItems.map { it.documentFile!! },
							target.file,
							deleteSource,
						) { cur, total, name ->
							screenState = TransferState.Executing(progressOffset + cur, selectedItems.size, name)
						}
						totalTransferred += zipResult.transferred
						totalSkipped += zipResult.skipped
						totalFailed += zipResult.failed
						totalErrors.addAll(zipResult.errors)
					}

					SafMigrationHelper.TransferResult(
						selectedItems.size, totalTransferred, totalSkipped, totalFailed, totalErrors,
					)
				}
				else -> SafMigrationHelper.TransferResult(selectedItems.size, 0, 0, selectedItems.size, listOf("Invalid source/target combination"))
			}

			screenState = TransferState.Complete(result)
		}
	}

	private fun getWorkspaceDescription(workspace: WorkspaceManager.Workspace): String {
		val name = workspace.name
		return when {
			name == getString(R.string.workspace_app_storage) ->
				getString(R.string.workspace_desc_app_storage)
			name == getString(R.string.workspace_documents_android10) ->
				getString(R.string.workspace_desc_documents_android10)
			name.contains("SD") ->
				getString(R.string.workspace_desc_external_sd)
			else -> ""
		}
	}
}

// -- Data --

private data class TransferItem(
	val name: String,
	val file: File? = null,
	val documentFile: DocumentFile? = null,
	val selected: Boolean = true,
	val isZip: Boolean = false,
)

private data class TransferTarget(
	val id: String,
	val name: String,
	val description: String = "",
	val file: File? = null,
	val safUri: String? = null,
	val needsSaf: Boolean = false,
)

private enum class TransferMode { COPY, MOVE }
private enum class TransferFormat { FOLDER, ZIP }

// -- State --

private sealed class TransferState {
	data object Loading : TransferState()
	data class Configuration(
		val sourceName: String,
		val sourceId: String?,
		val sourceSelectable: Boolean,
		val items: MutableList<TransferItem>,
		val targets: List<TransferTarget>,
		val selectedTargetId: String?,
		val mode: TransferMode = TransferMode.COPY,
		val format: TransferFormat = TransferFormat.FOLDER,
		val showFormatSelector: Boolean = false,
		val sourceOptions: List<TransferTarget>? = null,
		val needsSourceSaf: Boolean = false,
		val fixedTargetName: String? = null,
	) : TransferState()

	data class Executing(val current: Int, val total: Int, val currentName: String) : TransferState()
	data class Complete(val result: SafMigrationHelper.TransferResult) : TransferState()
}

// -- Colors (matching SettingsActivity) --

private val ContentBg = Color(0xFF161E2B)
private val CardBg = Color(0xFF1E2736)
private val TextPrimary = Color(0xFFE8ECF2)
private val TextSecondary = Color(0xFF8A96A8)
private val Accent = Color(0xFF4283E6)
private val DividerColor = Color(0xFF2A3648)
private val DisabledAlpha = 0.38f

// -- Root --

@Composable
private fun TransferScreen(
	state: TransferState,
	customTitle: String? = null,
	onItemToggle: (Int) -> Unit,
	onSelectAll: (Boolean) -> Unit,
	onSelectFiltered: (List<Int>, Boolean) -> Unit,
	onTargetSelect: (String) -> Unit,
	onModeSelect: (TransferMode) -> Unit,
	onFormatSelect: (TransferFormat) -> Unit,
	onSourceSelect: (String) -> Unit,
	onGrantBackupAccess: () -> Unit,
	onStartTransfer: () -> Unit,
	onCancel: () -> Unit,
	onDone: () -> Unit,
) {
	BackHandler(enabled = state is TransferState.Executing) { /* no-op during transfer */ }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(ContentBg),
	) {
		TopBar(
			state = state,
			title = customTitle,
			onStartTransfer = onStartTransfer,
			onCancel = onCancel,
		)

		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 24.dp),
			contentAlignment = Alignment.TopCenter,
		) {
			when (state) {
				is TransferState.Loading -> LoadingContent()
				is TransferState.Configuration -> ConfigurationContent(
					state = state,
					onItemToggle = onItemToggle,
					onSelectAll = onSelectAll,
					onSelectFiltered = onSelectFiltered,
					onTargetSelect = onTargetSelect,
					onModeSelect = onModeSelect,
					onFormatSelect = onFormatSelect,
					onSourceSelect = onSourceSelect,
					onGrantBackupAccess = onGrantBackupAccess,
				)
				is TransferState.Executing -> ExecutingContent(state)
				is TransferState.Complete -> CompleteContent(
					result = state.result,
					onDone = onDone,
				)
			}
		}
	}
}

// -- Top Bar --

@Composable
private fun TopBar(
	state: TransferState,
	title: String? = null,
	onStartTransfer: () -> Unit,
	onCancel: () -> Unit,
) {
	val startEnabled = if (state is TransferState.Configuration) {
		val hasSelection = state.items.any { it.selected }
		val hasTarget = state.selectedTargetId != null
		val targetAccessible = state.targets.firstOrNull { it.id == state.selectedTargetId }?.needsSaf != true
		hasSelection && hasTarget && targetAccessible && state.sourceId != null
	} else false

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.background(ContentBg)
			.padding(horizontal = 8.dp, vertical = 12.dp),
	) {
		IconButton(
			onClick = {
				if (state !is TransferState.Executing) onCancel()
			},
		) {
			Icon(
				imageVector = Icons.AutoMirrored.Filled.ArrowBack,
				contentDescription = null,
				tint = TextPrimary,
			)
		}
		Text(
			text = title ?: stringResource(R.string.transfer_title),
			fontSize = 20.sp,
			fontWeight = FontWeight.Bold,
			color = TextPrimary,
		)
		Spacer(Modifier.weight(1f))
		if (state is TransferState.Configuration) {
			Button(
				onClick = onStartTransfer,
				enabled = startEnabled,
				colors = ButtonDefaults.buttonColors(
					containerColor = Accent,
					disabledContainerColor = Accent.copy(alpha = DisabledAlpha),
				),
			) {
				Text(stringResource(R.string.transfer_start))
			}
		}
	}
}

// -- Loading --

@Composable
private fun LoadingContent() {
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
	}
}

// -- Configuration --

@Composable
private fun ConfigurationContent(
	state: TransferState.Configuration,
	onItemToggle: (Int) -> Unit,
	onSelectAll: (Boolean) -> Unit,
	onSelectFiltered: (List<Int>, Boolean) -> Unit,
	onTargetSelect: (String) -> Unit,
	onModeSelect: (TransferMode) -> Unit,
	onFormatSelect: (TransferFormat) -> Unit,
	onSourceSelect: (String) -> Unit,
	onGrantBackupAccess: () -> Unit,
) {
	var searchQuery by remember { mutableStateOf("") }
	Row(
		modifier = Modifier.fillMaxSize(),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		// Left: Source items / source selection
		Column(
			modifier = Modifier
				.weight(1f)
				.fillMaxHeight(),
		) {
			if (state.sourceSelectable && state.sourceId == null) {
				// Source selection mode
				SectionLabel(stringResource(R.string.transfer_select_source))
				LazyColumn(modifier = Modifier.weight(1f)) {
					itemsIndexed(state.sourceOptions ?: emptyList()) { _, source ->
						TransferCard(
							modifier = Modifier
								.fillMaxWidth()
								.padding(bottom = 8.dp)
								.clickable { onSourceSelect(source.id) },
						) {
							Row(
								modifier = Modifier.padding(12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = source.name,
										color = TextPrimary,
										fontSize = 14.sp,
										fontWeight = FontWeight.SemiBold,
									)
									if (source.description.isNotEmpty()) {
										Text(
											text = source.description,
											color = TextSecondary,
											fontSize = 11.sp,
											modifier = Modifier.padding(top = 2.dp),
										)
									}
								}
							}
						}
					}
				}
			} else if (state.needsSourceSaf) {
				SectionLabel(stringResource(R.string.transfer_source) + ": " + state.sourceName)
				Spacer(Modifier.height(8.dp))
				Text(
					text = stringResource(R.string.migration_source_status_no_access),
					color = Orange,
					fontSize = 12.sp,
				)
				Spacer(Modifier.height(8.dp))
				Button(
					onClick = onGrantBackupAccess,
					colors = ButtonDefaults.buttonColors(containerColor = Accent),
				) {
					Text(stringResource(R.string.migration_grant_access))
				}
			} else {
				// Item checklist
				SectionLabel(stringResource(R.string.transfer_source) + ": " + state.sourceName)

				if (state.items.isNotEmpty()) {
					// Search bar
					OutlinedTextField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						placeholder = {
							Text(
								text = stringResource(R.string.transfer_search),
								color = TextSecondary,
								fontSize = 13.sp,
							)
						},
						leadingIcon = {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = null,
								tint = TextSecondary,
								modifier = Modifier.size(20.dp),
							)
						},
						trailingIcon = {
							if (searchQuery.isNotEmpty()) {
								IconButton(onClick = { searchQuery = "" }) {
									Icon(
										imageVector = Icons.Default.Clear,
										contentDescription = null,
										tint = TextSecondary,
										modifier = Modifier.size(20.dp),
									)
								}
							}
						},
						singleLine = true,
						colors = OutlinedTextFieldDefaults.colors(
							focusedTextColor = TextPrimary,
							unfocusedTextColor = TextPrimary,
							cursorColor = Accent,
							focusedBorderColor = Accent,
							unfocusedBorderColor = DividerColor,
						),
						modifier = Modifier
							.fillMaxWidth()
							.padding(bottom = 8.dp),
					)

					// Build filtered list with original indices
					val filteredItems = remember(searchQuery, state.items.size, state.items.hashCode()) {
						state.items.mapIndexedNotNull { index, item ->
							if (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)) {
								index to item
							} else null
						}
					}

					// Select all row (operates on filtered items)
					val filteredIndices = filteredItems.map { it.first }
					val allFilteredSelected = filteredItems.all { it.second.selected }
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.clickable {
								if (searchQuery.isBlank()) onSelectAll(!allFilteredSelected)
								else onSelectFiltered(filteredIndices, !allFilteredSelected)
							}
							.padding(vertical = 4.dp),
					) {
						Checkbox(
							checked = allFilteredSelected && filteredItems.isNotEmpty(),
							onCheckedChange = {
								if (searchQuery.isBlank()) onSelectAll(it)
								else onSelectFiltered(filteredIndices, it)
							},
							colors = CheckboxDefaults.colors(
								checkedColor = Accent,
								uncheckedColor = TextSecondary,
							),
						)
						Text(
							text = if (allFilteredSelected && filteredItems.isNotEmpty()) stringResource(R.string.transfer_deselect_all)
							else stringResource(R.string.transfer_select_all),
							color = TextPrimary,
							fontSize = 13.sp,
						)
						Spacer(Modifier.weight(1f))
						Text(
							text = stringResource(R.string.transfer_selected_count, state.items.count { it.selected }),
							color = TextSecondary,
							fontSize = 12.sp,
						)
					}

					LazyColumn(modifier = Modifier.weight(1f)) {
						itemsIndexed(filteredItems) { _, (originalIndex, item) ->
							Row(
								verticalAlignment = Alignment.CenterVertically,
								modifier = Modifier
									.fillMaxWidth()
									.clickable { onItemToggle(originalIndex) }
									.padding(vertical = 2.dp),
							) {
								Checkbox(
									checked = item.selected,
									onCheckedChange = { onItemToggle(originalIndex) },
									colors = CheckboxDefaults.colors(
										checkedColor = Accent,
										uncheckedColor = TextSecondary,
									),
								)
								// Folder/ZIP indicator
								if (item.isZip) {
									Text(
										text = "ZIP",
										color = Color.White,
										fontSize = 10.sp,
										fontWeight = FontWeight.Bold,
										modifier = Modifier
											.background(Accent, RoundedCornerShape(4.dp))
											.padding(horizontal = 5.dp, vertical = 1.dp),
									)
								} else {
									Icon(
										painter = painterResource(R.drawable.baseline_folder_open_white_24),
										contentDescription = null,
										tint = TextSecondary,
										modifier = Modifier.size(18.dp),
									)
								}
								Spacer(Modifier.width(6.dp))
								Text(
									text = item.name,
									color = if (item.selected) TextPrimary else TextSecondary,
									fontSize = 13.sp,
								)
							}
						}
					}
				} else {
					Text(
						text = stringResource(R.string.transfer_no_items),
						color = TextSecondary,
						fontSize = 13.sp,
						modifier = Modifier.padding(top = 8.dp),
					)
				}
			}
		}

		// Right: Target selection + mode
		Column(
			modifier = Modifier
				.weight(1f)
				.fillMaxHeight()
				.verticalScroll(rememberScrollState()),
		) {
			SectionLabel(stringResource(R.string.transfer_target))

			if (state.fixedTargetName != null) {
				// Fixed target (e.g. backup mode)
				TransferCard(
					modifier = Modifier
						.fillMaxWidth()
						.padding(bottom = 8.dp),
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = state.fixedTargetName,
							color = TextPrimary,
							fontSize = 14.sp,
							fontWeight = FontWeight.SemiBold,
						)
						val backupTarget = state.targets.firstOrNull { it.id == state.selectedTargetId }
						if (backupTarget != null) {
							if (backupTarget.needsSaf) {
								Spacer(Modifier.height(4.dp))
								Text(
									text = stringResource(R.string.migration_source_status_no_access),
									color = Orange,
									fontSize = 12.sp,
								)
								Spacer(Modifier.height(8.dp))
								Button(
									onClick = onGrantBackupAccess,
									colors = ButtonDefaults.buttonColors(containerColor = Accent),
								) {
									Text(stringResource(R.string.migration_grant_access))
								}
							} else if (backupTarget.description.isNotEmpty()) {
								Text(
									text = backupTarget.description,
									color = TextSecondary,
									fontSize = 11.sp,
									modifier = Modifier.padding(top = 2.dp),
								)
							}
						}
					}
				}
			} else {
				state.targets.forEach { target ->
					val isSameAsSource = target.id == state.sourceId
					val enabled = !isSameAsSource && !target.needsSaf
					val isSelected = target.id == state.selectedTargetId

					TransferCard(
						modifier = Modifier
							.fillMaxWidth()
							.padding(bottom = 8.dp)
							.then(
								if (enabled) Modifier.clickable { onTargetSelect(target.id) }
								else Modifier
							),
					) {
						Row(
							modifier = Modifier.padding(12.dp),
							verticalAlignment = Alignment.Top,
						) {
							RadioButton(
								selected = isSelected,
								onClick = { if (enabled) onTargetSelect(target.id) },
								enabled = enabled,
								colors = RadioButtonDefaults.colors(
									selectedColor = Accent,
									unselectedColor = TextSecondary,
									disabledSelectedColor = Accent.copy(alpha = DisabledAlpha),
									disabledUnselectedColor = TextSecondary.copy(alpha = DisabledAlpha),
								),
							)
							Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
								Text(
									text = target.name,
									color = if (enabled) TextPrimary else TextPrimary.copy(alpha = DisabledAlpha),
									fontSize = 14.sp,
									fontWeight = FontWeight.SemiBold,
								)
								if (target.description.isNotEmpty()) {
									Text(
										text = target.description,
										color = if (enabled) TextSecondary else TextSecondary.copy(alpha = DisabledAlpha),
										fontSize = 11.sp,
										modifier = Modifier.padding(top = 2.dp),
									)
								}
								if (target.needsSaf) {
									Spacer(Modifier.height(4.dp))
									Text(
										text = stringResource(R.string.migration_source_status_no_access),
										color = Orange,
										fontSize = 12.sp,
									)
									Spacer(Modifier.height(8.dp))
									Button(
										onClick = onGrantBackupAccess,
										colors = ButtonDefaults.buttonColors(containerColor = Accent),
									) {
										Text(stringResource(R.string.migration_grant_access))
									}
								}
								if (isSameAsSource) {
									Spacer(Modifier.height(4.dp))
									Text(
										text = stringResource(R.string.migration_same_as_source),
										color = Orange.copy(alpha = 0.7f),
										fontSize = 12.sp,
									)
								}
							}
						}
					}
				}
			}

			Spacer(Modifier.height(16.dp))

			// Mode selection
			SectionLabel(stringResource(R.string.transfer_mode_label))

			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				ModeButton(
					text = stringResource(R.string.transfer_mode_copy),
					selected = state.mode == TransferMode.COPY,
					onClick = { onModeSelect(TransferMode.COPY) },
				)
				ModeButton(
					text = stringResource(R.string.transfer_mode_move),
					selected = state.mode == TransferMode.MOVE,
					onClick = { onModeSelect(TransferMode.MOVE) },
				)
			}

			if (state.showFormatSelector) {
				Spacer(Modifier.height(16.dp))

				// Format selection
				SectionLabel(stringResource(R.string.transfer_format_label))

				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					ModeButton(
						text = stringResource(R.string.transfer_format_folder),
						selected = state.format == TransferFormat.FOLDER,
						onClick = { onFormatSelect(TransferFormat.FOLDER) },
					)
					ModeButton(
						text = stringResource(R.string.transfer_format_zip),
						selected = state.format == TransferFormat.ZIP,
						onClick = { onFormatSelect(TransferFormat.ZIP) },
					)
				}
			}
		}
	}
}

@Composable
private fun ModeButton(
	text: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val bg = if (selected) Accent else Color.Transparent
	val border = if (selected) Accent else TextSecondary.copy(alpha = 0.5f)
	val textColor = if (selected) Color.White else TextSecondary

	Text(
		text = text,
		color = textColor,
		fontSize = 13.sp,
		fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
		modifier = Modifier
			.border(1.dp, border, RoundedCornerShape(8.dp))
			.background(bg, RoundedCornerShape(8.dp))
			.clickable { onClick() }
			.padding(horizontal = 16.dp, vertical = 8.dp),
	)
}

// -- Executing --

@Composable
private fun ExecutingContent(state: TransferState.Executing) {
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier.fillMaxWidth(),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			LinearProgressIndicator(
				progress = { if (state.total > 0) state.current.toFloat() / state.total else 0f },
				modifier = Modifier.fillMaxWidth(),
				color = Accent,
				trackColor = CardBg,
			drawStopIndicator = {},
			)
			Spacer(Modifier.height(16.dp))
			Text(
				text = "${state.current} / ${state.total}",
				color = TextPrimary,
				fontSize = 16.sp,
				fontWeight = FontWeight.SemiBold,
			)
			if (state.currentName.isNotEmpty()) {
				Spacer(Modifier.height(8.dp))
				Text(
					text = stringResource(R.string.transfer_copying, state.currentName),
					color = TextSecondary,
					fontSize = 14.sp,
				)
			}
		}
	}
}

// -- Complete --

@Composable
private fun CompleteContent(
	result: SafMigrationHelper.TransferResult,
	onDone: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState()),
	) {
		SectionLabel(stringResource(R.string.migration_result))
		TransferCard {
			Column(modifier = Modifier.padding(16.dp)) {
				ResultRow(
					label = stringResource(R.string.transfer_result_transferred),
					count = result.transferred,
					color = Green,
				)
				if (result.skipped > 0) {
					CardDivider()
					ResultRow(
						label = stringResource(R.string.transfer_result_skipped),
						count = result.skipped,
						color = Orange,
					)
				}
				if (result.failed > 0) {
					CardDivider()
					ResultRow(
						label = stringResource(R.string.transfer_result_failed),
						count = result.failed,
						color = Red,
					)
				}
			}
		}

		if (result.errors.isNotEmpty()) {
			Spacer(Modifier.height(16.dp))
			SectionLabel(stringResource(R.string.error))
			TransferCard {
				Column(modifier = Modifier.padding(16.dp)) {
					result.errors.forEach { error ->
						Text(
							text = error,
							color = Red,
							fontSize = 12.sp,
							modifier = Modifier.padding(vertical = 2.dp),
						)
					}
				}
			}
		}

		Spacer(Modifier.weight(1f))

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp),
			horizontalArrangement = Arrangement.End,
		) {
			Button(
				onClick = onDone,
				colors = ButtonDefaults.buttonColors(containerColor = Accent),
			) {
				Text(stringResource(R.string.transfer_done))
			}
		}
	}
}

@Composable
private fun ResultRow(label: String, count: Int, color: Color) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(text = label, color = TextPrimary, fontSize = 14.sp)
		Text(text = count.toString(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
	}
}

// -- Reusable composables --

@Composable
private fun TransferCard(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(12.dp),
		color = CardBg,
	) {
		content()
	}
}

@Composable
private fun SectionLabel(text: String) {
	Text(
		text = text,
		fontSize = 12.sp,
		fontWeight = FontWeight.SemiBold,
		color = Accent,
		letterSpacing = 0.5.sp,
		modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
	)
}

@Composable
private fun CardDivider() {
	Box(
		Modifier
			.fillMaxWidth()
			.height(1.dp)
			.background(DividerColor),
	)
}
