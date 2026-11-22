package com.kimjisub.launchpad.midi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

/** Explicit, non-exported destination for the user's USB permission result. */
class UsbMidiPermissionReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) return
		val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
		// Scan only permitted MIDI devices, as on a normal app launch.
		MidiConnection.initConnection(Intent(), manager, context)
	}
}
