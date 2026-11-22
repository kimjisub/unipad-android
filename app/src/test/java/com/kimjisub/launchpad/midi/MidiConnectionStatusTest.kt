package com.kimjisub.launchpad.midi

import android.content.Intent
import android.content.Context
import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.kimjisub.launchpad.midi.driver.LaunchpadPROCFW
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.MasterKeyboard
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class MidiConnectionStatusTest {
    private fun initialize(manager: UsbManager) {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns null
        @Suppress("DEPRECATION")
        every { intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) } returns null
        every { intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) } returns null
        MidiConnection.initConnection(intent, manager)
    }

    @Test fun permissionWaitingIsDistinctAndNeverPromptsDuringEnumeration() {
        val manager = mockk<UsbManager>(relaxed = true)
        val device = mockk<UsbDevice>(relaxed = true)
        every { device.deviceId } returns 41
        every { device.productId } returns 0x0036
        every { device.productName } returns "My Mini"
        every { manager.deviceList } returns hashMapOf("mini" to device)
        every { manager.hasPermission(device) } returns false
        // Permission must also be visible before any attach callback has initialised MIDI.
        val status = MidiConnection.connectionStatus(permissionManager = manager)
        assertEquals(MidiConnectionStatus.Phase.PERMISSION, status.phase)
        assertEquals("My Mini", status.deviceName)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any<android.app.PendingIntent>()) }
        verify(exactly = 0) { manager.openDevice(any()) }
        every { manager.deviceList } returns hashMapOf()
    }

    @Test fun virtualInputUsesDecodedPadsAndAPickWhileAttachedClaimsNothing() {
        val manager = mockk<UsbManager>(relaxed = true)
        every { manager.deviceList } returns hashMapOf()
        initialize(manager)
        val pad = LaunchpadS()
        val output = mockk<com.kimjisub.launchpad.midi.driver.DriverRef.OnSendSignalListener>(relaxed = true)
        val connection = MidiConnection.attachTransport("Virtual Mini", pad, output)
        try {
            assertEquals("Virtual Mini", MidiConnection.connectionStatus().deviceName)
            assertFalse(MidiConnection.connectionStatus().manuallyChosen)
            pad.getSignal(15, 0xf8, 0, 0)
            pad.getSignal(4, 0xf0, 0x7e, 0)
            assertEquals(MidiConnectionStatus.Phase.WAITING, MidiConnection.connectionStatus().phase)
            pad.getSignal(9, 0x90, 0, 127)
            assertEquals(MidiConnectionStatus.Phase.INPUT, MidiConnection.connectionStatus().phase)
            // A virtual connection keeps its own layout, so a model pick neither applies nor claims firmware.
            MidiConnection.driver = LaunchpadPROCFW()
            assertFalse(MidiConnection.connectionStatus().manuallyChosen)
            assertNull(MidiConnection.connectionStatus().firmware)
        } finally { connection.close() }
        assertEquals(MidiConnectionStatus.Phase.DISCONNECTED, MidiConnection.connectionStatus().phase)
        MidiConnection.attachTransport("Unrecognised keyboard", MasterKeyboard(), output).use {
            assertFalse(MidiConnection.connectionStatus().manuallyChosen)
            assertTrue(MidiConnection.connectionStatus().modelUnknown)
            assertEquals(MidiConnectionStatus.Phase.WAITING, MidiConnection.connectionStatus().phase)
        }
    }

    @Test fun explicitPermissionRequestTargetsOnlyTheChosenUnpermittedDevice() {
        val manager = mockk<UsbManager>(relaxed = true)
        val device = mockk<UsbDevice>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val result = mockk<PendingIntent>()
        every { context.getSystemService(Context.USB_SERVICE) } returns manager
        every { device.deviceId } returns 41
        every { device.productId } returns 0x0036
        every { manager.deviceList } returns hashMapOf("mini" to device)
        every { manager.hasPermission(device) } returns false
        mockkStatic(PendingIntent::class)
        try {
            every { PendingIntent.getBroadcast(context, 41, any(), any()) } returns result
            MidiConnection.requestUsbPermission(context, 41)
            verify(exactly = 1) { manager.requestPermission(device, result) }
            MidiConnection.requestUsbPermission(context, 42)
            every { manager.hasPermission(device) } returns true
            MidiConnection.requestUsbPermission(context, 41)
            verify(exactly = 1) { manager.requestPermission(device, result) }
        } finally { unmockkStatic(PendingIntent::class) }
    }

    @Test fun deniedPermissionDoesNotStartConnectionAndStillShowsPermissionNeeded() {
        val manager = mockk<UsbManager>(relaxed = true)
        val device = mockk<UsbDevice>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val result = mockk<Intent>(relaxed = true)
        every { device.deviceId } returns 41
        every { device.productId } returns 0x0036
        every { manager.deviceList } returns hashMapOf("mini" to device)
        every { manager.hasPermission(device) } returns false
        every { result.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) } returns false
        UsbMidiPermissionReceiver().onReceive(context, result)
        assertEquals(MidiConnectionStatus.Phase.PERMISSION, MidiConnection.connectionStatus(permissionManager = manager).phase)
        verify(exactly = 0) { context.getSystemService(any<String>()) }
        verify(exactly = 0) { manager.openDevice(any()) }
    }

    @Test fun previousTransportCannotConfirmInputOnTheNextDevice() {
        val manager = mockk<UsbManager>(relaxed = true)
        every { manager.deviceList } returns hashMapOf()
        initialize(manager)
        val oldPad = LaunchpadS()
        val currentPad = LaunchpadS()
        val output = mockk<com.kimjisub.launchpad.midi.driver.DriverRef.OnSendSignalListener>(relaxed = true)
        MidiConnection.attachTransport("First pad", oldPad, output).close()
        MidiConnection.attachTransport("Second pad", currentPad, output).use {
            oldPad.getSignal(9, 0x90, 0, 127)
            assertEquals(MidiConnectionStatus.Phase.WAITING, MidiConnection.connectionStatus().phase)
            currentPad.getSignal(9, 0x90, 0, 127)
            assertEquals(MidiConnectionStatus.Phase.INPUT, MidiConnection.connectionStatus().phase)
        }
    }

    @Test fun detectedCustomFirmwareIsReportedWithoutInferringItFromLaterChoices() {
        val manager = mockk<UsbManager>(relaxed = true)
        every { manager.deviceList } returns hashMapOf()
        initialize(manager)
        MidiConnection.attachTransport("Launchpad Open", LaunchpadPROCFW(), mockk(relaxed = true)).use {
            assertEquals("Launchpad Open", MidiConnection.connectionStatus().firmware)
            assertEquals(5, MidiConnection.connectionStatus().initialHelp)
            MidiConnection.driver = LaunchpadS()
            assertEquals("Launchpad Open", MidiConnection.connectionStatus().firmware)
        }
    }

    @Test fun decodedInputAlreadyInFlightCannotConfirmTheNextDevice() {
        val manager = mockk<UsbManager>(relaxed = true)
        every { manager.deviceList } returns hashMapOf()
        initialize(manager)
        val oldPad = LaunchpadS()
        val currentPad = LaunchpadS()
        val output = mockk<com.kimjisub.launchpad.midi.driver.DriverRef.OnSendSignalListener>(relaxed = true)
        val previous = MidiConnection.attachTransport("First pad", oldPad, output)
        // Model a decoded callback that was already read before the listener was removed.
        val inFlight = com.kimjisub.launchpad.midi.driver.DriverRef::class.java
            .getDeclaredField("onReceiveSignalListener").apply { isAccessible = true }.get(oldPad)
            as com.kimjisub.launchpad.midi.driver.DriverRef.OnReceiveSignalListener
        previous.close()
        MidiConnection.attachTransport("Second pad", currentPad, output).use {
            inFlight.onPadTouch(0, 0, true, 127)
            assertEquals(MidiConnectionStatus.Phase.WAITING, MidiConnection.connectionStatus().phase)
            currentPad.getSignal(9, 0x90, 0, 127)
            assertEquals(MidiConnectionStatus.Phase.INPUT, MidiConnection.connectionStatus().phase)
        }
    }
}
