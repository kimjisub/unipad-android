package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.driver.Matrix
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualMidiConnectionTest {
	private class Port : VirtualMidiBackend.Port {
		var closed = 0
		val sent = mutableListOf<ByteArray>()
		override fun send(bytes: ByteArray) { sent.add(bytes) }
		override fun close() { closed++ }
	}
	private class Device : VirtualMidiBackend.Device {
		val input = Port()
		val output = Port()
		var busy = false
		var inputBusy = false
		var alive: suspend () -> Boolean = { true }
		override suspend fun isAlive() = alive()
		var receive: ((ByteArray, Int, Int) -> Unit)? = null
		var closed = 0
		override fun openPerformancePort(number: Int, receiver: (ByteArray, Int, Int) -> Unit): VirtualMidiBackend.Port? {
			assertEquals(1, number)
			receive = receiver
			return if (inputBusy) null else output
		}
		override fun openLightPort(number: Int): VirtualMidiBackend.Port? {
			assertEquals(0, number)
			return if (busy) null else input
		}
		override fun close() { closed++ }
	}
	private class Backend : VirtualMidiBackend {
		val opened = CompletableDeferred<VirtualMidiBackend.Device?>()
		var removed: ((Int) -> Unit)? = null
		var listingFailure = false
		override suspend fun devices(): List<VirtualMidiDevice> {
			if (listingFailure) throw SecurityException("MIDI service unavailable")
			return listOf(descriptor)
		}
		override suspend fun open(id: Int) = opened.await()
		override fun watchRemoval(callback: (Int) -> Unit): AutoCloseable {
			removed = callback
			return AutoCloseable { removed = null }
		}
	}

	@Test fun deadlineAndCancelDoNotPublishOrRetainGate() = runTest {
		val backend = Backend()
		val gate = MidiTransportGate()
		val dispatcher = StandardTestDispatcher(testScheduler)
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> error("Must not attach") }
		assertTrue(connection.connect(selection, Matrix()))
		runCurrent()
		advanceTimeBy(10_000)
		runCurrent()
		assertEquals(VirtualMidiConnection.Failure.TIMEOUT, (connection.state as VirtualMidiConnection.State.Failed).reason)
		assertNotNull(gate.acquireUsb()?.also { it.close() })
		assertTrue(connection.connect(selection, Matrix()))
		runCurrent()
		connection.disconnect()
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Idle, connection.state)
		assertNull(backend.removed)
	}

	@Test fun lightsNeedAnExplicitDecisionAndDisconnectClosesEveryResourceOnce() = runTest {
		val backend = Backend()
		val device = Device().apply { busy = true }
		backend.opened.complete(device)
		val dispatcher = StandardTestDispatcher(testScheduler)
		var attached = 0
		var detached = 0
		val gate = MidiTransportGate()
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,_ ->
			attached++
			AutoCloseable { detached++ }
		}
		assertTrue(connection.connect(selection, Matrix()))
		runCurrent()
		assertEquals(VirtualMidiConnection.State.LightUnavailable, connection.state)
		assertEquals(0, attached)
		assertNull(gate.acquireUsb())
		assertFalse(connection.connect(selection, Matrix()))
		connection.continueWithoutLights()
		runCurrent()
		assertEquals(1, attached)
		assertEquals(VirtualMidiConnection.State.Connected(false), connection.state)
		backend.removed!!(99)
		runCurrent()
		assertEquals(0, detached)
		backend.removed!!(7)
		runCurrent()
		connection.disconnect()
		runCurrent()
		assertEquals(1, detached)
		assertEquals(1, device.closed)
		assertEquals(1, device.output.closed)
		assertEquals(0, device.input.closed)
		assertNotNull(gate.acquireUsb()?.also { it.close() })
	}

	@Test fun virtualConnectIsRefusedWhileAComputerConnectionHoldsTheTransport() = runTest {
		val gate = MidiTransportGate()
		val peripheral = gate.acquirePeripheral()!!
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = Backend()
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> error("Must not attach") }
		assertFalse(connection.connect(selection, Matrix()))
		runCurrent()
		assertEquals(VirtualMidiConnection.Failure.BUSY, (connection.state as VirtualMidiConnection.State.Failed).reason)
		assertNull(backend.removed)
		assertNull(gate.acquireUsb())
		peripheral.close()
		assertNotNull(gate.acquireVirtual()?.also { it.close() })
	}

	@Test fun virtualAndUsbReservationsAreMutuallyExclusive() {
		val gate = MidiTransportGate()
		val usb1 = gate.acquireUsb()!!
		val usb2 = gate.acquireUsb()!!
		assertNull(gate.acquireVirtual())
		usb1.close(); usb1.close()
		assertNull(gate.acquireVirtual())
		usb2.close()
		val virtual = gate.acquireVirtual()!!
		assertNull(gate.acquireVirtual())
		assertNull(gate.acquireUsb())
		virtual.close()
		assertNotNull(gate.acquireUsb()?.also { it.close() })
	}


	@Test fun immediateCancelStillReleasesReservation() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val gate = MidiTransportGate()
		val connection = VirtualMidiConnection(Backend(), backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> error("Must not attach") }
		connection.connect(selection, Matrix())
		connection.disconnect()
		runCurrent()
		assertNotNull(gate.acquireUsb()?.also { it.close() })
		assertEquals(VirtualMidiConnection.State.Idle, connection.state)
	}

	@Test fun inputPortFailureAndNullDeviceNeverAttach() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		for (device in listOf(null, Device().apply { inputBusy = true })) {
			val backend = Backend().apply { opened.complete(device) }
			val gate = MidiTransportGate()
			val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> error("Must not attach") }
			connection.connect(selection, Matrix())
			runCurrent()
			assertEquals(if (device == null) VirtualMidiConnection.Failure.UNAVAILABLE else VirtualMidiConnection.Failure.INPUT_PORT,
				(connection.state as VirtualMidiConnection.State.Failed).reason)
			assertNull(backend.removed)
			assertNotNull(gate.acquireUsb()?.also { it.close() })
			if (device != null) assertEquals(1, device.closed)
		}
	}

	@Test fun noLightPortSendsNothingAndClosesOnlyThePerformancePort() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = Backend()
		val device = Device()
		backend.opened.complete(device)
		val gate = MidiTransportGate()
		var sender: com.kimjisub.launchpad.midi.driver.DriverRef.OnSendSignalListener? = null
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,output ->
			sender = output
			AutoCloseable { }
		}
		connection.connect(selection.copy(lightPort = null), Matrix())
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Connected(false), connection.state)
		sender!!.onSend(9, 0x91.toByte(), 36, 127)
		runCurrent()
		assertTrue(device.input.sent.isEmpty())
		device.receive!!(byteArrayOf(0x91.toByte(),36,100),0,3)
		connection.disconnect()
		runCurrent()
		assertEquals(0, device.input.closed)
		assertEquals(1, device.output.closed)
	}

	@Test fun outputIsRawMidiAndALightBurstKeepsTheSessionWithItsNewestLights() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = Backend()
		val device = Device()
		backend.opened.complete(device)
		var sender: com.kimjisub.launchpad.midi.driver.DriverRef.OnSendSignalListener? = null
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, MidiTransportGate()) { _,_,output ->
			sender = output
			AutoCloseable { }
		}
		connection.connect(selection, Matrix())
		runCurrent()
		sender!!.onSend(9, 0x91.toByte(), 36, 127)
		runCurrent()
		assertArrayEquals(byteArrayOf(0x91.toByte(),36,127), device.input.sent.single())
		device.input.sent.clear()
		// More than the queue holds while the writer cannot run.
		repeat(600) { sender.onSend(9, 0x91.toByte(), (36 + it % 64).toByte(), (it % 128).toByte()) }
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Connected(true), connection.state)
		assertEquals(0, device.closed)
		val newest = (344 until 600).map { listOf(0x91.toByte(), (36 + it % 64).toByte(), (it % 128).toByte()) }
		assertEquals("The newest lights must arrive in order", newest, device.input.sent.map { it.toList() }.takeLast(256))
		connection.disconnect()
		runCurrent()
		assertEquals(1, device.closed)
	}

	@Test fun attachTimeRedrawLongerThanTheQueueKeepsTheSession() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = Backend()
		val device = Device()
		backend.opened.complete(device)
		// The play screen redraws every pad and the ring inside attach, before attach returns.
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, MidiTransportGate()) { _,_,output ->
			repeat(400) { output.onSend(9, 0x91.toByte(), (36 + it % 64).toByte(), (it % 128).toByte()) }
			AutoCloseable { }
		}
		connection.connect(selection, Matrix())
		runCurrent()
		advanceTimeBy(5_000)
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Connected(true), connection.state)
		assertEquals(listOf(0x91.toByte(), (36 + 399 % 64).toByte(), (399 % 128).toByte()), device.input.sent.last().toList())
		connection.disconnect()
		runCurrent()
	}

	@Test fun enumerationFailureDoesNotOpenOrChangeAConnection() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = Backend().apply { listingFailure = true }
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, MidiTransportGate()) { _,_,_ -> error("Must not attach") }
		assertTrue(connection.devices().isFailure)
		assertEquals(VirtualMidiConnection.State.Idle, connection.state)
		assertNull(backend.removed)
	}

	@Test fun cancellationFromTheConnectingObserverCannotStartALateSession() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val gate = MidiTransportGate()
		val connection = VirtualMidiConnection(Backend(), backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> error("Must not attach") }
		connection.onStateChanged = { if (it == VirtualMidiConnection.State.Connecting) connection.disconnect() }
		connection.connect(selection,Matrix())
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Idle,connection.state)
		assertNotNull(gate.acquireUsb()?.also { it.close() })
	}

	@Test fun cancellationAcrossRealDispatchersClosesPortsBeforeReleasingGate() {
		val mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		val ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
		val gate = MidiTransportGate()
		val device = Device()
		val backend = Backend().apply { opened.complete(device) }
		val detached = AtomicInteger()
		val cleanupEntered = CountDownLatch(1)
		val allowCleanup = CountDownLatch(1)
		val connection = VirtualMidiConnection(backend,scope,mainDispatcher,ioDispatcher,gate) { _,_,_ -> AutoCloseable {
			detached.incrementAndGet()
			cleanupEntered.countDown()
			check(allowCleanup.await(5,TimeUnit.SECONDS)) { "Cleanup gate not opened" }
		} }
		try {
			runBlocking {
				withContext(mainDispatcher) { assertTrue(connection.connect(selection,Matrix())) }
				withTimeout(5_000) {
					while (withContext(mainDispatcher) { connection.state !is VirtualMidiConnection.State.Connected }) delay(5)
				}
				withContext(mainDispatcher) { connection.disconnect() }
				assertTrue("Cleanup did not reach the main dispatcher",cleanupEntered.await(5,TimeUnit.SECONDS))
				// This IO barrier proves the session suspended before the main cleanup returns.
				withContext(ioDispatcher) { }
				allowCleanup.countDown()
				try {
					withTimeout(5_000) {
						while (gate.acquireUsb()?.let { it.close(); false } != false) delay(5)
					}
				} catch (error: TimeoutCancellationException) {
					throw AssertionError("Cleanup stopped: detach=${detached.get()}, performance=${device.output.closed}, lights=${device.input.closed}, device=${device.closed}; gate still reserved",error)
				}
				assertEquals(1,detached.get())
				assertEquals(1,device.output.closed)
				assertEquals(1,device.input.closed)
				assertEquals(1,device.closed)
			}
		} finally {
			allowCleanup.countDown()
			runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
			mainDispatcher.close()
			ioDispatcher.close()
		}
	}

	@Test fun delayedStatusIsUnknownUntilTheDeviceReportsItHasClosed() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val device = Device().apply { alive = { delay(3_000); true } }
		val backend = Backend().apply { opened.complete(device) }
		val gate = MidiTransportGate()
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> AutoCloseable { } }
		connection.connect(selection, Matrix())
		runCurrent()
		advanceTimeBy(8_000)
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Connected(true), connection.state)
		assertEquals(0, device.closed)
		assertNull(gate.acquireUsb())
		device.alive = { false }
		advanceTimeBy(4_000)
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Failed(VirtualMidiConnection.Failure.DISCONNECTED), connection.state)
		assertEquals(1, device.closed)
	}

	@Test fun missingMidiServiceReportsUnavailable() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = object : VirtualMidiBackend {
			override suspend fun devices() = emptyList<VirtualMidiDevice>()
			override suspend fun open(id: Int): VirtualMidiBackend.Device? = null
			override fun watchRemoval(callback: (Int) -> Unit): AutoCloseable = error("MIDI service unavailable")
		}
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, MidiTransportGate()) { _,_,_ -> error("Must not attach") }
		connection.connect(selection, Matrix())
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Failed(VirtualMidiConnection.Failure.UNAVAILABLE), connection.state)
	}

	@Test fun disconnectPublishesIdleOnceEvenWhenTheFinalLightWriteFails() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val backend = Backend()
		val device = object : VirtualMidiBackend.Device {
			override fun openPerformancePort(number: Int, receiver: (ByteArray, Int, Int) -> Unit) = Port()
			override fun openLightPort(number: Int) = object : VirtualMidiBackend.Port {
				override fun send(bytes: ByteArray) = error("Peer closed during all-off")
				override fun close() { }
			}
			override fun close() { }
		}
		backend.opened.complete(device)
		val states = mutableListOf<VirtualMidiConnection.State>()
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, MidiTransportGate()) { _,_,output ->
			AutoCloseable { output.onSend(9, 0x91.toByte(), 36, 0) }
		}
		connection.onStateChanged = { states.add(it) }
		connection.connect(selection, Matrix())
		runCurrent()
		connection.disconnect()
		runCurrent()
		assertEquals(VirtualMidiConnection.State.Idle, connection.state)
		assertEquals(listOf(VirtualMidiConnection.State.Connecting, VirtualMidiConnection.State.Connected(true), VirtualMidiConnection.State.Idle), states)
	}

	@Test fun terminalObserversCanReconnectAfterEveryTeardownPath() = runTest {
		for (ending in listOf("disconnect", "cancel", "timeout", "input failure", "peer exit", "overflow")) {
			val dispatcher = StandardTestDispatcher(testScheduler)
			val device = Device().apply { inputBusy = ending == "input failure" }
			val backend = Backend()
			if (ending != "timeout" && ending != "cancel") backend.opened.complete(device)
			val gate = MidiTransportGate()
			val terminalReadiness = mutableListOf<Boolean>()
			val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, gate) { _,_,_ -> AutoCloseable { } }
			connection.onStateChanged = { state ->
				if (state == VirtualMidiConnection.State.Idle || state is VirtualMidiConnection.State.Failed) {
					terminalReadiness.add(connection.canConnect)
					assertNull("$ending must remove the device watcher before notifying", backend.removed)
					assertNotNull("$ending must release the transport before notifying", gate.acquireUsb()?.also { it.close() })
				}
			}
			assertTrue(connection.connect(selection, Matrix()))
			runCurrent()
			when (ending) {
				"disconnect", "cancel" -> connection.disconnect()
				"timeout" -> advanceTimeBy(10_000)
				"peer exit" -> backend.removed!!(descriptor.id)
				// Input the main thread cannot keep up with; lights drop their oldest writes instead.
				"overflow" -> repeat(258) { device.receive!!(byteArrayOf(0x91.toByte(), 36, 100), 0, 3) }
			}
			runCurrent()
			assertEquals("$ending must notify exactly once, ready to reconnect", listOf(true), terminalReadiness)
			connection.onStateChanged = null
			assertTrue("$ending must allow reconnection", connection.connect(selection, Matrix()))
			connection.disconnect()
			runCurrent()
		}
	}

	private companion object {
		val descriptor = VirtualMidiDevice(7, "Matrix", "Mystrix", "test.provider", listOf(VirtualMidiPort(1,"Pads")), listOf(VirtualMidiPort(0,"Lights")))
		val selection = VirtualMidiSelection(descriptor, 1, 0)
	}
}
