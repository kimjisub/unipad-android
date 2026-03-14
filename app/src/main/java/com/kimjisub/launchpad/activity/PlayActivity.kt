package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.manager.IThemeResources
import com.kimjisub.launchpad.manager.DefaultThemeResources
import com.kimjisub.launchpad.manager.loadTheme
import com.kimjisub.launchpad.manager.putClipboard
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.Log.log
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.CHAIN_INDEX_OFFSET
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.CIRCLE_ARRAY_SIZE
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.LED_BLUE
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.LOCKED_ALPHA
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.MAX_CHAIN_BUTTONS
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.TOP_BAR_COUNT
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.VOLUME_LEVELS
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.CheckBoxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.snackbar.Snackbar
import android.content.Intent
import kotlin.math.roundToInt

class PlayActivity : BaseActivity() {

	private lateinit var vm: PlayActivityViewModel

	// Dialog state
	private var unipackErrorDialog by mutableStateOf<Pair<String, String>?>(null) // title, message
	private var unipackErrorIsCritical by mutableStateOf(false)
	private var showRestartDialog by mutableStateOf(false)

	// UI - Theme
	private var theme by mutableStateOf<IThemeResources?>(null)

	// UI - AndroidView references
	private var padsContainer: LinearLayout? = null
	private var chainsTopContainer: LinearLayout? = null
	private var chainsRightContainer: LinearLayout? = null
	private var chainsBottomContainer: LinearLayout? = null
	private var chainsLeftContainer: LinearLayout? = null

	// UI - Layout dimensions (for relayout)
	private var lastScreenWidth = 0
	private var lastScreenHeight = 0
	private var lastPaddingWidth = 0
	private var lastPaddingHeight = 0

	// UI - Pad/chain views
	private lateinit var padViews: Array<Array<PadView?>>
	private lateinit var chainViews: Array<ChainView?>

	private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

	private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
		override fun onChange(selfChange: Boolean) {
			log("changed volume 1")
			updateVolumeUI()
			super.onChange(selfChange)
		}

		override fun onChange(selfChange: Boolean, uri: Uri?) {
			log("changed volume 2")
			updateVolumeUI()
			super.onChange(selfChange, uri)
		}
	}

	private val uiCallback = object : PlayActivityViewModel.UiCallback {
		override fun setLedPad(x: Int, y: Int) {
			if (vm.enable) {
				setLedLaunchpad(x, y)
				lifecycleScope.launch { setLedUI(x, y) }
			}
		}

		override fun setLedChain(c: Int) {
			if (vm.enable) {
				setLedLaunchpadChain(c)
				lifecycleScope.launch { setLedUIChain(c) }
			}
		}

		override fun updateTraceLogView(x: Int, y: Int) {
			val pad = padViews[x][y] ?: return
			pad.setTraceLogText("")
			for (i in vm.traceLogTable[vm.chain.value][x][y].indices) pad.appendTraceLog(
				"${vm.traceLogTable[vm.chain.value][x][y][i]} "
			)
		}

		override fun showTraceLog() {
			for (i in 0 until vm.unipack.buttonX) {
				for (j in 0 until vm.unipack.buttonY) {
					val pad = padViews[i][j] ?: continue
					pad.setTraceLogText("")
					for (k in vm.traceLogTable[vm.chain.value][i][j].indices) pad.appendTraceLog(
						"${vm.traceLogTable[vm.chain.value][i][j][k]} "
					)
				}
			}
		}

		override fun clearTraceLogViews() {
			if (::padViews.isInitialized) {
				for (i in 0 until vm.unipack.buttonX) for (j in 0 until vm.unipack.buttonY) padViews[i][j]?.setTraceLogText("")
			}
		}

		override fun showToast(resId: Int) {
			Snackbar.make(findViewById(android.R.id.content), resId, Snackbar.LENGTH_SHORT).show()
		}

		override fun finishActivity() {
			finish()
		}

		override fun copyToClipboard(text: String) {
			putClipboard(text)
		}

		override fun setChainViewVisibility(index: Int, visibility: Int) {
			chainViews[index]?.visibility = visibility
		}

		override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {
			padViews[x][y]?.startGuideAnimation(targetWallTimeMs)
		}

		override fun stopGuideAnimation(x: Int, y: Int) {
			padViews[x][y]?.stopGuideAnimation()
		}

		override fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int) {
			driver.sendPadLed(x, y, velocity)
		}

		override fun onRequestRelayout() {
			if (lastScreenWidth > 0 && lastScreenHeight > 0) {
				vm.uiLoaded = false
				initLayout(lastScreenWidth, lastScreenHeight, lastPaddingWidth, lastPaddingHeight)
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		vm = ViewModelProvider(
			this,
			PlayActivityViewModel.Factory(unipackRepo)
		)[PlayActivityViewModel::class.java]
		vm.uiCallback = uiCallback
		vm.enable = true

		val path = intent.getStringExtra("path") ?: run {
			finish()
			return
		}

		setContent {
			UniPadTheme {
				BackHandler(enabled = true) {
					if (vm.isOptionWindowVisible) {
						vm.toggleOptionWindow(false)
					} else {
						vm.toggleOptionWindow(true)
					}
				}
				PlayScreen()

				unipackErrorDialog?.let { (title, message) ->
					androidx.compose.material3.AlertDialog(
						onDismissRequest = {},
						title = { Text(title) },
						text = { Text(message) },
						confirmButton = {
							TextButton(onClick = {
								unipackErrorDialog = null
								if (unipackErrorIsCritical) finish()
							}) {
								Text(stringResource(if (unipackErrorIsCritical) string.quit else string.accept))
							}
						},
					)
				}

				if (showRestartDialog) {
					androidx.compose.material3.AlertDialog(
						onDismissRequest = {},
						title = { Text(stringResource(string.requireRestart)) },
						text = { Text(stringResource(string.doYouWantToRestartApp)) },
						confirmButton = {
							TextButton(onClick = {
								showRestartDialog = false
								restartApp(this@PlayActivity)
							}) {
								Text(stringResource(string.restart))
							}
						},
						dismissButton = {
							TextButton(onClick = {
								showRestartDialog = false
								finish()
							}) {
								Text(stringResource(string.cancel))
							}
						},
					)
				}
			}
		}

		// Load theme and unipack asynchronously after first frame
		lifecycleScope.launch {
			// Load theme on main thread (needs resource access) after first frame is drawn
			initTheme()

			try {
				val unipack = withContext(Dispatchers.IO) {
					vm.loadUnipack(path)
				}
				if (unipack.errorDetail != null) {
					unipackErrorIsCritical = unipack.criticalError
					unipackErrorDialog = Pair(
						if (unipack.criticalError) getString(string.error) else getString(string.warning),
						unipack.errorDetail.orEmpty(),
					)
				}
				if (!unipack.criticalError) {
					start()
				} else {
					vm.unipackLoading = false
				}
			} catch (e: OutOfMemoryError) {
				Log.err("UniPack load failed (OOM)", e)
				vm.unipackLoading = false
				vm.unipackLoadError = getString(string.outOfMemory)
				finish()
			} catch (e: Exception) {
				Log.err("UniPack load failed", e)
				vm.unipackLoading = false
				vm.unipackLoadError = "${getString(string.exceptionOccurred)}\n${e.message}"
				finish()
			}
		}
	}

	private fun start() {
		vm.initState()
		padViews = Array(vm.unipack.buttonX) { Array(vm.unipack.buttonY) { null } }
		chainViews = Array(CIRCLE_ARRAY_SIZE) { null }
		vm.startReady = theme != null
	}

	private fun initTheme() {
		val themeId = p.selectedTheme
		theme = try {
			loadTheme(this@PlayActivity, themeId, true)
		} catch (e: OutOfMemoryError) {
			Log.err("Theme OOM: $themeId", e)
			Snackbar.make(findViewById(android.R.id.content), "${getString(string.skinMemoryErr)}\n$themeId", Snackbar.LENGTH_SHORT).show()
			showRestartDialog = true
			null
		} catch (e: Exception) {
			Log.err("Theme load failed: $themeId", e)
			Snackbar.make(findViewById(android.R.id.content), "${getString(string.skinErr)}\n$themeId", Snackbar.LENGTH_SHORT).show()
			p.selectedTheme = getPackageName()
			DefaultThemeResources(this@PlayActivity)
		}
	}

	// region Compose UI

	@Composable
	private fun LoadingContent() {
		val isSoundLoading = vm.soundLoadingActive
		val phaseLabel: String
		val progress: Float

		if (isSoundLoading) {
			phaseLabel = stringResource(string.loading_phase_audio)
			progress = if (vm.soundLoadingMax > 0)
				vm.soundLoadingProgress.toFloat() / vm.soundLoadingMax.toFloat()
			else 0f
		} else {
			phaseLabel = when (vm.loadingPhase) {
				"info" -> stringResource(string.loading_phase_info)
				"keySound" -> stringResource(string.loading_phase_keysound)
				"keyLed" -> stringResource(string.loading_phase_keyled)
				"autoPlay" -> stringResource(string.loading_phase_autoplay)
				else -> stringResource(string.loading)
			}
			progress = vm.loadingPhaseIndex.toFloat() / vm.loadingPhaseTotal.toFloat()
		}

		Column(
			modifier = Modifier
				.width(280.dp)
				.background(
					Color(0x99000000),
					RoundedCornerShape(16.dp),
				)
				.padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = stringResource(string.loading),
				color = Color.White,
				fontSize = 18.sp,
				fontWeight = FontWeight.Bold,
			)
			Spacer(modifier = Modifier.height(16.dp))
			LinearProgressIndicator(
				progress = { progress },
				modifier = Modifier.fillMaxWidth().height(6.dp),
				color = Color(0xFF4FC3F7),
				trackColor = Color(0xFF333333),
			)
			Spacer(modifier = Modifier.height(12.dp))
			Text(
				text = if (isSoundLoading) "$phaseLabel (${vm.soundLoadingProgress}/${vm.soundLoadingMax})" else phaseLabel,
				color = Color(0xFFCCCCCC),
				fontSize = 13.sp,
			)
		}
	}

	@Composable
	private fun PlayScreen() {
		val density = LocalDensity.current
		val paddingPx = with(density) { 8.dp.toPx().toInt() }

		Box(
			modifier = Modifier
				.fillMaxSize()
				.let { mod ->
					if (vm.startReady) {
						mod.onSizeChanged { size ->
							if (!vm.uiLoaded && size.width > 0 && size.height > 0) {
								Handler(Looper.getMainLooper()).post {
									initLayout(size.width, size.height, size.width - 2 * paddingPx, size.height - 2 * paddingPx)
									vm.initRunner()
									vm.initSetting()
								}
							}
						}
					} else mod
				}
		) {
			// Background - always visible
			Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
				theme?.playbg?.let { bg ->
					val bitmap = remember(bg) { bg.toBitmap().asImageBitmap() }
					Image(
						bitmap = bitmap,
						contentDescription = null,
						contentScale = ContentScale.FillHeight,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}
			theme?.customLogo?.let { logo ->
				val bitmap = remember(logo) { logo.toBitmap().asImageBitmap() }
				Image(
					bitmap = bitmap,
					contentDescription = null,
					contentScale = ContentScale.Fit,
					modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).width(90.dp)
				)
			}

			if (vm.startReady) {
				// Main play content
				Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
					if (vm.optionViewVisible) {
						SideCheckPanel(modifier = Modifier.align(Alignment.CenterStart))
					}
					// Custom layout that centers pads independently and positions chains relative to pads
					Layout(
						content = {
							AndroidView(
								factory = { ctx -> LinearLayout(ctx).apply {
									orientation = LinearLayout.HORIZONTAL
									layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
								}.also { chainsTopContainer = it } },
								modifier = Modifier.wrapContentSize()
							)
							AndroidView(
								factory = { ctx -> LinearLayout(ctx).apply {
									orientation = LinearLayout.VERTICAL
									layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
								}.also { chainsLeftContainer = it } },
								modifier = Modifier.wrapContentSize()
							)
							AndroidView(
								factory = { ctx -> LinearLayout(ctx).apply {
									orientation = LinearLayout.VERTICAL
									layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
								}.also { padsContainer = it } },
								modifier = Modifier.wrapContentSize()
							)
							AndroidView(
								factory = { ctx -> LinearLayout(ctx).apply {
									orientation = LinearLayout.VERTICAL
									layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
								}.also { chainsRightContainer = it } },
								modifier = Modifier.wrapContentSize()
							)
							AndroidView(
								factory = { ctx -> LinearLayout(ctx).apply {
									orientation = LinearLayout.HORIZONTAL
									layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
								}.also { chainsBottomContainer = it } },
								modifier = Modifier.wrapContentSize()
							)
						},
						modifier = Modifier.fillMaxSize()
					) { measurables, constraints ->
						val unconstrained = constraints.copy(minWidth = 0, minHeight = 0)
						val topPlaceable = measurables[0].measure(unconstrained)
						val leftPlaceable = measurables[1].measure(unconstrained)
						val padPlaceable = measurables[2].measure(unconstrained)
						val rightPlaceable = measurables[3].measure(unconstrained)
						val bottomPlaceable = measurables[4].measure(unconstrained)
						layout(constraints.maxWidth, constraints.maxHeight) {
							val padX = (constraints.maxWidth - padPlaceable.width) / 2
							val padY = (constraints.maxHeight - padPlaceable.height) / 2
							padPlaceable.place(padX, padY)
							leftPlaceable.place(padX - leftPlaceable.width, padY + (padPlaceable.height - leftPlaceable.height) / 2)
							rightPlaceable.place(padX + padPlaceable.width, padY + (padPlaceable.height - rightPlaceable.height) / 2)
							topPlaceable.place(padX + (padPlaceable.width - topPlaceable.width) / 2, padY - topPlaceable.height)
							bottomPlaceable.place(padX + (padPlaceable.width - bottomPlaceable.width) / 2, padY + padPlaceable.height)
						}
					}
				}
				// Menu button (bottom-right)
				if (!vm.isOptionWindowVisible) {
					Icon(
						imageVector = Icons.Default.Menu,
						contentDescription = stringResource(string.menu),
						tint = Color.White.copy(alpha = 0.7f),
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.padding(16.dp)
							.size(32.dp)
							.clickable { vm.toggleOptionWindow(true) },
					)
				}
				AnimatedVisibility(visible = vm.isOptionWindowVisible, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
					Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { vm.toggleOptionWindow(false) })
				}
				AnimatedVisibility(
					visible = vm.isOptionWindowVisible,
					enter = slideInHorizontally(tween(300)) { it },
					exit = slideOutHorizontally(tween(250)) { it },
					modifier = Modifier.align(Alignment.CenterEnd),
				) {
					OptionPanel()
				}
			}

			// Loading overlay (unipack parsing or sound loading)
			if (vm.unipackLoading || vm.soundLoadingActive) {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					LoadingContent()
				}
			}
		}
	}


	@Composable
	private fun SideCheckPanel(modifier: Modifier = Modifier) {
		val cbColor = theme?.checkbox?.let { Color(it) } ?: colorResource(R.color.checkbox)
		val panelBg = Color.Black.copy(alpha = 0.35f)
		val panelShape = RoundedCornerShape(12.dp)

		Column(
			modifier = modifier.fillMaxHeight(),
			verticalArrangement = Arrangement.SpaceBetween,
		) {
			// Top group: performance controls
			Column(
				modifier = Modifier
					.background(panelBg, panelShape)
					.padding(horizontal = 6.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				PlayCheckBox(vm.scbFeedbackLight, string.feedbackLight, cbColor)
				PlayCheckBox(vm.scbLed, string.led, cbColor)
				PlayCheckBox(vm.scbAutoPlay, string.autoPlay, cbColor)
				if (vm.autoPlayControlVisible) AutoPlayControls(cbColor)
				if (vm.scbAutoPlay.visible && !vm.scbAutoPlay.locked && !vm.autoPlayControlVisible) {
					PracticeModeButton(cbColor)
				}
			}

			// Bottom group: tools
			Column(
				modifier = Modifier
					.background(panelBg, panelShape)
					.padding(horizontal = 6.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				PlayCheckBox(vm.scbTraceLog, string.traceLog, cbColor, hasLongClick = true)
				PlayCheckBox(vm.scbRecord, string.record, cbColor)
			}
		}
	}

	@Composable
	private fun AutoPlayControls(accentColor: Color) {
		val progressFraction = if (vm.autoPlayProgressMax > 0) vm.autoPlayProgress.toFloat() / vm.autoPlayProgressMax else 0f

		Column(
			modifier = Modifier.padding(top = 4.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			// Progress bar
			LinearProgressIndicator(
				progress = { progressFraction },
				modifier = Modifier
					.width(120.dp)
					.height(3.dp),
				color = accentColor,
				trackColor = Color.White.copy(alpha = 0.15f),
				drawStopIndicator = {},
			)

			// Transport controls
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				modifier = Modifier.padding(top = 2.dp),
			) {
				IconButton(onClick = { vm.autoPlayPrev() }, modifier = Modifier.size(32.dp)) {
					Icon(Icons.Default.SkipPrevious, stringResource(string.cd_autoplay_prev), tint = Color.White, modifier = Modifier.size(18.dp))
				}
				IconButton(
					onClick = { if (vm.autoPlayRunner?.playmode == true) vm.autoPlayStop() else vm.autoPlayPlay() },
					modifier = Modifier.size(36.dp),
				) {
					Icon(
						imageVector = if (vm.isAutoPlayPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
						contentDescription = stringResource(if (vm.isAutoPlayPlaying) string.cd_autoplay_pause else string.cd_autoplay_play),
						tint = Color.White,
						modifier = Modifier.size(22.dp),
					)
				}
				IconButton(onClick = { vm.autoPlayNext() }, modifier = Modifier.size(32.dp)) {
					Icon(Icons.Default.SkipNext, stringResource(string.cd_autoplay_next), tint = Color.White, modifier = Modifier.size(18.dp))
				}
			}

			// Practice/Playback mode toggle
			val isPractice = vm.isPracticeMode
			val modeText = if (isPractice) stringResource(string.practiceMode) else stringResource(string.autoPlay)
			val modeColor = if (isPractice) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.6f)

			Row(
				modifier = Modifier
					.padding(top = 2.dp)
					.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
					.clickable { vm.togglePracticeMode() }
					.padding(horizontal = 8.dp, vertical = 3.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Icon(
					imageVector = Icons.Default.FiberManualRecord,
					contentDescription = null,
					tint = modeColor,
					modifier = Modifier.size(6.dp),
				)
				Text(text = modeText, color = modeColor, fontSize = 10.sp)
			}
		}
	}

	@Composable
	private fun PracticeModeButton(color: Color) {
		val scale = 0.75f
		Row(
			modifier = Modifier
				.graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f))
				.then(scaleLayoutModifier(scale))
				.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
				.clickable { vm.practiceStart() }
				.padding(horizontal = 8.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Icon(
				imageVector = Icons.Default.PlayArrow,
				contentDescription = null,
				tint = color,
				modifier = Modifier.size(14.dp),
			)
			Text(text = stringResource(string.practiceMode), color = color, fontSize = 13.sp)
		}
	}

	@Composable
	private fun OptionPanel() {
		val panelBg = Color(0xF0161E2B)
		val accentColor = Color(0xFFE8A44A)
		val textColor = Color.White
		val sectionColor = textColor.copy(alpha = 0.4f)

		Column(
			modifier = Modifier
				.fillMaxHeight()
				.width(280.dp)
				.background(panelBg)
				.verticalScroll(rememberScrollState())
				.padding(vertical = 24.dp),
		) {
			// Title with close button
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 24.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					text = stringResource(string.menu),
					color = textColor,
					fontSize = 22.sp,
				)
				Icon(
					painter = painterResource(R.drawable.ic_exit),
					contentDescription = stringResource(string.quit),
					tint = Color(0xFFFF6B6B),
					modifier = Modifier
						.size(24.dp)
						.clickable { finish() },
				)
			}

			Spacer(modifier = Modifier.height(8.dp))

			// UniPack info
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 24.dp)
					.background(Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp))
					.padding(16.dp),
			) {
				Text(
					text = vm.unipack.title.ifEmpty { "Untitled" },
					color = textColor,
					fontSize = 16.sp,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (vm.unipack.producerName.isNotEmpty()) {
					Text(
						text = vm.unipack.producerName,
						color = sectionColor,
						fontSize = 13.sp,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = "${vm.unipack.buttonX}×${vm.unipack.buttonY}  ·  ${vm.unipack.chain} chain",
					color = sectionColor,
					fontSize = 12.sp,
				)
			}

			Spacer(modifier = Modifier.height(16.dp))

			// Performance section
			SectionTitle("Performance", sectionColor)
			OptionSwitch(vm.scbFeedbackLight, string.feedbackLight, textColor, accentColor)
			OptionSwitch(vm.scbLed, string.led, textColor, accentColor)
			OptionSwitch(vm.scbAutoPlay, string.autoPlay, textColor, accentColor)
			if (vm.scbAutoPlay.visible && !vm.scbAutoPlay.locked) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clickable { vm.practiceStart(); vm.toggleOptionWindow(false) }
						.padding(horizontal = 24.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(string.practiceMode),
						color = textColor,
						fontSize = 14.sp,
						modifier = Modifier.weight(1f),
					)
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = stringResource(string.practiceMode),
						tint = accentColor,
						modifier = Modifier.size(20.dp),
					)
				}
			}

			Spacer(modifier = Modifier.height(16.dp))

			// Display section
			SectionTitle("Display", sectionColor)
			OptionSwitch(vm.scbHideUI, string.hideUI, textColor, accentColor)
			OptionSwitch(vm.scbWatermark, string.watermark, textColor, accentColor)
			OptionSwitch(vm.scbProLightMode, string.proLightMode, textColor, accentColor)

			Spacer(modifier = Modifier.height(16.dp))

			// Tools section
			SectionTitle("Tools", sectionColor)
			OptionSwitch(vm.scbTraceLog, string.traceLog, textColor, accentColor, hasLongClick = true)
			OptionSwitch(vm.scbRecord, string.record, textColor, accentColor)

			Spacer(modifier = Modifier.weight(1f))

			// Quit button
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clickable { finish() }
					.padding(horizontal = 24.dp, vertical = 16.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(
					painter = painterResource(R.drawable.ic_exit),
					contentDescription = stringResource(string.quit),
					tint = Color(0xFFFF6B6B),
					modifier = Modifier.size(20.dp),
				)
				Spacer(modifier = Modifier.width(12.dp))
				Text(
					text = stringResource(string.quit),
					color = Color(0xFFFF6B6B),
					fontSize = 15.sp,
				)
			}
		}
	}

	@Composable
	private fun SectionTitle(title: String, color: Color) {
		Text(
			text = title.uppercase(),
			color = color,
			fontSize = 11.sp,
			letterSpacing = 1.sp,
			modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
		)
	}

	@OptIn(ExperimentalFoundationApi::class)
	@Composable
	private fun OptionSwitch(state: CheckBoxState, textResId: Int, textColor: Color, accentColor: Color, hasLongClick: Boolean = false) {
		if (!state.visible) return
		val alpha = if (state.locked) LOCKED_ALPHA else 1f
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.alpha(alpha)
				.then(
					if (!state.locked) {
						if (hasLongClick && state.onLongClick != null)
							Modifier.combinedClickable(onClick = { state.toggleChecked() }, onLongClick = state.onLongClick)
						else
							Modifier.clickable { state.toggleChecked() }
					} else Modifier
				)
				.padding(horizontal = 24.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = stringResource(textResId),
				color = textColor,
				fontSize = 14.sp,
				modifier = Modifier.weight(1f),
			)
			Switch(
				checked = state.checked,
				onCheckedChange = if (!state.locked) { { state.setChecked(it) } } else null,
				colors = SwitchDefaults.colors(
					checkedThumbColor = Color.White,
					checkedTrackColor = accentColor,
					uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
					uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
					uncheckedBorderColor = Color.White.copy(alpha = 0.2f),
				),
			)
		}
	}

	@OptIn(ExperimentalFoundationApi::class)
	@Composable
	private fun PlayCheckBox(state: CheckBoxState, textResId: Int, color: Color, hasLongClick: Boolean = false) {
		if (!state.visible) return
		val alpha = if (state.locked) LOCKED_ALPHA else 1f
		val indicatorColor = if (state.checked) color else color.copy(alpha = 0.25f)
		Row(
			modifier = Modifier
				.alpha(alpha)
				.then(
					if (!state.locked) {
						if (hasLongClick && state.onLongClick != null)
							Modifier.combinedClickable(onClick = { state.toggleChecked() }, onLongClick = state.onLongClick)
						else
							Modifier.clickable { state.toggleChecked() }
					} else Modifier
				)
				.padding(horizontal = 2.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			// Dot indicator instead of switch
			Box(
				modifier = Modifier
					.size(8.dp)
					.background(indicatorColor, RoundedCornerShape(50)),
			)
			Text(
				text = stringResource(textResId),
				color = if (state.checked) Color.White else Color.White.copy(alpha = 0.5f),
				fontSize = 12.sp,
			)
		}
	}

	private fun scaleLayoutModifier(scale: Float): Modifier = object : LayoutModifier {
		override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
			val placeable = measurable.measure(constraints)
			return layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
				placeable.place(0, 0)
			}
		}
	}

	@Composable
	private fun DrawableView(drawable: Drawable?, modifier: Modifier = Modifier, scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER) {
		if (drawable == null) return
		AndroidView(factory = { ctx -> ImageView(ctx).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; this.scaleType = scaleType; setImageDrawable(drawable) } }, modifier = modifier)
	}

	// endregion

	// region Layout initialization

	private fun initLayout(screenWidth: Int, screenHeight: Int, paddingWidth: Int, paddingHeight: Int) {
		lastScreenWidth = screenWidth
		lastScreenHeight = screenHeight
		lastPaddingWidth = paddingWidth
		lastPaddingHeight = paddingHeight
		try {
			log("[05] Set Button Layout (squareButton = ${vm.unipack.squareButton})")
			vm.setupCheckBoxVisibility()

			val buttonSizeX: Int
			val buttonSizeY: Int
			if (vm.unipack.squareButton) {
				val chainColumns = 2
				val chainRows = if (vm.scbProLightMode.isChecked()) 2 else 0
				val s = (paddingWidth / (vm.unipack.buttonX + chainColumns)).coerceAtMost(paddingHeight / (vm.unipack.buttonY + chainRows))
				buttonSizeX = s; buttonSizeY = s
			} else {
				buttonSizeX = screenWidth / vm.unipack.buttonY
				buttonSizeY = screenHeight / vm.unipack.buttonX
			}
			val buttonSizeMin = buttonSizeX.coerceAtMost(buttonSizeY)

			vm.setupCheckBoxListeners()
			padsContainer?.removeAllViews()
			chainsTopContainer?.removeAllViews()
			chainsRightContainer?.removeAllViews()
			chainsBottomContainer?.removeAllViews()
			chainsLeftContainer?.removeAllViews()
			setupPads(buttonSizeX, buttonSizeY)
			setupChains(buttonSizeMin)
			vm.traceLogInit()
			vm.proLightMode(vm.scbProLightMode.isChecked())
			vm.uiLoaded = true
			vm.chainBtnsRefresh()
			updateVolumeUI()
			controller = midiController
		} catch (e: RuntimeException) {
			Log.err("initLayout failed", e)
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private fun setupPads(buttonSizeX: Int, buttonSizeY: Int) {
		for (x in 0 until vm.unipack.buttonX) {
			val row = LinearLayout(this)
			row.layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1F)
			for (y in 0 until vm.unipack.buttonY) {
				val view = PadView(this)
				view.layoutParams = LayoutParams(buttonSizeX, buttonSizeY)
				view.setBackgroundImageDrawable(theme?.btn)
				theme?.traceLog?.let { view.setTraceLogTextColor(it) }
				view.setOnTouchListener { _, event ->
					when (event?.action) {
						MotionEvent.ACTION_DOWN -> vm.padTouch(x, y, true)
						MotionEvent.ACTION_UP -> vm.padTouch(x, y, false)
					}
					false
				}
				padViews[x][y] = view
				row.addView(view)
			}
			padsContainer?.addView(row)
		}
		if (vm.unipack.buttonX < 16 && vm.unipack.buttonY < 16) {
			for (i in 0 until vm.unipack.buttonX)
				for (j in 0 until vm.unipack.buttonY)
					padViews[i][j]?.setPhantomImageDrawable(theme?.phantom)
			if (vm.unipack.buttonX % 2 == 0 && vm.unipack.buttonY % 2 == 0 && vm.unipack.squareButton && theme?.phantomVariant != null) {
				val x = vm.unipack.buttonX / 2 - 1
				val y = vm.unipack.buttonY / 2 - 1
				padViews[x][y]?.setPhantomImageDrawable(theme?.phantomVariant)
				padViews[x + 1][y]?.setPhantomImageDrawable(theme?.phantomVariant)
				padViews[x + 1][y]?.setPhantomRotation(270f)
				padViews[x][y + 1]?.setPhantomImageDrawable(theme?.phantomVariant)
				padViews[x][y + 1]?.setPhantomRotation(90f)
				padViews[x + 1][y + 1]?.setPhantomImageDrawable(theme?.phantomVariant)
				padViews[x + 1][y + 1]?.setPhantomRotation(180f)
			}
		}
	}

	private fun setupChains(buttonSizeMin: Int) {
		for (i in 0 until CIRCLE_ARRAY_SIZE) {
			val c = i - CHAIN_INDEX_OFFSET
			val view = ChainView(this)
			view.layoutParams = RelativeLayout.LayoutParams(buttonSizeMin, buttonSizeMin)
			if (theme?.isChainLed == true) { view.setBackgroundImageDrawable(theme?.btn); view.setPhantomImageDrawable(theme?.chainled) }
			else { view.setPhantomImageDrawable(theme?.chain); view.setLedVisibility(View.GONE) }
			chainViews[i] = view
			if (i in 0..7) { chainsTopContainer?.addView(view) }
			if (i in 8..15) { view.setOnClickListener { vm.chain.value = c }; chainsRightContainer?.addView(view) }
			if (i in 16..23) { view.setOnClickListener { vm.chain.value = c }; chainsBottomContainer?.addView(view, 0) }
			if (i in 24..31) { view.setOnClickListener { vm.chain.value = c }; chainsLeftContainer?.addView(view, 0) }
		}
	}

	// endregion

	// region LED rendering

	private fun setLedUI(x: Int, y: Int) {
		val pad = padViews[x][y] ?: return
		val item = vm.channelManager.get(x, y)
		if (item != null) {
			when (item.channel) {
				Channel.GUIDE -> pad.setLedBackgroundColor(item.color)
				Channel.PRESSED -> pad.setLedBackground(theme?.btnPressed)
				Channel.LED -> pad.setLedBackgroundColor(item.color)
				else -> {}
			}
		} else pad.setLedBackgroundColor(0)
	}

	private fun setLedLaunchpad(x: Int, y: Int) {
		val item = vm.channelManager.get(x, y)
		if (item != null) driver.sendPadLed(x, y, item.code) else driver.sendPadLed(x, y, 0)
	}

	private fun setLedUIChain(y: Int) {
		if (y !in chainViews.indices) return
		val item = vm.channelManager.get(-1, y)
		val circle = chainViews[y] ?: return
		if (theme?.isChainLed == true) {
			if (item != null) {
				when (item.channel) {
					Channel.GUIDE -> circle.setLedBackgroundColor(item.color)
					Channel.CHAIN -> circle.setLedBackgroundColor(item.color)
					Channel.LED -> circle.setLedBackgroundColor(item.color)
					else -> {}
				}
			} else circle.setLedBackgroundColor(0)
		} else {
			if (item != null) {
				when (item.channel) {
					Channel.GUIDE -> circle.setBackgroundImageDrawable(theme?.chainGuide)
					Channel.CHAIN -> circle.setBackgroundImageDrawable(theme?.chainSelected)
					Channel.LED -> circle.setBackgroundImageDrawable(theme?.chain)
					else -> {}
				}
			} else circle.setBackgroundImageDrawable(theme?.chain)
		}
	}

	private fun setLedLaunchpadChain(c: Int) {
		val item = vm.channelManager.get(-1, c)
		if (item != null) driver.sendFunctionKeyLed(c, item.code) else driver.sendFunctionKeyLed(c, 0)
	}

	private fun redrawAllLaunchpadLeds() {
		driver.sendClearLed()
		for (x in 0 until vm.unipack.buttonX)
			for (y in 0 until vm.unipack.buttonY)
				setLedLaunchpad(x, y)
		for (c in 0 until CIRCLE_ARRAY_SIZE)
			setLedLaunchpadChain(c)
	}

	// endregion

	// region MIDI controller

	private var midiController: MidiController? = object : MidiController() {
		override fun onAttach() { redrawAllLaunchpadLeds(); vm.chain.refresh(); vm.refreshWatermark() }
		override fun onDetach() {}
		override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
			if (!vm.isOptionWindowVisible) vm.padTouch(x, y, upDown)
		}
		override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
			if (upDown) {
				if (!vm.isOptionWindowVisible) {
					when (f) {
						0 -> vm.scbFeedbackLight.toggleChecked()
						1 -> vm.scbLed.toggleChecked()
						2 -> vm.scbAutoPlay.toggleChecked()
						3 -> vm.toggleOptionWindow()
						4, 5, 6, 7 -> vm.scbWatermark.toggleChecked()
					}
				} else {
					if (f in 0 until TOP_BAR_COUNT) when (f) {
						0 -> vm.scbFeedbackLight.toggleChecked()
						1 -> vm.scbLed.toggleChecked()
						2 -> vm.scbAutoPlay.toggleChecked()
						3 -> vm.toggleOptionWindow()
						4 -> vm.scbHideUI.toggleChecked()
						5 -> vm.scbWatermark.toggleChecked()
						6 -> vm.scbProLightMode.toggleChecked()
						7 -> finish()
					} else if (f in CHAIN_INDEX_OFFSET until CHAIN_INDEX_OFFSET + TOP_BAR_COUNT) {
						setVolume(CHAIN_INDEX_OFFSET + VOLUME_LEVELS - f)
					}
				}
			}
		}
		override fun onChainTouch(c: Int, upDown: Boolean) {
			if (!vm.isOptionWindowVisible && upDown && vm.unipack.chain > c) vm.chain.value = c
		}
		override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) {
			if (cmd == 7 && sig == 46 && note == 0 && velocity == -9) { vm.chain.refresh(); vm.refreshWatermark() }
		}
	}

	// endregion

	// region Volume

	private fun setVolume(level: Int) {
		val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * level / VOLUME_LEVELS).toInt(), 0)
	}

	private fun updateVolumeUI() {
		if (!::vm.isInitialized || !vm.isChannelManagerInitialized) return
		val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
		val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
		var level = (volume / maxVolume * VOLUME_LEVELS).roundToInt() + 1
		if (level == 1) level = 0
		val range = VOLUME_LEVELS downTo TOP_BAR_COUNT - level
		for (c in 0 until TOP_BAR_COUNT) {
			val y = CHAIN_INDEX_OFFSET + c
			if (c in range) vm.channelManager.add(-1, y, Channel.UI, -1, LED_BLUE) else vm.channelManager.remove(-1, y, Channel.UI)
			uiCallback.setLedChain(y)
		}
	}

	// endregion

	// region Lifecycle

	override fun onResume() {
		super.onResume()
		contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		if (vm.uiLoaded) controller = midiController
	}

	override fun onPause() {
		super.onPause()
		window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		contentResolver.unregisterContentObserver(volumeObserver)
		driver.sendClearLed()
		midiController?.let { removeController(it) }
	}

	override fun onDestroy() {
		super.onDestroy()
		vm.uiCallback = null
		vm.enable = false
		midiController?.let { removeController(it) }
	}

	// endregion
}
