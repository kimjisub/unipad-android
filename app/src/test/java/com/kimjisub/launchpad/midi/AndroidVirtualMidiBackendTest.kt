package com.kimjisub.launchpad.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@Suppress("DEPRECATION")
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidVirtualMidiBackendTest {
	@Test fun cancellationClosesLateAndroidDeviceWithoutOpeningAnyPort() = runTest {
		val context = mockk<Context>()
		val manager = mockk<MidiManager>(relaxed = true)
		val info = mockk<MidiDeviceInfo>()
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns manager
		every { info.id } returns 7
		every { info.type } returns MidiDeviceInfo.TYPE_VIRTUAL
		manager.stubDeviceList() returns arrayOf(info)
		val callback = slot<MidiManager.OnDeviceOpenedListener>()
		every { manager.openDevice(info, capture(callback), any()) } returns Unit
		val backend = AndroidVirtualMidiBackend(context)
		val job = launch { backend.open(7) }
		runCurrent()
		job.cancel()
		runCurrent()
		val late = mockk<MidiDevice>(relaxed = true)
		callback.captured.onDeviceOpened(late)
		runCurrent()
		verify(exactly = 1) { late.close() }
		verify(exactly = 0) { late.openInputPort(any()) }
		verify(exactly = 0) { late.openOutputPort(any()) }
	}

	@Test fun usbAndBluetoothIdsCannotBeOpenedThroughTheVirtualBackend() = runTest {
		val context = mockk<Context>()
		val manager = mockk<MidiManager>(relaxed = true)
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns manager
		val usb = mockk<MidiDeviceInfo>()
		every { usb.id } returns 7
		every { usb.type } returns MidiDeviceInfo.TYPE_USB
		manager.stubDeviceList() returns arrayOf(usb)
		assertNull(AndroidVirtualMidiBackend(context).open(7))
		verify(exactly = 0) { manager.openDevice(any(), any(), any()) }
	}
}
