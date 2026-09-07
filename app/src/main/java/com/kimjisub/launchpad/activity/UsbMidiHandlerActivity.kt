package com.kimjisub.launchpad.activity

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kimjisub.launchpad.midi.MidiConnection

/**
 * UI 없이 USB_DEVICE_ATTACHED intent를 수신하여 MIDI 연결만 처리하고 즉시 종료.
 * 연결 후 MidiBannerController가 포그라운드 Activity에서 배너를 표시한다.
 */
class UsbMidiHandlerActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		handle(intent)
		finish()
	}

	// singleInstance + noHistory: a second attach delivered to a live instance arrives here
	// instead of onCreate and used to be dropped.
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handle(intent)
		finish()
	}

	private fun handle(intent: Intent) {
		// Devices without USB host support have no UsbManager; the manifest never required the feature.
		val usbManager = getSystemService(USB_SERVICE) as? UsbManager ?: return
		MidiConnection.initConnection(intent, usbManager, this)
	}
}
