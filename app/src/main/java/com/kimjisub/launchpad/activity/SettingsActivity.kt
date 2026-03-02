package com.kimjisub.launchpad.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.manager.putClipboard
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import org.koin.android.ext.android.inject
import splitties.activities.start
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {
	companion object {
		const val EXTRA_INITIAL_CATEGORY = "initial_category"
		const val EXTRA_HIGHLIGHT_BACKUP = "highlight_backup"
		const val CATEGORY_STORAGE = "STORAGE"
	}

	private val prefManager: PreferenceManager by inject()
	private val workspaceManager: WorkspaceManager by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val appVersionInfo = getAppVersionInfo()
		val workspaces = workspaceManager.availableWorkspaces.toList()
		val initialCategory = when (intent.getStringExtra(EXTRA_INITIAL_CATEGORY)) {
			CATEGORY_STORAGE -> SettingsCategory.STORAGE
			else -> SettingsCategory.INFO
		}
		val highlightBackup = intent.getBooleanExtra(EXTRA_HIGHLIGHT_BACKUP, false)

		setContent {
			UniPadTheme {
				SettingsScreen(
					appVersionInfo = appVersionInfo,
					workspaces = workspaces,
					prefManager = prefManager,
					workspaceManager = workspaceManager,
					initialCategory = initialCategory,
					highlightBackup = highlightBackup,
					onBackClick = { finish() },
					onThemeClick = { start<ThemeActivity>() },
					onGithubClick = {
						startActivity(
							Intent(
								Intent.ACTION_VIEW,
								"https://github.com/kimjisub/unipad-android".toUri()
							)
						)
					},
					onOssLicenseClick = { start<OssLicensesMenuActivity>() },
					onFcmTokenCopy = { copyFcmToken() },
					onCommunityItemClick = { action, url ->
						startActivity(Intent(action, url.toUri()))
					},
					onReconnectClick = { start<MidiSelectActivity>() },
					onTransferClick = { sourcePath ->
						startActivity(Intent(this@SettingsActivity, TransferActivity::class.java).apply {
							putExtra(TransferActivity.EXTRA_SOURCE_TYPE, "workspace")
							putExtra(TransferActivity.EXTRA_SOURCE_PATH, sourcePath)
						})
					},
					onBackupClick = {
						startActivity(Intent(this@SettingsActivity, TransferActivity::class.java).apply {
							putExtra(TransferActivity.EXTRA_TARGET_TYPE, "backup")
							putExtra(TransferActivity.EXTRA_MODE, "copy")
							putExtra(TransferActivity.EXTRA_TITLE, getString(R.string.backup_title))
						})
					},
					onRestoreClick = {
						startActivity(Intent(this@SettingsActivity, TransferActivity::class.java).apply {
							putExtra(TransferActivity.EXTRA_SOURCE_TYPE, "backup")
							putExtra(TransferActivity.EXTRA_MODE, "copy")
							putExtra(TransferActivity.EXTRA_TITLE, getString(R.string.restore_title))
						})
					},
				)
			}
		}
	}

	private fun getAppVersionInfo(): String {
		val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
		} else {
			@Suppress("DEPRECATION")
			packageManager.getPackageInfo(packageName, 0)
		}
		val appName = getString(R.string.app_name)
		val versionName = packageInfo.versionName
		val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
		return "$appName $versionName ($versionCode)"
	}

	private fun copyFcmToken() {
		try {
			FirebaseMessaging.getInstance().token.addOnCompleteListener {
				putClipboard(it.result)
				Snackbar.make(findViewById(android.R.id.content), R.string.copied, Snackbar.LENGTH_SHORT).show()
			}
		} catch (e: IllegalStateException) {
			Log.err("FCM token copy failed", e)
			Snackbar.make(findViewById(android.R.id.content), e.toString(), Snackbar.LENGTH_SHORT).show()
		}
	}
}

// -- Data --

private enum class SettingsCategory { INFO, STORAGE }

private data class CategoryItemData(
	val category: SettingsCategory?,
	val titleResId: Int,
	val iconResId: Int,
)

private val categoryItems = listOf(
	CategoryItemData(SettingsCategory.INFO, R.string.settings_info, R.drawable.ic_info),
	CategoryItemData(SettingsCategory.STORAGE, R.string.settings_storage, R.drawable.ic_storage),
	CategoryItemData(null, R.string.settings_theme, R.drawable.ic_palette),
)

// -- Colors --

private val NavBg = Color(0xFF111825)
private val NavItemSelected = Color(0xFF1E2E44)
private val ContentBg = Color(0xFF161E2B)
private val CardBg = Color(0xFF1E2736)
private val TextPrimary = Color(0xFFE8ECF2)
private val TextSecondary = Color(0xFF8A96A8)
private val Accent = Color(0xFF4283E6)
private val Danger = Color(0xFFFF6B4E)
private val DividerColor = Color(0xFF2A3648)

// -- Root --

@Composable
private fun SettingsScreen(
	appVersionInfo: String,
	workspaces: List<WorkspaceManager.Workspace>,
	prefManager: PreferenceManager,
	workspaceManager: WorkspaceManager,
	initialCategory: SettingsCategory = SettingsCategory.INFO,
	highlightBackup: Boolean = false,
	onBackClick: () -> Unit,
	onThemeClick: () -> Unit,
	onGithubClick: () -> Unit,
	onOssLicenseClick: () -> Unit,
	onFcmTokenCopy: () -> Unit,
	onCommunityItemClick: (action: String, url: String) -> Unit,
	onReconnectClick: () -> Unit = {},
	onTransferClick: (sourcePath: String) -> Unit = {},
	onBackupClick: () -> Unit = {},
	onRestoreClick: () -> Unit = {},
) {
	var selectedCategory by remember { mutableStateOf(initialCategory) }

	Row(
		modifier = Modifier
			.fillMaxSize()
			.background(ContentBg),
	) {
		// Left navigation rail
		CategoryNav(
			selectedCategory = selectedCategory,
			onCategorySelect = { selectedCategory = it },
			onBackClick = onBackClick,
			onThemeClick = onThemeClick,
			modifier = Modifier
				.width(220.dp)
				.fillMaxHeight(),
		)

		// Right content area
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxHeight(),
		) {
			when (selectedCategory) {
				SettingsCategory.INFO -> InfoContent(
					appVersionInfo = appVersionInfo,
					onGithubClick = onGithubClick,
					onOssLicenseClick = onOssLicenseClick,
					onFcmTokenCopy = onFcmTokenCopy,
					onCommunityItemClick = onCommunityItemClick,
					onReconnectClick = onReconnectClick,
				)

				SettingsCategory.STORAGE -> StorageContent(
					workspaces = workspaces,
					prefManager = prefManager,
					workspaceManager = workspaceManager,
					highlightBackup = highlightBackup,
					onTransferClick = onTransferClick,
					onBackupClick = onBackupClick,
					onRestoreClick = onRestoreClick,
				)
			}
		}
	}
}

// -- Left navigation --

@Composable
private fun CategoryNav(
	selectedCategory: SettingsCategory,
	onCategorySelect: (SettingsCategory) -> Unit,
	onBackClick: () -> Unit,
	onThemeClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.background(NavBg)
			.padding(vertical = 24.dp, horizontal = 12.dp),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(bottom = 24.dp),
		) {
			IconButton(onClick = onBackClick) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = null,
					tint = TextPrimary,
				)
			}
			Text(
				text = stringResource(R.string.setting),
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold,
				color = TextPrimary,
			)
		}

		categoryItems.forEach { item ->
			val isSelected = item.category != null && item.category == selectedCategory
			val bgColor by animateColorAsState(
				targetValue = if (isSelected) NavItemSelected else Color.Transparent,
				label = "navBg",
			)
			val iconTint by animateColorAsState(
				targetValue = if (isSelected) Accent else TextSecondary,
				label = "navIcon",
			)
			val textColor by animateColorAsState(
				targetValue = if (isSelected) TextPrimary else TextSecondary,
				label = "navText",
			)

			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(10.dp))
					.background(bgColor)
					.clickable {
						if (item.category != null) onCategorySelect(item.category)
						else onThemeClick()
					}
					.padding(horizontal = 12.dp, vertical = 14.dp),
			) {
				Icon(
					painter = painterResource(item.iconResId),
					contentDescription = null,
					tint = iconTint,
					modifier = Modifier.size(22.dp),
				)
				Spacer(Modifier.width(12.dp))
				Text(
					text = stringResource(item.titleResId),
					fontSize = 14.sp,
					fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
					color = textColor,
				)
				if (item.category == null) {
					Spacer(Modifier.weight(1f))
					Icon(
						imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
						contentDescription = null,
						tint = TextSecondary,
						modifier = Modifier.size(18.dp),
					)
				}
			}

			Spacer(Modifier.height(4.dp))
		}
	}
}

// -- Reusable row / card composables --

@Composable
private fun SettingsCard(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(12.dp),
		color = CardBg,
	) {
		Column { content() }
	}
}

@Composable
private fun SettingsRow(
	title: String,
	subtitle: String? = null,
	titleColor: Color = TextPrimary,
	onClick: (() -> Unit)? = null,
	trailing: @Composable (() -> Unit)? = null,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.then(
				if (onClick != null) Modifier.clickable(onClick = onClick)
				else Modifier
			)
			.padding(horizontal = 16.dp, vertical = 14.dp),
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				fontSize = 14.sp,
				color = titleColor,
			)
			if (subtitle != null) {
				Text(
					text = subtitle,
					fontSize = 12.sp,
					color = TextSecondary,
					modifier = Modifier.padding(top = 2.dp),
				)
			}
		}
		if (trailing != null) {
			Spacer(Modifier.width(12.dp))
			trailing()
		} else if (onClick != null) {
			Spacer(Modifier.width(12.dp))
			Icon(
				imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
				contentDescription = null,
				tint = TextSecondary,
				modifier = Modifier.size(20.dp),
			)
		}
	}
}

@Composable
private fun CardDivider() {
	Box(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.height(1.dp)
			.background(DividerColor),
	)
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

// -- Info content --

@Composable
private fun InfoContent(
	appVersionInfo: String,
	onGithubClick: () -> Unit,
	onOssLicenseClick: () -> Unit,
	onFcmTokenCopy: () -> Unit,
	onCommunityItemClick: (action: String, url: String) -> Unit,
	onReconnectClick: () -> Unit = {},
) {
	var showCommunityDialog by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(24.dp),
	) {
		// -- Device section --
		SectionLabel(stringResource(R.string.settings_device))

		SettingsCard {
			SettingsRow(
				title = stringResource(R.string.reconnect_launchpad),
				onClick = onReconnectClick,
			)
		}

		Spacer(Modifier.height(24.dp))

		// -- App section --
		SectionLabel(stringResource(R.string.settings_info))

		SettingsCard {
			SettingsRow(
				title = appVersionInfo,
				subtitle = stringResource(R.string.copyright),
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.language),
				subtitle = stringResource(R.string.translated_by),
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.community),
				onClick = { showCommunityDialog = true },
			)
		}

		Spacer(Modifier.height(24.dp))

		// -- Developer section --
		SectionLabel(stringResource(R.string.settings_developer))

		SettingsCard {
			SettingsRow(
				title = stringResource(R.string.github),
				onClick = onGithubClick,
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.openSourceLicense),
				onClick = onOssLicenseClick,
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.FCMToken),
				subtitle = "Tap to copy",
				onClick = onFcmTokenCopy,
			)
		}
	}

	if (showCommunityDialog) {
		CommunityDialog(
			onDismiss = { showCommunityDialog = false },
			onItemClick = { action, url ->
				showCommunityDialog = false
				onCommunityItemClick(action, url)
			},
		)
	}
}

// -- Community dialog --

private data class CommunityItemData(
	val titleResId: Int,
	val subtitleResId: Int,
	val iconResId: Int,
	val url: String,
	val action: String,
)

private val communityItems = listOf(
	CommunityItemData(
		R.string.officialHomepage, R.string.officialHomepage_,
		R.drawable.community_web, "https://unipad.io", Intent.ACTION_VIEW
	),
	CommunityItemData(
		R.string.officialFacebook, R.string.officialFacebook_,
		R.drawable.community_facebook, "https://www.facebook.com/playunipad", Intent.ACTION_VIEW
	),
	CommunityItemData(
		R.string.facebookCommunity, R.string.facebookCommunity_,
		R.drawable.community_facebook_group, "https://www.facebook.com/groups/playunipad",
		Intent.ACTION_VIEW
	),
	CommunityItemData(
		R.string.naverCafe, R.string.naverCafe_,
		R.drawable.community_cafe, "https://cafe.naver.com/unipad", Intent.ACTION_VIEW
	),
	CommunityItemData(
		R.string.discord, R.string.discord_,
		R.drawable.community_discord, "https://discord.gg/ESDgyNs", Intent.ACTION_VIEW
	),
	CommunityItemData(
		R.string.kakaotalk, R.string.kakaotalk_,
		R.drawable.community_kakaotalk,
		"https://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-", Intent.ACTION_VIEW
	),
	CommunityItemData(
		R.string.email, R.string.email_,
		R.drawable.community_mail, "mailto:0226unipad@gmail.com", Intent.ACTION_SENDTO
	),
)

@Composable
private fun CommunityDialog(
	onDismiss: () -> Unit,
	onItemClick: (action: String, url: String) -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = CardBg,
		titleContentColor = TextPrimary,
		title = {
			Text(
				text = stringResource(R.string.community),
				fontWeight = FontWeight.Bold,
			)
		},
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
			) {
				communityItems.forEach { item ->
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(8.dp))
							.clickable { onItemClick(item.action, item.url) }
							.padding(vertical = 10.dp, horizontal = 4.dp),
					) {
						Image(
							painter = painterResource(item.iconResId),
							contentDescription = null,
							modifier = Modifier
								.size(36.dp)
								.clip(CircleShape),
						)
						Spacer(Modifier.width(12.dp))
						Column {
							Text(
								text = stringResource(item.titleResId),
								fontSize = 14.sp,
								color = TextPrimary,
							)
							Text(
								text = stringResource(item.subtitleResId),
								fontSize = 12.sp,
								color = TextSecondary,
							)
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(
					text = stringResource(android.R.string.ok),
					color = Accent,
				)
			}
		},
	)
}

// -- Storage content --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageContent(
	workspaces: List<WorkspaceManager.Workspace>,
	prefManager: PreferenceManager,
	workspaceManager: WorkspaceManager,
	highlightBackup: Boolean = false,
	onTransferClick: (sourcePath: String) -> Unit = {},
	onBackupClick: () -> Unit = {},
	onRestoreClick: () -> Unit = {},
) {
	var downloadPath by remember { mutableStateOf(workspaceManager.downloadWorkspace.file.path) }
	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()
	var refreshKey by remember { mutableIntStateOf(0) }

	LifecycleResumeEffect(Unit) {
		refreshKey++
		onPauseOrDispose {}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(24.dp),
		) {
			// -- Workspaces: radio (download target) --
			SectionLabel(stringResource(R.string.settings_storage))

			SettingsCard {
				workspaces.forEachIndexed { index, workspace ->
					val isDownloadTarget = workspace.file.path == downloadPath
					val description = getWorkspaceDescription(workspace)
					val unipackCount = remember(workspace.file.path, refreshKey) {
						workspaceManager.getUnipackCount(workspace)
					}

					if (index > 0) CardDivider()

					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = {
									prefManager.downloadStoragePath = workspace.file.path
									downloadPath = workspace.file.path
								},
								onLongClick = {
									scope.launch {
										snackbarHostState.showSnackbar(workspace.file.path)
									}
								},
							)
							.padding(horizontal = 16.dp, vertical = 12.dp),
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = workspace.name,
								fontSize = 14.sp,
								color = TextPrimary,
							)
							if (description.isNotEmpty()) {
								Text(
									text = description,
									fontSize = 12.sp,
									color = TextSecondary,
									modifier = Modifier.padding(top = 2.dp),
								)
							}
							Text(
								text = stringResource(R.string.workspace_unipack_count, unipackCount),
								fontSize = 12.sp,
								color = TextSecondary,
								modifier = Modifier.padding(top = 2.dp),
							)
						}
						Spacer(Modifier.width(8.dp))
						TextButton(
							onClick = { onTransferClick(workspace.file.path) },
						) {
							Text(
								text = stringResource(R.string.transfer_button),
								color = Accent,
								fontSize = 12.sp,
							)
						}
						Spacer(Modifier.width(4.dp))
						RadioButton(
							selected = isDownloadTarget,
							onClick = {
								prefManager.downloadStoragePath = workspace.file.path
								downloadPath = workspace.file.path
							},
							colors = RadioButtonDefaults.colors(
								selectedColor = Accent,
								unselectedColor = TextSecondary,
							),
						)
					}
				}
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				Spacer(Modifier.height(24.dp))

				BackupSection(
					prefManager = prefManager,
					snackbarHostState = snackbarHostState,
					highlightBackup = highlightBackup,
					onBackupClick = onBackupClick,
					onRestoreClick = onRestoreClick,
				)
			}
		}

		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier.align(Alignment.BottomCenter),
		)
	}
}

// -- Backup section --

@Composable
private fun BackupSection(
	prefManager: PreferenceManager,
	snackbarHostState: SnackbarHostState,
	highlightBackup: Boolean = false,
	onBackupClick: () -> Unit = {},
	onRestoreClick: () -> Unit = {},
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var backupUri by remember { mutableStateOf(prefManager.backupSafUri) }
	var unipackCount by remember { mutableIntStateOf(0) }
	var isHighlighted by remember { mutableStateOf(highlightBackup) }
	var showGuideDialog by remember { mutableStateOf(false) }
	var showRecoveryDialog by remember { mutableStateOf(false) }
	var refreshKey by remember { mutableIntStateOf(0) }

	LifecycleResumeEffect(Unit) {
		refreshKey++
		onPauseOrDispose {}
	}

	LaunchedEffect(backupUri, refreshKey) {
		if (backupUri != null) {
			unipackCount = withContext(Dispatchers.IO) {
				val treeDoc = DocumentFile.fromTreeUri(context, backupUri!!.toUri())
				treeDoc?.listFiles()?.count {
					it.isDirectory || (it.name?.endsWith(".zip", ignoreCase = true) == true)
				} ?: 0
			}
		}
	}

	val launcher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri != null) {
			val treeDoc = DocumentFile.fromTreeUri(context, uri)
			if (treeDoc?.name.equals("Unipad", ignoreCase = true)) {
				context.contentResolver.takePersistableUriPermission(
					uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
				)
				prefManager.backupSafUri = uri.toString()
				backupUri = uri.toString()
				// After successful connection, count packs and show recovery dialog
				scope.launch {
					val count = withContext(Dispatchers.IO) {
						val tree = DocumentFile.fromTreeUri(context, uri)
						tree?.listFiles()?.count {
					it.isDirectory || (it.name?.endsWith(".zip", ignoreCase = true) == true)
				} ?: 0
					}
					unipackCount = count
					if (count > 0) {
						showRecoveryDialog = true
					}
				}
			} else {
				scope.launch {
					snackbarHostState.showSnackbar(
						context.getString(R.string.backup_invalid_folder)
					)
				}
			}
		}
	}

	// Pulse animation for highlight
	val highlightAlpha = if (isHighlighted) {
		val transition = rememberInfiniteTransition(label = "backupHighlight")
		val alpha by transition.animateFloat(
			initialValue = 0.3f,
			targetValue = 1f,
			animationSpec = infiniteRepeatable(
				animation = tween(800),
				repeatMode = RepeatMode.Reverse,
			),
			label = "pulseAlpha",
		)
		alpha
	} else {
		0f
	}

	val highlightModifier = if (isHighlighted) {
		Modifier.border(
			width = 2.dp,
			color = Accent.copy(alpha = highlightAlpha),
			shape = RoundedCornerShape(12.dp),
		)
	} else {
		Modifier
	}

	SectionLabel(stringResource(R.string.backup_section))

	SettingsCard(modifier = highlightModifier) {
		if (backupUri != null) {
			SettingsRow(
				title = "Documents/Unipad",
				subtitle = stringResource(R.string.workspace_unipack_count, unipackCount),
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.backup_backup_button),
				subtitle = stringResource(R.string.backup_backup_description),
				onClick = onBackupClick,
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.backup_restore_button),
				subtitle = stringResource(R.string.backup_restore_description),
				onClick = {
					isHighlighted = false
					onRestoreClick()
				},
			)
			CardDivider()
			SettingsRow(
				title = stringResource(R.string.backup_revoke),
				titleColor = Danger,
				onClick = {
					isHighlighted = false
					val uri = backupUri!!.toUri()
					context.contentResolver.releasePersistableUriPermission(
						uri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
					)
					prefManager.backupSafUri = null
					backupUri = null
					unipackCount = 0
				},
			)
		} else {
			SettingsRow(
				title = stringResource(R.string.backup_grant_access),
				subtitle = stringResource(R.string.backup_description),
				onClick = {
					isHighlighted = false
					if (highlightBackup) {
						showGuideDialog = true
					} else {
						val initialUri = DocumentsContract.buildDocumentUri(
							"com.android.externalstorage.documents",
							"primary:Documents"
						)
						launcher.launch(initialUri)
					}
				},
			)
		}
	}

	// Guide dialog — shown before SAF picker when coming from restore flow
	if (showGuideDialog) {
		BackupGuideDialog(
			onDismiss = { showGuideDialog = false },
			onContinue = {
				showGuideDialog = false
				val initialUri = DocumentsContract.buildDocumentUri(
					"com.android.externalstorage.documents",
					"primary:Documents"
				)
				launcher.launch(initialUri)
			},
		)
	}

	// Recovery dialog — shown after successful SAF connection with packs found
	if (showRecoveryDialog) {
		RecoveryDialog(
			unipackCount = unipackCount,
			onRestore = {
				showRecoveryDialog = false
				isHighlighted = false
				onRestoreClick()
			},
			onDismiss = {
				showRecoveryDialog = false
				isHighlighted = false
			},
		)
	}
}

@Composable
private fun BackupGuideDialog(
	onDismiss: () -> Unit,
	onContinue: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = CardBg,
		titleContentColor = TextPrimary,
		title = {
			Text(
				text = stringResource(R.string.backup_guide_title),
				fontWeight = FontWeight.Bold,
			)
		},
		text = {
			Column {
				Text(
					text = stringResource(R.string.backup_guide_description),
					fontSize = 14.sp,
					color = TextSecondary,
				)
				Spacer(Modifier.height(16.dp))
				Text(
					text = stringResource(R.string.backup_guide_step1),
					fontSize = 13.sp,
					color = TextPrimary,
				)
				Spacer(Modifier.height(6.dp))
				Text(
					text = stringResource(R.string.backup_guide_step2),
					fontSize = 13.sp,
					color = TextPrimary,
				)
				Spacer(Modifier.height(6.dp))
				Text(
					text = stringResource(R.string.backup_guide_step3),
					fontSize = 13.sp,
					color = TextPrimary,
				)
			}
		},
		confirmButton = {
			TextButton(onClick = onContinue) {
				Text(
					text = stringResource(R.string.backup_guide_continue),
					color = Accent,
				)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(
					text = stringResource(R.string.cancel),
					color = TextSecondary,
				)
			}
		},
	)
}

@Composable
private fun RecoveryDialog(
	unipackCount: Int,
	onRestore: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = CardBg,
		titleContentColor = TextPrimary,
		title = {
			Text(
				text = stringResource(R.string.backup_recovery_title),
				fontWeight = FontWeight.Bold,
			)
		},
		text = {
			Text(
				text = stringResource(R.string.backup_recovery_message, unipackCount),
				fontSize = 14.sp,
				color = TextSecondary,
			)
		},
		confirmButton = {
			TextButton(onClick = onRestore) {
				Text(
					text = stringResource(R.string.backup_recovery_restore),
					color = Accent,
				)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(
					text = stringResource(R.string.backup_recovery_later),
					color = TextSecondary,
				)
			}
		},
	)
}

@Composable
private fun getWorkspaceDescription(workspace: WorkspaceManager.Workspace): String {
	val name = workspace.name
	val appStorage = stringResource(R.string.workspace_app_storage)
	val docsAndroid10 = stringResource(R.string.workspace_documents_android10)
	return when {
		name == appStorage -> stringResource(R.string.workspace_desc_app_storage)
		name == docsAndroid10 -> stringResource(R.string.workspace_desc_documents_android10)
		name.contains("SD") -> stringResource(R.string.workspace_desc_external_sd)
		else -> ""
	}
}
