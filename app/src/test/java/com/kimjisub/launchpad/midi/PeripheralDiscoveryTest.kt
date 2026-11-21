package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.kimjisub.launchpad.midi.driver.Noting
import com.kimjisub.launchpad.midi.driver.DriverRef
import android.media.midi.MidiManager
import android.media.midi.MidiDeviceInfo
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class PeripheralDiscoveryTest {
    @Test fun onlySystemUsbWithoutUsbPropertyIsPeripheralRegardlessOfName() {
        fun info(type: Int, hasUsb: Boolean): MidiDeviceInfo {
            val info = mockk<MidiDeviceInfo>()
            val properties = mockk<Bundle>()
            every { info.type } returns type
            every { info.properties } returns properties
            every { properties.containsKey(MidiDeviceInfo.PROPERTY_USB_DEVICE) } returns hasUsb
            return info
        }
        assertTrue(AndroidPeripheralMidiAccess.isPeripheral(info(MidiDeviceInfo.TYPE_USB,false)))
        assertFalse(AndroidPeripheralMidiAccess.isPeripheral(info(MidiDeviceInfo.TYPE_USB,true)))
        assertFalse(AndroidPeripheralMidiAccess.isPeripheral(info(MidiDeviceInfo.TYPE_VIRTUAL,false)))
        assertFalse(AndroidPeripheralMidiAccess.isPeripheral(info(MidiDeviceInfo.TYPE_BLUETOOTH,false)))
    }

    @Suppress("DEPRECATION")
    @Test fun listsOnlyByteStreamTransportOnNewAndroidAndLegacyListOnOldAndroid() {
        val context = mockk<Context>()
        val manager = mockk<MidiManager>()
        every { context.getSystemService(Context.MIDI_SERVICE) } returns manager
        every { manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM) } returns emptySet()
        every { manager.devices } returns emptyArray()
        AndroidPeripheralMidiAccess(context,33).infos()
        verify(exactly = 1) { manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM) }
        verify(exactly = 0) { manager.devices }
        AndroidPeripheralMidiAccess(context,24).infos()
        verify(exactly = 1) { manager.devices }
    }

    @Suppress("DEPRECATION")
    @Test fun existingHostAndVirtualEntryPointsRespectPeripheralOwnership() {
        val lease = MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_PERIPHERAL)!!
        try {
            val usb = mockk<UsbDevice>()
            val manager = mockk<UsbManager>(relaxed = true)
            val intent = mockk<Intent>()
            every { intent.action } returns UsbManager.ACTION_USB_DEVICE_ATTACHED
            every { intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) } returns usb
            MidiConnection.initConnection(intent,manager)
            verify(exactly = 0) { manager.openDevice(any()) }
            assertThrows(IllegalStateException::class.java) {
                MidiConnection.attachTransport("test",Noting(),mockk<DriverRef.OnSendSignalListener>(relaxed = true))
            }
        } finally { lease.close() }
    }

    @Test fun hostAllowsTwoLeasesButOtherModesWaitUntilBothEnd() {
        val first = MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_HOST)!!
        val second = MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_HOST)!!
        try {
            assertNull(MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_PERIPHERAL))
            first.close()
            assertNull(MidiModeOwnership.acquire(MidiModeOwnership.Mode.VIRTUAL))
        } finally { first.close(); second.close() }
        MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_PERIPHERAL)!!.close()
    }
}
