package com.kimjisub.launchpad.midi

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        assertTrue(connection.send(PadChange(0,true)))
        assertTrue(connection.send(PadChange(64,true)))
        assertTrue(connection.send(PadChange(1,true)))
        assertTrue(connection.send(PadChange(1,false)))
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
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.INPUT_PORT) }
        assertTrue(peer.deviceClosed)
        peer.noInput = false
        peer.noOutput = true
        connection.connect(endpoint,0,0)
        await { connection.state.value == UsbPeripheralConnection.State.Open(false) }
        assertTrue(connection.send(PadChange(0,true)))
        await { peer.sent.size == 1 }
    }

    @Test fun throwingOutputIsAlsoLightsUnavailable() {
        val (connection, peer) = connection()
        peer.outputThrows = true
        connection.connect(endpoint,0,0)
        await { connection.state.value == UsbPeripheralConnection.State.Open(false) }
    }

    @Test fun enumerationAndOpenExceptionsAreReportedWithoutEscaping() {
        val (connection, peer) = connection()
        peer.listThrows = true
        connection.refresh()
        await { connection.discoveryFailure.value == UsbPeripheralConnection.Failure.DISCOVERY }
        peer.openThrows = true
        connection.connect(endpoint,0,0)
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.OPEN) }
    }

    @Test fun sendFailureAndRemovalCloseEverythingAndClearLights() {
        val (connection, peer) = connection()
        open(connection)
        peer.sendThrows = true
        connection.send(PadChange(0,true))
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.SEND) }
        assertTrue(peer.inputClosed && peer.outputClosed && peer.deviceClosed)
        peer.sendThrows = false
        open(connection)
        peer.receive!!(byteArrayOf(0x90.toByte(),81,5),0,3)
        peer.removed!!(1)
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.REMOVED) }
        assertTrue(connection.lights.snapshot().all { it == 0 })
    }

    @Test fun queueAccepts1024WaitingThenFailsRatherThanLosingRelease() {
        val (connection, peer) = connection()
        peer.blockSend = true
        open(connection)
        assertTrue(connection.send(PadChange(0,true)))
        assertTrue(peer.entered.await(10,TimeUnit.SECONDS))
        repeat(1024) { assertTrue("Queue entry $it", connection.send(PadChange(1,true))) }
        assertFalse(connection.send(PadChange(0,false)))
        peer.unblock.countDown()
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.OVERFLOW) }
    }

    @Test fun hostAndVirtualOwnersAreNeverStolenAndPeripheralBlocksBoth() {
        val (connection, _) = connection()
        for (mode in listOf(MidiModeOwnership.Mode.USB_HOST, MidiModeOwnership.Mode.VIRTUAL)) {
            val lease = MidiModeOwnership.acquire(mode)!!
            try {
                connection.connect(endpoint,0,0)
                assertEquals(UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.BUSY),connection.state.value)
            } finally { lease.close() }
        }
        open(connection)
        assertNull(MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_HOST))
        assertNull(MidiModeOwnership.acquire(MidiModeOwnership.Mode.VIRTUAL))
        assertNull(MidiModeOwnership.acquire(MidiModeOwnership.Mode.USB_PERIPHERAL))
    }

    @Test fun missingInputOrThrowingInputNeverEscapes() {
        val (connection, peer) = connection()
        connection.connect(endpoint,7,0)
        assertEquals(UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.INPUT_PORT),connection.state.value)
        peer.inputThrows = true
        connection.connect(endpoint,0,0)
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.INPUT_PORT) }
        assertTrue(peer.deviceClosed)
    }

    @Test fun disconnectDoesNotWaitOnBlockedSenderAndClosesAtDeadline() {
        val (connection, peer) = connection()
        peer.blockSend = true
        open(connection)
        connection.send(PadChange(0,true))
        assertTrue(peer.entered.await(10,TimeUnit.SECONDS))
        connection.disconnect()
        assertEquals(UsbPeripheralConnection.State.Closing, connection.state.value)
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.SEND) }
        assertTrue(peer.deviceClosed)
    }

    @Test fun removedPendingEndpointCannotReopenAndDoesNotReplayEvents() {
        val (connection, peer) = connection()
        peer.defer = true
        connection.connect(endpoint,0,0)
        await { peer.completion != null }
        peer.removed!!(1)
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.REMOVED) }
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
        assertEquals(UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.TIMEOUT),connection.state.value)
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
        await { connection.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.SEND) }
        assertTrue(peer.inputClosed && peer.outputClosed && peer.deviceClosed)
    }

    @Test fun simultaneousConnectionsHaveExactlyOneOwner() {
        val first = connection().first
        val second = connection().first
        val start = CountDownLatch(1)
        val threads = listOf(first,second).map { connection ->
            Thread { start.await(); connection.connect(endpoint,0,0) }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join(10000); assertFalse(it.isAlive) }
        await { listOf(first,second).all { it.state.value is UsbPeripheralConnection.State.Open ||
            it.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.BUSY) } }
        assertEquals(1,listOf(first,second).count { it.state.value is UsbPeripheralConnection.State.Open })
        assertEquals(1,listOf(first,second).count { it.state.value == UsbPeripheralConnection.State.Failed(UsbPeripheralConnection.Failure.BUSY) })
    }

    @Test fun reconnectHasDistinctIdentityForLateReleases() {
        val (connection, peer) = connection()
        open(connection)
        val first = connection.state.value as UsbPeripheralConnection.State.Open
        connection.send(PadChange(0,true))
        await { peer.sent.size == 1 }
        connection.disconnect()
        await { connection.state.value == UsbPeripheralConnection.State.Idle }
        open(connection)
        val second = connection.state.value as UsbPeripheralConnection.State.Open
        connection.send(PadChange(0,true))
        await { peer.sent.size == 3 }
        assertNotEquals(first,second)
    }

    private fun connection(): Pair<UsbPeripheralConnection, FakeAccess> {
        val peer = FakeAccess()
        val connection = UsbPeripheralConnection(peer)
        peers.add(connection to peer)
        await { peer.removed != null }
        return connection to peer
    }
    private fun open(connection: UsbPeripheralConnection) {
        connection.connect(endpoint,0,0)
        await { connection.state.value == UsbPeripheralConnection.State.Open(true) }
    }
    private fun await(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Condition did not complete",check())
    }

    private class FakeAccess : PeripheralMidiAccess {
        @Volatile var completion: ((PeripheralMidiAccess.Device?) -> Unit)? = null
        @Volatile var removed: ((Int) -> Unit)? = null
        @Volatile var receive: ((ByteArray,Int,Int) -> Unit)? = null
        @Volatile var defer = false
        @Volatile var closeThrows = false
        @Volatile var noInput = false
        @Volatile var inputThrows = false
        @Volatile var noOutput = false
        @Volatile var outputThrows = false
        @Volatile var listThrows = false
        @Volatile var openThrows = false
        @Volatile var sendThrows = false
        @Volatile var blockSend = false
        @Volatile var inputClosed = false
        @Volatile var outputClosed = false
        @Volatile var deviceClosed = false
        @Volatile var inputsOpened = 0
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val sent = Collections.synchronizedList(mutableListOf<List<Int>>())
        val sendThreads = Collections.synchronizedSet(mutableSetOf<String>())
        override fun devices(): List<PeripheralDevice> {
            if (listThrows) throw SecurityException("list")
            return emptyList()
        }
        override fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable {
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
                        if (sendThrows) throw IOException("send")
                        if (blockSend) { entered.countDown(); unblock.await(10,TimeUnit.SECONDS) }
                        sendThreads.add(Thread.currentThread().name)
                        sent.add(bytes.map { it.toInt() and 255 })
                    }
                    override fun close() { inputClosed = true; unblock.countDown() }
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
