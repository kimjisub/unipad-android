package com.kimjisub.launchpad.basefeatures

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import java.util.concurrent.CopyOnWriteArrayList

/** Launchpad S transport whose outgoing packets can be checked without USB hardware. */
class VirtualLaunchpad(private val screen: FeatureScreen) : AutoCloseable {
    val driver = LaunchpadS()
    val packets = CopyOnWriteArrayList<List<Int>>()
    private val output = object : DriverRef.OnSendSignalListener {
        override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
            packets += listOf(cmd, sig, note, velocity).map { it.toInt() and 0xff }
        }
        override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
    }
    private val connection = screen.onMain {
        MidiConnection.initConnection(Intent(), screen.context.getSystemService(Context.USB_SERVICE) as UsbManager, screen.context)
        MidiConnection.attachTransport("Virtual Launchpad", driver, output)
    }

    override fun close() = screen.onMain { connection.close() }
}
