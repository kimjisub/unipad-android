package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.adapter.MidiDeviceAdapter
import com.kimjisub.launchpad.adapter.MidiDeviceItem
import com.kimjisub.launchpad.databinding.ActivityMidiSelectBinding
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.*
import com.kimjisub.launchpad.tool.AutorunTimer
import com.yarolegovich.discretescrollview.transform.ScaleTransformer


class MidiSelectActivity : BaseActivity() {
	private lateinit var b: ActivityMidiSelectBinding
	private val midiDeviceList = lazy {
		arrayListOf(
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_lp_s, null)!!,
				"Launchpad S",
				LaunchpadS::class
			),
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_lp_mk2, null)!!,
				"Launchpad MK2",
				LaunchpadMK2::class
			),
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_lp_pro, null)!!,
				"Launchpad PRO",
				LaunchpadPRO::class
			),
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_lp_x, null)!!,
				"Launchpad X",
				LaunchpadX::class
			),
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_lp_mk3, null)!!,
				"Launchpad MK3",
				LaunchpadMK3::class
			),
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_midifighter, null)!!,
				"Midi Fighter",
				MidiFighter::class
			),
			MidiDeviceItem(
				ResourcesCompat.getDrawable(resources, R.drawable.midi_master_keyboard, null)!!,
				"Master Keyboard",
				MasterKeyboard::class
			)
		)
	}

	private val modeViewList: Array<LinearLayout> by lazy {
		arrayOf(
			b.speedFirst,
			b.avoidAfterimage
		)
	}

	private fun userInteract() {
		autorunTimer.cancel()
	}

	private val autorunTimer: AutorunTimer = AutorunTimer(object : AutorunTimer.OnListener {
		override fun onEverySec(leftTime: Long, elapsedTime: Long) {
			runOnUiThread {
				b.timer.text = (leftTime / 1000 - 1).toString()
			}
		}

		override fun onTimeOut() {
			runOnUiThread {
				finish()
			}
		}

		override fun onCanceled() {
			runOnUiThread {
				b.timer.text = ""
			}
		}

	}, 60000)

	@SuppressLint("ClickableViewAccessibility")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityMidiSelectBinding.inflate(layoutInflater)
		setContentView(b.root)
		autorunTimer.start()

		b.root.setOnTouchListener { _, _ ->
			userInteract()
			true
		}

		val adapter = MidiDeviceAdapter(midiDeviceList.value)
		b.picker.setSlideOnFling(true)
		b.picker.adapter = adapter
		b.picker.addOnItemChangedListener { _, adapterPosition ->
			val item = midiDeviceList.value[adapterPosition]
			MidiConnection.driver = item.driver.java.newInstance() as DriverRef
		}
		b.picker.setItemTransitionTimeMillis(50)
		b.picker.setItemTransformer(
			ScaleTransformer.Builder()
				.setMinScale(0.7f)
				.build()
		)


		val service = (getSystemService(Context.USB_SERVICE) as UsbManager)


		MidiConnection.listener = object : MidiConnection.Listener {
			override fun onConnectedListener() {
				b.err.visibility = View.GONE
			}

			override fun onChangeDriver(driverRef: DriverRef) {
				for ((i, item) in midiDeviceList.value.withIndex()) {
					if (item.driver == driverRef::class)
						b.picker.scrollToPosition(i)
				}
			}

			override fun onChangeMode(mode: Int) {
				showMode(mode)
			}

			override fun onUiLog(log: String) {
				b.log.append(log + "\n")
			}
		}
		MidiConnection.mode = p.launchpadConnectMethod
		MidiConnection.initConnection(intent, service)


	}

	// Select Mode

	fun selectModeXml(v: View) {
		MidiConnection.mode = Integer.parseInt(v.tag as String)
	}

	fun showMode(mode: Int) {
		for (i in modeViewList.indices) {
			if (mode == i)
				changeViewColor(modeViewList[i], color.gray1, color.background1)
			else
				changeViewColor(modeViewList[i], color.background1, color.gray1)
		}
		p.launchpadConnectMethod = mode
	}

	// Functions

	private fun changeViewColor(layout: ViewGroup, backgroundColorRes: Int, textColorRes: Int) {
		layout.setBackgroundColor(colors.get(backgroundColorRes))
		val count = layout.childCount
		for (j in 0 until count) {
			val textView = layout.getChildAt(j) as TextView
			textView.setTextColor(colors.get(textColorRes))
		}
	}

	// ActivityCycle

	@SuppressLint("StaticFieldLeak")
	override fun onDestroy() {
		super.onDestroy()
	}
}