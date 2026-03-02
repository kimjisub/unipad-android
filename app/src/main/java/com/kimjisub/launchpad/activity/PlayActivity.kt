package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
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
import androidx.appcompat.app.AlertDialog.Builder
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

class PlayActivity : BaseActivity() {

	private lateinit var vm: PlayActivityViewModel

	// UI - Theme
	private var theme: IThemeResources? = null

	// UI - AndroidView references
	private var padsContainer: LinearLayout? = null
	private var chainsRightContainer: LinearLayout? = null
	private var chainsLeftContainer: LinearLayout? = null

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

		try {
			val unipack = vm.loadUnipack(path)
			if (unipack.errorDetail != null) {
				Builder(this@PlayActivity)
					.setTitle(
						if (unipack.criticalError) getString(string.error) else getString(string.warning)
					)
					.setMessage(unipack.errorDetail)
					.setPositiveButton(
						if (unipack.criticalError) getString(string.quit) else getString(string.accept),
						if (unipack.criticalError) OnClickListener { _: DialogInterface?, _: Int -> finish() } else null
					)
					.setCancelable(false)
					.show()
			}
			if (!unipack.criticalError) start()
		} catch (e: OutOfMemoryError) {
			Log.err("UniPack load failed (OOM)", e)
			Snackbar.make(findViewById(android.R.id.content), string.outOfMemory, Snackbar.LENGTH_SHORT).show()
			finish()
		} catch (e: Exception) {
			Log.err("UniPack load failed", e)
			Snackbar.make(findViewById(android.R.id.content), "${getString(string.exceptionOccurred)}\n${e.message}", Snackbar.LENGTH_SHORT).show()
			finish()
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
			}
		}
	}

	private fun start() {
		vm.initState()
		padViews = Array(vm.unipack.buttonX) { Array(vm.unipack.buttonY) { null } }
		chainViews = Array(CIRCLE_ARRAY_SIZE) { null }

		initTheme()
		vm.startReady = theme != null
	}

	private fun initTheme() {
		val themeId = p.selectedTheme
		theme = try {
			loadTheme(this@PlayActivity, themeId, true)
		} catch (e: OutOfMemoryError) {
			Log.err("Theme OOM: $themeId", e)
			Snackbar.make(findViewById(android.R.id.content), "${getString(string.skinMemoryErr)}\n$themeId", Snackbar.LENGTH_SHORT).show()
			requestRestart(this)
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
	private fun PlayScreen() {
		if (!vm.startReady) return

		val density = LocalDensity.current
		val paddingPx = with(density) { 8.dp.toPx().toInt() }

		Box(
			modifier = Modifier
				.fillMaxSize()
				.onSizeChanged { size ->
					if (!vm.uiLoaded && size.width > 0 && size.height > 0) {
						// Post to run after the current layout pass so that native views
						// added in initLayout are measured in the next Compose layout pass
						Handler(Looper.getMainLooper()).post {
							initLayout(size.width, size.height, size.width - 2 * paddingPx, size.height - 2 * paddingPx)
							vm.initRunner()
							vm.initSetting()
						}
					}
				}
		) {
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
			DrawableView(
				drawable = theme?.customLogo,
				scaleType = ImageView.ScaleType.FIT_START,
				modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).width(90.dp)
			)
			Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
				if (vm.optionViewVisible) {
					SideCheckPanel(modifier = Modifier.align(Alignment.CenterStart))
				}
				// Custom layout that centers pads independently and positions chains relative to pads
				// Replicates the old RelativeLayout behavior where pads were layout_centerHorizontal/Vertical
				// and chains were layout_toStartOf/toEndOf pads
				Layout(
					content = {
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
					},
					modifier = Modifier.fillMaxSize()
				) { measurables, constraints ->
					val unconstrained = constraints.copy(minWidth = 0, minHeight = 0)
					val leftPlaceable = measurables[0].measure(unconstrained)
					val padPlaceable = measurables[1].measure(unconstrained)
					val rightPlaceable = measurables[2].measure(unconstrained)
					layout(constraints.maxWidth, constraints.maxHeight) {
						// Center pads independently
						val padX = (constraints.maxWidth - padPlaceable.width) / 2
						val padY = (constraints.maxHeight - padPlaceable.height) / 2
						padPlaceable.place(padX, padY)
						// Position chains relative to pads (aligned to top of pads)
						leftPlaceable.place(padX - leftPlaceable.width, padY)
						rightPlaceable.place(padX + padPlaceable.width, padY)
					}
				}
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

			// Sound loading overlay
			if (vm.soundLoadingActive) {
				SoundLoadingOverlay(
					progress = vm.soundLoadingProgress,
					max = vm.soundLoadingMax,
				)
			}
		}
	}

	@Composable
	private fun SoundLoadingOverlay(progress: Int, max: Int) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color(0xCC000000)),
			contentAlignment = Alignment.Center,
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier
					.background(Color(0xFF1E2736), RoundedCornerShape(12.dp))
					.padding(horizontal = 32.dp, vertical = 24.dp),
			) {
				Text(
					text = stringResource(string.loading),
					color = Color.White,
					fontSize = 16.sp,
				)

				androidx.compose.foundation.layout.Spacer(
					modifier = Modifier.padding(top = 12.dp),
				)

				LinearProgressIndicator(
					progress = { if (max > 0) progress.toFloat() / max else 0f },
					modifier = Modifier
						.width(200.dp)
						.padding(vertical = 4.dp),
					color = Color(0xFF4283E6),
					trackColor = Color(0xFF2A3648),
					drawStopIndicator = {},
				)

				Text(
					text = "$progress / $max",
					color = Color(0xFFA6B4C9),
					fontSize = 12.sp,
					modifier = Modifier.padding(top = 4.dp),
				)
			}
		}
	}

	@Composable
	private fun SideCheckPanel(modifier: Modifier = Modifier) {
		val cbColor = theme?.checkbox?.let { Color(it) } ?: colorResource(R.color.checkbox)
		Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
			Column {
				PlayCheckBox(vm.scbFeedbackLight, string.feedbackLight, cbColor)
				PlayCheckBox(vm.scbLed, string.led, cbColor)
				PlayCheckBox(vm.scbAutoPlay, string.autoPlay, cbColor)
				if (vm.autoPlayControlVisible) AutoPlayControls()
			}
			Column {
				PlayCheckBox(vm.scbTraceLog, string.traceLog, cbColor, hasLongClick = true)
				PlayCheckBox(vm.scbRecord, string.record, cbColor)
			}
		}
	}

	@Composable
	private fun AutoPlayControls() {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			LinearProgressIndicator(
				progress = { if (vm.autoPlayProgressMax > 0) vm.autoPlayProgress.toFloat() / vm.autoPlayProgressMax else 0f },
				modifier = Modifier.width(120.dp),
				drawStopIndicator = {},
			)
			Row {
				DrawableButton(ResourcesCompat.getDrawable(resources, R.drawable.xml_prev, null), stringResource(string.cd_autoplay_prev)) { vm.autoPlayPrev() }
				DrawableButton(
					ResourcesCompat.getDrawable(resources, if (vm.isAutoPlayPlaying) R.drawable.xml_pause else R.drawable.xml_play, null),
					stringResource(if (vm.isAutoPlayPlaying) string.cd_autoplay_pause else string.cd_autoplay_play)
				) { if (vm.autoPlayRunner?.playmode == true) vm.autoPlayStop() else vm.autoPlayPlay() }
				DrawableButton(ResourcesCompat.getDrawable(resources, R.drawable.xml_next, null), stringResource(string.cd_autoplay_next)) { vm.autoPlayNext() }
			}
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
			// Title
			Text(
				text = stringResource(string.menu),
				color = textColor,
				fontSize = 22.sp,
				modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
			)

			Spacer(modifier = Modifier.height(16.dp))

			// Performance section
			SectionTitle("Performance", sectionColor)
			OptionSwitch(vm.scbFeedbackLight, string.feedbackLight, textColor, accentColor)
			OptionSwitch(vm.scbLed, string.led, textColor, accentColor)
			OptionSwitch(vm.scbAutoPlay, string.autoPlay, textColor, accentColor)

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
				.padding(horizontal = 4.dp, vertical = 2.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Switch(
				checked = state.checked,
				onCheckedChange = if (!state.locked) { { state.setChecked(it) } } else null,
				colors = SwitchDefaults.colors(
					checkedThumbColor = Color.White,
					checkedTrackColor = color,
					uncheckedThumbColor = color.copy(alpha = 0.5f),
					uncheckedTrackColor = Color.Transparent,
					uncheckedBorderColor = color.copy(alpha = 0.3f),
				),
				modifier = Modifier.size(width = 40.dp, height = 24.dp).padding(end = 6.dp),
			)
			Text(text = stringResource(textResId), color = color, fontSize = 13.sp)
		}
	}

	@Composable
	private fun DrawableView(drawable: Drawable?, modifier: Modifier = Modifier, scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER) {
		if (drawable == null) return
		AndroidView(factory = { ctx -> ImageView(ctx).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; this.scaleType = scaleType; setImageDrawable(drawable) } }, modifier = modifier)
	}

	@Composable
	private fun DrawableButton(drawable: Drawable?, contentDescription: String, onClick: () -> Unit) {
		AndroidView(
			factory = { ctx -> ImageView(ctx).apply {
				layoutParams = LayoutParams(resources.getDimensionPixelSize(R.dimen.autoplay_button_size), resources.getDimensionPixelSize(R.dimen.autoplay_button_size))
				val pad = resources.getDimensionPixelSize(R.dimen.autoplay_button_padding)
				setPadding(pad, pad, pad, pad)
				this.contentDescription = contentDescription
				isFocusable = true; isClickable = true; background = drawable
				setOnClickListener { onClick() }
			} },
			update = { it.background = drawable },
			modifier = Modifier.size(48.dp)
		)
	}

	// endregion

	// region Layout initialization

	private fun initLayout(screenWidth: Int, screenHeight: Int, paddingWidth: Int, paddingHeight: Int) {
		try {
			log("[05] Set Button Layout (squareButton = ${vm.unipack.squareButton})")
			vm.setupCheckBoxVisibility()

			val buttonSizeX: Int
			val buttonSizeY: Int
			if (vm.unipack.squareButton) {
				val s = (paddingWidth / vm.unipack.buttonX).coerceAtMost(paddingHeight / vm.unipack.buttonY)
				buttonSizeX = s; buttonSizeY = s
			} else {
				buttonSizeX = screenWidth / vm.unipack.buttonY
				buttonSizeY = screenHeight / vm.unipack.buttonX
			}
			val buttonSizeMin = buttonSizeX.coerceAtMost(buttonSizeY)

			vm.setupCheckBoxListeners()
			padsContainer?.removeAllViews()
			chainsRightContainer?.removeAllViews()
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
			if (c in 0 until TOP_BAR_COUNT) { view.setOnClickListener { vm.chain.value = c }; chainsRightContainer?.addView(view) }
			if (c in 16..23) { view.setOnClickListener { vm.chain.value = c }; chainsLeftContainer?.addView(view, 0) }
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
		val c = y - CHAIN_INDEX_OFFSET
		val item = vm.channelManager.get(-1, y)
		val circle = chainViews[y] ?: return
		if (c in 0 until MAX_CHAIN_BUTTONS)
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

	// endregion

	// region MIDI controller

	private var midiController: MidiController? = object : MidiController() {
		override fun onAttach() { vm.chain.refresh(); vm.refreshWatermark() }
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
	}

	override fun onDestroy() {
		super.onDestroy()
		vm.uiCallback = null
		vm.enable = false
		midiController?.let { removeController(it) }
	}

	// endregion
}
