package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.provider.Settings.System
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.Transformation
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog.Builder
import com.anjlab.android.iab.v3.TransactionDetails
import com.kimjisub.design.Chain
import com.kimjisub.design.Pad
import com.kimjisub.design.manage.SyncCheckBox
import com.kimjisub.design.manage.SyncCheckBox.OnCheckedChange
import com.kimjisub.design.manage.SyncCheckBox.OnLongClick
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.BillingManager.BillingEventListener
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.manager.Constant.VUNGLE
import com.kimjisub.launchpad.manager.Functions.putClipboard
import com.kimjisub.launchpad.manager.PreferenceManager.SelectedTheme
import com.kimjisub.launchpad.manager.ThemeResources
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.runner.SoundLoader
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.Sound
import com.kimjisub.manager.Log.log
import com.kimjisub.manager.Log.vungle
import com.vungle.warren.LoadAdCallback
import com.vungle.warren.PlayAdCallback
import com.vungle.warren.Vungle
import com.vungle.warren.error.VungleException
import kotlinx.android.synthetic.main.activity_play.*
import org.jetbrains.anko.toast
import java.io.File
import java.util.*

class PlayActivity : BaseActivity() {
	// UI /////////////////////////////////////////////////////////////////////////////////////////

	private val CB1s: Array<CheckBox> by lazy { arrayOf(CB1_feedbackLight, CB1_LED, CB1_autoPlay, CB1_traceLog, CB1_record) }
	private val CB2s: Array<CheckBox> by lazy {
		arrayOf(
			CB2_feedbackLight,
			CB2_LED,
			CB2_autoPlay,
			CB2_traceLog,
			CB2_record,
			CB2_hideUI,
			CB2_watermark,
			CB2_proLightMode
		)
	}
	private val SCB_feedbackLight: SyncCheckBox = SyncCheckBox()
	private val SCB_LED: SyncCheckBox = SyncCheckBox()
	private val SCB_autoPlay: SyncCheckBox = SyncCheckBox()
	private val SCB_traceLog: SyncCheckBox = SyncCheckBox()
	private val SCB_record: SyncCheckBox = SyncCheckBox()
	private val SCB_hideUI: SyncCheckBox = SyncCheckBox()
	private val SCB_watermark: SyncCheckBox = SyncCheckBox()
	private val SCB_proLightMode: SyncCheckBox = SyncCheckBox()


	private var billingManager: BillingManager? = null

	// =============================================================================================
	private var theme: ThemeResources? = null
	private var unipack: Unipack? = null
	private var unipackLoaded = false
	private var UILoaded = false
	private var enable = true
	private var U_pads: Array<Array<Pad?>?>? = null
	private var U_chains: Array<Chain?>? = null
	private var ledRunner: LedRunner? = null
	private var autoPlayRunner: AutoPlayRunner? = null
	private var soundPool: SoundPool? = null
	private var stopID: Array<Array<Array<Int>>>? = null
	private var chain = 0
	private var audioManager: AudioManager? = null
	private var channelManager: ChannelManager? = null
	// ============================================================================================= Manager
	private var traceLog_table: Array<Array<Array<ArrayList<Int>?>?>?>? = null
	private var traceLog_nextNum: IntArray? = null
	private var rec_prevEventMS: Long = 0
	private var rec_log: String? = ""
	private var bool_toggleOption_window = false

	// ============================================================================================= 특성 다른 LED 처리


	private fun initVar() {
		SCB_feedbackLight.addCheckBox(CB1_feedbackLight, CB2_feedbackLight)
		SCB_LED.addCheckBox(CB1_LED, CB2_LED)
		SCB_autoPlay.addCheckBox(CB1_autoPlay, CB2_autoPlay)
		SCB_traceLog.addCheckBox(CB1_traceLog, CB2_traceLog)
		SCB_record.addCheckBox(CB1_record, CB2_record)
		SCB_hideUI.addCheckBox(CB2_hideUI)
		SCB_watermark.addCheckBox(CB2_watermark)
		SCB_proLightMode.addCheckBox(CB2_proLightMode)

		SCB_watermark.forceSetChecked(true)

		/*if (!BillingManager.showAds())
			AV_adview.setVisibility(View.GONE);*/
		billingManager = BillingManager(
			this@PlayActivity,
			object : BillingEventListener {
				override fun onProductPurchased(
					productId: String,
					details: TransactionDetails?
				) {
				}

				override fun onPurchaseHistoryRestored() {}
				override fun onBillingError(errorCode: Int, error: Throwable?) {}
				override fun onBillingInitialized() {}
				override fun onRefresh() {
					setProMode(billingManager!!.unlockProTools)
				}
			})
	}

	@SuppressLint("StaticFieldLeak")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_play)
		initVar()

		val path: String? = intent.getStringExtra("path")
		val file = File(path)
		try {
			unipack = Unipack(file, true)
			if (unipack!!.errorDetail != null) {
				Builder(this@PlayActivity)
					.setTitle(if (unipack!!.criticalError) getString(string.error) else getString(string.warning))
					.setMessage(unipack!!.errorDetail)
					.setPositiveButton(
						if (unipack!!.criticalError) getString(string.quit) else getString(string.accept),
						if (unipack!!.criticalError) OnClickListener { _: DialogInterface?, _: Int -> finish() } else null
					)
					.setCancelable(false)
					.show()
			}
			log("[03] Init Vars")
			U_pads = Array(unipack!!.buttonX) { arrayOfNulls<Pad?>(unipack!!.buttonY) }
			U_chains = arrayOfNulls<Chain?>(32)
			channelManager = ChannelManager(unipack!!.buttonX, unipack!!.buttonY)
			log("[04] Start LEDTask (isKeyLED = " + unipack!!.keyLEDExist.toString() + ")")
			initRunner()
			initLayout()

			SoundLoader(unipack!!, listener = object : SoundLoader.Listener {
				var progressDialog: ProgressDialog = ProgressDialog(this@PlayActivity)
				override fun onStart(soundCount: Int, soundPool: SoundPool, stopID: Array<Array<Array<Int>>>) {
					this@PlayActivity.soundPool = soundPool
					this@PlayActivity.stopID = stopID
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
						try {
							initTheme()
						} catch (e: ArithmeticException) {
							e.printStackTrace()
						} catch (e: NullPointerException) {
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
		} catch (e: OutOfMemoryError) {
			e.printStackTrace()
			toast(string.outOfMemory)
			finish()
		} catch (e: Exception) {
			e.printStackTrace()
			toast(string.exceptionOccurred)
			finish()
		}
	}

	private fun initRunner() {
		if (unipack!!.keyLEDExist) {
			ledRunner = LedRunner(unipack!!, listener = object : LedRunner.Listener {
				override fun onStart() {
					TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
				}

				override fun onLedTurnOn(x: Int, y: Int, color: Int, velo: Int) {
					channelManager!!.add(x, y, Channel.LED, color, velo)
					setLed(x, y)
				}

				override fun onLedTurnOff(x: Int, y: Int) {
					channelManager!!.remove(x, y, Channel.LED)
					setLed(x, y)
				}

				override fun onEnd() {
					TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
				}

			})
		}

		if (unipack!!.autoPlayExist) {
			autoPlayRunner = AutoPlayRunner(unipack!!, listener = object : AutoPlayRunner.Listener {
				override fun onStart() {
					runOnUiThread {
						if (unipack!!.squareButton) autoPlayControlView.visibility = View.VISIBLE
						autoPlayProgressBar.max = unipack!!.autoPlayTable!!.elements.size
						autoPlayProgressBar.progress = 0
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
						chainChange(c)
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
						autoPlayProgressBar.progress = progress
					}
				}

				override fun onEnd() {
					runOnUiThread { }
					SCB_autoPlay.setChecked(false)
					if (unipack!!.ledAnimationTable != null) {
						SCB_LED.setChecked(true)
						SCB_feedbackLight.setChecked(false)
					} else {
						SCB_feedbackLight.setChecked(true)
					}
					autoPlayControlView.visibility = View.GONE
				}
			})
		}
	}

	private fun initLayout() {
		log("[05] Set Button Layout (squareButton = " + unipack!!.squareButton + ")")
		if (unipack!!.squareButton) {
			if (!unipack!!.keyLEDExist) {
				SCB_LED.setVisibility(View.GONE)
				SCB_LED.isLocked = true
			}
			if (!unipack!!.autoPlayExist) {
				SCB_autoPlay.setVisibility(View.GONE)
				SCB_autoPlay.isLocked = true
			}
		} else {
			RL_root.setPadding(0, 0, 0, 0)
			SCB_feedbackLight.setVisibility(View.GONE)
			SCB_LED.setVisibility(View.GONE)
			SCB_autoPlay.setVisibility(View.GONE)
			SCB_traceLog.setVisibility(View.GONE)
			SCB_record.setVisibility(View.GONE)
			SCB_feedbackLight.isLocked = true
			SCB_LED.isLocked = true
			SCB_autoPlay.isLocked = true
			SCB_traceLog.isLocked = true
			SCB_record.isLocked = true
		}
		log("[06] Set CheckBox Checked")
		if (unipack!!.keyLEDExist) {
			SCB_feedbackLight.setChecked(false)
			SCB_LED.setChecked(true)
		} else SCB_feedbackLight.setChecked(true)
	}

	private fun initTheme() {
		val packageName = SelectedTheme.load(this@PlayActivity)

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
			SelectedTheme.save(this@PlayActivity, getPackageName())
			ThemeResources(this@PlayActivity, true)
		}

		if (theme != null)
			showUI()

		/*if (num >= 2) {//하다하다 안되면
			try {
				theme = ThemeResources(this@PlayActivity, true)
			} catch (ignore: Exception) {
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
			SelectedTheme.save(this@PlayActivity, getPackageName())
			initTheme(num + 1)
		}*/
	}


	@SuppressLint("ClickableViewAccessibility")
	private fun showUI() {
		val buttonSizeX: Int
		val buttonSizeY: Int
		if (unipack!!.squareButton) {
			buttonSizeY = Math.min(
				Scale_PaddingHeight / unipack!!.buttonX,
				Scale_PaddingWidth / unipack!!.buttonY
			)
			buttonSizeX = buttonSizeY
		} else {
			buttonSizeX = Scale_Width / unipack!!.buttonY
			buttonSizeY = Scale_Height / unipack!!.buttonX
		}
		purchase.setOnCheckedChangeListener { compoundButton: CompoundButton?, b: Boolean ->
			compoundButton!!.isChecked = false
			startActivity(Intent(this@PlayActivity, SettingActivity::class.java))
		}
		SCB_feedbackLight.onCheckedChange = object : OnCheckedChange {
			override fun onCheckedChange(b: Boolean) {
				padInit()
				refreshWatermark()
			}
		}
		SCB_LED.onCheckedChange = object : OnCheckedChange {
			override fun onCheckedChange(b: Boolean) {
				if (unipack!!.keyLEDExist) {
					if (b) {
						ledRunner?.launch()
					} else {
						ledRunner?.stop()
						LEDInit()
					}
				}
				refreshWatermark()
			}
		}
		SCB_autoPlay.onCheckedChange = object : OnCheckedChange {
			override fun onCheckedChange(b: Boolean) {
				if (b) {
					autoPlayRunner?.launch()
				} else {
					autoPlayRunner?.stop()
					padInit()
					LEDInit()
					autoPlay_removeGuide()
					autoPlayControlView.visibility = View.GONE
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
			override fun onCheckedChange(b: Boolean) {
				if (SCB_record.isChecked()) {
					rec_prevEventMS = java.lang.System.currentTimeMillis()
					rec_log = "c " + (chain + 1)
				} else {
					putClipboard(this@PlayActivity, rec_log!!)
					toast(string.copied)
					rec_log = ""
				}
				refreshWatermark()
			}
		}
		SCB_hideUI.onCheckedChange = object : OnCheckedChange {
			override fun onCheckedChange(b: Boolean) {
				option_view.visibility = if (b) View.GONE else View.VISIBLE
				refreshWatermark()
			}
		}
		SCB_watermark.onCheckedChange = object : OnCheckedChange {
			override fun onCheckedChange(b: Boolean) {
				refreshWatermark()
			}
		}
		SCB_proLightMode.onCheckedChange = object : OnCheckedChange {
			override fun onCheckedChange(b: Boolean) {
				proLightMode(b)
				refreshWatermark()
			}
		}
		prev.setOnClickListener { autoPlay_prev() }
		play.setOnClickListener { if (autoPlayRunner!!.playmode) autoPlay_stop() else autoPlay_play() }
		next.setOnClickListener { autoPlay_after() }
		option_blur.setOnClickListener { v: View? ->
			if (bool_toggleOption_window) toggleOption_window(
				false
			)
		}
		quit.setOnClickListener { finish() }
		pads.removeAllViews()
		chainsRight.removeAllViews()
		chainsLeft.removeAllViews()
		for (i in 0 until unipack!!.buttonX) {
			val row = LinearLayout(this)
			row.layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1F)
			for (j in 0 until unipack!!.buttonY) {
				val pad = Pad(this)
				pad.layoutParams = LayoutParams(buttonSizeX, buttonSizeY)
				pad.setOnTouchListener { _: View?, event: MotionEvent? ->
					when (event!!.action) {
						MotionEvent.ACTION_DOWN -> padTouch(i, j, true)
						MotionEvent.ACTION_UP -> padTouch(i, j, false)
					}
					false
				}
				U_pads!![i]!![j] = pad
				row.addView(pad)
			}
			pads.addView(row)
		}
		for (i in 0..31) {
			U_chains!![i] = Chain(this)
			U_chains!![i]!!.layoutParams = RelativeLayout.LayoutParams(buttonSizeX, buttonSizeY)
			if (i in 0..7) {
				U_chains!![i]!!.setOnClickListener { v: View? -> chainChange(i) }
				chainsRight.addView(U_chains!![i])
			}
			if (i in 16..23) {
				U_chains!![i]!!.setOnClickListener { v: View? -> chainChange(i) }
				chainsLeft.addView(U_chains!![i], 0)
			}
		}
		traceLog_init()
		chainChange(chain)
		proLightMode(SCB_proLightMode.isChecked())
		skin_set()
		UILoaded = true
		UILoaded()
		controller = midiController
	}

	private fun skin_set() {
		log("[12] skin_set")
		background.setImageDrawable(theme!!.playbg)
		if (theme!!.custom_logo != null) custom_logo.setImageDrawable(theme!!.custom_logo) else custom_logo.setImageDrawable(null)
		for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) {
			U_pads!![i]!![j]!!.setBackgroundImageDrawable(theme!!.btn)
			U_pads!![i]!![j]!!.setTraceLogTextColor(theme!!.trace_log!!)
		}
		if (unipack!!.buttonX < 16 && unipack!!.buttonY < 16) {
			for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) U_pads!![i]!![j]!!.setPhantomImageDrawable(theme!!.phantom)
			if (unipack!!.buttonX % 2 == 0 && unipack!!.buttonY % 2 == 0 && unipack!!.squareButton && theme!!.phantom_ != null) {
				val x = unipack!!.buttonX / 2 - 1
				val y = unipack!!.buttonY / 2 - 1
				U_pads!![x]!![y]!!.setPhantomImageDrawable(theme!!.phantom_)
				U_pads!![x + 1]!![y]!!.setPhantomImageDrawable(theme!!.phantom_)
				U_pads!![x + 1]!![y]!!.setPhantomRotation(270f)
				U_pads!![x]!![y + 1]!!.setPhantomImageDrawable(theme!!.phantom_)
				U_pads!![x]!![y + 1]!!.setPhantomRotation(90f)
				U_pads!![x + 1]!![y + 1]!!.setPhantomImageDrawable(theme!!.phantom_)
				U_pads!![x + 1]!![y + 1]!!.setPhantomRotation(180f)
			}
		}
		for (i in 0..31) {
			if (theme!!.isChainLED) {
				U_chains!![i]!!.setBackgroundImageDrawable(theme!!.btn)
				U_chains!![i]!!.setPhantomImageDrawable(theme!!.chainled)
			} else {
				U_chains!![i]!!.setPhantomImageDrawable(theme!!.chain)
				U_chains!![i]!!.setLedVisibility(View.GONE)
			}
		}
		chainBtnsRefresh()
		prev.background = theme!!.xml_prev
		play.background = theme!!.xml_play
		next.background = theme!!.xml_next


		for (cb1 in CB1s) {
			cb1.setTextColor(theme!!.checkbox!!)
			if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) cb1.buttonTintList = ColorStateList.valueOf(
				theme!!.checkbox!!
			)
		}
		for (cb2 in CB2s) {
			cb2.setTextColor(theme!!.option_window_checkbox!!)
			if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) cb2.buttonTintList = ColorStateList.valueOf(
				theme!!.option_window_checkbox!!
			)
		}
		//RL_option_window.setBackgroundColor(theme.option_window);
		//IB_option_quit.setBackgroundColor(theme.option_window_btn);
		//IB_option_quit.setTextColor(theme.option_window_btn_text);
	}

	private fun UILoaded() {
		if (unipack!!.keyLEDExist)
			SCB_LED.setChecked(true)
	}

	// Led /////////////////////////////////////////////////////////////////////////////////////////

	private fun setLed(x: Int, y: Int) {
		if (enable) {
			setLedLaunchpad(x, y)
			runOnUiThread { setLedUI(x, y) }
		}
	}

	private fun setLedUI(x: Int, y: Int) {
		val Item = channelManager!!.get(x, y)
		if (x != -1) {
			if (Item != null) {
				when (Item.channel) {
					Channel.GUIDE -> U_pads!![x]!![y]!!.setLedBackgroundColor(Item.color)
					Channel.PRESSED -> U_pads!![x]!![y]!!.setLedBackground(theme!!.btn_)
					Channel.LED -> U_pads!![x]!![y]!!.setLedBackgroundColor(Item.color)
				}
			} else U_pads!![x]!![y]!!.setLedBackgroundColor(0)
		} else {
			val c = y - 8
			if (c in 0..31) if (theme!!.isChainLED) {
				if (Item != null) {
					when (Item.channel) {
						Channel.GUIDE -> U_chains!![c]!!.setLedBackgroundColor(Item.color)
						Channel.PRESSED -> U_chains!![c]!!.setLedBackgroundColor(Item.color)
						Channel.LED -> U_chains!![c]!!.setLedBackgroundColor(Item.color)
					}
				} else U_chains!![c]!!.setLedBackgroundColor(0)
			} else {
				if (Item != null) {
					when (Item.channel) {
						Channel.GUIDE -> U_chains!![c]!!.setBackgroundImageDrawable(theme!!.chain__)
						Channel.PRESSED -> U_chains!![c]!!.setBackgroundImageDrawable(theme!!.chain_)
						Channel.LED -> U_chains!![c]!!.setBackgroundImageDrawable(theme!!.chain)
					}
				} else U_chains!![c]!!.setBackgroundImageDrawable(theme!!.chain)
			}
		}
	}

	private fun setLedLaunchpad(x: Int, y: Int) {
		val Item = channelManager!!.get(x, y)
		if (x != -1) {
			if (Item != null) driver.sendPadLED(
				x,
				y,
				Item.code
			) else driver.sendPadLED(x, y, 0)
		} else {
			if (Item != null) driver.sendFunctionkeyLED(
				y,
				Item.code
			) else driver.sendFunctionkeyLED(y, 0)
		}
	}

	private fun LEDInit() {
		log("LEDInit")
		if (unipack!!.keyLEDExist) {
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
					setLed(-1, i)
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	// unishare /////////////////////////////////////////////////////////////////////////////////////////

	private fun autoPlay_play() {
		log("autoPlay_play")
		padInit()
		LEDInit()
		autoPlayRunner!!.playmode = true
		play.background = theme!!.xml_pause
		if (unipack!!.keyLEDExist) {
			SCB_LED.setChecked(true)
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
		LEDInit()
		play.background = theme!!.xml_play
		autoPlayRunner!!.achieve = -1
		SCB_feedbackLight.setChecked(false)
		SCB_LED.setChecked(false)
	}

	private fun autoPlay_prev() {
		log("autoPlay_prev")
		padInit()
		LEDInit()
		var progress = autoPlayRunner!!.progress - 40
		if (progress < 0) progress = 0
		autoPlayRunner!!.progress = progress
		if (!autoPlayRunner!!.playmode) {
			log("!playmode")
			autoPlayRunner!!.achieve = -1
			autoPlayRunner!!.check()
		}
		autoPlayProgressBar.progress = autoPlayRunner!!.progress
	}

	private fun autoPlay_after() {
		log("autoPlay_after")
		padInit()
		LEDInit()
		autoPlayRunner!!.progress += 40
		autoPlayRunner!!.achieve = -1
		if (!autoPlayRunner!!.playmode) {
			log("!playmode")
			autoPlayRunner!!.achieve = -1
			autoPlayRunner!!.check()
		}
		autoPlayProgressBar.progress = autoPlayRunner!!.progress
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
			setLed(-1, 8 + c)
		} else {
			channelManager!!.remove(-1, 8 + c, Channel.GUIDE)
			setLed(-1, 8 + c)
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
				setLed(-1, i)
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
					if (x == autoPlay.x && y == autoPlay.y && chain == autoPlay.currChain) {
						autoPlayRunner!!.achieve++
						break@loop
					}
				}
			}
		}
	}

	private fun padTouch(x: Int, y: Int, upDown: Boolean) {
		//Log.log("padTouch (" + buttonX + ", " + buttonY + ", " + upDown + ")");

		try {
			if (upDown) {
				soundPool!!.stop(stopID!![chain][x][y])
				val e: Sound? = unipack!!.Sound_get(chain, x, y)
				if (e != null) {
					stopID!![chain][x][y] = soundPool!!.play(e.id, 1.0f, 1.0f, 0, e.loop, 1.0f)
					unipack!!.Sound_push(chain, x, y)
					if (e.wormhole != -1)
						Handler().postDelayed({ chainChange(e.wormhole) }, 100)
				}
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
				if (unipack!!.Sound_get(chain, x, y)!!.loop == -1)
					soundPool!!.stop(stopID!![chain][x][y])
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

	// ============================================================================================= Launchpad Connection


	private fun padInit() {
		log("padInit")
		for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) padTouch(i, j, false)
	}

	private fun chainChange(num: Int) {
		//Log.log("chainChange (" + num + ")");

		ledRunner?.chain = num
		autoPlayRunner?.chain = num

		try {
			if (num >= 0 && num < unipack!!.chain) {
				chain = num
				chainBtnsRefresh()
			}

			// 다중매핑 초기화


			for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) {
				unipack!!.Sound_push(chain, i, j, 0)
				unipack!!.LED_push(chain, i, j, 0)
			}


			// 녹음 chain 추가


			if (SCB_record.isChecked()) {
				val currTime = java.lang.System.currentTimeMillis()
				rec_addLog("d " + (currTime - rec_prevEventMS))
				rec_addLog("chain " + (chain + 1))
				rec_prevEventMS = currTime
			}

			// 순서기록 표시


			traceLog_show()
		} catch (e: ArrayIndexOutOfBoundsException) {
			e.printStackTrace()
		}
	}

	// ============================================================================================= Trace Log


	private fun chainBtnsRefresh() {
		log("chainBtnsRefresh")
		try {
			// chain

			for (i in 0..7) {
				val x = -1
				val y = 8 + i

				if (i == chain)
					channelManager!!.add(x, y, Channel.CHAIN, -1, 3)
				else
					channelManager!!.remove(x, y, Channel.CHAIN)

				setLed(x, y)
			}

			// volume


			val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
			val currVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
			val currPercent = currVolume / maxVolume
			var currLevel = Math.round(currPercent * 7) + 1
			if (currLevel == 1) currLevel = 0
			for (i in 0..7) {
				if (8 - i <= currLevel) channelManager!!.add(
					-1,
					i + 8,
					Channel.UI,
					-1,
					40
				) else channelManager!!.remove(-1, i + 8, Channel.UI)
				setLed(-1, i + 8)
			}
		} catch (e: Exception) {
		}
	}

	private var midiController: MidiController? =
		object : MidiController() {
			override fun onAttach() {
				updateLP()
			}

			override fun onDetach() {}
			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int) {
				if (!bool_toggleOption_window) {
					padTouch(x, y, upDown)
				}
			}

			override fun onFunctionkeyTouch(f: Int, upDown: Boolean) {
				if (upDown) {
					if (!bool_toggleOption_window) {
						when (f) {
							0 -> SCB_feedbackLight.toggleChecked()
							1 -> SCB_LED.toggleChecked()
							2 -> SCB_autoPlay.toggleChecked()
							3 -> toggleOption_window()
							4, 5, 6, 7 -> SCB_watermark.toggleChecked()
						}
					} else {
						if (f in 0..7) when (f) {
							0 -> SCB_feedbackLight.toggleChecked()
							1 -> SCB_LED.toggleChecked()
							2 -> SCB_autoPlay.toggleChecked()
							3 -> toggleOption_window()
							4 -> SCB_hideUI.toggleChecked()
							5 -> SCB_watermark.toggleChecked()
							6 -> SCB_proLightMode.toggleChecked()
							7 -> finish()
						} else if (f in 8..15) {
							val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
							val currLevel = 8 - (f - 8) - 1
							val currPercent = currLevel.toFloat() / 7.toFloat()
							val currVolume = (maxVolume * currPercent).toInt()
							audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, currVolume, 0)
						}
					}
				}
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {
				if (!bool_toggleOption_window) {
					if (upDown && unipack!!.chain > c) chainChange(c)
				}
			}

			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velo: Int) {
				if (cmd == 7 && sig == 46 && note == 0 && velo == -9) updateLP()
			}
		}

	private fun updateLP() {
		chainChange(chain)
		refreshWatermark()
	}

	// TraceLog /////////////////////////////////////////////////////////////////////////////////////////


	private fun traceLog_show() {
		//Log.log("traceLog_show");

		for (i in 0 until unipack!!.buttonX) {
			for (j in 0 until unipack!!.buttonY) {
				U_pads!![i]!![j]!!.setTraceLogText("")
				for (k in traceLog_table!![chain]!![i]!![j]!!.indices) U_pads!![i]!![j]!!.appendTraceLog(
					traceLog_table!![chain]!![i]!![j]!![k].toString() + " "
				)
			}
		}
	}

	private fun traceLog_log(x: Int, y: Int) {
		//Log.log("traceLog_log (" + buttonX + ", " + buttonY + ")");

		traceLog_table!![chain]!![x]!![y]!!.add(traceLog_nextNum!![chain]++)
		U_pads!![x]!![y]!!.setTraceLogText("")
		for (i in traceLog_table!![chain]!![x]!![y]!!.indices) U_pads!![x]!![y]!!.appendTraceLog(traceLog_table!![chain]!![x]!![y]!![i].toString() + " ")
	}

	private fun traceLog_init() {
		log("traceLog_init")
		traceLog_table = Array(
			unipack!!.chain
		) { Array<Array<ArrayList<Int>?>?>(unipack!!.buttonX) { arrayOfNulls<ArrayList<Int>?>(unipack!!.buttonY) } }
		traceLog_nextNum = IntArray(unipack!!.chain)
		for (i in 0 until unipack!!.chain) {
			for (j in 0 until unipack!!.buttonX) for (k in 0 until unipack!!.buttonY) if (traceLog_table!![i]!![j]!![k] == null) traceLog_table!![i]!![j]!![k] =
				ArrayList() else traceLog_table!![i]!![j]!![k]!!.clear()
			traceLog_nextNum!![i] = 1
		}
		try {
			for (i in 0 until unipack!!.buttonX) for (j in 0 until unipack!!.buttonY) U_pads!![i]!![j]!!.setTraceLogText("")
		} catch (e: NullPointerException) {
			e.printStackTrace()
		}
	}

	private fun rec_addLog(msg: String?) {
		rec_log += "\n" + msg
	}

	private fun setProMode(bool: Boolean) {
		purchase.visibility = if (bool) View.GONE else View.VISIBLE
		proTools.alpha = if (bool) 1f else 0.3f
		SCB_hideUI.isLocked = !bool
		SCB_watermark.isLocked = !bool
		SCB_proLightMode.isLocked = !bool
	}


	// ============================================================================================= setProMode


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
				if (topBar[i] != 0) channelManager!!.add(
					-1,
					i,
					Channel.UI_UNIPAD,
					-1,
					topBar[i]
				) else channelManager!!.remove(-1, i, Channel.UI_UNIPAD)
				setLed(-1, i)
			}
		} else {
			topBar[0] = if (SCB_feedbackLight.isLocked) 0 else if (SCB_feedbackLight.isChecked()) 3 else 1
			topBar[1] = if (SCB_LED.isLocked) 0 else if (SCB_LED.isChecked()) 52 else 55
			topBar[2] = if (SCB_autoPlay.isLocked) 0 else if (SCB_autoPlay.isChecked()) 17 else 19
			topBar[3] = 0
			topBar[4] = if (SCB_hideUI.isLocked) 0 else if (SCB_hideUI!!.isChecked()) 3 else 1
			topBar[5] = if (SCB_watermark.isLocked) 0 else if (SCB_watermark!!.isChecked()) 61 else 11
			topBar[6] = if (SCB_proLightMode!!.isLocked) 0 else if (SCB_proLightMode!!.isChecked()) 40 else 43
			topBar[7] = 5
			for (i in 0..7) {
				if (topBar[i] != 0) channelManager!!.add(
					-1,
					i,
					Channel.UI,
					-1,
					topBar[i]
				) else channelManager!!.remove(-1, i, Channel.UI)
				setLed(-1, i)
			}
		}
		chainBtnsRefresh()
	}

	// ============================================================================================= Watermark


	private fun proLightMode(bool: Boolean) {
		if (bool) {
			for (chain in U_chains!!) {
				chain!!.visibility = View.VISIBLE
				//chain.setLedVisibility(View.VISIBLE);

			}
		} else {
			if (unipack!!.chain > 1) {
				for (i in 0..31) {
					val chain = U_chains!![i]
					if (i < unipack!!.chain) chain!!.visibility = View.VISIBLE

					//chain.setLedVisibility(View.INVISIBLE);
					else chain!!.visibility = View.INVISIBLE
				}
			} else {
				for (chain in U_chains!!) chain!!.visibility = View.INVISIBLE
			}
		}
		channelManager!!.setCirIgnore(Channel.LED, !bool)
		chainBtnsRefresh()
	}

	// ============================================================================================= Pro Mode


	// ============================================================================================= Option Window

	@JvmOverloads
	private fun toggleOption_window(bool: Boolean = !bool_toggleOption_window) {
		bool_toggleOption_window = bool
		refreshWatermark()
		if (bool) {
			val a: Animation = object : Animation() {
				override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
					option_blur.alpha = interpolatedTime
					option_window.alpha = interpolatedTime
				}
			}
			a.duration = 200
			a.setAnimationListener(object : AnimationListener {
				override fun onAnimationStart(animation: Animation?) {
					option_blur.visibility = View.VISIBLE
					option_window.visibility = View.VISIBLE
				}

				override fun onAnimationEnd(animation: Animation?) {}
				override fun onAnimationRepeat(animation: Animation?) {}
			})
			option_blur.startAnimation(a)
		} else {
			val a: Animation = object : Animation() {
				override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
					option_blur.alpha = 1 - interpolatedTime
					option_window.alpha = 1 - interpolatedTime
				}
			}
			a.duration = 500
			a.setAnimationListener(object : AnimationListener {
				override fun onAnimationStart(animation: Animation?) {}
				override fun onAnimationEnd(animation: Animation?) {
					option_blur.visibility = View.INVISIBLE
					option_window.visibility = View.INVISIBLE
				}

				override fun onAnimationRepeat(animation: Animation?) {}
			})
			option_blur.startAnimation(a)
		}
	}


	// Activity /////////////////////////////////////////////////////////////////////////////////////////


	override fun onBackPressed() {
		toggleOption_window()
	}

	override fun onResume() {
		super.onResume()
		//initVar();

		audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
		contentResolver.registerContentObserver(
			System.CONTENT_URI,
			true,
			object : ContentObserver(Handler()) {
				override fun deliverSelfNotifications(): Boolean {
					return super.deliverSelfNotifications()
				}

				override fun onChange(selfChange: Boolean) {
					log("changed volume 1")
					chainBtnsRefresh()
					super.onChange(selfChange)
				}

				override fun onChange(selfChange: Boolean, uri: Uri?) {
					log("changed volume 2")
					chainBtnsRefresh()
					super.onChange(selfChange, uri)
				}
			})
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		if (UILoaded) controller = midiController
		if (Scale_PaddingHeight == 0) {
			log("padding 크기값들이 잘못되었습니다.")
			requestRestart(this@PlayActivity)
		}
		if (billingManager!!.showAds) {
			/*AdRequest adRequest = new AdRequest.Builder().build();
			AV_adview.loadAd(adRequest);*/

			if (Vungle.isInitialized()) {
				Vungle.loadAd(VUNGLE.PLAY_END, object : LoadAdCallback {
					override fun onAdLoad(placementReferenceId: String?) {
						vungle("PLAY_END loadAd : placementReferenceId == $placementReferenceId")
					}

					override fun onError(placementReferenceId: String?, throwable: Throwable?) {
						vungle("PLAY_END loadAd : getLocalizedMessage() == " + throwable!!.localizedMessage)
						try {
							val ex = throwable as VungleException?
							if (ex!!.exceptionCode == VungleException.VUNGLE_NOT_INTIALIZED) initVungle()
						} catch (cex: ClassCastException) {
							vungle(cex.message!!)
						}
					}
				})
			} else vungle("PLAY_END loadAd : isInitialized() == false")
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		autoPlayRunner?.stop()
		ledRunner?.stop()
		if (soundPool != null) {
			for (i in 0 until unipack!!.chain) {
				for (j in 0 until unipack!!.buttonX) {
					for (k in 0 until unipack!!.buttonY) {
						val arrayList: ArrayList<*>? = unipack!!.soundTable!![i][j][k]
						if (arrayList != null) {
							for (l in arrayList.indices) {
								if (unipack!!.soundTable!![i][j][k]!![l] != null) {
									try {
										soundPool!!.unload(unipack!!.soundTable!![i][j][k]!![l].id)
									} catch (e: Exception) {
										e.printStackTrace()
									}
								}
							}
						}
					}
				}
			}
			soundPool!!.release()
			soundPool = null
		}
		//LEDInit();
		//padInit();

		enable = false
		removeController(midiController!!)
		if (unipackLoaded) {
			if (billingManager!!.showAds) {
				if (checkAdsCooltime()) {
					updateAdsCooltime()
					if (Math.random() * 10 > 3) showAdmob() else {
						if (Vungle.canPlayAd(VUNGLE.PLAY_END)) {
							Vungle.playAd(
								VUNGLE.PLAY_END,
								null,
								object : PlayAdCallback {
									override fun onAdStart(placementReferenceId: String?) {
										vungle("PLAY_END playAd : onAdStart()")
									}

									override fun onAdEnd(
										placementReferenceId: String?,
										completed: Boolean,
										isCTAClicked: Boolean
									) {
										vungle("PLAY_END onAdEnd : onAdEnd()")
									}

									override fun onError(placementReferenceId: String?, throwable: Throwable?) {
										vungle("PLAY_END onError : onError() == " + throwable!!.localizedMessage)
										try {
											val ex = throwable as VungleException?
											if (ex!!.exceptionCode == VungleException.VUNGLE_NOT_INTIALIZED) initVungle()
										} catch (cex: ClassCastException) {
											vungle(cex.message!!)
										}
									}
								})
						}
					}
				}
			}
		}
	}
}