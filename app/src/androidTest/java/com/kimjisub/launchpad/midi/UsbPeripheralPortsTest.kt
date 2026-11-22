package com.kimjisub.launchpad.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Android byte-stream ports against a test-only virtual peer. This is not USB testing. */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class UsbPeripheralPortsTest {
    private lateinit var manager: MidiManager
    private lateinit var info: MidiDeviceInfo
    private lateinit var observer: MidiDevice
    private lateinit var observedPort: AutoCloseable
    private val echoed = Collections.synchronizedList(mutableListOf<List<Int>>())
    private val partial = mutableListOf<Int>()
    private val connections = mutableListOf<UsbPeripheralConnection>()
    private val resources = mutableListOf<AutoCloseable>()
    private val endpoint get() = PeripheralDevice(info.id,"test peer",listOf(PeripheralDevice.Port(0,"press")),listOf(PeripheralDevice.Port(0,"lights")))

    @Suppress("DEPRECATION")
    @Before fun setup() {
        manager = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(Context.MIDI_SERVICE) as MidiManager
        await { manager.devices.any { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == "Peripheral port test peer" } }
        info = manager.devices.first { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == "Peripheral port test peer" }
        assertFalse("Production discovery must reject a virtual service", AndroidPeripheralMidiAccess.isPeripheral(info))
        observer = openDevice()
        observedPort = AndroidMidiPorts(observer).output(0, ::recordWire)!!
        // Closing the port right after sending can drop the byte before the peer reads it.
        observer.openInputPort(1)!!.use {
            it.send(byteArrayOf(0),0,1)
            await { echoed.contains(listOf(0xb0,111,0)) }
        }
        echoed.clear()
    }

    /** Records the exact wire bytes (no Note Off normalization); every message here is three bytes long. */
    private fun recordWire(bytes: ByteArray, offset: Int, count: Int) = synchronized(echoed) {
        for (i in offset until offset + count) {
            partial.add(bytes[i].toInt() and 255)
            if (partial.size == 3) { echoed.add(partial.toList()); partial.clear() }
        }
    }

    @After fun cleanup() {
        connections.forEach { it.close() }
        connections.forEach { c -> await { c.state.value !is UsbPeripheralConnection.State.Closing } }
        resources.reversed().forEach { try { it.close() } catch (_: Exception) { } }
        if (::observedPort.isInitialized) observedPort.close()
        if (::observer.isInitialized) observer.close()
    }

    @Test fun actualPortsRoundTripAndNormalCloseReleasesEveryHeldButton() {
        val connection = connection()
        connect(connection)
        assertTrue(connection.sendCurrent(PadChange(0,true)))
        assertTrue(connection.sendCurrent(PadChange(64,true)))
        await { connection.lights.snapshot()[0] == 127 && connection.lights.snapshot()[64] == 127 && connection.lights.snapshot()[71] == 2 }
        await { echoed.toList().count { it[0] != 0xb0 || it[1] != 111 } == 2 }
        assertEquals(2, connection.lights.snapshot()[71])
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        await { echoed.contains(listOf(0x80,81,0)) && echoed.contains(listOf(0xb0,104,0)) }
        assertEquals(listOf(listOf(0x90,81,127),listOf(0xb0,104,127),listOf(0x80,81,0),listOf(0xb0,104,0)),
            echoed.toList().filter { it[0] != 0xb0 || it[1] != 111 })
        println("Android port round trip and releases: ${echoed.toList()}")
    }

    @Test fun actualInputPortOccupancyIsAnErrorAndCanBeRetriedAfterRelease() {
        val occupied = observer.openInputPort(0)!!
        resources.add(occupied)
        val connection = connection()
        connection.connect(endpoint,0,0)
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.INPUT_PORT) }
        occupied.close()
        connect(connection)
    }

    @Test fun actualButtonsWorkWhenOnlyLightPortOpeningFails() {
        val connection = connection(noLights = true)
        connection.connect(endpoint,0,0)
        await { (connection.state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == false }
        assertTrue(connection.sendCurrent(PadChange(79,true)))
        await { echoed.contains(listOf(0x90,19,127)) }
        assertTrue(connection.lights.snapshot().all { it == 0 })
    }

    @Test fun cancelledActualOpenClosesLateDeviceBeforeAnyPortIsOpened() {
        var deliver: (() -> Unit)? = null
        val ready = CountDownLatch(1)
        val lateClosed = CountDownLatch(1)
        val connection = connection(defer = { action -> deliver = action; ready.countDown() }, onClosed = { lateClosed.countDown() })
        connection.connect(endpoint,0,0)
        assertTrue(ready.await(15,TimeUnit.SECONDS))
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        deliver!!()
        assertTrue(lateClosed.await(15,TimeUnit.SECONDS))
        // A new actual connection must still own the exclusive input port.
        connect(connection)
        connection.sendCurrent(PadChange(0,true))
        await { echoed.contains(listOf(0x90,81,127)) }
    }

    @Test fun peerProcessDeathMakesSendingFailWithoutCrashingTheApp() {
        val connection = connection()
        connect(connection)
        val control = observer.openInputPort(1)!!
        resources.add(control)
        control.send(byteArrayOf(1),0,1)
        await {
            connection.sendCurrent(PadChange(0,true))
            connection.state.value is UsbPeripheralConnection.State.Failed
        }
        assertTrue((connection.state.value as UsbPeripheralConnection.State.Failed).reason in
            setOf(UsbPeripheralConnection.Failure.SEND,UsbPeripheralConnection.Failure.REMOVED))
        assertTrue(connection.lights.snapshot().all { it == 0 })
    }

    @Test fun releaseDeadlineHandsTheTransportBackWhileTheComputerIsNotReading() =
        whileThePeerIsNotReading(UsbPeripheralConnection.Failure.RELEASE) { connection ->
            blockTheWriter(connection)
            connection.disconnect()
        }

    @Test fun overflowHandsTheTransportBackWhileTheComputerIsNotReading() =
        whileThePeerIsNotReading(UsbPeripheralConnection.Failure.OVERFLOW) { connection ->
            blockTheWriter(connection)
            var sent = 0
            while (sent < 200_000 && connection.sendCurrent(PadChange(sent % 64, sent % 2 == 0))) sent++
            assertTrue("the queue never refused", sent < 200_000)
        }

    /** Fills the socket so the writer blocks in the Android port, staying under the 1,024 queued messages. */
    private fun blockTheWriter(connection: UsbPeripheralConnection) {
        var sent = 0
        repeat(6) {
            repeat(100) { if (connection.sendCurrent(PadChange(sent % 64, sent % 2 == 0))) sent++ }
            Thread.sleep(100)
        }
        assertTrue("sends refused early: $sent of 600 accepted", sent == 600)
    }

    /** The one-second release deadline must hold even when a send is stuck inside the Android port. */
    private fun whileThePeerIsNotReading(expected: UsbPeripheralConnection.Failure, fill: (UsbPeripheralConnection) -> Unit) {
        val gate = MidiTransportGate()
        val devicesClosed = CountDownLatch(1)
        val connection = connection(gate = gate, onClosed = { devicesClosed.countDown() })
        connect(connection)
        val control = observer.openInputPort(1)!!
        resources.add(control)
        control.send(byteArrayOf(2),0,1)
        try {
            await { echoed.contains(listOf(0xb0,111,127)) }
            fill(connection)
            awaitWithin(4) { connection.state.value == UsbPeripheralConnection.State.Failed(expected) }
            assertNotNull("the transport was not handed back", gate.acquireUsb()?.also { it.close() })
        } finally {
            control.send(byteArrayOf(3),0,1)
            // The stuck port closes once the peer reads again; the next test needs the port free.
            devicesClosed.await(15,TimeUnit.SECONDS)
        }
    }

    private fun UsbPeripheralConnection.sendCurrent(change: PadChange): Boolean {
        val opened = state.value as? UsbPeripheralConnection.State.Open ?: return false
        return send(change, opened.generation)
    }

    private fun connection(noLights: Boolean = false, defer: ((() -> Unit) -> Unit)? = null, onClosed: () -> Unit = {},
                           gate: MidiTransportGate = MidiTransportGate()): UsbPeripheralConnection {
        var delayedOpen = defer
        val access = object : PeripheralMidiAccess {
            override fun devices() = listOf(endpoint)
            override fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable {
                val callback = object : MidiManager.DeviceCallback() {
                    override fun onDeviceRemoved(device: MidiDeviceInfo) { removed(device.id) }
                }
                manager.registerDeviceCallback(callback, Handler(Looper.getMainLooper()))
                return AutoCloseable { manager.unregisterDeviceCallback(callback) }
            }
            override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) {
                manager.openDevice(info, { device ->
                    val ports = device?.let(::AndroidMidiPorts)
                    val wrapped = ports?.let { object : PeripheralMidiAccess.Device by it {
                        override fun output(number: Int, receive: (ByteArray,Int,Int) -> Unit): AutoCloseable? =
                            if (noLights) null else it.output(number,receive)
                        override fun close() { try { it.close() } finally { onClosed() } }
                    } }
                    val action = { completed(wrapped) }
                    val delay = delayedOpen
                    delayedOpen = null
                    if (delay == null) action() else delay(action)
                }, Handler(Looper.getMainLooper()))
            }
        }
        return UsbPeripheralConnection(access, gate).also { connections.add(it) }
    }
    private fun connect(connection: UsbPeripheralConnection) {
        connection.connect(endpoint,0,0)
        await { (connection.state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == true }
    }
    private fun openDevice(): MidiDevice {
        var device: MidiDevice? = null
        val ready = CountDownLatch(1)
        manager.openDevice(info, { device = it; ready.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue(ready.await(15,TimeUnit.SECONDS))
        return device!!
    }
    private fun await(condition: () -> Boolean) = awaitWithin(15, condition)
    private fun awaitWithin(seconds: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Android port condition did not complete; echo=${echoed.toList()}",condition())
    }
}
