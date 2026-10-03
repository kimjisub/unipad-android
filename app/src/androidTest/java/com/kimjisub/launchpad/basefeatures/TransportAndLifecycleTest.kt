package com.kimjisub.launchpad.basefeatures

import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransportAndLifecycleTest : PlaybackScreenTest() {
    private fun hintedPad(): Int? = screen.onMain {
        var result: Int? = null
        for (index in 0 until 64) {
            val item = screen.vm().channelManager.get(index / 8, index % 8)
            if (item?.channel == com.kimjisub.launchpad.manager.ChannelManager.Channel.GUIDE && item.code > 0) {
                result = index
                break
            }
        }
        result
    }

    @Test fun autoplayStartsPausesResumesAndStopsThroughScreenControls() {
        screen.openPlay()
        screen.options()
        screen.clickText(R.string.autoPlay)
        screen.await("Autoplay did not start") { screen.onMain { screen.vm().isAutoPlayPlaying } && audio.plays.isNotEmpty() }
        screen.clickDescription(R.string.cd_autoplay_pause)
        screen.await("Autoplay did not pause") { screen.onMain { !screen.vm().isAutoPlayPlaying } }
        // Allow the progress callback already posted on the main queue to settle.
        screen.instrumentation.waitForIdleSync()
        SystemClock.sleep(100)
        val paused = screen.onMain { screen.vm().autoPlayProgress }
        val requests = audio.plays.size
        SystemClock.sleep(600)
        assertEquals(paused, screen.onMain { screen.vm().autoPlayProgress })
        assertEquals(requests, audio.plays.size)
        screen.clickDescription(R.string.cd_autoplay_play)
        screen.await("Autoplay did not resume") { screen.onMain { screen.vm().autoPlayProgress > paused } }
        screen.options()
        screen.clickText(R.string.autoPlay)
        screen.await("Selecting active mode did not stop autoplay") { screen.onMain { screen.vm().playMode == PlayMode.None && !screen.vm().autoPlayRunner!!.active } }
        assertFalse(screen.device.hasObject(By.desc(screen.text(R.string.cd_autoplay_pause))))
        screen.capture("autoplay-stopped")
    }

    @Test fun guideAndStepPracticeShowHintsAndStepWaitsForPadInput() {
        screen.openPlay()
        screen.options()
        screen.clickText(R.string.guidePlay)
        screen.await("Guide mode did not start") { screen.onMain { screen.vm().playMode == PlayMode.GuidePlay } }
        screen.await("Guide did not illuminate a pad") { hintedPad() != null }
        screen.capture("guide-hint")
        screen.options()
        screen.clickText(R.string.guidePlay)
        screen.await("Guide did not stop") { screen.onMain { screen.vm().playMode == PlayMode.None } }
        screen.options()
        screen.clickText(R.string.stepPractice)
        screen.await("Step mode did not start") { screen.onMain { screen.vm().playMode == PlayMode.StepPractice } }
        screen.await("Step hint did not appear") { hintedPad() != null }
        val hint = hintedPad()!!
        val before = screen.onMain { screen.vm().autoPlayProgress }
        SystemClock.sleep(300)
        assertEquals("Practice advanced without a press", before, screen.onMain { screen.vm().autoPlayProgress })
        screen.tapPad(hint)
        screen.await("Practice did not advance after the expected pad") { screen.onMain { screen.vm().autoPlayProgress > before } }
        screen.capture("step-practice")
    }

    @Test fun playingSurvivesHomeAndScreenLockWithoutLosingPackOrChain() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.openPlay()
        val process = Process.myPid()
        val vm = screen.vm()
        screen.onMain { vm.chain.value = 1 }
        screen.tapPad(0)
        assertEquals(audio.loaded.getValue("silence2.wav"), audio.plays.last())
        val original = screen.resumed()
        val silenceCount = audio.silences.get()
        // pressHome() waits only 1 second for an accessibility event, even if Home succeeded.
        // Require actual key injection and the launcher/lifecycle result under the existing deadline.
        // Resolve through the device shell: target package visibility can hide the real launcher
        // and make UiDevice.launcherPackageName return Settings' fallback home activity.
        val launcher = screen.device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        ).lineSequence().map(String::trim).lastOrNull { it.contains('/') }?.substringBefore('/')
        assertNotNull("Default launcher missing", launcher)
        assertTrue("Home key injection failed", screen.device.pressKeyCode(android.view.KeyEvent.KEYCODE_HOME))
        screen.await("Home did not hide play and show the launcher") {
            screen.onMain { !vm.screenVisible } && screen.device.currentPackageName == launcher
        }
        screen.await("Leaving play did not silence voices") { audio.silences.get() > silenceCount }
        screen.context.getSystemService(android.app.ActivityManager::class.java).appTasks
            .single { it.taskInfo?.taskId == original.taskId }.moveToFront()
        screen.await("Play activity did not resume") { screen.onMain { vm.screenVisible } }
        assertSame(original, screen.resumed())
        assertSame(vm, screen.vm())
        assertEquals(1, screen.onMain { vm.chain.value })
        screen.device.sleep()
        screen.await("Lock did not hide play") { screen.onMain { !vm.screenVisible } }
        screen.device.wakeUp()
        screen.device.executeShellCommand("wm dismiss-keyguard")
        screen.await("Unlock did not return to play") { screen.onMain { vm.screenVisible } }
        assertEquals(process, Process.myPid())
        assertSame(vm, screen.vm())
        assertEquals(1, screen.onMain { vm.chain.value })
        val before = audio.plays.size
        screen.tapPad(1)
        screen.await("No sound request after returning") { audio.plays.size == before + 1 }
        screen.capture("play-after-home-and-lock")
    }
}
