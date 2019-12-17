package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.*
import kotlinx.android.synthetic.main.activity_midi_select.*

class MidiSelectActivity : BaseActivity() {
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
		setContentView(layout.activity_midi_select)


		val service = (getSystemService(Context.USB_SERVICE) as UsbManager)


		MidiConnection.listener = object : MidiConnection.Listener {
			override fun onConnectedListener() {
				RL_err.visibility = View.GONE
			}

			override fun onChangeDriver(driverRef: DriverRef) {
				showDriver(driverRef)
			}

			override fun onChangeMode(mode: Int) {
				showMode(mode)
			}

			override fun onUiLog(log: String) {
				TV_log.append(log + "\n")
			}
		}
		MidiConnection.mode = preference.launchpadConnectMethod
		MidiConnection.initConnection(intent, service)

		Handler().postDelayed({ finish() }, 5000)
	}

	// Select Driver /////////////////////////////////////////////////////////////////////////////////////////

	fun selectDriver(v: View) {
		val index = Integer.parseInt(v.tag as String)

		MidiConnection.driver = when (index) {
			0 -> LaunchpadS()
			1 -> LaunchpadMK2()
			2 -> LaunchpadPRO()
			3 -> MidiFighter()
			4 -> MasterKeyboard()
			5 -> LaunchpadX()
			else -> MasterKeyboard()
		}
	}

	fun showDriver(driverRef: DriverRef) {
		var index = 0
		when (driverRef.javaClass.simpleName) {
			"LaunchpadS" -> index = 0
			"LaunchpadMK2" -> index = 1
			"LaunchpadPRO" -> index = 2
			"MidiFighter" -> index = 3
			"MasterKeyboard" -> index = 4
			"LaunchpadX" -> index = 5
		}
		for (i in LL_Launchpad.indices) {
			if (index == i)
				changeViewColor(LL_Launchpad[i], color.ugray1, color.background1)
			else
				changeViewColor(LL_Launchpad[i], color.background1, color.ugray1)
		}
	}

	// Select Mode /////////////////////////////////////////////////////////////////////////////////////////

	fun selectModeXml(v: View) {
		MidiConnection.mode = Integer.parseInt(v.tag as String)
	}

	fun showMode(mode: Int) {
		for (i in LL_mode.indices) {
			if (mode == i)
				changeViewColor(LL_mode[i], color.ugray1, color.background1)
			else
				changeViewColor(LL_mode[i], color.background1, color.ugray1)
		}
		preference.launchpadConnectMethod = mode
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