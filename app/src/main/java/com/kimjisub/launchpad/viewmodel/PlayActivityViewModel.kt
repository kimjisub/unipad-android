package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.Log.log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.ChainObserver
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

enum class PlayMode {
	None,
	AutoPlay,
	GuidePlay,
	StepPractice,
}

class PlayActivityViewModel(
	private val unipackRepo: UnipackRepository,
) : ViewModel() {

	companion object {
		// Launchpad LED velocity color codes
		const val LED_RED_DIM = 1
		const val LED_RED = 3
		const val LED_RED_BRIGHT = 5
		const val LED_WARM = 11
		const val LED_ORANGE = 17
		const val LED_YELLOW = 19
		const val LED_BLUE = 40
		const val LED_LAVENDER = 43
		const val LED_CYAN = 52
		const val LED_LIGHT_BLUE = 55
		const val LED_GREEN = 61

		// Circle/chain layout constants
		const val CIRCLE_ARRAY_SIZE = 32
		const val CHAIN_INDEX_OFFSET = 8
		const val TOP_BAR_COUNT = 8
		const val MAX_CHAIN_BUTTONS = 24
		const val FUNCTION_KEY_COUNT = 36
		const val VOLUME_LEVELS = 7

		const val LOCKED_ALPHA = 0.3f
	}

	// Compose checkbox state holder
	class CheckBoxState(initialChecked: Boolean = false) {
		private var _checked by mutableStateOf(initialChecked)
		val checked: Boolean get() = _checked
		var locked by mutableStateOf(false)
		var visible by mutableStateOf(true)
		var onCheckedChange: ((Boolean) -> Unit)? = null
		var onLongClick: (() -> Unit)? = null

		fun isChecked() = _checked

		fun setChecked(value: Boolean) {
			if (!locked) forceSetChecked(value)
		}

		fun forceSetChecked(value: Boolean) {
			_checked = value
			onCheckedChange?.invoke(value)
		}

		fun setCheckedSilently(value: Boolean) {
			_checked = value
		}

		fun toggleChecked() {
			if (!locked) forceSetChecked(!_checked)
		}
	}

	// UI callback for operations requiring Activity context
	interface UiCallback {
		fun setLedPad(x: Int, y: Int)
		fun setLedChain(c: Int)
		fun updateTraceLogView(x: Int, y: Int)
		fun showTraceLog()
		fun clearTraceLogViews()
		fun showToast(resId: Int)
		fun finishActivity()
		fun copyToClipboard(text: String)
		fun setChainViewVisibility(index: Int, visibility: Int)
		fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long)
		fun stopGuideAnimation(x: Int, y: Int)
		fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int)
		fun onRequestRelayout()
	}

	var uiCallback: UiCallback? = null

	// State
	lateinit var unipack: UniPack
	var uiLoaded = false
	var enable = true
	val chain = ChainObserver()

	// Checkbox states
	val scbFeedbackLight = CheckBoxState()
	val scbLed = CheckBoxState()
	val scbAutoPlay = CheckBoxState()
	val scbTraceLog = CheckBoxState()
	val scbRecord = CheckBoxState()
	val scbHideUI = CheckBoxState()
	val scbWatermark = CheckBoxState(initialChecked = true)
	val scbProLightMode = CheckBoxState()

	// Compose UI state
	var autoPlayControlVisible by mutableStateOf(false)
	var autoPlayProgress by mutableIntStateOf(0)
	var autoPlayProgressMax by mutableIntStateOf(0)
	var isAutoPlayPlaying by mutableStateOf(false)
	var isPracticeMode by mutableStateOf(false)
	val playMode: PlayMode
		get() = when {
			!scbAutoPlay.checked -> PlayMode.None
			!isPracticeMode -> PlayMode.AutoPlay
			isAutoPlayPlaying -> PlayMode.GuidePlay
			else -> PlayMode.StepPractice
		}
	var optionViewVisible by mutableStateOf(true)
	var isOptionWindowVisible by mutableStateOf(false)
	var startReady by mutableStateOf(false)

	// Unipack loading state
	var unipackLoading by mutableStateOf(true)
	var unipackLoadError by mutableStateOf<String?>(null)
	var loadingPhase by mutableStateOf("")
	var loadingPhaseIndex by mutableIntStateOf(0)
	var loadingPhaseTotal by mutableIntStateOf(1)

	// Sound loading state
	var soundLoadingActive by mutableStateOf(false)
	var soundLoadingProgress by mutableIntStateOf(0)
	var soundLoadingMax by mutableIntStateOf(0)

	// Core
	lateinit var channelManager: ChannelManager
	val isChannelManagerInitialized get() = ::channelManager.isInitialized

	// Runners
	var ledRunner: LedRunner? = null
	var autoPlayRunner: AutoPlayRunner? = null
	var soundRunner: SoundRunner? = null

	// Recording
	private var recPrevEventMs: Long = 0
	private val logBuilder = StringBuilder()

	// TraceLog
	lateinit var traceLogTable: Array<Array<Array<ArrayList<Int>>>>
	lateinit var traceLogNextNum: IntArray

	/** Load unipack from path with progress reporting. */
	fun loadUnipack(path: String): UniPack {
		loadingPhase = "info"
		loadingPhaseIndex = 0
		loadingPhaseTotal = 4 // info, keySound, keyLed, autoPlay
		val pack = UniPackFolder(File(path)).load()
		loadingPhaseIndex = 1
		pack.loadDetailWithProgress { phase, index, _ ->
			loadingPhase = phase
			loadingPhaseIndex = index + 1 // +1 because info is phase 0
		}
		unipack = pack
		return pack
	}

	/** Initialize core state after unipack is loaded. */
	fun initState() {
		viewModelScope.launch(Dispatchers.IO) {
			unipackRepo.recordOpen(unipack.id)
		}
		chain.range = 0 until unipack.chain
		channelManager = ChannelManager(unipack.buttonX, unipack.buttonY)
		log("[04] Start ledTask (isKeyLed = ${unipack.keyLedExist})")
	}

	fun setupCheckBoxVisibility() {
		if (unipack.squareButton) {
			if (!unipack.keyLedExist) {
				scbLed.visible = false
				scbLed.locked = true
			}
			if (!unipack.autoPlayExist) {
				scbAutoPlay.visible = false
				scbAutoPlay.locked = true
			}
		} else {
			scbFeedbackLight.visible = false
			scbLed.visible = false
			scbAutoPlay.visible = false
			scbTraceLog.visible = false
			scbRecord.visible = false
			scbFeedbackLight.locked = true
			scbLed.locked = true
			scbAutoPlay.locked = true
			scbTraceLog.locked = true
			scbRecord.locked = true
		}
	}

	fun setupCheckBoxListeners() {
		scbFeedbackLight.onCheckedChange = {
			padInit()
			refreshWatermark()
		}
		scbLed.onCheckedChange = { bool ->
			if (unipack.keyLedExist) {
				if (bool) {
					ledRunner?.launch()
				} else {
					ledRunner?.stop()
					ledInit()
				}
			}
			refreshWatermark()
		}
		scbAutoPlay.onCheckedChange = { bool ->
			if (!bool && playMode != PlayMode.None) {
				switchPlayMode(PlayMode.None)
			}
			refreshWatermark()
		}
		scbTraceLog.onLongClick = {
			traceLogInit()
			uiCallback?.showToast(string.traceLogClear)
			refreshWatermark()
		}
		scbRecord.onCheckedChange = {
			if (scbRecord.isChecked()) {
				recPrevEventMs = SystemClock.elapsedRealtime()
				logBuilder.clear()
				logBuilder.append("c ").append(chain.value + 1)
			} else {
				uiCallback?.copyToClipboard(logBuilder.toString())
				uiCallback?.showToast(string.copied)
				logBuilder.clear()
			}
			refreshWatermark()
		}
		scbHideUI.onCheckedChange = { bool ->
			optionViewVisible = !bool
			refreshWatermark()
		}
		scbWatermark.onCheckedChange = {
			refreshWatermark()
		}
		scbProLightMode.onCheckedChange = { bool ->
			proLightMode(bool)
			uiCallback?.onRequestRelayout()
			refreshWatermark()
		}
	}

	fun initRunner() {
		if (unipack.keyLedExist) {
			ledRunner = LedRunner(
				unipack = unipack,
				chain = chain,
				listener = object : LedRunner.Listener {
					override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) {
						channelManager.add(x, y, Channel.LED, color, velocity)
						uiCallback?.setLedPad(x, y)
					}

					override fun onPadLedTurnOff(x: Int, y: Int) {
						channelManager.remove(x, y, Channel.LED)
						uiCallback?.setLedPad(x, y)
					}

					override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) {
						channelManager.add(-1, c, Channel.LED, color, velocity)
						uiCallback?.setLedChain(c)
					}

					override fun onChainLedTurnOff(c: Int) {
						channelManager.remove(-1, c, Channel.LED)
						uiCallback?.setLedChain(c)
					}

					})
		}

		if (unipack.autoPlayExist) {
			autoPlayRunner = AutoPlayRunner(
				unipack = unipack,
				chain = chain,
				listener = object : AutoPlayRunner.Listener {
					override fun onStart() {
						viewModelScope.launch {
							if (unipack.squareButton) autoPlayControlVisible = true
							autoPlayProgressMax = unipack.autoPlayTable?.elements?.size ?: 0
							autoPlayProgress = 0
						}
					}

					override fun onPadTouchOn(x: Int, y: Int) {
						viewModelScope.launch { padTouch(x, y, true) }
					}

					override fun onPadTouchOff(x: Int, y: Int) {
						viewModelScope.launch { padTouch(x, y, false) }
					}

					override fun onChainChange(c: Int) {
						viewModelScope.launch { chain.value = c }
					}

					override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {
						viewModelScope.launch { autoPlayGuidePad(x, y, true, targetWallTimeMs) }
					}

					override fun onGuidePadOff(x: Int, y: Int) {
						viewModelScope.launch { autoPlayGuidePad(x, y, false) }
					}

					override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {
						viewModelScope.launch { uiCallback?.sendGuideLedToLaunchpad(x, y, velocity) }
					}

					override fun onGuideChainOn(c: Int) {
						viewModelScope.launch {
							channelManager.add(-1, CHAIN_INDEX_OFFSET + c, Channel.GUIDE, -1, LED_ORANGE)
							uiCallback?.setLedChain(CHAIN_INDEX_OFFSET + c)
						}
					}

					override fun onRemoveGuide() {
						viewModelScope.launch { autoPlayRemoveGuide() }
					}

					override fun chainButsRefresh() {
						viewModelScope.launch { chainBtnsRefresh() }
					}

					override fun onProgressUpdate(progress: Int) {
						viewModelScope.launch { autoPlayProgress = progress }
					}

					override fun onEnd() {
						viewModelScope.launch {
							isAutoPlayPlaying = false
							autoPlayRunner?.practiceGuide = false
							autoPlayRunner?.stepMode = false
							isPracticeMode = false
							scbAutoPlay.setCheckedSilently(false)
							autoPlayControlVisible = false
							if (unipack.ledAnimationTable != null) {
								scbLed.setChecked(true)
								scbFeedbackLight.setChecked(false)
							} else {
								scbFeedbackLight.setChecked(true)
							}
							refreshWatermark()
						}
					}
				})
		}

		soundRunner = SoundRunner(
			unipack = unipack,
			chain = chain,
			scope = viewModelScope,
			loadingListener = object : SoundRunner.LoadingListener {
				override fun onStart(soundCount: Int) {
					viewModelScope.launch {
						loadingPhase = "audio"
						soundLoadingMax = soundCount
						soundLoadingProgress = 0
						soundLoadingActive = true
						unipackLoading = false
					}
				}

				override fun onProgressTick() {
					viewModelScope.launch {
						soundLoadingProgress++
					}
				}

				override fun onEnd() {
					viewModelScope.launch {
						soundLoadingActive = false
					}
				}

				override fun onException(throwable: Throwable) {
					viewModelScope.launch {
						soundLoadingActive = false
						uiCallback?.showToast(string.outOfCPU)
						uiCallback?.finishActivity()
					}
				}
			})

		chain.addObserver { curr: Int, _: Int ->
			try {
				chainBtnsRefresh()

				// Reset multi-mapping indices
				for (i in 0 until unipack.buttonX)
					for (j in 0 until unipack.buttonY) {
						unipack.soundPush(curr, i, j, 0)
						unipack.ledPush(curr, i, j, 0)
					}

				// Record chain change
				if (scbRecord.isChecked()) {
					val currTime = SystemClock.elapsedRealtime()
					addLog("d ${currTime - recPrevEventMs}")
					addLog("chain ${curr + 1}")
					recPrevEventMs = currTime
				}

				uiCallback?.showTraceLog()
			} catch (e: ArrayIndexOutOfBoundsException) {
				Log.err("Chain observer ArrayIndexOutOfBounds", e)
			}
		}
	}

	fun initSetting() {
		log("[06] Set CheckBox Checked")
		if (unipack.keyLedExist) {
			scbFeedbackLight.setChecked(false)
			scbLed.setChecked(true)
		} else
			scbFeedbackLight.setChecked(true)
	}

	// pad, chain

	fun padTouch(x: Int, y: Int, upDown: Boolean) {
		try {
			if (upDown) {
				if (autoPlayRunner?.stepMode == true) {
					autoPlayRunner?.stepPadPressed(x, y)
				}
				soundRunner?.soundOn(x, y)
				if (scbRecord.isChecked()) {
					val currTime = SystemClock.elapsedRealtime()
					addLog("d ${currTime - recPrevEventMs}")
					addLog("t ${x + 1} ${y + 1}")
					recPrevEventMs = currTime
				}
				if (scbTraceLog.isChecked())
					traceLogLog(x, y)
				if (scbFeedbackLight.isChecked()) {
					channelManager.add(x, y, Channel.PRESSED, -1, LED_RED)
					uiCallback?.setLedPad(x, y)
				}
				ledRunner?.eventOn(x, y)
			} else {
				soundRunner?.soundOff(x, y)
				channelManager.remove(x, y, Channel.PRESSED)
				uiCallback?.setLedPad(x, y)
				ledRunner?.eventOff(x, y)
			}
		} catch (e: ArrayIndexOutOfBoundsException) {
			Log.err("padTouch ArrayIndexOutOfBounds", e)
		} catch (e: NullPointerException) {
			Log.err("padTouch NullPointerException", e)
		}
	}

	fun padInit() {
		log("padInit")
		for (i in 0 until unipack.buttonX) for (j in 0 until unipack.buttonY) padTouch(i, j, false)
	}

	fun chainBtnsRefresh() {
		log("chainBtnsRefresh")
		try {
			for (c in 0 until MAX_CHAIN_BUTTONS) {
				val y = CHAIN_INDEX_OFFSET + c
				if (c == chain.value)
					channelManager.add(-1, y, Channel.CHAIN, -1, LED_RED)
				else
					channelManager.remove(-1, y, Channel.CHAIN)
				uiCallback?.setLedChain(y)
			}
		} catch (e: IndexOutOfBoundsException) {
			Log.err("chainBtnsRefresh failed", e)
		}
	}

	fun refreshWatermark() {
		log("refreshWatermark")
		val topBar = IntArray(TOP_BAR_COUNT)
		val showUi: Boolean
		val showUiUnipad: Boolean
		val showChain: Boolean
		if (!isOptionWindowVisible) {
			if (scbWatermark.isChecked()) {
				showUi = false
				showUiUnipad = true
				showChain = true
			} else {
				showUi = false
				showUiUnipad = false
				showChain = false
			}
		} else {
			if (!scbHideUI.isChecked()) {
				showUi = true
				showUiUnipad = false
				showChain = false
			} else {
				showUi = false
				showUiUnipad = false
				showChain = false
			}
		}
		channelManager.setCirIgnore(Channel.UI, !showUi)
		channelManager.setCirIgnore(Channel.UI_UNIPAD, !showUiUnipad)
		channelManager.setCirIgnore(Channel.CHAIN, !showChain)
		if (!isOptionWindowVisible) {
			topBar[0] = 0
			topBar[1] = 0
			topBar[2] = 0
			topBar[3] = 0
			topBar[4] = LED_GREEN
			topBar[5] = LED_BLUE
			topBar[6] = LED_GREEN
			topBar[7] = LED_BLUE
			for (i in 0 until TOP_BAR_COUNT) {
				if (topBar[i] != 0)
					channelManager.add(-1, i, Channel.UI_UNIPAD, -1, topBar[i])
				else channelManager.remove(-1, i, Channel.UI_UNIPAD)
				uiCallback?.setLedChain(i)
			}
		} else {
			topBar[0] =
				if (scbFeedbackLight.locked) 0 else if (scbFeedbackLight.isChecked()) LED_RED else LED_RED_DIM
			topBar[1] = if (scbLed.locked) 0 else if (scbLed.isChecked()) LED_CYAN else LED_LIGHT_BLUE
			topBar[2] = if (scbAutoPlay.locked) 0 else if (playMode != PlayMode.None) LED_ORANGE else LED_YELLOW
			topBar[3] = 0
			topBar[4] = if (scbHideUI.locked) 0 else if (scbHideUI.isChecked()) LED_RED else LED_RED_DIM
			topBar[5] = if (scbWatermark.locked) 0 else if (scbWatermark.isChecked()) LED_GREEN else LED_WARM
			topBar[6] =
				if (scbProLightMode.locked) 0 else if (scbProLightMode.isChecked()) LED_BLUE else LED_LAVENDER
			topBar[7] = LED_RED_BRIGHT
			for (i in 0 until TOP_BAR_COUNT) {
				if (topBar[i] != 0) channelManager.add(
					-1, i, Channel.UI, -1, topBar[i]
				) else channelManager.remove(-1, i, Channel.UI)
				uiCallback?.setLedChain(i)
			}
		}
		chainBtnsRefresh()
	}

	fun proLightMode(bool: Boolean) {
		if (bool) {
			for (i in 0 until CIRCLE_ARRAY_SIZE) {
				uiCallback?.setChainViewVisibility(i, View.VISIBLE)
			}
		} else {
			val chainRange = 0 until (if (unipack.chain > 1) unipack.chain else 0)
			for (i in 0 until CIRCLE_ARRAY_SIZE) {
				val c = i - CHAIN_INDEX_OFFSET
				uiCallback?.setChainViewVisibility(
					i,
					if (c in chainRange) View.VISIBLE else View.INVISIBLE
				)
			}
		}
		channelManager.setCirIgnore(Channel.LED, !bool)
		chainBtnsRefresh()
	}

	fun toggleOptionWindow(bool: Boolean = !isOptionWindowVisible) {
		isOptionWindowVisible = bool
		refreshWatermark()
	}

	// LED

	fun ledInit() {
		log("ledInit")
		if (unipack.keyLedExist) {
			val runner = ledRunner ?: return
			try {
				for (i in 0 until unipack.buttonX) {
					for (j in 0 until unipack.buttonY) {
						if (runner.isEventExist(i, j))
							runner.eventOff(i, j)
						channelManager.remove(i, j, Channel.LED)
						uiCallback?.setLedPad(i, j)
					}
				}
				for (i in 0 until FUNCTION_KEY_COUNT) {
					if (runner.isEventExist(-1, i))
						runner.eventOff(-1, i)
					channelManager.remove(-1, i, Channel.LED)
					uiCallback?.setLedChain(i)
				}
			} catch (e: IndexOutOfBoundsException) {
				Log.err("ledInit failed", e)
			}
		}
	}

	// autoPlay

	fun switchPlayMode(mode: PlayMode) {
		log("switchPlayMode: $mode")
		val runner = autoPlayRunner ?: return
		val currentMode = playMode

		if (mode == currentMode) {
			switchPlayMode(PlayMode.None)
			return
		}

		if (mode == PlayMode.None) {
			runner.practiceGuide = false
			runner.stepMode = false
			runner.resetStepState()
			runner.playmode = false
			autoPlayRemoveGuide()
			if (runner.active) runner.stop()
			padInit()
			ledInit()
			isPracticeMode = false
			isAutoPlayPlaying = false
			scbAutoPlay.setCheckedSilently(false)
			autoPlayControlVisible = false
			if (unipack.keyLedExist) {
				scbLed.setChecked(true)
				scbFeedbackLight.setChecked(false)
			} else {
				scbFeedbackLight.setChecked(true)
			}
			refreshWatermark()
			return
		}

		if (currentMode == PlayMode.None) {
			// None → Active: runner 시작 필요
			applyModeFlags(runner, mode)
			scbAutoPlay.setCheckedSilently(true)
			autoPlayControlVisible = unipack.squareButton
			runner.launch()
		} else {
			// Active → Active: 플래그만 변경
			applyModeFlags(runner, mode)
		}
		refreshWatermark()
	}

	private fun applyModeFlags(runner: AutoPlayRunner, mode: PlayMode) {
		when (mode) {
			PlayMode.AutoPlay -> {
				runner.practiceGuide = false
				runner.stepMode = false
				runner.resetStepState()
				autoPlayRemoveGuide()
				runner.playmode = true
				isPracticeMode = false
				isAutoPlayPlaying = true
				runner.beforeStartPlaying = true
			}
			PlayMode.GuidePlay -> {
				runner.practiceGuide = true
				runner.stepMode = false
				runner.resetStepState()
				autoPlayRemoveGuide()
				runner.playmode = true
				isPracticeMode = true
				isAutoPlayPlaying = true
				runner.beforeStartPlaying = true
			}
			PlayMode.StepPractice -> {
				runner.practiceGuide = true
				runner.playmode = false
				isPracticeMode = true
				isAutoPlayPlaying = false
				runner.stepMode = true
			}
			PlayMode.None -> {}
		}
	}

	fun cyclePlayMode() {
		val next = when (playMode) {
			PlayMode.None -> PlayMode.AutoPlay
			PlayMode.AutoPlay -> PlayMode.GuidePlay
			PlayMode.GuidePlay -> PlayMode.StepPractice
			PlayMode.StepPractice -> PlayMode.None
		}
		switchPlayMode(next)
	}

	fun autoPlayResume() {
		log("autoPlayResume")
		val runner = autoPlayRunner ?: return
		runner.stepMode = false
		runner.resetStepState()
		autoPlayRemoveGuide()
		padInit()
		ledInit()
		runner.playmode = true
		isAutoPlayPlaying = true
		if (unipack.keyLedExist) {
			scbLed.setChecked(true)
			scbFeedbackLight.setChecked(false)
		} else {
			scbFeedbackLight.setChecked(true)
		}
		runner.beforeStartPlaying = true
	}

	fun autoPlayPause() {
		log("autoPlayPause")
		val runner = autoPlayRunner ?: return
		runner.playmode = false
		padInit()
		ledInit()
		isAutoPlayPlaying = false
		if (playMode == PlayMode.StepPractice) {
			runner.stepMode = true
		}
	}

	fun autoPlayPrev() {
		log("autoPlayPrev")
		val runner = autoPlayRunner ?: return
		padInit()
		ledInit()
		autoPlayRemoveGuide()
		runner.progressOffset(-40)
	}

	fun autoPlayNext() {
		log("autoPlayNext")
		val runner = autoPlayRunner ?: return
		padInit()
		ledInit()
		autoPlayRemoveGuide()
		runner.progressOffset(40)
	}

	private fun autoPlayGuidePad(x: Int, y: Int, onOff: Boolean, targetWallTimeMs: Long = 0) {
		if (onOff) {
			channelManager.add(x, y, Channel.GUIDE, -1, LED_ORANGE)
			uiCallback?.setLedPad(x, y)
			uiCallback?.startGuideAnimation(x, y, targetWallTimeMs)
		} else {
			channelManager.remove(x, y, Channel.GUIDE)
			uiCallback?.setLedPad(x, y)
			uiCallback?.stopGuideAnimation(x, y)
		}
	}

	fun autoPlayRemoveGuide() {
		log("autoPlayRemoveGuide")
		try {
			for (i in 0 until unipack.buttonX) for (j in 0 until unipack.buttonY) {
				channelManager.remove(i, j, Channel.GUIDE)
				uiCallback?.setLedPad(i, j)
				uiCallback?.stopGuideAnimation(i, j)
			}
			for (i in 0 until CIRCLE_ARRAY_SIZE) {
				channelManager.remove(-1, i, Channel.GUIDE)
				uiCallback?.setLedChain(i)
			}
			chainBtnsRefresh()
		} catch (e: IndexOutOfBoundsException) {
			Log.err("autoPlayRemoveGuide failed", e)
		}
	}

	// TraceLog

	fun traceLogInit() {
		log("traceLogInit")
		traceLogTable = Array(unipack.chain) {
			Array(unipack.buttonX) {
				Array(unipack.buttonY) {
					ArrayList()
				}
			}
		}
		traceLogNextNum = IntArray(unipack.chain)
		for (i in 0 until unipack.chain) {
			for (j in 0 until unipack.buttonX) for (k in 0 until unipack.buttonY) traceLogTable[i][j][k].clear()
			traceLogNextNum[i] = 1
		}
		uiCallback?.clearTraceLogViews()
	}

	private fun traceLogLog(x: Int, y: Int) {
		traceLogTable[chain.value][x][y].add(traceLogNextNum[chain.value]++)
		uiCallback?.updateTraceLogView(x, y)
	}

	private fun addLog(msg: String) {
		logBuilder.append('\n').append(msg)
	}

	override fun onCleared() {
		super.onCleared()
		autoPlayRunner?.stop()
		ledRunner?.stop()
		soundRunner?.destroy()
		chain.clearObserver()
	}

	class Factory(
		private val unipackRepo: UnipackRepository,
	) : ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return PlayActivityViewModel(unipackRepo) as T
		}
	}
}
