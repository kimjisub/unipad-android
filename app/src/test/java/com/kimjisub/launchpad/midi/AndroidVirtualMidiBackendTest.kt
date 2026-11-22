package com.kimjisub.launchpad.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiDeviceStatus
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

	@Test fun twoSilentStatusChecksInARowMeanTheDeviceClosed() = runTest {
		val context = mockk<Context>()
		val manager = mockk<MidiManager>(relaxed = true)
		val info = mockk<MidiDeviceInfo>()
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns manager
		every { info.id } returns 7
		every { info.type } returns MidiDeviceInfo.TYPE_VIRTUAL
		manager.stubDeviceList() returns arrayOf(info)
		val opened = slot<MidiManager.OnDeviceOpenedListener>()
		every { manager.openDevice(info, capture(opened), any()) } answers { opened.captured.onDeviceOpened(device(info)) }
		// The server answers a registration with its port status only while it is running.
		var running = true
		every { manager.registerDeviceCallback(any(), any()) } answers {
			if (running) firstArg<MidiManager.DeviceCallback>().onDeviceStatusChanged(status(info, open = 1))
		}
		val device = AndroidVirtualMidiBackend(context).open(7)!!
		device.openPerformancePort(1) { _, _, _ -> }
		assertEquals(true, device.isAlive())
		running = false
		assertNull("One silent check is unknown", device.isAlive())
		running = true
		assertEquals("An answer resets the silent checks", true, device.isAlive())
		running = false
		assertNull(device.isAlive())
		assertEquals("A second silent check in a row is a closed device", false, device.isAlive())
		running = true
		every { manager.registerDeviceCallback(any(), any()) } answers {
			firstArg<MidiManager.DeviceCallback>().onDeviceStatusChanged(status(info, open = 0))
		}
		assertEquals("A reported closed port is a closed device", false, device.isAlive())
	}

	private fun device(info: MidiDeviceInfo): MidiDevice = mockk<MidiDevice>(relaxed = true).also {
		every { it.info } returns info
	}

	private fun status(info: MidiDeviceInfo, open: Int): MidiDeviceStatus = mockk<MidiDeviceStatus>().also {
		every { it.deviceInfo } returns info
		every { it.getOutputPortOpenCount(1) } returns open
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
