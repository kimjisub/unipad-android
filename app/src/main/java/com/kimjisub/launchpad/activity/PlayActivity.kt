package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.provider.Settings.System
import android.view.*
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.Transformation
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog.Builder
import com.kimjisub.design.manage.SyncCheckBox
import com.kimjisub.design.manage.SyncCheckBox.OnCheckedChange
import com.kimjisub.design.manage.SyncCheckBox.OnLongClick
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.databinding.ActivityPlayBinding
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.manager.Functions.putClipboard
import com.kimjisub.launchpad.manager.ThemeResources
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.tool.Log.log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.ChainObserver
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import splitties.activities.start
import splitties.toast.toast
import java.io.File
import kotlin.math.roundToInt

class PlayActivity : BaseActivity() {
	private lateinit var b: ActivityPlayBinding

	private var unipack: UniPack? = null
	private var unipackLoaded = false
	private var uiLoaded = false
	private var enable = true
	private var chain: ChainObserver = ChainObserver()

	private var isPro = false
		set(value) {
			field = value
			b.CB2Purchase.visibility = if (field) View.GONE else View.VISIBLE
			SCB_hideUI.isLocked = !field
			SCB_watermark.isLocked = !field
			SCB_proLightMode.isLocked = !field
		}

	// UI /////////////////////////////////////////////////////////////////////////////////////////

	private var theme: ThemeResources? = null

	private val CB1s: Array<CheckBox> by lazy {
		arrayOf(
			b.CB1FeedbackLight,
			b.CB1Led,
			b.CB1AutoPlay,
			b.CB1TraceLog,
			b.CB1Record
		)
	}
	private val CB2s: Array<CheckBox> by lazy {
		arrayOf(
			b.CB2FeedbackLight,
			b.CB2Led,
			b.CB2AutoPlay,
			b.CB2TraceLog,
			b.CB2Record,
			b.CB2HideUI,
			b.CB2Watermark,
			b.CB2ProLightMode,
			b.CB2Purchase
		)
	}
	private val SCB_feedbackLight: SyncCheckBox = SyncCheckBox()
	private val SCB_led: SyncCheckBox = SyncCheckBox()
	private val SCB_autoPlay: SyncCheckBox = SyncCheckBox()
	private val SCB_traceLog: SyncCheckBox = SyncCheckBox()
	private val SCB_record: SyncCheckBox = SyncCheckBox()
	private val SCB_hideUI: SyncCheckBox = SyncCheckBox()
	private val SCB_watermark: SyncCheckBox = SyncCheckBox()
	private val SCB_proLightMode: SyncCheckBox = SyncCheckBox()


	//private lateinit var bm: DeprecatedBillingManager

	// =============================================================================================

	private var U_pads: Array<Array<PadView?>>? = null
	private var U_circle: Array<ChainView?>? = null

	// Runner, Manager /////////////////////////////////////////////////////////////////////////////////////////

	private var ledRunner: LedRunner? = null
	private var autoPlayRunner: AutoPlayRunner? = null
	private var soundRunner: SoundRunner? = null

	private val audioManager: AudioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
	private var channelManager: ChannelManager? = null

	// ============================================================================================= Manager
	private var traceLog_table: Array<Array<Array<ArrayList<Int>>>>? = null
	private var traceLog_nextNum: IntArray? = null
	private var rec_prevEventMS: Long = 0
	private var rec_log: String? = ""
	private var bool_toggleOption_window = false

	private fun initVar() {
		SCB_feedbackLight.addCheckBox(b.CB1FeedbackLight, b.CB2FeedbackLight)
		SCB_led.addCheckBox(b.CB1Led, b.CB2Led)
		SCB_autoPlay.addCheckBox(b.CB1AutoPlay, b.CB2AutoPlay)
		SCB_traceLog.addCheckBox(b.CB1TraceLog, b.CB2TraceLog)
		SCB_record.addCheckBox(b.CB1Record, b.CB2Record)
		SCB_hideUI.addCheckBox(b.CB2HideUI)
		SCB_watermark.addCheckBox(b.CB2Watermark)
		SCB_proLightMode.addCheckBox(b.CB2ProLightMode)

		SCB_watermark.forceSetChecked(true)

		/*bm = DeprecatedBillingManager(this, object : BillingProcessor.IBillingHandler {
			override fun onProductPurchased(productId: String, details: TransactionDetails?) {}
			override fun onPurchaseHistoryRestored() {}
			override fun onBillingError(errorCode: Int, error: Throwable?) {}
			override fun onBillingInitialized() {
				val proMode = bm.isPro

				b.CB2Purchase.visibility = if (proMode) View.GONE else View.VISIBLE
				SCB_hideUI.isLocked = !proMode
				SCB_watermark.isLocked = !proMode
				SCB_proLightMode.isLocked = !proMode

			}
		})
		bm.initialize()*/

	}

	@SuppressLint("StaticFieldLeak")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityPlayBinding.inflate(layoutInflater)
		setContentView(b.root)
		bm.load()

		initVar()

		val path: String? = intent.getStringExtra("path")


		try {
			unipack =
				UniPackFolder(File(path)).load().loadDetail()
			if (unipack!!.errorDetail != null) {
				Builder(this@PlayActivity)
					.setTitle(
						if (unipack!!.criticalError) getString(string.error) else getString(
							string.warning
						)
					)
					.setMessage(unipack!!.errorDetail)
					.setPositiveButton(
						if (unipack!!.criticalError) getString(string.quit) else getString(string.accept),
						if (unipack!!.criticalError) OnClickListener { _: DialogInterface?, _: Int -> finish() } else null
					)
					.setCancelable(false)
					.show()
			}

			if (!unipack!!.criticalError)
				start()
		} catch (e: Exception) {
			e.printStackTrace()
			when (e) {
				is OutOfMemoryError -> toast(string.outOfMemory)
				else -> toast("${getString(string.exceptionOccurred)}\n${e.message}")
			}
			finish()
		}
	}

	override fun onProStatusUpdated(isPro: Boolean) {
		this.isPro = isPro
	}

	private fun start() {
		chain.range = 0 until unipack!!.chain
		U_pads = Array(unipack!!.buttonX) { Array<PadView?>(unipack!!.buttonY) { null } }
		U_circle = Array(32) { null }
		channelManager = ChannelManager(unipack!!.buttonX, unipack!!.buttonY)
		log("[04] Start ledTask (isKeyLed = " + unipack!!.keyLedExist.toString() + ")")

		initTheme()
		b.themeResources = theme

		if (theme != null) {
			b.root.viewTreeObserver.addOnGlobalLayoutListener(object :
				ViewTreeObserver.OnGlobalLayoutListener {
				override fun onGlobalLayout() {
					b.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
					initLayout()
					initRunner()
					initSetting()
				}
			})
		}
	}


	private fun initTheme() {
		val packageName = p.selectedTheme

		theme = try {
			ThemeResources(this@PlayActivity, packageName, true)
		} catch (e: OutOfMemoryError) {
			e.printStackTrace()
			toast("${getString(string.skinMemoryErr)}\n$packageName")
			requestRestart(this)
			null
		} catch (e: Exception) {
			e.printStackTrace()
			toast("${getString(string.skinErr)}\n$packageName")
			p.selectedTheme = getPackageName()
			ThemeResources(this@PlayActivity)
		}


		/*if (num >= 2) {//하다하다 안되면
			try {
				theme = ThemeResources(this@PlayActivity, true)
			} catch (e: Exception) {
			}
			return true
		}
		return try {
			theme = ThemeResources(this@PlayActivity, packageName, true)
			true
		} catch (e: OutOfMemoryError) {
			e.printStackTrace()
			requestRestart(this)
			toast(getString(string.skinMemoryErr) + "\n" + packageName)
			false
		} catch (e: Exception) {
			e.printStackTrace()
			toast(getString(string.skinErr) + "\n" + packageName)
			preference.SelectedTheme = getPackageName()
			initTheme(num + 1)
		}*/
	}


	@SuppressLint("ClickableViewAccessibility")
	private fun initLayout() {
		try {
			log("[05] Set Button Layout (squareButton = " + unipack!!.squareButton + ")")
			if (unipack!!.squareButton) {
				if (!unipack!!.keyLedExist) {
					SCB_led.setVisibility(View.GONE)
					SCB_led.isLocked = true
				}
				if (!unipack!!.autoPlayExist) {
					SCB_autoPlay.setVisibility(View.GONE)
					SCB_autoPlay.isLocked = true
				}
			} else {
				SCB_feedbackLight.setVisibility(View.GONE)
				SCB_led.setVisibility(View.GONE)
				SCB_autoPlay.setVisibility(View.GONE)
				SCB_traceLog.setVisibility(View.GONE)
				SCB_record.setVisibility(View.GONE)
				SCB_feedbackLight.isLocked = true
				SCB_led.isLocked = true
				SCB_autoPlay.isLocked = true
				SCB_traceLog.isLocked = true
				SCB_record.isLocked = true
			}

			// Calc Button size
			val buttonSizeX: Int
			val buttonSizeY: Int
			val buttonSizeMin: Int

			val screenWidth = b.root.width
			val screenHeight = b.root.height
			val paddingWidth = b.paddingScale.width
			val paddingheight = b.paddingScale.height

			if (unipack!!.squareButton) {
				val xSize = paddingWidth / unipack!!.buttonX
				val ySize = paddingheight / unipack!!.buttonY

				buttonSizeY = xSize.coerceAtMost(ySize)
				buttonSizeX = buttonSizeY
			} else {
				buttonSizeX = screenWidth / unipack!!.buttonY
				buttonSizeY = screenHeight / unipack!!.buttonX
			}
			buttonSizeMin = buttonSizeX.coerceAtMost(buttonSizeY)


			// Setting
			b.CB2Purchase.setOnCheckedChangeListener { compoundButton: CompoundButton?, _: Boolean ->
				compoundButton!!.isChecked = false
				start<SettingLegacyActivity>()
			}
			SCB_feedbackLight.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					padInit()
					refreshWatermark()
				}
			}
			SCB_led.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					if (unipack!!.keyLedExist) {
						if (bool) {
							ledRunner?.launch()
						} else {
							ledRunner?.stop()
							ledInit()
						}
					}
					refreshWatermark()
				}
			}
			SCB_autoPlay.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					if (bool) {
						autoPlayRunner?.launch()
					} else {
						autoPlayRunner?.stop()
						padInit()
						ledInit()
						autoPlay_removeGuide()
						b.autoPlayControlView.visibility = View.GONE
					}
					refreshWatermark()
				}
			}
			SCB_traceLog.onLongClick = object : OnLongClick {
				override fun onLongClick() {
					traceLog_init()
					toast(string.traceLogClear)
					refreshWatermark()
				}
			}
			SCB_record.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					if (SCB_record.isChecked()) {
						rec_prevEventMS = java.lang.System.currentTimeMillis()
						rec_log = "c " + (chain.value + 1)
					} else {
						putClipboard(this@PlayActivity, rec_log!!)
						toast(string.copied)
						rec_log = ""
					}
					refreshWatermark()
				}
			}
			SCB_hideUI.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					b.optionView.visibility = if (bool) View.GONE else View.VISIBLE
					refreshWatermark()
				}
			}
			SCB_watermark.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					refreshWatermark()
				}
			}
			SCB_proLightMode.onCheckedChange = object : OnCheckedChange {
				override fun onCheckedChange(bool: Boolean) {
					proLightMode(bool)
					refreshWatermark()
				}
			}
			b.prev.setOnClickListener { autoPlay_prev() }
			b.play.setOnClickListener { if (autoPlayRunner!!.playmode) autoPlay_stop() else autoPlay_play() }
			b.next.setOnClickListener { autoPlay_next() }
			b.optionBlur.setOnClickListener {
				if (bool_toggleOption_window)
					toggleOption_window(false)
			}
			b.quit.setOnClickListener { finish() }
			b.pads.removeAllViews()
			b.chainsRight.removeAllViews()
			b.chainsLeft.removeAllViews()


			// Setup Pads
			for (x in 0 until unipack!!.buttonX) {
				val row = LinearLayout(this)
				row.layoutParams = LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT,
					1F
				)
				for (y in 0 until unipack!!.buttonY) {
					val view = PadView(this)
					view.layoutParams = LayoutParams(buttonSizeX, buttonSizeY)
					view.setBackgroundImageDrawable(theme!!.btn)
					view.setTraceLogTextColor(theme!!.trace_log!!)
					view.setOnTouchListener { _: View?, event: MotionEvent? ->
						when (event!!.action) {
							MotionEvent.ACTION_DOWN -> padTouch(x, y, true)
							MotionEvent.ACTION_UP -> padTouch(x, y, false)
						}
						false
					}
					U_pads!![x][y] = view
					row.addView(view)
				}
				b.pads.addView(row)
			}
			if (unipack!!.buttonX < 16 && unipack!!.buttonY < 16) {
				for (i in 0 until unipack!!.buttonX)
					for (j in 0 until unipack!!.buttonY)
						U_pads!![i][j]!!.setPhantomImageDrawable(theme!!.phantom)
				if (unipack!!.buttonX % 2 == 0 && unipack!!.buttonY % 2 == 0 && unipack!!.squareButton && theme!!.phantom_ != null) {
					val x = unipack!!.buttonX / 2 - 1
					val y = unipack!!.buttonY / 2 - 1
					U_pads!![x][y]!!.setPhantomImageDrawable(theme!!.phantom_)
					U_pads!![x + 1][y]!!.setPhantomImageDrawable(theme!!.phantom_)
					U_pads!![x + 1][y]!!.setPhantomRotation(270f)
					U_pads!![x][y + 1]!!.setPhantomImageDrawable(theme!!.phantom_)
					U_pads!![x][y + 1]!!.setPhantomRotation(90f)
					U_pads!![x + 1][y + 1]!!.setPhantomImageDrawable(theme!!.phantom_)
					U_pads!![x + 1][y + 1]!!.setPhantomRotation(180f)
				}
			}

			// Setup Chains
			for (i in 0..31) {
				val c = i - 8
				val view = ChainView(this)
				view.layoutParams = RelativeLayout.LayoutParams(buttonSizeMin, buttonSizeMin)
				if (theme!!.isChainLed) {
					view.setBackgroundImageDrawable(theme!!.btn)
					view.setPhantomImageDrawable(theme!!.chainled)
				} else {
					view.setPhantomImageDrawable(theme!!.chain)
					view.setLedVisibility(View.GONE)
				}

				U_circle!![i] = view
				if (c in 0..7) {
					U_circle!![i]!!.setOnClickListener { chain.value = c }
					b.chainsRight.addView(U_circle!![i])
				}
				if (c in 16..23) {
					U_circle!![i]!!.setOnClickListener { chain.value = c }
					b.chainsLeft.addView(U_circle!![i], 0)
				}
			}


			traceLog_init()

			proLightMode(SCB_proLightMode.isChecked())




			for (cb1 in CB1s) {
				cb1.setTextColor(theme!!.checkbox!!)
				if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) cb1.buttonTintList =
					ColorStateList.valueOf(
						theme!!.checkbox!!
					)
			}
			for (cb2 in CB2s) {
				cb2.setTextColor(theme!!.option_window_checkbox!!)
				if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) cb2.buttonTintList =
					ColorStateList.valueOf(
						theme!!.option_window_checkbox!!
					)
			}


			uiLoaded = true
			UILoaded()
			controller = midiController
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun UILoaded() {
		chainBtnsRefresh()
		updateVolumeUI()
	}

	private fun initRunner() {
		if (unipack!!.keyLedExist) {
			ledRunner = LedRunner(
				unipack = unipack!!,
				chain = chain,
				listener = object : LedRunner.Listener {

					override fun onStart() {
					}

					override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) {
						channelManager!!.add(x, y, Channel.LED, color, velocity)
						setLed(x, y)
					}

					override fun onPadLedTurnOff(x: Int, y: Int) {
						channelManager!!.remove(x, y, Channel.LED)
						setLed(x, y)
					}

					override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) {
						channelManager!!.add(-1, c, Channel.LED, color, velocity)
						setLed(c)
					}

					override fun onChainLedTurnOff(c: Int) {
						channelManager!!.remove(-1, c, Channel.LED)
						setLed(c)
					}

					override fun onEnd() {
					}

				})
		}

		if (unipack!!.autoPlayExist) {
			autoPlayRunner = AutoPlayRunner(
				unipack = unipack!!,
				chain = chain,
				listener = object : AutoPlayRunner.Listener {
					override fun onStart() {
						runOnUiThread {
							if (unipack!!.squareButton) b.autoPlayControlView.visibility =
								View.VISIBLE
							b.autoPlayProgressBar.max = unipack!!.autoPlayTable!!.elements.size
							b.autoPlayProgressBar.progress = 0
							autoPlay_play()
						}
					}

					override fun onPadTouchOn(x: Int, y: Int) {
						runOnUiThread {
							padTouch(x, y, true)
						}
					}

					override fun onPadTouchOff(x: Int, y: Int) {
						runOnUiThread {
							padTouch(x, y, false)
						}
					}

					override fun onChainChange(c: Int) {
						runOnUiThread {
							chain.value = c
						}
					}

					override fun onGuidePadOn(x: Int, y: Int) {
						runOnUiThread {
							autoPlay_guidePad(x, y, true)
						}
					}

					override fun onGuidePadOff(x: Int, y: Int) {
						runOnUiThread {
							autoPlay_guidePad(x, y, false)
						}
					}

					override fun onGuideChainOn(c: Int) {
						runOnUiThread {
							autoPlay_guideChain(c, true)
						}
					}

					override fun onGuideChainOff(c: Int) {
						runOnUiThread {
							autoPlay_guideChain(c, false)
						}
					}

					override fun onRemoveGuide() {
						runOnUiThread {
							autoPlay_removeGuide()
						}
					}

					override fun chainButsRefresh() {
						runOnUiThread {
							chainBtnsRefresh()
						}
					}

					override fun onProgressUpdate(progress: Int) {
						runOnUiThread {
							b.autoPlayProgressBar.progress = progress
						}
					}

					override fun onEnd() {
						runOnUiThread {
							SCB_autoPlay.setChecked(false)
							if (unipack!!.ledAnimationTable != null) {
								SCB_led.setChecked(true)
								SCB_feedbackLight.setChecked(false)
							} else {
								SCB_feedbackLight.setChecked(true)
							}
							b.autoPlayControlView.visibility = View.GONE
						}
					}
				})
		}

		soundRunner = SoundRunner(
			unipack = unipack!!,
			chain = chain,
			loadingListener = object : SoundRunner.LoadingListener {
				var progressDialog: ProgressDialog = ProgressDialog(this@PlayActivity)
				override fun onStart(soundCount: Int) {
					runOnUiThread {
						progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
						progressDialog.setTitle(getString(string.loading))
						progressDialog.setMessage(getString(string.wait_a_sec))
						progressDialog.setCancelable(false)
						progressDialog.max = soundCount
						progressDialog.show()
					}
				}

				override fun onProgressTick() {
					runOnUiThread {
						progressDialog.incrementProgressBy(1)
					}
				}

				override fun onEnd() {
					runOnUiThread {
						unipackLoaded = true
						try {
							if (progressDialog.isShowing)
								progressDialog.dismiss()
						} catch (e: Exception) {
							e.printStackTrace()
						}
					}
				}

				override fun onException(throwable: Throwable) {
					runOnUiThread {
						toast(string.outOfCPU)
						finish()
					}
				}
			})

		chain.addObserver { curr: Int, prev: Int ->

			//Log.log("chainChange (" + num + ")");
			try {
				chainBtnsRefresh()

				// 다중매핑 초기화
				for (i in 0 until unipack!!.buttonX)
					for (j in 0 until unipack!!.buttonY) {
						unipack!!.Sound_push(curr, i, j, 0)
						unipack!!.led_push(curr, i, j, 0)
					}


				// 녹음 chain 추가
				if (SCB_record.isChecked()) {
					val currTime = java.lang.System.currentTimeMillis()
					rec_addLog("d " + (currTime - rec_prevEventMS))
					rec_addLog("chain " + (curr + 1))
					rec_prevEventMS = currTime
				}

				// 순서기록 표시
				traceLog_show()
			} catch (e: ArrayIndexOutOfBoundsException) {
				e.printStackTrace()
			}
		}
	}

	private fun initSetting() {
		log("[06] Set CheckBox Checked")
		if (unipack!!.keyLedExist) {
			SCB_feedbackLight.setChecked(false)
			SCB_led.setChecked(true)
		} else
			SCB_feedbackLight.setChecked(true)
	}

	// pad, chain /////////////////////////////////////////////////////////////////////////////////////////

	private fun padTouch(x: Int, y: Int, upDown: Boolean) {
		//Log.log("padTouch (" + buttonX + ", " + buttonY + ", " + upDown + ")");

		try {
			if (upDown) {
				soundRunner?.soundOn(x, y)
				if (SCB_record.isChecked()) {
					val currTime = java.lang.System.currentTimeMillis()
					rec_addLog("d " + (currTime - rec_prevEventMS))
					rec_addLog("t " + (x + 1).toString() + " " + (y + 1))
					rec_prevEventMS = currTime
				}
				if (SCB_traceLog.isChecked())
					traceLog_log(x, y)
				if (SCB_feedbackLight.isChecked()) {
					channelManager!!.add(x, y, Channel.PRESSED, -1, 3)
					setLed(x, y)
				}
				ledRunner?.eventOn(x, y)
				autoPlay_checkGuide(x, y)

			} else {
				soundRunner?.soundOff(x, y)
				channelManager!!.remove(x, y, Channel.PRESSED)
				setLed(x, y)
				ledRunner?.eventOff(x, y)

			}
		} catch (e: ArrayIndexOutOfBoundsException) {
			e.printStackTrace()
		} catch (e: NullPointerException) {
			e.printStackTrace()
		}
	}

	private fun padInit() {
		log("padInit")
		for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) padTouch(
			i,
			j,
			false
		)
	}

	private fun chainBtnsRefresh() {
		log("chainBtnsRefresh")
		try {
			for (c in 0..23) {
				val y = 8 + c

				if (c == chain.value)
					channelManager!!.add(-1, y, Channel.CHAIN, -1, 3)
				else
					channelManager!!.remove(-1, y, Channel.CHAIN)

				setLed(y)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	//  /////////////////////////////////////////////////////////////////////////////////////////

	private fun refreshWatermark() {
		log("refreshWatermark")
		val topBar = IntArray(8)
		val UI: Boolean
		val UI_UNIPAD: Boolean
		val CHAIN: Boolean
		if (!bool_toggleOption_window) {
			if (SCB_watermark.isChecked()) {
				UI = false
				UI_UNIPAD = true
				CHAIN = true
			} else {
				UI = false
				UI_UNIPAD = false
				CHAIN = false
			}
		} else {
			if (!SCB_hideUI.isChecked()) {
				UI = true
				UI_UNIPAD = false
				CHAIN = false
			} else {
				UI = false
				UI_UNIPAD = false
				CHAIN = false
			}
		}
		channelManager!!.setCirIgnore(Channel.UI, !UI)
		channelManager!!.setCirIgnore(Channel.UI_UNIPAD, !UI_UNIPAD)
		channelManager!!.setCirIgnore(Channel.CHAIN, !CHAIN)
		if (!bool_toggleOption_window) {
			topBar[0] = 0
			topBar[1] = 0
			topBar[2] = 0
			topBar[3] = 0
			topBar[4] = 61
			topBar[5] = 40
			topBar[6] = 61
			topBar[7] = 40
			for (i in 0..7) {
				if (topBar[i] != 0)
					channelManager!!.add(-1, i, Channel.UI_UNIPAD, -1, topBar[i])
				else channelManager!!.remove(-1, i, Channel.UI_UNIPAD)
				setLed(i)
			}
		} else {
			topBar[0] =
				if (SCB_feedbackLight.isLocked) 0 else if (SCB_feedbackLight.isChecked()) 3 else 1
			topBar[1] = if (SCB_led.isLocked) 0 else if (SCB_led.isChecked()) 52 else 55
			topBar[2] = if (SCB_autoPlay.isLocked) 0 else if (SCB_autoPlay.isChecked()) 17 else 19
			topBar[3] = 0
			topBar[4] = if (SCB_hideUI.isLocked) 0 else if (SCB_hideUI.isChecked()) 3 else 1
			topBar[5] = if (SCB_watermark.isLocked) 0 else if (SCB_watermark.isChecked()) 61 else 11
			topBar[6] =
				if (SCB_proLightMode.isLocked) 0 else if (SCB_proLightMode.isChecked()) 40 else 43
			topBar[7] = 5
			for (i in 0..7) {
				if (topBar[i] != 0) channelManager!!.add(
					-1,
					i,
					Channel.UI,
					-1,
					topBar[i]
				) else channelManager!!.remove(-1, i, Channel.UI)
				setLed(i)
			}
		}
		chainBtnsRefresh()
	}

	private fun proLightMode(bool: Boolean) {
		if (bool) {
			for (chain in U_circle!!) {
				chain!!.visibility = View.VISIBLE
			}
		} else {
			val chainRange = 0 until (if (unipack!!.chain > 1) unipack!!.chain else 0)

			for (i in 0..31) {
				val c = i - 8

				val circleView = U_circle!![i]
				if (c in chainRange)
					circleView!!.visibility = View.VISIBLE
				else
					circleView!!.visibility = View.INVISIBLE
			}
		}
		channelManager!!.setCirIgnore(Channel.LED, !bool)
		chainBtnsRefresh()
	}

	private fun toggleOption_window(bool: Boolean = !bool_toggleOption_window) {
		bool_toggleOption_window = bool
		refreshWatermark()
		if (bool) {
			val a: Animation = object : Animation() {
				override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
					b.optionBlur.alpha = interpolatedTime
					b.optionWindow.alpha = interpolatedTime
				}
			}
			a.duration = 200
			a.setAnimationListener(object : AnimationListener {
				override fun onAnimationStart(animation: Animation?) {
					b.optionBlur.visibility = View.VISIBLE
					b.optionWindow.visibility = View.VISIBLE
				}

				override fun onAnimationEnd(animation: Animation?) {}
				override fun onAnimationRepeat(animation: Animation?) {}
			})
			b.optionBlur.startAnimation(a)
		} else {
			val a: Animation = object : Animation() {
				override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
					b.optionBlur.alpha = 1 - interpolatedTime
					b.optionWindow.alpha = 1 - interpolatedTime
				}
			}
			a.duration = 500
			a.setAnimationListener(object : AnimationListener {
				override fun onAnimationStart(animation: Animation?) {}
				override fun onAnimationEnd(animation: Animation?) {
					b.optionBlur.visibility = View.INVISIBLE
					b.optionWindow.visibility = View.INVISIBLE
				}

				override fun onAnimationRepeat(animation: Animation?) {}
			})
			b.optionBlur.startAnimation(a)
		}
	}

	// volume /////////////////////////////////////////////////////////////////////////////////////////

	private fun setVolume(level: Int, maxLevel: Int) {
		val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
		val percent = level.toFloat() / maxLevel
		val volume = (maxVolume * percent).toInt()
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
	}

	private fun updateVolumeUI() {
		val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
		val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
		val percent = volume / maxVolume
		var level = (percent * 7).roundToInt() + 1
		if (level == 1) level = 0
		val range = 7 downTo 8 - level
		for (c in 0..7) {
			val y = 8 + c
			if (c in range)
				channelManager!!.add(-1, y, Channel.UI, -1, 40)
			else
				channelManager!!.remove(-1, y, Channel.UI)
			setLed(y)
		}
	}

	// Led /////////////////////////////////////////////////////////////////////////////////////////

	private fun setLed(x: Int, y: Int) {
		if (enable) {
			setLedLaunchpad(x, y)
			runOnUiThread { setLedUI(x, y) }
		}
	}

	private fun setLedUI(x: Int, y: Int) {
		val item = channelManager!!.get(x, y)
		if (item != null) {
			when (item.channel) {
				Channel.GUIDE -> U_pads!![x][y]!!.setLedBackgroundColor(item.color)
				Channel.PRESSED -> U_pads!![x][y]!!.setLedBackground(theme!!.btn_)
				Channel.LED -> U_pads!![x][y]!!.setLedBackgroundColor(item.color)
			}
		} else U_pads!![x][y]!!.setLedBackgroundColor(0)
	}

	private fun setLedLaunchpad(x: Int, y: Int) {
		val item = channelManager!!.get(x, y)
		if (item != null)
			driver.sendPadLed(x, y, item.code)
		else
			driver.sendPadLed(x, y, 0)
	}

	private fun setLed(c: Int) {
		if (enable) {
			setLedLaunchpad(c)
			runOnUiThread { setLedUI(c) }
		}
	}

	private fun setLedUI(y: Int) {
		val c = y - 8
		val item = channelManager!!.get(-1, y)

		if (c in 0..23)
			if (theme!!.isChainLed) {
				if (item != null) {
					when (item.channel) {
						Channel.GUIDE -> U_circle!![y]!!.setLedBackgroundColor(item.color)
						Channel.CHAIN -> U_circle!![y]!!.setLedBackgroundColor(item.color)
						Channel.LED -> U_circle!![y]!!.setLedBackgroundColor(item.color)
					}
				} else U_circle!![y]!!.setLedBackgroundColor(0)
			} else {
				if (item != null) {
					when (item.channel) {
						Channel.GUIDE -> U_circle!![y]!!.setBackgroundImageDrawable(theme!!.chain__)
						Channel.CHAIN -> U_circle!![y]!!.setBackgroundImageDrawable(theme!!.chain_)
						Channel.LED -> U_circle!![y]!!.setBackgroundImageDrawable(theme!!.chain)
					}
				} else U_circle!![y]!!.setBackgroundImageDrawable(theme!!.chain)
			}
	}

	private fun setLedLaunchpad(c: Int) {
		val Item = channelManager!!.get(-1, c)
		if (Item != null)
			driver.sendFunctionkeyLed(c, Item.code)
		else
			driver.sendFunctionkeyLed(c, 0)
	}

	private fun ledInit() {
		log("ledInit")
		if (unipack!!.keyLedExist) {
			try {
				for (i in 0 until unipack!!.buttonX) {
					for (j in 0 until unipack!!.buttonY) {
						if (ledRunner!!.isEventExist(i, j))
							ledRunner!!.eventOff(i, j)
						channelManager!!.remove(i, j, Channel.LED)
						setLed(i, j)
					}
				}
				for (i in 0..35) {
					if (ledRunner!!.isEventExist(-1, i))
						ledRunner!!.eventOff(-1, i)
					channelManager!!.remove(-1, i, Channel.LED)
					setLed(i)
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	// midiController /////////////////////////////////////////////////////////////////////////////////////////

	private var midiController: MidiController? =
		object : MidiController() {
			override fun onAttach() {
				updateLP()
			}

			override fun onDetach() {}
			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
				if (!bool_toggleOption_window) {
					padTouch(x, y, upDown)
				}
			}

			override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
				if (upDown) {
					if (!bool_toggleOption_window) {
						when (f) {
							0 -> SCB_feedbackLight.toggleChecked()
							1 -> SCB_led.toggleChecked()
							2 -> SCB_autoPlay.toggleChecked()
							3 -> toggleOption_window()
							4, 5, 6, 7 -> SCB_watermark.toggleChecked()
						}
					} else {
						if (f in 0..7) when (f) {
							0 -> SCB_feedbackLight.toggleChecked()
							1 -> SCB_led.toggleChecked()
							2 -> SCB_autoPlay.toggleChecked()
							3 -> toggleOption_window()
							4 -> SCB_hideUI.toggleChecked()
							5 -> SCB_watermark.toggleChecked()
							6 -> SCB_proLightMode.toggleChecked()
							7 -> finish()
						} else if (f in 8..15) {
							setVolume(8 - (f - 8) - 1, 7)
						}
					}
				}
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {
				if (!bool_toggleOption_window) {
					if (upDown && unipack!!.chain > c) chain.value = c
				}
			}

			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) {
				if (cmd == 7 && sig == 46 && note == 0 && velocity == -9) updateLP()
			}
		}

	private fun updateLP() {
		chain.refresh()
		refreshWatermark()
	}

	// autoPlay /////////////////////////////////////////////////////////////////////////////////////////

	private fun autoPlay_play() {
		log("autoPlay_play")
		padInit()
		ledInit()
		autoPlayRunner!!.playmode = true
		b.play.background = theme!!.xml_pause
		if (unipack!!.keyLedExist) {
			SCB_led.setChecked(true)
			SCB_feedbackLight.setChecked(false)
		} else {
			SCB_feedbackLight.setChecked(true)
		}
		autoPlayRunner!!.beforeStartPlaying = true
	}

	private fun autoPlay_stop() {
		log("autoPlay_stop")
		autoPlayRunner!!.playmode = false
		padInit()
		ledInit()
		b.play.background = theme!!.xml_play
		autoPlayRunner!!.achieve = -1
		SCB_feedbackLight.setChecked(false)
		SCB_led.setChecked(false)
	}

	private fun autoPlay_prev() {
		log("autoPlay_prev")
		padInit()
		ledInit()
		autoPlayRunner!!.progressOffset(-40)
		if (!autoPlayRunner!!.playmode) {
			autoPlayRunner!!.achieve = -1
			autoPlayRunner!!.guideCheck()
		}
	}

	private fun autoPlay_next() {
		log("autoPlay_next")
		padInit()
		ledInit()
		autoPlayRunner!!.progressOffset(40)
		if (!autoPlayRunner!!.playmode) {
			autoPlayRunner!!.achieve = -1
			autoPlayRunner!!.guideCheck()
		}
	}

	private fun autoPlay_guidePad(x: Int, y: Int, onOff: Boolean) {
		//Log.log("autoPlay_guidePad (" + buttonX + ", " + buttonY + ", " + onOff + ")");

		if (onOff) {
			channelManager!!.add(x, y, Channel.GUIDE, -1, 17)
			setLed(x, y)
		} else {
			channelManager!!.remove(x, y, Channel.GUIDE)
			setLed(x, y)
		}
	}

	private fun autoPlay_guideChain(c: Int, onOff: Boolean) {
		log("autoPlay_guideChain ($c, $onOff)")
		if (onOff) {
			channelManager!!.add(-1, 8 + c, Channel.GUIDE, -1, 17)
			setLed(8 + c)
		} else {
			channelManager!!.remove(-1, 8 + c, Channel.GUIDE)
			setLed(8 + c)
			chainBtnsRefresh()
		}
	}

	private fun autoPlay_removeGuide() {
		log("autoPlay_removeGuide")
		try {
			for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) {
				channelManager!!.remove(i, j, Channel.GUIDE)
				setLed(i, j)
			}
			for (i in 0..31) {
				channelManager!!.remove(-1, i, Channel.GUIDE)
				setLed(i)
			}
			chainBtnsRefresh()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun autoPlay_checkGuide(x: Int, y: Int) {
		//Log.log("autoPlay_checkGuide (" + buttonX + ", " + buttonY + ")");

		if (autoPlayRunner != null && autoPlayRunner!!.active && !autoPlayRunner!!.playmode) {
			val guideItems: ArrayList<AutoPlay.Element.On>? = autoPlayRunner!!.guideItems
			if (guideItems != null) {
				loop@ for (autoPlay in guideItems) {
					if (x == autoPlay.x && y == autoPlay.y && chain.value == autoPlay.currChain) {
						autoPlayRunner!!.achieve++
						break@loop
					}
				}
			}
		}
	}

	// TraceLog /////////////////////////////////////////////////////////////////////////////////////////

	private fun traceLog_show() {
		//Log.log("traceLog_show");

		for (i in 0 until unipack!!.buttonX) {
			for (j in 0 until unipack!!.buttonY) {
				U_pads!![i][j]!!.setTraceLogText("")
				for (k in traceLog_table!![chain.value][i][j].indices) U_pads!![i][j]!!.appendTraceLog(
					traceLog_table!![chain.value][i][j][k].toString() + " "
				)
			}
		}
	}

	private fun traceLog_log(x: Int, y: Int) {
		//Log.log("traceLog_log (" + buttonX + ", " + buttonY + ")");

		traceLog_table!![chain.value][x][y].add(traceLog_nextNum!![chain.value]++)
		U_pads!![x][y]!!.setTraceLogText("")
		for (i in traceLog_table!![chain.value][x][y].indices) U_pads!![x][y]!!.appendTraceLog(
			traceLog_table!![chain.value][x][y][i].toString() + " "
		)
	}

	private fun traceLog_init() {
		log("traceLog_init")
		traceLog_table = Array(unipack!!.chain) {
			Array(unipack!!.buttonX) {
				Array(unipack!!.buttonY) {
					ArrayList<Int>()
				}
			}
		}

		traceLog_nextNum = IntArray(unipack!!.chain)
		for (i in 0 until unipack!!.chain) {
			for (j in 0 until unipack!!.buttonX) for (k in 0 until unipack!!.buttonY) traceLog_table!![i][j][k].clear()
			traceLog_nextNum!![i] = 1
		}
		try {
			for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) U_pads!![i][j]!!.setTraceLogText(
				""
			)
		} catch (e: NullPointerException) {
			e.printStackTrace()
		}
	}

	private fun rec_addLog(msg: String?) {
		rec_log += "\n" + msg
	}

	// Activity /////////////////////////////////////////////////////////////////////////////////////////


	override fun onBackPressed() {
		toggleOption_window()
	}

	override fun onResume() {
		super.onResume()
		//initVar();

		contentResolver.registerContentObserver(
			System.CONTENT_URI,
			true,
			object : ContentObserver(Handler()) {
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
			})
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		if (uiLoaded) controller = midiController
	}

	override fun onDestroy() {
		super.onDestroy()
		autoPlayRunner?.stop()
		ledRunner?.stop()
		soundRunner?.destroy()
		//ledInit();
		//padInit();

		enable = false
		removeController(midiController!!)
		if (unipackLoaded) {
			//showAds(adsPlayEnd)
		}
		bm.release()
	}
}