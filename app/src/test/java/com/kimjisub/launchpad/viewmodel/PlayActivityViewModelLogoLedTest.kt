package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.LedRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * A keyLED `l` line, from the pack file to the bytes a Launchpad X receives: parser, LED runner, view
 * model and driver are the real ones. The fake activity sends a circle LED the way PlayActivity's
 * setLedLaunchpadChain does, the channel's current code or 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelLogoLedTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val main = FakeMainDispatcher()
	private val clock = AtomicLong(1000)
	private lateinit var vm: PlayActivityViewModel
	private lateinit var runner: LedRunner
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

	private val driver = LaunchpadX()
	/** Messages the Launchpad received, as hex. */
	private val sent = mutableListOf<String>()

	@Before
	fun setUp() {
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
		driver.setOnSendSignalListener(object : DriverRef.OnSendSignalListener {
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
				sent += byteArrayOf(cmd, sig, note, velocity).joinToString(" ") { "%02X".format(it) }
			}

			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {}
		})
		vm = PlayActivityViewModel(mockk<UnipackRepository>())
		vm.channelManager = ChannelManager(8, 8)
		vm.chain.range = 0 until 1
		vm.uiCallback = FakeActivity()
	}

	@After
	fun tearDown() {
		runner.stop()
		unmockkStatic(SystemClock::class)
		Dispatchers.resetMain()
	}

	private inner class FakeActivity : PlayActivityViewModel.UiCallback {
		override fun setLedPad(x: Int, y: Int) {
			driver.sendPadLed(x, y, vm.channelManager.get(x, y)?.code ?: 0)
		}

		override fun setLedChain(c: Int) {
			driver.sendFunctionKeyLed(c, vm.channelManager.get(-1, c)?.code ?: 0)
		}

		override fun updateTraceLogOverlay() {}
		override fun showToast(resId: Int) {}
		override fun finishActivity() {}
		override fun copyToClipboard(text: String) {}
		override fun setChainViewVisibility(index: Int, visibility: Int) {}
		override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {}
		override fun stopGuideAnimation(x: Int, y: Int) {}
		override fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int) {}
		override fun onRequestRelayout() {}
	}

	/** A pack whose pad (1,1) plays [keyLed], [loop] times. */
	private fun play(keyLed: String, loop: Int = 1) {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "keyLed").mkdir()
		File(root, "keyLed/1 1 1 $loop").writeText(keyLed)
		val pack: UniPack = UniPackFolder(root).load().loadDetail()
		vm.unipack = pack
		runner = LedRunner(pack, vm.ledRunnerListener, vm.chain, 3_600_000L)
		vm.ledRunner = runner
		runner.launch()
		Thread.sleep(150)
		runner.eventOn(0, 0)
		// The first tick only takes the press in; the second plays it.
		tick(clock.get())
		tick(clock.get())
	}

	private fun tick(t: Long) {
		clock.set(t)
		Thread { loopMethod.invoke(runner) }.apply { start() }.join()
		main.runAll()
	}

	@Test
	fun logoOnAndOff_reachTheLaunchpadAsCC99() {
		play("o l a 5\nd 100\nf l\n")
		assertEquals(listOf("1B B0 63 05"), sent)

		tick(1100)
		assertEquals(listOf("1B B0 63 05", "1B B0 63 00"), sent)
	}

	@Test
	fun padAndRoundLines_stillReachTheirOwnButtons() {
		play("o 1 1 a 3\no mc 1 a 9\no l a 5\n")
		assertEquals(listOf("19 90 51 03", "1B B0 5B 09", "1B B0 63 05"), sent)
	}

	@Test
	fun roundLed33_doesNotLightTheLogo() {
		play("o mc 33 a 5\n")
		assertEquals(emptyList<String>(), sent)
	}

	@Test
	fun ledInit_turnsALitLogoOff() {
		// Stopping autoplay, leaving the screen and turning LED off all reset the LEDs through ledInit.
		play("o l a 5\n", loop = 0)
		sent.clear()

		vm.ledInit()

		assertEquals("1B B0 63 00", sent.single { it.startsWith("1B B0 63") })
	}

	@Test
	fun proLightModeOff_keepsTheLogoDark_likeIos() {
		vm.channelManager.setCirIgnore(ChannelManager.Channel.LED, true)
		play("o l a 5\n")
		assertEquals(listOf("1B B0 63 00"), sent)
	}
}
