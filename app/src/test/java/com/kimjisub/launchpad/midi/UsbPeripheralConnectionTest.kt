package com.kimjisub.launchpad.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UsbPeripheralConnectionTest {
    private val port = PeripheralDevice.Port(0, "port")
    private val endpoint = PeripheralDevice(1, "system", listOf(port), listOf(port))
    private val peers = mutableListOf<Pair<UsbPeripheralConnection, FakeAccess>>()

    @After fun cleanup() {
        peers.forEach { (connection, peer) -> peer.unblock.countDown(); connection.close() }
        peers.forEach { (connection, _) -> await { connection.state.value !is UsbPeripheralConnection.State.Closing } }
    }

    @Test fun orderedMessagesLeaveCallerThreadAndCloseSendsEachActiveRelease() {
        val (connection, peer) = connection()
        open(connection)
        assertTrue(connection.sendCurrent(PadChange(0,true)))
        assertTrue(connection.sendCurrent(PadChange(64,true)))
        assertTrue(connection.sendCurrent(PadChange(1,true)))
        assertTrue(connection.sendCurrent(PadChange(1,false)))
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        assertEquals(listOf(listOf(0x90,81,127), listOf(0xb0,104,127), listOf(0x90,82,127),
            listOf(0x80,82,0), listOf(0x80,81,0), listOf(0xb0,104,0)), peer.sent.toList())
        assertFalse(peer.sendThreads.contains(Thread.currentThread().name))
        assertTrue(peer.inputClosed && peer.outputClosed && peer.deviceClosed)
    }

    @Test fun receivedLightsDoNotEchoAndOldReceiverCannotChangeNewSession() {
        val (connection, peer) = connection()
        open(connection)
        peer.receive!!(byteArrayOf(0x90.toByte(),81,5),0,3)
        assertEquals(5,connection.lights.snapshot()[0])
        peer.receive!!(byteArrayOf(0x80.toByte(),81,64),0,3)
        assertEquals(0,connection.lights.snapshot()[0])
        peer.receive!!(byteArrayOf(0x90.toByte(),81,5),0,3)
        assertTrue(peer.sent.isEmpty())
        val old = peer.receive!!
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        open(connection)
        old(byteArrayOf(0x90.toByte(),81,127),0,3)
        assertEquals(0,connection.lights.snapshot()[0])
    }

    @Test fun cancelPendingThenOpenClosesLateResultAndAllowsFreshAttempt() {
        val (connection, peer) = connection()
        peer.defer = true
        connection.connect(endpoint,0,0)
        await { peer.completion != null }
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        val late = FakeAccess()
        peer.completion!!(late.device)
        await { late.deviceClosed }
        assertEquals(0,late.inputsOpened)
        peer.defer = false
        open(connection)
    }

    @Test fun lateResultAfterWholeConnectionClosedIsStillClosed() {
        val (connection, peer) = connection()
        peer.defer = true
        connection.connect(endpoint,0,0)
        await { peer.completion != null }
        connection.close()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        val late = FakeAccess()
        peer.completion!!(late.device)
        await { late.deviceClosed }
    }

    @Test fun occupiedInputFailsButLightsOnlyFailureKeepsButtonsAvailable() {
        val (connection, peer) = connection()
        peer.noInput = true
        connection.connect(endpoint,0,0)
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.INPUT_PORT) }
        assertTrue(peer.deviceClosed)
        // The failure is published before the ended session's ports finish closing; until then a connect is ignored.
        await { !connection.portsClosing.value }
        peer.noInput = false
        peer.noOutput = true
        connection.connect(endpoint,0,0)
        await { (connection.state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == false }
        assertTrue(connection.sendCurrent(PadChange(0,true)))
        await { peer.sent.size == 1 }
    }

    @Test fun aFailureIsPublishedOnlyOnceItsPortsAreMarkedClosing() {
        val (connection, peer) = connection()
        peer.noInput = true
        val closingWhenFailed = Collections.synchronizedList(mutableListOf<Boolean>())
        // Unconfined runs the collector inside the state update, before anything that follows it.
        val watcher = CoroutineScope(Dispatchers.Unconfined).launch {
            connection.state.collect {
                if (it.failedWith(UsbPeripheralConnection.Failure.INPUT_PORT)) closingWhenFailed.add(connection.portsClosing.value)
            }
        }
        try {
            connection.connect(endpoint,0,0)
            await { closingWhenFailed.isNotEmpty() }
            assertEquals(listOf(true), closingWhenFailed.toList())
        } finally { watcher.cancel() }
    }

    @Test fun throwingOutputIsAlsoLightsUnavailable() {
        val (connection, peer) = connection()
        peer.outputThrows = true
        connection.connect(endpoint,0,0)
        await { (connection.state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == false }
    }

    @Test fun enumerationAndOpenExceptionsAreReportedWithoutEscaping() {
        val (connection, peer) = connection()
        peer.listThrows = true
        connection.refresh()
        await { connection.discoveryFailure.value == UsbPeripheralConnection.Failure.DISCOVERY }
        peer.openThrows = true
        connection.connect(endpoint,0,0)
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.OPEN) }
    }

    @Test fun sendFailureAndRemovalCloseEverythingAndClearLights() {
        val (connection, peer) = connection()
        open(connection)
        peer.sendThrows = true
        connection.sendCurrent(PadChange(0,true))
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.SEND) }
        await { peer.inputClosed && peer.outputClosed && peer.deviceClosed }
        peer.sendThrows = false
        open(connection)
        peer.receive!!(byteArrayOf(0x90.toByte(),81,5),0,3)
        peer.removed!!(1)
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.REMOVED) }
        assertTrue(connection.lights.snapshot().all { it == 0 })
    }

    @Test fun queueAccepts1024WaitingThenFailsRatherThanLosingRelease() {
        val (connection, peer) = connection()
        peer.blockSend = true
        open(connection)
        assertTrue(connection.sendCurrent(PadChange(0,true)))
        assertTrue(peer.entered.await(10,TimeUnit.SECONDS))
        repeat(1024) { assertTrue("Queue entry $it", connection.sendCurrent(PadChange(1,true))) }
        assertFalse(connection.sendCurrent(PadChange(0,false)))
        peer.unblock.countDown()
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.OVERFLOW) }
    }

    @Test fun hostAndVirtualOwnersAreNeverStolenAndPeripheralBlocksBoth() {
        val gate = MidiTransportGate()
        val (connection, _) = connection(gate)
        for (acquire in listOf(gate::acquireUsb, gate::acquireVirtual)) {
            val lease = acquire()!!
            try {
                connection.connect(endpoint,0,0)
                assertTrue(connection.state.value.failedWith(UsbPeripheralConnection.Failure.BUSY))
            } finally { lease.close() }
        }
        open(connection)
        assertNull(gate.acquireUsb())
        assertNull(gate.acquireVirtual())
        assertNull(gate.acquirePeripheral())
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        assertNotNull(gate.acquireUsb()?.also { it.close() })
    }

    @Test fun sharedGateLetsHostControllersShareButNeverHandsAnExclusiveOwnerAway() {
        val gate = MidiTransportGate()
        val first = gate.acquireUsb()!!
        val second = gate.acquireUsb()!!
        assertNull(gate.acquirePeripheral())
        first.close(); first.close()
        assertNull(gate.acquirePeripheral())
        assertNull(gate.acquireVirtual())
        second.close()
        val peripheral = gate.acquirePeripheral()!!
        assertNull(gate.acquirePeripheral())
        assertNull(gate.acquireVirtual())
        assertNull(gate.acquireUsb())
        peripheral.close()
        val virtual = gate.acquireVirtual()!!
        assertNull(gate.acquirePeripheral())
        virtual.close()
        gate.acquirePeripheral()!!.close()
    }

    @Test fun missingOutputPortNumberIsReportedAsLightPortFailure() {
        val (connection, _) = connection()
        connection.connect(endpoint,0,7)
        assertTrue(connection.state.value.failedWith(UsbPeripheralConnection.Failure.OUTPUT_PORT))
    }

    @Test fun deviceAddedWhileListingIsReadAgainAfterTheRunningRead() {
        val (connection, peer) = connection()
        await { peer.listCalls.get() >= 1 }
        val before = peer.listCalls.get()
        peer.holdList = CountDownLatch(1)
        peer.changed!!()
        assertTrue(peer.listEntered.await(10,TimeUnit.SECONDS))
        peer.listed = listOf(endpoint)
        peer.changed!!()
        peer.holdList!!.countDown()
        await { connection.devices.value == listOf(endpoint) }
        assertEquals(before + 2, peer.listCalls.get())
    }

    @Test fun missingInputOrThrowingInputNeverEscapes() {
        val (connection, peer) = connection()
        connection.connect(endpoint,7,0)
        assertTrue(connection.state.value.failedWith(UsbPeripheralConnection.Failure.INPUT_PORT))
        peer.inputThrows = true
        connection.connect(endpoint,0,0)
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.INPUT_PORT) }
        await { peer.deviceClosed }
    }

    @Test fun disconnectDoesNotWaitOnBlockedSenderAndClosesAtDeadline() {
        val (connection, peer) = connection()
        peer.blockSend = true
        open(connection)
        connection.sendCurrent(PadChange(0,true))
        assertTrue(peer.entered.await(10,TimeUnit.SECONDS))
        connection.disconnect()
        assertEquals(UsbPeripheralConnection.State.Closing, connection.state.value)
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.RELEASE) }
        await { peer.deviceClosed }
    }

    @Test fun releaseDeadlineFreesTheTransportWhileClosingWaitsForABlockedSend() {
        val gate = MidiTransportGate()
        val (connection, peer) = connection(gate)
        peer.closeWaitsForSend = true
        peer.blockSend = true
        open(connection)
        connection.sendCurrent(PadChange(0,true))
        assertTrue(peer.entered.await(10,TimeUnit.SECONDS))
        connection.disconnect()
        awaitWithin(3) { connection.state.value.failedWith(UsbPeripheralConnection.Failure.RELEASE) }
        assertNotNull(gate.acquireUsb()?.also { it.close() })
        assertFalse(peer.inputClosed)
        peer.unblock.countDown()
        await { peer.inputClosed && peer.deviceClosed }
    }

    @Test fun overflowDeadlineFreesTheTransportWhileClosingWaitsForABlockedSend() {
        val gate = MidiTransportGate()
        val (connection, peer) = connection(gate)
        peer.closeWaitsForSend = true
        peer.blockSend = true
        open(connection)
        connection.sendCurrent(PadChange(0,true))
        assertTrue(peer.entered.await(10,TimeUnit.SECONDS))
        var accepted = 0
        while (accepted < 5_000 && connection.sendCurrent(PadChange(accepted % 64, accepted % 2 == 0))) accepted++
        assertTrue("the queue must refuse before 5,000 sends", accepted < 5_000)
        awaitWithin(3) { connection.state.value.failedWith(UsbPeripheralConnection.Failure.OVERFLOW) }
        assertNotNull(gate.acquireUsb()?.also { it.close() })
        peer.unblock.countDown()
        await { peer.inputClosed && peer.deviceClosed }
    }

    @Test fun removedPendingEndpointCannotReopenAndDoesNotReplayEvents() {
        val (connection, peer) = connection()
        peer.defer = true
        connection.connect(endpoint,0,0)
        await { peer.completion != null }
        peer.removed!!(1)
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.REMOVED) }
        val late = FakeAccess()
        peer.completion!!(late.device)
        await { late.deviceClosed }
        assertEquals(0,late.inputsOpened)
    }

    @Test fun openTimeoutClosesLateDeviceInsteadOfReopening() {
        val (connection, peer) = connection()
        peer.defer = true
        connection.connect(endpoint,0,0)
        await { peer.completion != null }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (connection.state.value !is UsbPeripheralConnection.State.Failed && System.nanoTime() < deadline)
            Thread.sleep(10)
        assertTrue(connection.state.value.failedWith(UsbPeripheralConnection.Failure.TIMEOUT))
        val late = FakeAccess()
        peer.completion!!(late.device)
        await { late.deviceClosed }
        assertEquals(0,late.inputsOpened)
    }

    @Test fun closingFailureStillAttemptsEveryResourceAndReportsFailure() {
        val (connection, peer) = connection()
        open(connection)
        peer.closeThrows = true
        connection.disconnect()
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.CLOSE) }
        assertTrue(peer.inputClosed && peer.outputClosed && peer.deviceClosed)
    }

    @Test fun simultaneousConnectionsHaveExactlyOneOwner() {
        val gate = MidiTransportGate()
        val first = connection(gate).first
        val second = connection(gate).first
        val start = CountDownLatch(1)
        val threads = listOf(first,second).map { connection ->
            Thread { start.await(); connection.connect(endpoint,0,0) }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join(10000); assertFalse(it.isAlive) }
        await { listOf(first,second).all { it.state.value is UsbPeripheralConnection.State.Open ||
            it.state.value.failedWith(UsbPeripheralConnection.Failure.BUSY) } }
        assertEquals(1,listOf(first,second).count { it.state.value is UsbPeripheralConnection.State.Open })
        assertEquals(1,listOf(first,second).count { it.state.value.failedWith(UsbPeripheralConnection.Failure.BUSY) })
    }

    @Test fun reconnectHasDistinctIdentityForLateReleases() {
        val (connection, peer) = connection()
        open(connection)
        val first = connection.state.value as UsbPeripheralConnection.State.Open
        connection.sendCurrent(PadChange(0,true))
        await { peer.sent.size == 1 }
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        open(connection)
        val second = connection.state.value as UsbPeripheralConnection.State.Open
        connection.sendCurrent(PadChange(0,true))
        await { peer.sent.size == 3 }
        assertNotEquals(first,second)
        assertFalse(connection.send(PadChange(0,false), first.generation))
        assertEquals(3,peer.sent.size)
    }

    @Test fun aRepeatedFailureIsStillANewState() {
        val gate = MidiTransportGate()
        val (connection, _) = connection(gate)
        val lease = gate.acquireUsb()!!
        try {
            connection.connect(endpoint,0,0)
            val first = connection.state.value
            connection.connect(endpoint,0,0)
            val second = connection.state.value
            assertTrue(first.failedWith(UsbPeripheralConnection.Failure.BUSY) && second.failedWith(UsbPeripheralConnection.Failure.BUSY))
            assertNotEquals(first, second)
        } finally { lease.close() }
    }

    @Test fun reconnectWaitsUntilTheEndedSessionsPortsHaveClosed() {
        val (connection, peer) = connection()
        open(connection)
        val closing = CountDownLatch(1)
        peer.holdInputClose = closing
        peer.sendThrows = true
        connection.sendCurrent(PadChange(0,true))
        await { connection.state.value.failedWith(UsbPeripheralConnection.Failure.SEND) }
        assertTrue(connection.portsClosing.value)
        peer.sendThrows = false
        val opened = peer.inputsOpened
        connection.connect(endpoint,0,0)
        assertTrue(connection.state.value.failedWith(UsbPeripheralConnection.Failure.SEND))
        assertEquals(opened, peer.inputsOpened)
        closing.countDown()
        await { !connection.portsClosing.value }
        open(connection)
    }

    @Test fun aFailedDeviceWatchIsRegisteredAgainOnTheNextSearch() {
        val peer = FakeAccess()
        peer.watchThrows = true
        val connection = UsbPeripheralConnection(peer, MidiTransportGate())
        peers.add(connection to peer)
        await { connection.discoveryFailure.value == UsbPeripheralConnection.Failure.UNAVAILABLE }
        assertEquals(0, peer.listCalls.get())
        peer.watchThrows = false
        peer.listed = listOf(endpoint)
        connection.refresh()
        await { peer.removed != null && connection.devices.value == listOf(endpoint) }
        assertNull(connection.discoveryFailure.value)
    }

    private fun UsbPeripheralConnection.State.failedWith(reason: UsbPeripheralConnection.Failure) =
        (this as? UsbPeripheralConnection.State.Failed)?.reason == reason

    private fun UsbPeripheralConnection.sendCurrent(change: PadChange): Boolean {
        val opened = state.value as? UsbPeripheralConnection.State.Open ?: return false
        return send(change, opened.generation)
    }

    private fun connection(gate: MidiTransportGate = MidiTransportGate()): Pair<UsbPeripheralConnection, FakeAccess> {
        val peer = FakeAccess()
        val connection = UsbPeripheralConnection(peer, gate)
        peers.add(connection to peer)
        await { peer.removed != null }
        return connection to peer
    }
    private fun open(connection: UsbPeripheralConnection) {
        connection.connect(endpoint,0,0)
        await { (connection.state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == true }
    }
    private fun await(check: () -> Boolean) = awaitWithin(10, check)
    private fun awaitWithin(seconds: Long, check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Condition did not complete",check())
    }

    private class FakeAccess : PeripheralMidiAccess {
        @Volatile var completion: ((PeripheralMidiAccess.Device?) -> Unit)? = null
        @Volatile var removed: ((Int) -> Unit)? = null
        @Volatile var changed: (() -> Unit)? = null
        @Volatile var listed: List<PeripheralDevice> = emptyList()
        @Volatile var holdList: CountDownLatch? = null
        val listEntered = CountDownLatch(1)
        val listCalls = AtomicInteger()
        @Volatile var receive: ((ByteArray,Int,Int) -> Unit)? = null
        @Volatile var defer = false
        @Volatile var closeThrows = false
        @Volatile var noInput = false
        @Volatile var inputThrows = false
        @Volatile var noOutput = false
        @Volatile var outputThrows = false
        @Volatile var listThrows = false
        @Volatile var watchThrows = false
        @Volatile var holdInputClose: CountDownLatch? = null
        @Volatile var openThrows = false
        @Volatile var sendThrows = false
        @Volatile var blockSend = false
        /** Like Android's MidiInputPort: close() waits for a send that is still writing. */
        @Volatile var closeWaitsForSend = false
        private val sendLock = Any()
        @Volatile var inputClosed = false
        @Volatile var outputClosed = false
        @Volatile var deviceClosed = false
        @Volatile var inputsOpened = 0
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val sent = Collections.synchronizedList(mutableListOf<List<Int>>())
        val sendThreads = Collections.synchronizedSet(mutableSetOf<String>())
        /** A socket write blocked on a reader that stopped is not ended by interrupting the writer. */
        private fun awaitUnblockIgnoringInterrupts() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var interrupted = false
            while (true) {
                try {
                    unblock.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
                    break
                } catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
        override fun devices(): List<PeripheralDevice> {
            listCalls.incrementAndGet()
            if (listThrows) throw SecurityException("list")
            val snapshot = listed
            holdList?.let { listEntered.countDown(); it.await(10,TimeUnit.SECONDS) }
            return snapshot
        }
        override fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable {
            if (watchThrows) throw SecurityException("watch")
            this.changed = changed
            this.removed = removed
            return AutoCloseable { }
        }
        override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) {
            if (openThrows) throw SecurityException("open")
            completion = completed
            if (!defer) completed(device)
        }
        val device = object : PeripheralMidiAccess.Device {
            override fun input(number: Int): PeripheralMidiAccess.Sender? {
                inputsOpened++
                if (inputThrows) throw IOException("input")
                if (noInput) return null
                return object : PeripheralMidiAccess.Sender {
                    override fun send(bytes: ByteArray) {
                        synchronized(sendLock) {
                            if (sendThrows) throw IOException("send")
                            if (blockSend) { entered.countDown(); awaitUnblockIgnoringInterrupts() }
                            sendThreads.add(Thread.currentThread().name)
                            sent.add(bytes.map { it.toInt() and 255 })
                        }
                    }
                    override fun close() {
                        holdInputClose?.await(10, TimeUnit.SECONDS)
                        if (closeWaitsForSend) synchronized(sendLock) { inputClosed = true }
                        else { inputClosed = true; unblock.countDown() }
                    }
                }
            }
            override fun output(number: Int, receive: (ByteArray, Int, Int) -> Unit): AutoCloseable? {
                if (outputThrows) throw IOException("output")
                if (noOutput) return null
                this@FakeAccess.receive = receive
                return AutoCloseable { outputClosed = true; if (closeThrows) throw IOException("close") }
            }
            override fun close() { deviceClosed = true }
        }
    }
}
