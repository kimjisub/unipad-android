package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.manager.PreferenceManager.LaunchpadConnectMethod
import com.kimjisub.launchpad.midi.MidiConnection.Listener
import com.kimjisub.launchpad.midi.MidiConnection.initConnection
import com.kimjisub.launchpad.midi.MidiConnection.setListener
import com.kimjisub.launchpad.midi.MidiConnection.setMode
import com.kimjisub.launchpad.midi.driver.*
import kotlinx.android.synthetic.main.activity_usbmidi.*

class LaunchpadActivity : BaseActivity() {
	private val LL_Launchpad: Array<LinearLayout> by lazy {
		arrayOf(
			BTN_S,
			BTN_Mk2,
			BTN_Pro,
			BTN_Midifighter,
			BTN_Piano
		)
	}

	private val LL_mode: Array<LinearLayout> by lazy {
		arrayOf(
			BTN_speedFirst,
			BTN_avoidAfterimage
		)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_usbmidi)
		setListener(object : Listener {
			override fun onConnectedListener() {
				RL_err.visibility = View.GONE
			}

			override fun onChangeDriver(cls: Class<*>) {
				selectDriver(cls)
			}

			override fun onChangeMode(mode: Int) {
				selectMode(mode)
			}

			override fun onUiLog(log: String) {
				TV_log.append(log + "\n")
			}
		})
		setMode(LaunchpadConnectMethod.load(this@LaunchpadActivity))
		val intent: Intent? = intent
		initConnection(intent!!, (getSystemService(Context.USB_SERVICE) as UsbManager))
		Handler().postDelayed({ finish() }, 2000)
	}

	// Select Driver /////////////////////////////////////////////////////////////////////////////////////////


	fun selectDriver(v: View) {
		val index = Integer.parseInt(v.tag as String)
		selectDriver(
			arrayOf<Class<*>>(
				LaunchpadS::class.java,
				LaunchpadMK2::class.java,
				LaunchpadPRO::class.java,
				MidiFighter::class.java,
				MasterKeyboard::class.java
			)[index]
		)
	}

	fun selectDriver(cls: Class<*>) {
		var index = 0
		when (cls.simpleName) {
			"LaunchpadS" -> index = 0
			"LaunchpadMK2" -> index = 1
			"LaunchpadPRO" -> index = 2
			"MidiFighter" -> index = 3
			"MasterKeyboard" -> index = 4
		}
		for (i in LL_Launchpad.indices) {
			if (index == i)
				changeViewColor(LL_Launchpad[i], color.gray1, color.background1)
			else
				changeViewColor(LL_Launchpad[i], color.background1, color.gray1)

		}
	}


	// Select Mode /////////////////////////////////////////////////////////////////////////////////////////


	fun selectModeXml(v: View) {
		selectMode(Integer.parseInt(v.tag as String))
	}

	fun selectMode(mode: Int) {
		for (i in LL_mode.indices) {
			if (mode == i)
				changeViewColor(LL_mode[i], color.gray1, color.background1)
			else
				changeViewColor(LL_mode[i], color.background1, color.gray1)
		}
		LaunchpadConnectMethod.save(this@LaunchpadActivity, mode)
	}

	// Functions /////////////////////////////////////////////////////////////////////////////////////////

	private fun changeViewColor(layout: ViewGroup, backgroundColorRes: Int, textColorRes: Int) {
		layout.setBackgroundColor(colors.get(backgroundColorRes))
		val count = layout.childCount
		for (j in 0 until count) {
			val textView = layout.getChildAt(j) as TextView
			textView.setTextColor(colors.get(textColorRes))
		}
	}

	// ActivityCycle /////////////////////////////////////////////////////////////////////////////////////////

	@SuppressLint("StaticFieldLeak")
	override fun onDestroy() {
		super.onDestroy()
	}
}