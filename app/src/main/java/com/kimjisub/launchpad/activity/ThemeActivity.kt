package com.kimjisub.launchpad.activity

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.ThemeItem
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.launchpad.adapter.ThemeType
import com.kimjisub.launchpad.manager.loadTheme
import com.kimjisub.launchpad.tool.ZipThemeImporter
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.ui.theme.Orange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThemeActivity : BaseActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			var themes by remember { mutableStateOf(ThemeTool.getThemePackList(applicationContext)) }
			var appliedIndex by remember(themes) { mutableIntStateOf(getSavedTheme(themes)) }

			ThemeScreen(
				themes = themes,
				appliedIndex = appliedIndex,
				onBackClick = { finish() },
				onApply = { index ->
					if (index < themes.size) {
						p.selectedTheme = themes[index].id
						appliedIndex = index
					}
				},
				onBrowseStore = {
					browse("https://play.google.com/store/search?q=com.kimjisub.launchpad.theme.")
				},
				onCreateGuide = {
					browse("https://github.com/kimjisub/unipad-android/blob/main/docs/THEME_CREATION_GUIDE.md")
				},
				onImport = { uri ->
					try {
						ZipThemeImporter.import(this, uri)
						themes = ThemeTool.getThemePackList(applicationContext)
						Toast.makeText(this, getString(R.string.theme_import_success), Toast.LENGTH_SHORT).show()
					} catch (e: ZipThemeImporter.InvalidThemeException) {
						Toast.makeText(this, "${getString(R.string.theme_import_invalid)}\n${e.message}", Toast.LENGTH_SHORT).show()
					} catch (e: Exception) {
						Toast.makeText(this, "${getString(R.string.theme_import_failed)}\n${e.message}", Toast.LENGTH_SHORT).show()
					}
				},
				onDelete = { themeItem ->
					val folderName = themeItem.id.removePrefix("zip://")
					if (ZipThemeImporter.delete(this, folderName)) {
						if (p.selectedTheme == themeItem.id) {
							p.selectedTheme = packageName
						}
						themes = ThemeTool.getThemePackList(applicationContext)
					}
				},
			)
		}
	}

	private fun getSavedTheme(themes: List<ThemeItem>): Int {
		val selectedThemeId: String = p.selectedTheme
		val index = themes.indexOfFirst { it.id == selectedThemeId }
		return if (index >= 0) index else 0
	}
}

private data class PreviewBitmaps(
	val playbg: ImageBitmap?,
	val btn: ImageBitmap?,
	val phantom: ImageBitmap?,
	val phantomVariant: ImageBitmap?,
	val chainDrawable: ImageBitmap?,
	val isChainLed: Boolean,
	val customLogo: ImageBitmap?,
)

@Composable
private fun ThemeScreen(
	themes: List<ThemeItem>,
	appliedIndex: Int,
	onBackClick: () -> Unit,
	onApply: (Int) -> Unit,
	onBrowseStore: () -> Unit,
	onCreateGuide: () -> Unit,
	onImport: (Uri) -> Unit,
	onDelete: (ThemeItem) -> Unit,
) {
	val context = LocalContext.current
	var selectedIndex by remember(themes) { mutableIntStateOf(appliedIndex) }
	var previewBitmaps by remember { mutableStateOf<PreviewBitmaps?>(null) }
	var deleteTarget by remember { mutableStateOf<ThemeItem?>(null) }
	var isFullscreen by remember { mutableStateOf(false) }

	BackHandler(enabled = isFullscreen) {
		isFullscreen = false
	}

	val filePickerLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.GetContent()
	) { uri: Uri? ->
		if (uri != null) onImport(uri)
	}

	LaunchedEffect(selectedIndex, themes) {
		if (selectedIndex < themes.size) {
			val themeId = themes[selectedIndex].id
			val bitmaps = withContext(Dispatchers.IO) {
				try {
					val res = loadTheme(context, themeId, fullLoad = true)
					PreviewBitmaps(
						playbg = res.playbg?.toBitmap()?.asImageBitmap(),
						btn = res.btn?.toBitmap()?.asImageBitmap(),
						phantom = res.phantom?.toBitmap()?.asImageBitmap(),
						phantomVariant = res.phantomVariant?.toBitmap()?.asImageBitmap(),
						chainDrawable = if (res.isChainLed) {
							res.chainled?.toBitmap()?.asImageBitmap()
						} else {
							res.chain?.toBitmap()?.asImageBitmap()
						},
						isChainLed = res.isChainLed,
						customLogo = res.customLogo?.toBitmap()?.asImageBitmap(),
					)
				} catch (_: OutOfMemoryError) {
					null
				}
			}
			previewBitmaps = bitmaps
		} else {
			previewBitmaps = null
		}
	}

	// Delete confirmation dialog
	deleteTarget?.let { theme ->
		AlertDialog(
			onDismissRequest = { deleteTarget = null },
			title = { Text(stringResource(R.string.theme_delete_title)) },
			text = { Text(stringResource(R.string.theme_delete_confirm, theme.name)) },
			confirmButton = {
				TextButton(onClick = {
					onDelete(theme)
					deleteTarget = null
					selectedIndex = 0
				}) {
					Text(stringResource(R.string.delete))
				}
			},
			dismissButton = {
				TextButton(onClick = { deleteTarget = null }) {
					Text(stringResource(R.string.cancel))
				}
			},
		)
	}

	BoxWithConstraints(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF161E2B)),
	) {
		val leftPanelWidth = maxWidth * 0.35f
		val animatedLeftWidth by animateDpAsState(
			targetValue = if (isFullscreen) 0.dp else leftPanelWidth,
			animationSpec = tween(300),
			label = "leftPanelWidth",
		)

		Row(modifier = Modifier.fillMaxSize()) {
			// Left panel: theme list (animated slide)
			Column(
				modifier = Modifier
					.width(animatedLeftWidth)
					.fillMaxHeight()
					.clipToBounds()
					.offset(x = animatedLeftWidth - leftPanelWidth)
					.background(Color(0xFF111825)),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
				) {
					IconButton(onClick = onBackClick) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = null,
							tint = Color.White,
						)
					}
					Text(
						text = stringResource(R.string.theme),
						color = Color.White,
						fontSize = 24.sp,
						modifier = Modifier.weight(1f),
					)
					IconButton(onClick = { selectedIndex = themes.size }) {
						Icon(
							imageVector = Icons.Default.Add,
							contentDescription = stringResource(R.string.theme_add_title),
							tint = Color.White,
						)
					}
				}

				LazyColumn(
					modifier = Modifier.weight(1f),
					contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
				) {
					itemsIndexed(themes) { index, theme ->
						ThemeListItem(
							theme = theme,
							isSelected = index == selectedIndex,
							isApplied = index == appliedIndex,
							onClick = { selectedIndex = index },
							onApply = { onApply(index) },
							onLongClick = if (theme.isDeletable) {
								{ deleteTarget = theme }
							} else null,
						)
					}

					item {
						AddThemeGuideItem(
							isSelected = selectedIndex == themes.size,
							onClick = { selectedIndex = themes.size },
						)
					}
				}
			}

			// Right panel
			Box(
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight(),
			) {
				if (selectedIndex == themes.size) {
					AddThemePanel(
						onBrowseStore = onBrowseStore,
						onCreateGuide = onCreateGuide,
						onImportZip = { filePickerLauncher.launch("application/zip") },
					)
				} else {
					Box(modifier = Modifier.fillMaxSize().clickable { isFullscreen = !isFullscreen }) {
						Crossfade(
							targetState = previewBitmaps,
							animationSpec = tween(400),
							label = "themePreview",
						) { bitmaps ->
							ThemePreview(bitmaps)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeListItem(
	theme: ThemeItem,
	isSelected: Boolean,
	isApplied: Boolean,
	onClick: () -> Unit,
	onApply: () -> Unit,
	onLongClick: (() -> Unit)? = null,
) {
	val bitmap = remember(theme.id) {
		theme.icon.toBitmap().asImageBitmap()
	}
	val borderColor = if (isSelected) Orange else Color.Transparent
	val bgColor = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 3.dp)
			.clip(RoundedCornerShape(10.dp))
			.border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
			.background(bgColor)
			.then(
				if (onLongClick != null) {
					Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
				} else {
					Modifier.clickable(onClick = onClick)
				}
			)
			.padding(horizontal = 12.dp, vertical = 10.dp),
	) {
		Image(
			bitmap = bitmap,
			contentDescription = theme.name,
			modifier = Modifier
				.size(44.dp)
				.clip(RoundedCornerShape(8.dp)),
		)
		Spacer(modifier = Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = theme.name,
				color = Color.White,
				fontSize = 14.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Row(
				verticalAlignment = Alignment.CenterVertically,
			) {
				ThemeTypeBadge(theme.type)
				Spacer(modifier = Modifier.width(6.dp))
				Text(
					text = "${theme.author}  ${theme.version ?: ""}".trim(),
					color = Color.White.copy(alpha = 0.6f),
					fontSize = 11.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
		if (isApplied) {
			Icon(
				imageVector = Icons.Default.Check,
				contentDescription = null,
				tint = Orange,
				modifier = Modifier.size(22.dp),
			)
		} else if (isSelected) {
			Button(
				onClick = onApply,
				colors = ButtonDefaults.buttonColors(containerColor = Orange),
				shape = RoundedCornerShape(6.dp),
				modifier = Modifier.height(30.dp),
				contentPadding = PaddingValues(horizontal = 12.dp),
			) {
				Text(
					text = stringResource(R.string.apply),
					color = Color.White,
					fontSize = 12.sp,
				)
			}
		}
	}
}

@Composable
private fun ThemeTypeBadge(type: ThemeType) {
	val (label, color) = when (type) {
		ThemeType.BUILTIN -> stringResource(R.string.theme_type_builtin) to Color(0xFF4CAF50)
		ThemeType.ZIP -> stringResource(R.string.theme_type_zip) to Color(0xFF42A5F5)
		ThemeType.APK -> stringResource(R.string.theme_type_apk) to Color(0xFFAB47BC)
	}
	Text(
		text = label,
		color = color,
		fontSize = 9.sp,
		maxLines = 1,
		modifier = Modifier
			.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
			.padding(horizontal = 4.dp, vertical = 1.dp),
	)
}

@Composable
private fun AddThemeGuideItem(
	isSelected: Boolean,
	onClick: () -> Unit,
) {
	val borderColor = if (isSelected) Orange else Color.Transparent
	val bgColor = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 3.dp)
			.clip(RoundedCornerShape(10.dp))
			.border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
			.background(bgColor)
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
	) {
		Icon(
			imageVector = Icons.Default.Add,
			contentDescription = null,
			tint = Orange,
			modifier = Modifier.size(24.dp),
		)
		Spacer(modifier = Modifier.width(10.dp))
		Text(
			text = stringResource(R.string.theme_add_title),
			color = Color.White,
			fontSize = 15.sp,
		)
	}
}

@Composable
private fun AddThemePanel(
	onBrowseStore: () -> Unit,
	onCreateGuide: () -> Unit,
	onImportZip: () -> Unit,
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF161E2B)),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier
				.width(320.dp)
				.padding(24.dp),
		) {
			Text(
				text = stringResource(R.string.theme_add_title),
				color = Color.White,
				fontSize = 20.sp,
			)
			Spacer(modifier = Modifier.height(20.dp))

			// Play Store option
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(12.dp))
					.background(Color.White.copy(alpha = 0.08f))
					.clickable(onClick = onBrowseStore)
					.padding(16.dp),
			) {
				Icon(
					imageVector = Icons.Default.Search,
					contentDescription = null,
					tint = Orange,
					modifier = Modifier.size(28.dp),
				)
				Spacer(modifier = Modifier.width(14.dp))
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.theme_add_store),
						color = Color.White,
						fontSize = 15.sp,
					)
					Spacer(modifier = Modifier.height(2.dp))
					Text(
						text = stringResource(R.string.theme_add_store_desc),
						color = Color.White.copy(alpha = 0.5f),
						fontSize = 12.sp,
					)
				}
			}

			Spacer(modifier = Modifier.height(12.dp))

			// ZIP import option
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(12.dp))
					.background(Color.White.copy(alpha = 0.08f))
					.clickable(onClick = onImportZip)
					.padding(16.dp),
			) {
				Icon(
					imageVector = Icons.Outlined.FolderOpen,
					contentDescription = null,
					tint = Orange,
					modifier = Modifier.size(28.dp),
				)
				Spacer(modifier = Modifier.width(14.dp))
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.theme_add_zip),
						color = Color.White,
						fontSize = 15.sp,
					)
					Spacer(modifier = Modifier.height(2.dp))
					Text(
						text = stringResource(R.string.theme_add_zip_desc),
						color = Color.White.copy(alpha = 0.5f),
						fontSize = 12.sp,
					)
				}
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Create theme guide option
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(12.dp))
					.background(Color.White.copy(alpha = 0.08f))
					.clickable(onClick = onCreateGuide)
					.padding(16.dp),
			) {
				Icon(
					imageVector = Icons.Outlined.Edit,
					contentDescription = null,
					tint = Orange,
					modifier = Modifier.size(28.dp),
				)
				Spacer(modifier = Modifier.width(14.dp))
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.theme_add_create),
						color = Color.White,
						fontSize = 15.sp,
					)
					Spacer(modifier = Modifier.height(2.dp))
					Text(
						text = stringResource(R.string.theme_add_create_desc),
						color = Color.White.copy(alpha = 0.5f),
						fontSize = 12.sp,
					)
				}
			}
		}
	}
}

// region Preview composables

@Composable
private fun ThemePreview(bitmaps: PreviewBitmaps?) {
	if (bitmaps == null) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color(0xFF161E2B)),
		)
		return
	}

	Box(modifier = Modifier.fillMaxSize()) {
		// Background - 검정 바탕 위에 높이 기준 스케일링 (좌우 잘림 허용, 상하는 정확히 표시)
		Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
			bitmaps.playbg?.let { bg ->
				Image(
					bitmap = bg,
					contentDescription = null,
					contentScale = ContentScale.FillHeight,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		// Custom logo - TopEnd, 16dp padding, 90dp width (matching PlayActivity)
		bitmaps.customLogo?.let { logo ->
			Image(
				bitmap = logo,
				contentDescription = null,
				contentScale = ContentScale.FillWidth,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(16.dp)
					.width(90.dp),
			)
		}

		// Pad grid + chain column - 8dp padding, matching PlayActivity layout
		BoxWithConstraints(
			modifier = Modifier
				.fillMaxSize()
				.padding(8.dp),
		) {
			val padCols = 8
			val padRows = 8

			// Cell size based on pad grid only (PlayActivity: min(W/buttonY, H/buttonX))
			val cellSize = min(maxWidth / padCols, maxHeight / padRows)

			// Center the pad grid (chains extend to the right)
			val gridWidth = cellSize * padCols
			val gridHeight = cellSize * padRows
			val offsetX = (maxWidth - gridWidth) / 2
			val offsetY = (maxHeight - gridHeight) / 2

			Row(
				verticalAlignment = Alignment.Top,
				modifier = Modifier.offset(x = offsetX, y = offsetY),
			) {
				PadGridPreview(bitmaps, cellSize)
				ChainColumnPreview(bitmaps, cellSize)
			}
		}
	}
}

@Composable
private fun PadGridPreview(bitmaps: PreviewBitmaps, cellSize: Dp) {
	val cols = 8
	val rows = 8

	val litCells = remember {
		setOf(
			1 to 2, 2 to 2, 3 to 2,
			1 to 3, 3 to 3,
			1 to 4, 2 to 4, 3 to 4,
			5 to 2, 6 to 2,
			5 to 3, 6 to 3, 7 to 3,
			5 to 4, 6 to 4,
		)
	}

	val litColors = remember {
		listOf(
			Color(0x8800FF00),
			Color(0x88FF0000),
			Color(0x880088FF),
			Color(0x88FFAA00),
		)
	}

	// No spacing between cells - matching PlayActivity (cells are adjacent)
	Column {
		for (y in 0 until rows) {
			Row {
				for (x in 0 until cols) {
					Box(modifier = Modifier.size(cellSize)) {
						// Layer 1: btn background
						bitmaps.btn?.let { btnBitmap ->
							Image(
								bitmap = btnBitmap,
								contentDescription = null,
								contentScale = ContentScale.FillBounds,
								modifier = Modifier.fillMaxSize(),
							)
						}
						// Layer 2: LED color
						if ((x to y) in litCells) {
							val color = litColors[(x + y) % litColors.size]
							Box(
								modifier = Modifier
									.fillMaxSize()
									.background(color),
							)
						}
						// Layer 3: phantom on top
						val cx = cols / 2 - 1
						val cy = rows / 2 - 1
						val variantRotation = when {
							bitmaps.phantomVariant == null -> null
							x == cx && y == cy -> 0f
							x == cx + 1 && y == cy -> 90f
							x == cx && y == cy + 1 -> 270f
							x == cx + 1 && y == cy + 1 -> 180f
							else -> null
						}
						val phantomBitmap = if (variantRotation != null) {
							bitmaps.phantomVariant
						} else {
							bitmaps.phantom
						}
						phantomBitmap?.let {
							Image(
								bitmap = it,
								contentDescription = null,
								contentScale = ContentScale.FillBounds,
								modifier = Modifier
									.fillMaxSize()
									.graphicsLayer {
										rotationZ = variantRotation ?: 0f
									},
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ChainColumnPreview(bitmaps: PreviewBitmaps, cellSize: Dp) {
	val chainCount = 8
	val selectedChainColor = Color(0x8800FF00)

	// No spacing between cells - matching PlayActivity
	Column {
		for (i in 0 until chainCount) {
			Box(modifier = Modifier.size(cellSize)) {
				if (bitmaps.isChainLed) {
					// LED mode: btn(background) → led color(middle) → chainled(top)
					bitmaps.btn?.let { btnBitmap ->
						Image(
							bitmap = btnBitmap,
							contentDescription = null,
							contentScale = ContentScale.FillBounds,
							modifier = Modifier.fillMaxSize(),
						)
					}
					if (i == 0) {
						Box(
							modifier = Modifier
								.fillMaxSize()
								.background(selectedChainColor),
						)
					}
					bitmaps.chainDrawable?.let { chainledBitmap ->
						Image(
							bitmap = chainledBitmap,
							contentDescription = null,
							contentScale = ContentScale.FillBounds,
							modifier = Modifier.fillMaxSize(),
						)
					}
				} else {
					// Drawable mode: chain drawable only
					bitmaps.chainDrawable?.let { chainBitmap ->
						Image(
							bitmap = chainBitmap,
							contentDescription = null,
							contentScale = ContentScale.FillBounds,
							modifier = Modifier
								.fillMaxSize()
								.alpha(if (i == 0) 1f else 0.5f),
						)
					}
				}
			}
		}
	}
}

// endregion
