package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Transport and background return through the real play screen, with silent decoded requests. */
class GuideContinuationTest : PlaybackScreenTest() {
    private fun open(script: String) {
        File(screen.pack(), "autoPlay").writeText(script)
        screen.openPlay()
    }

    private fun guides(): Set<Int> = screen.onMain {
        (0..2).filter { screen.vm().channelManager.get(0, it)?.channel == Channel.GUIDE }.toSet()
    }

    @Test fun switchingFromAutoplayDoesNotGuideAnAlreadyPlayedPad() {
        open("on 1 1\nd 1000\non 1 2\nd 60000\n")
        val vm = screen.vm()
        val hinted = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Int>>()
        screen.onMain {
            val actual = checkNotNull(vm.uiCallback)
            vm.uiCallback = object : com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.UiCallback by actual {
                override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {
                    hinted += x to y
                    actual.startGuideAnimation(x, y, targetWallTimeMs)
                }
            }
            vm.switchPlayMode(PlayMode.AutoPlay)
        }
        screen.await("First autoplay note missing") { audio.plays.size == 1 }
        screen.onMain {
            vm.autoPlayRunner!!.progressOffset(1 - vm.autoPlayRunner!!.progress)
            vm.switchPlayMode(PlayMode.GuidePlay)
        }
        screen.await("The unprocessed note did not get its guide") { 0 to 1 in hinted }
        screen.capture("guide-transition-processed-note")
        assertEquals("Only the unprocessed note is owed", listOf(0 to 1), hinted.toList())
    }

    @Test fun pausingTheFinalGuideWaitKeepsPracticeUntilResume() {
        open("d 1000\ntouch 1 1\nd 6000\n")
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.GuidePlay) }
        screen.await("The final delay did not start") { screen.onMain { vm.autoPlayRunner!!.progress == 4 } }
        screen.clickDescription(R.string.cd_autoplay_pause)
        SystemClock.sleep(300)
        val active = screen.onMain { vm.autoPlayRunner!!.active }
        screen.capture("guide-final-wait-paused")
        assertTrue("Pausing must preserve the final wait", active)
        screen.clickDescription(R.string.cd_autoplay_play)
        screen.await("The final wait did not end after resume") { screen.onMain { !vm.autoPlayRunner!!.active } }
        screen.capture("guide-final-wait-finished")
    }

    @Test fun onePlayAfterBackgroundingAPausedGuideStartsTimedGuidance() {
        open("d 10000\ntouch 1 1\nd 60000\n")
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.GuidePlay) }
        screen.clickDescription(R.string.cd_autoplay_pause)
        screen.await("Paused step guide missing") { guides() == setOf(0) }
        val activity = screen.resumed()
        screen.capture("paused-guide-before-background")
        screen.goHome { screen.onMain { vm.autoPlayRunner!!.suspended && !vm.screenVisible } }
        screen.bringToFront(activity)
        screen.await("Play screen did not return") { screen.onMain { vm.screenVisible } }
        screen.clickDescription(R.string.cd_autoplay_play)
        SystemClock.sleep(150)
        screen.capture("paused-guide-one-play-after-background")
        screen.onMain {
            assertEquals(PlayMode.GuidePlay, vm.playMode)
            assertTrue(vm.isAutoPlayPlaying)
            assertTrue(vm.autoPlayRunner!!.practiceGuide)
            assertTrue(!vm.autoPlayRunner!!.stepMode)
        }
    }
}
