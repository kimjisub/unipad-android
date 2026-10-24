package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import com.kimjisub.design.view.ChainView
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.Companion.CHAIN_INDEX_OFFSET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** The real screen and controller callbacks must preserve a later explicit chain selection. */
class ManualChainSelectionTest : PlaybackScreenTest() {
    private fun checkSelection(name: String, select: (PlayActivity) -> Unit) {
        val pack = screen.pack()
        val info = File(pack, "info")
        info.writeText(info.readText().replace(Regex("(?m)^chain=.*$"), "chain=3"))
        File(pack, "keySound").writeText("1 1 1 silence.wav 1 2\n")
        screen.openPlay()
        val vm = screen.vm()
        val chains = CopyOnWriteArrayList<Int>()
        val observe: (Int, Int) -> Unit = { c, _ -> chains += c }
        vm.chain.addObserver(observe)
        try {
            val elapsed = screen.onMain {
                val started = SystemClock.elapsedRealtime()
                vm.padTouch(0, 0, true)
                select(screen.resumed() as PlayActivity)
                assertEquals("The manual selection must take effect immediately", 2, vm.chain.value)
                SystemClock.elapsedRealtime() - started
            }
            SystemClock.sleep(250)
            screen.capture(name)
            assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") {
                it.writeText("selection_elapsed_ms=$elapsed\nchain_changes=${chains.joinToString()}\nfinal_chain=${screen.onMain { vm.chain.value }}\nsound_requests=${audio.plays.size}\n")
                true
            })
            assertTrue("The selection must be within the pending move's 100 ms", elapsed < 100)
            assertEquals("A pending pad move must not override the later choice", 2, screen.onMain { vm.chain.value })
            assertEquals(listOf(2), chains.toList())
        } finally {
            vm.chain.removeObserver(observe)
        }
    }

    @Test fun aScreenChainButtonOverridesThePendingPadMove() {
        checkSelection("manual-screen-chain") { activity ->
            assertTrue(chainView(activity).click())
        }
    }

    private fun chainView(activity: PlayActivity): ChainView {
        @Suppress("UNCHECKED_CAST")
        val views = PlayActivity::class.java.getDeclaredField("chainViews")
            .apply { isAccessible = true }.get(activity) as Array<ChainView?>
        return requireNotNull(views[CHAIN_INDEX_OFFSET + 2])
    }

    @Test fun aKeyboardChainSelectionOverridesThePendingPadMove() {
        checkSelection("manual-keyboard-chain") { activity ->
            val button = chainView(activity).findViewById<View>(com.kimjisub.design.R.id.touchSpace)
            assertTrue("The chain button must accept keyboard focus", button.requestFocusFromTouch())
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        }
    }

    @Test fun aControllerChainKeyOverridesThePendingPadMove() {
        checkSelection("manual-controller-chain") { activity ->
            val controller = PlayActivity::class.java.getDeclaredField("midiController")
                .apply { isAccessible = true }.get(activity) as MidiController
            controller.onChainTouch(2, true)
        }
    }
}
