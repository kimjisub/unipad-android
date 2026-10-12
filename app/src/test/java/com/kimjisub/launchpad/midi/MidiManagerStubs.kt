package com.kimjisub.launchpad.midi

import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import io.mockk.MockKStubScope
import io.mockk.every

/**
 * Stubs the device list MidiConnection reads. MidiConnection lists devices with the deprecated
 * getDevices() on every API level, so the tests stub that same call.
 */
@Suppress("DEPRECATION")
internal fun MidiManager.stubDeviceList(): MockKStubScope<Array<MidiDeviceInfo>, Array<MidiDeviceInfo>> =
	every { devices }
