package com.kimjisub.launchpad.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.ComputerPadActivity
import com.kimjisub.launchpad.activity.ComputerPadTags
import com.kimjisub.launchpad.activity.cellDescription
import com.kimjisub.launchpad.ui.compose.PadGridGeometry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

/**
 * Longer runs of the computer pad screen against real Android MIDI ports of the test-only virtual
 * peer: repeated reconnection, every color on every light, a flood of lights, and the time inside
 * the phone from a touch to the MIDI send call. No cable or computer is involved, so this is not
 * USB testing, and the times measured on an emulator are the emulator's, not a phone's.
 */
@RunWith(AndroidJUnit4::class)
class ComputerPadPortLoadTest {
	@get:Rule val compose = createEmptyComposeRule()

	private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
	private val context: Context get() = instrumentation.targetContext
	private lateinit var manager: MidiManager
	private lateinit var info: MidiDeviceInfo
	private lateinit var observer: MidiDevice
	private lateinit var observed: AutoCloseable
	/** The peer's control port: lights from "the computer" and reports of what reached its press port. */
	private lateinit var control: MidiInputPort
	private val wire = PeerWire()
	private val originalFactory = ComputerPadActivity.connectionFactory
	private val created = mutableListOf<UsbPeripheralConnection>()
	private var scenario: ActivityScenario<ComputerPadActivity>? = null
	private val connection get() = created.single()

	@Suppress("DEPRECATION")
	@Before fun setup() {
		manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
		awaitTrue { manager.devices.any { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == PEER } }
		info = manager.devices.first { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == PEER }
		val ready = CountDownLatch(1)
		var device: MidiDevice? = null
		manager.openDevice(info, { device = it; ready.countDown() }, Handler(Looper.getMainLooper()))
		assertTrue(ready.await(15, TimeUnit.SECONDS))
		observer = device!!
		observed = AndroidMidiPorts(observer).output(0, wire::record)!!
		control = observer.openInputPort(1)!!
		control.send(byteArrayOf(0), 0, 1)
		awaitTrue { wire.since(0).contains(listOf(0xb0, 111, 0)) }
	}

	@After fun cleanup() {
		scenario?.close()
		ComputerPadActivity.connectionFactory = originalFactory
		awaitTrue { created.all { it.state.value !is UsbPeripheralConnection.State.Closing && !it.portsClosing.value } }
		if (::control.isInitialized) control.close()
		if (::observed.isInitialized) observed.close()
		if (::observer.isInitialized) observer.close()
	}

	/**
	 * Each round connects, taps one pad, holds another, receives one light and disconnects while the
	 * pad is held. Nothing of a round may reach the next: no light, and no press or release still
	 * carrying the ended connection, as a finger lifted late would.
	 */
	@Test fun tenReconnectionsCarryNoPressOrLightIntoTheNextRound() {
		launch()
		var ended: Pair<Long, Int>? = null
		repeat(ROUNDS) { round ->
			clickWhenEnabled(R.string.computer_pad_connect)
			val generation = awaitOpen()
			val start = wire.size()
			assertTrue("round $round: lights carried over: ${connection.lights.snapshot().toList()}", connection.lights.snapshot().all { it == 0 })
			awaitScreen("round $round opened", IntArray(Mk2Layout.SIZE).asMap())
			ended?.let { (oldGeneration, held) ->
				assertFalse("round $round: a late release of the last round was sent", connection.send(PadChange(held, false), oldGeneration))
				assertFalse("round $round: a late press of the last round was sent", connection.send(PadChange(held, true), oldGeneration))
			}
			val tapped = round
			val held = 20 + round
			val lit = 40 + round
			compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(tapped)); up(0); down(1, cell(held)) }
			computerSends(light(lit, LIGHT + round))
			awaitPressesSince(start, press(tapped), release(tapped), press(held))
			awaitScreen("round $round lit", mapOf(tapped to 0, held to 127, lit to LIGHT + round))

			clickWhenEnabled(R.string.computer_pad_disconnect)
			awaitTrue { connection.state.value == UsbPeripheralConnection.State.Idle && !connection.portsClosing.value }
			// Lifting the held finger now, and a light arriving after the ports closed, must reach no one.
			compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { up(1) }
			computerSends(light(lit + 1, LIGHT + round))
			awaitPressesSince(start, press(tapped), release(tapped), press(held), release(held))
			assertEquals("round $round: the app stopped", Lifecycle.State.RESUMED, scenario!!.state)
			ended = generation to held
		}
		clickWhenEnabled(R.string.computer_pad_connect)
		val last = awaitOpen()
		assertTrue(connection.lights.snapshot().all { it == 0 })
		ended!!.let { (oldGeneration, held) -> assertFalse(connection.send(PadChange(held, false), oldGeneration)) }
		Thread.sleep(SETTLE_MS)
		assertEquals("bytes at the press port", ROUNDS * 4L * 3, pressBytesAtPeer())
		println("Reconnected $ROUNDS times; last connection $last; presses ${presses().size}")
	}

	/** All 80 positions in every palette color through the real port, then every way to turn them off. */
	@Test fun everyPositionShowsEveryColorAndTurnsOffWithoutSendingAnything() {
		open()
		for (color in 0..127) {
			computerSends(*Array(Mk2Layout.SIZE) { light(it, color) })
			awaitScreen("color $color", IntArray(Mk2Layout.SIZE) { color }.asMap())
		}
		val off = IntArray(Mk2Layout.SIZE).asMap()
		computerSends(*Array(Mk2Layout.SIZE) { light(it, 5) })
		awaitScreen("before note off", IntArray(Mk2Layout.SIZE) { 5 }.asMap())
		// A note off turns a light off at any velocity, 127 included; the top buttons go off with CC value 0.
		computerSends(*Array(Mk2Layout.SIZE) { index ->
			val address = Mk2Layout.address(index)!!
			if (address.status == 0xb0) listOf(0xb0, address.number, 0) else listOf(0x80, address.number, 127 - index * 13 % 128)
		})
		awaitScreen("after note off", off)
		computerSends(*Array(Mk2Layout.SIZE) { light(it, 9) })
		awaitScreen("before note on 0", IntArray(Mk2Layout.SIZE) { 9 }.asMap())
		computerSends(*Array(Mk2Layout.SIZE) { light(it, 0) })
		awaitScreen("after note on 0", off)
		assertArrayEquals(IntArray(Mk2Layout.SIZE), connection.lights.snapshot())
		Thread.sleep(SETTLE_MS)
		assertEquals("lights were sent back to the computer", 0L, pressBytesAtPeer())
	}

	/**
	 * [FLOOD_MESSAGES] lights as fast as the port takes them. The main thread keeps answering within
	 * Android's five-second input limit, each position ends with its last color, and a press right
	 * after goes out.
	 */
	@Test fun aFloodOfLightsKeepsTheScreenAnsweringAndAPressRightAfterGoesOut() {
		open()
		val packets = FLOOD_MESSAGES / Mk2Layout.SIZE
		// Never 0 or 127, so no flood light reads as the peer's echo of a press or release.
		fun color(packet: Int, index: Int) = 1 + (packet * 7 + index) % 126
		val start = wire.size()
		val began = SystemClock.elapsedRealtimeNanos()
		val probe = MainThreadProbe()
		val sentMs: Long
		try {
			for (packet in 0 until packets) computerSends(*Array(Mk2Layout.SIZE) { light(it, color(packet, it)) })
			sentMs = (SystemClock.elapsedRealtimeNanos() - began) / 1_000_000
			awaitTrue(60) { wire.size() - start >= FLOOD_MESSAGES }
		} finally { probe.close() }
		val deliveredMs = (SystemClock.elapsedRealtimeNanos() - began) / 1_000_000
		println("Light flood: $FLOOD_MESSAGES messages sent in $sentMs ms, at the peer's other listener after $deliveredMs ms; " +
			"main thread answered ${probe.answered} probes, longest wait ${probe.longestMs} ms")
		assertTrue("the main thread never answered during the flood", probe.answered > 0)
		assertTrue("the main thread was blocked for ${probe.longestMs} ms", probe.longestMs < BLOCKED_MS)
		val last = IntArray(Mk2Layout.SIZE) { color(packets - 1, it) }
		awaitScreen("after the flood", last.asMap())
		assertArrayEquals(last, connection.lights.snapshot())
		Thread.sleep(SETTLE_MS)
		assertEquals("lights were sent back to the computer", 0L, pressBytesAtPeer())

		val pressStart = wire.size()
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(PRESSED)); up(0) }
		awaitPressesSince(pressStart, press(PRESSED), release(PRESSED))
		assertEquals(6L, pressBytesAtPeer())
	}

	/**
	 * Time inside the phone from the screen's window receiving a touch to the MIDI send call, both on
	 * the monotonic clock, for [pairs] press–release pairs at [PAIRS_PER_SECOND] a second. Only lost
	 * or reordered messages fail; the times are reported without a limit. Touches are handed to the
	 * window on the main thread, as the system's input dispatch does: injecting them through the
	 * system waits for the display after every event and cannot keep this rate. The figures leave out
	 * the touch screen and the system's dispatch, the cable and the computer; on an emulator they are
	 * the emulator's.
	 */
	@Test fun touchToSendCallTimesInsideThePhone() {
		val pairs = InstrumentationRegistry.getArguments().getString("computerPadLatencyPairs")?.toInt() ?: PAIRS
		val events = pairs * 2
		val touches = TimedLog(events)
		val sends = TimedLog(events)
		launch(sender = { port ->
			object : PeripheralMidiAccess.Sender by port {
				override fun send(bytes: ByteArray) {
					sends.add(SystemClock.elapsedRealtimeNanos(), bytes.map { it.toInt() and 255 })
					port.send(bytes)
				}
			}
		})
		clickWhenEnabled(R.string.computer_pad_connect)
		awaitOpen()
		compose.waitForIdle()
		lateinit var window: View
		scenario!!.onActivity { activity ->
			window = activity.window.decorView
			val original = activity.window.callback
			activity.window.callback = object : Window.Callback by original {
				override fun dispatchTouchEvent(event: MotionEvent): Boolean {
					touches.add(SystemClock.elapsedRealtimeNanos(), listOf(event.actionMasked))
					return original.dispatchTouchEvent(event)
				}
			}
		}
		val grid = gridInWindow()
		val main = Handler(Looper.getMainLooper())
		fun touch(downTime: Long, action: Int, x: Float, y: Float) = main.post {
			val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
			event.source = InputDevice.SOURCE_TOUCHSCREEN
			window.dispatchTouchEvent(event)
			event.recycle()
		}
		val interval = 1_000_000_000L / PAIRS_PER_SECOND
		val began = System.nanoTime()
		for (pair in 0 until pairs) {
			val (x, y) = grid.centre(pair % Mk2Layout.SIZE)
			waitUntil(began + pair * interval)
			val downTime = SystemClock.uptimeMillis()
			touch(downTime, MotionEvent.ACTION_DOWN, x, y)
			waitUntil(began + pair * interval + interval / 2)
			touch(downTime, MotionEvent.ACTION_UP, x, y)
		}
		val deliveredMs = (System.nanoTime() - began) / 1_000_000
		awaitProgress { sends.size() }
		Thread.sleep(SETTLE_MS)

		val report = TouchToSend.measure(touches.entries(), sends.entries(), pairs)
		val summary = buildString {
			val section = if (android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) "emulator internal section, not a phone figure" else "phone internal section"
			appendLine("Computer pad: window touch received -> MIDI send call ($section; not USB or computer time)")
			appendLine("device=${android.os.Build.MODEL} api=${android.os.Build.VERSION.SDK_INT} pairs=$pairs rate=${PAIRS_PER_SECOND}/s touches_handed_over_in=${deliveredMs}ms")
			append(report.describe())
		}
		println(summary)
		TestCaptures.save(UiDevice.getInstance(instrumentation), "computer_pad/touch_to_send.txt") { it.writeText(summary); true }
		assertEquals("touches received by the window", events, touches.size())
		assertEquals("messages not sent: ${report.missing}; out of order: ${report.outOfOrder}", 0, report.missing + report.outOfOrder)
		assertEquals("bytes at the press port", events * 3L, pressBytesAtPeer())
	}

	private fun launch(sender: (PeripheralMidiAccess.Sender) -> PeripheralMidiAccess.Sender = { it }) {
		ComputerPadActivity.connectionFactory = {
			UsbPeripheralConnection(PeerAccess(manager, info, sender = sender), MidiTransportGate()).also { created.add(it) }
		}
		scenario = ActivityScenario.launch(ComputerPadActivity::class.java)
	}

	private fun open() {
		launch()
		clickWhenEnabled(R.string.computer_pad_connect)
		awaitOpen()
		compose.waitForIdle()
	}

	private fun awaitOpen(): Long {
		awaitTrue { (connection.state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == true }
		return (connection.state.value as UsbPeripheralConnection.State.Open).generation
	}

	/** By semantics, so a finger the test keeps on the pads is not disturbed by a click on another pointer. */
	private fun clickWhenEnabled(text: Int) {
		val button = hasText(context.getString(text)) and hasClickAction() and isEnabled()
		compose.waitUntil(15_000) { compose.onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
		compose.onNode(button).performSemanticsAction(SemanticsActions.OnClick)
	}

	private fun light(index: Int, color: Int): List<Int> {
		val address = Mk2Layout.address(index)!!
		return listOf(address.status, address.number, color)
	}

	private fun press(index: Int) = Mk2Layout.message(index, true)!!.map { it.toInt() and 255 }
	private fun release(index: Int) = Mk2Layout.message(index, false)!!.map { it.toInt() and 255 }

	/** Sends messages to the app's light port as the computer would, in packets well below the port's limit. */
	private fun computerSends(vararg messages: List<Int>) {
		for (packet in messages.toList().chunked(Mk2Layout.SIZE)) {
			val bytes = byteArrayOf(4) + packet.flatten().map { it.toByte() }.toByteArray()
			control.send(bytes, 0, bytes.size)
		}
	}

	/** How many bytes reached the peer's press port since the reset in [setup]. */
	private fun pressBytesAtPeer(): Long {
		val from = wire.size()
		control.send(byteArrayOf(5), 0, 1)
		var report: List<Int>? = null
		awaitTrue { wire.since(from).lastOrNull { it[0] == 0xf0 }.also { report = it } != null }
		return report!!.subList(2, 7).fold(0L) { total, digit -> total * 128 + digit }
	}

	/** Echoes of what the screen sent: the peer's counter, its reports and lights not sent by the app are left out. */
	private fun presses(from: Int = 0) = wire.since(from).filter {
		it.size == 3 && !(it[0] == 0xb0 && it[1] == 111) && it[2] in setOf(0, 127)
	}

	private fun awaitPressesSince(from: Int, vararg expected: List<Int>) {
		awaitTrue { presses(from).size >= expected.size }
		Thread.sleep(SETTLE_MS)
		assertEquals(expected.toList(), presses(from))
	}

	/** Each light as the screen reader is told it, which is the color the pad is drawn with. */
	private fun shownDifferences(expected: Map<Int, Int>): Map<Int, Int> {
		val shown = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
			.fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.toSet()
		return expected.filter { (index, color) -> cellDescription(context.resources, index, color) !in shown }
	}

	private fun awaitScreen(what: String, expected: Map<Int, Int>) {
		try {
			compose.waitUntil(15_000) { shownDifferences(expected).isEmpty() }
		} catch (_: ComposeTimeoutException) {
			fail("$what: the screen does not show the expected light at ${shownDifferences(expected)} (expected colors); " +
				"connection lights ${connection.lights.snapshot().toList()}")
		}
	}

	private fun IntArray.asMap(): Map<Int, Int> = indices.associateWith { this[it] }

	/** Cell centres in the window, for touches handed to it outside Compose's test input. */
	private class GridInWindow(val left: Float, val top: Float, val side: Float) {
		fun centre(index: Int): Pair<Float, Float> {
			val (row, column) = PadGridGeometry.position(index)
			val pitch = side / PadGridGeometry.CELLS
			return (left + (column + 0.5f) * pitch) to (top + (row + 0.5f) * pitch)
		}
	}

	private fun gridInWindow(): GridInWindow {
		val bounds = compose.onNodeWithTag(ComputerPadTags.GRID).fetchSemanticsNode().boundsInWindow
		return GridInWindow(bounds.left, bounds.top, bounds.width)
	}

	private fun waitUntil(nanos: Long) {
		while (true) {
			val left = nanos - System.nanoTime()
			if (left <= 0) return
			LockSupport.parkNanos(left)
		}
	}

	/** Waits while a count still grows, so a long backlog is not cut off by a fixed limit. */
	private fun awaitProgress(count: () -> Int) {
		var last = -1
		while (count() != last) {
			last = count()
			Thread.sleep(2_000)
		}
	}

	private fun awaitTrue(seconds: Long = 15, condition: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
		while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
		assertTrue("Condition did not complete; last peer messages ${wire.since(maxOf(0, wire.size() - 20))}", condition())
	}

	private companion object {
		const val PEER = "Peripheral port test peer"
		const val ROUNDS = 10
		/** Light colors used in the reconnection rounds, apart from an echo's 0 and 127. */
		const val LIGHT = 30
		/** A burst of lights 500 times over the whole surface. */
		const val FLOOD_MESSAGES = 40_000
		/** Android reports an app not responding when input waits this long. */
		const val BLOCKED_MS = 5_000L
		const val PRESSED = 27
		const val PAIRS = 10_000
		const val PAIRS_PER_SECOND = 100
		/** Time for a message that should not arrive to show up before it is counted as absent. */
		const val SETTLE_MS = 300L
	}
}

/** The peer's output as whole messages: three-byte channel messages and its SysEx reports. */
private class PeerWire {
	private val messages = ArrayList<List<Int>>()
	private val partial = ArrayList<Int>()

	@Synchronized fun record(bytes: ByteArray, offset: Int, count: Int) {
		for (i in offset until offset + count) {
			val byte = bytes[i].toInt() and 255
			if (byte >= 0x80 && byte != 0xf7) partial.clear()
			partial.add(byte)
			val complete = if (partial[0] == 0xf0) byte == 0xf7 else partial.size == 3
			if (complete) { messages.add(partial.toList()); partial.clear() }
		}
	}

	@Synchronized fun size() = messages.size
	@Synchronized fun since(from: Int): List<List<Int>> = messages.subList(from, messages.size).toList()
}

/** Fixed-size and lock-free on the recording side, so recording adds little to what it measures. */
private class TimedLog(capacity: Int) {
	private val times = LongArray(capacity)
	private val values = arrayOfNulls<List<Int>>(capacity)
	private val next = AtomicInteger()

	fun add(nanos: Long, value: List<Int>) {
		val slot = next.getAndIncrement()
		if (slot < times.size) { times[slot] = nanos; values[slot] = value }
	}

	fun size() = next.get()
	fun entries(): List<Pair<Long, List<Int>>> = (0 until minOf(next.get(), times.size)).map { times[it] to values[it]!! }
}

/** Posts to the main thread every 10 ms and keeps the longest wait for an answer. */
private class MainThreadProbe : AutoCloseable {
	@Volatile private var running = true
	@Volatile var longestMs = 0L
		private set
	@Volatile var answered = 0
		private set
	private val handler = Handler(Looper.getMainLooper())
	private val worker = thread(name = "main-thread-probe") {
		while (running) {
			val posted = SystemClock.uptimeMillis()
			val done = CountDownLatch(1)
			handler.post { done.countDown() }
			val ok = done.await(10, TimeUnit.SECONDS)
			longestMs = maxOf(longestMs, SystemClock.uptimeMillis() - posted)
			if (!ok) break
			answered++
			Thread.sleep(10)
		}
	}

	override fun close() {
		running = false
		worker.join()
	}
}

/** Pairs each touch the window received with the send call it caused, in order. */
internal object TouchToSend {
	class Report(val latenciesNanos: LongArray, val missing: Int, val outOfOrder: Int) {
		private fun percentile(p: Double): Long =
			latenciesNanos.sortedArray().let { if (it.isEmpty()) -1 else it[(Math.ceil(p * it.size).toInt() - 1).coerceIn(0, it.size - 1)] }

		fun describe() = buildString {
			fun ms(nanos: Long) = if (nanos < 0) "none" else "%.3f ms".format(nanos / 1_000_000.0)
			appendLine("measured=${latenciesNanos.size} missing=$missing out_of_order=$outOfOrder")
			appendLine("median=${ms(percentile(0.5))} p95=${ms(percentile(0.95))} p99=${ms(percentile(0.99))} max=${ms(latenciesNanos.maxOrNull() ?: -1)}")
		}
	}

	/** Touch i is press or release of pad i / 2 modulo 80; send i must be exactly that message. */
	fun measure(touches: List<Pair<Long, List<Int>>>, sends: List<Pair<Long, List<Int>>>, pairs: Int): Report {
		val latencies = ArrayList<Long>(touches.size)
		var outOfOrder = 0
		for (i in 0 until minOf(touches.size, sends.size)) {
			val down = touches[i].second.single() == MotionEvent.ACTION_DOWN
			val expected = Mk2Layout.message(i / 2 % Mk2Layout.SIZE, down)!!.map { it.toInt() and 255 }
			if (sends[i].second != expected || down != (i % 2 == 0)) outOfOrder++
			else latencies += sends[i].first - touches[i].first
		}
		return Report(latencies.toLongArray(), maxOf(0, pairs * 2 - sends.size), outOfOrder)
	}
}
