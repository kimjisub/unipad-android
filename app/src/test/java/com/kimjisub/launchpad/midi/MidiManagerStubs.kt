package com.kimjisub.launchpad.midi

import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import io.mockk.MockKStubScope
import io.mockk.every

/**
 * Stubs the device list MidiConnection reads. A JVM unit test sees Build.VERSION.SDK_INT = 0, so
 * MidiConnection takes its API 24-32 path, where getDevices() is the only listing Android offers;
 * its replacement getDevicesForTransport() exists only from API 33.
 */
@Suppress("DEPRECATION")
internal fun MidiManager.stubDeviceList(): MockKStubScope<Array<MidiDeviceInfo>, Array<MidiDeviceInfo>> =
	every { devices }
