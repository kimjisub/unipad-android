package com.kimjisub.launchpad.basefeatures

import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.Test
import org.junit.runner.RunWith

/** Host-driven observation, not part of any suite: the host rings and hangs up with `adb emu gsm`. Run with scripts/run-incoming-call-probe.sh. */
@RunWith(AndroidJUnit4::class)
class IncomingCallProbe : HeldPadTest() {
    @Test fun heldPadThroughCallPadMode() = heldThroughCall(slide = false)

    @Test fun heldPadThroughCallSlideMode() = heldThroughCall(slide = true)

    @Test fun autoplayThroughCall() {
        startHeld()
        val vm = screen.vm()
        val pid = Process.myPid()
        screen.options()
        screen.clickText(R.string.autoPlay)
        screen.await("Autoplay did not start") { screen.onMain { vm.isAutoPlayPlaying } && audio.plays.isNotEmpty() }
        record("autoplay before call", vm, pid)
        ring()
        record("autoplay ringing", vm, pid)
        SystemClock.sleep(1500)
        record("autoplay ringing +1.5s", vm, pid)
        hangUp()
        record("autoplay after hang-up", vm, pid)
        SystemClock.sleep(1500)
        record("autoplay after hang-up +1.5s", vm, pid)
    }

    @Test fun liftWhilePaused() {
        startHeld()
        val vm = screen.vm()
        val pid = Process.myPid()
        val held = Cell(3, 3)
        val finger = fingers.down(center(held))
        awaitHeld("Held pad did not play, light and loop", setOf(held))
        play.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        SystemClock.sleep(2000)
        record("paused, finger down", vm, pid)
        fingers.up(finger)
        SystemClock.sleep(1500)
        record("paused, finger lifted", vm, pid)
        log("$received")
        play.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        SystemClock.sleep(1000)
        record("resumed", vm, pid)
    }

    @Test fun heldWhileShadeOpens() {
        startHeld()
        val vm = screen.vm()
        val pid = Process.myPid()
        val held = Cell(3, 3)
        val finger = fingers.down(center(held))
        awaitHeld("Held pad did not play, light and loop", setOf(held))
        screen.device.executeShellCommand("cmd statusbar expand-notifications")
        SystemClock.sleep(2500)
        record("shade open, finger down", vm, pid)
        val cancelled = received.events.any { it.action == MotionEvent.ACTION_CANCEL }
        if (cancelled) fingers.forgetCancelled() else fingers.up(finger)
        SystemClock.sleep(1500)
        record("shade open, finger lifted (cancelled=$cancelled)", vm, pid)
        log("$received")
        screen.device.executeShellCommand("cmd statusbar collapse")
        SystemClock.sleep(1500)
        record("shade closed", vm, pid)
    }

    private fun heldThroughCall(slide: Boolean) {
        startHeld(slide)
        val vm = screen.vm()
        val pid = Process.myPid()
        val held = Cell(3, 3)
        val finger = fingers.down(center(held))
        awaitHeld("Held pad did not play, light and loop", setOf(held))
        record("held before call slide=$slide", vm, pid)
        val before = received.events.size
        ring()
        record("held ringing slide=$slide", vm, pid)
        hangUp()
        record("held after hang-up slide=$slide", vm, pid)
        val cancelled = received.events.drop(before).any { it.action == MotionEvent.ACTION_CANCEL }
        log("system cancel during call: $cancelled\n$received")
        if (cancelled) fingers.forgetCancelled() else fingers.up(finger)
        settle()
        record("after lift slide=$slide", vm, pid)
        val plays = audio.plays.size
        val again = fingers.down(center(held))
        SystemClock.sleep(1000)
        record("re-pressed slide=$slide (plays before re-press: $plays)", vm, pid)
        screen.capture("call-repress-${if (slide) "slide" else "pad"}")
        fingers.up(again)
        settle()
        record("re-press lifted slide=$slide", vm, pid)
    }

    private fun ring() {
        log("ready")
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (callState() != 1) {
            check(SystemClock.elapsedRealtime() < deadline) { "No incoming call within 90 s" }
            SystemClock.sleep(200)
        }
        SystemClock.sleep(3000)
    }

    private fun hangUp() {
        log("ringing-recorded")
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (callState() != 0) {
            check(SystemClock.elapsedRealtime() < deadline) { "Call did not end within 90 s" }
            SystemClock.sleep(200)
        }
        SystemClock.sleep(3000)
    }

    private fun callState(): Int = Regex("mCallState=(\\d)").find(screen.device.executeShellCommand("dumpsys telephony.registry"))
        ?.groupValues?.get(1)?.toInt() ?: -1

    private fun record(stage: String, vm: PlayActivityViewModel, pid: Int) {
        val stages = screen.onMain {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            androidx.test.runner.lifecycle.Stage.values().associateWith { monitor.getActivitiesInStage(it).map { a -> a.javaClass.simpleName } }
                .filterValues { it.isNotEmpty() }
        }
        log("$stage: samePid=${pid == Process.myPid()} screenVisible=${screen.onMain { vm.screenVisible }} " +
            "stages=$stages autoplay=${screen.onMain { vm.isAutoPlayPlaying }} progress=${screen.onMain { vm.autoPlayProgress }} " +
            "lit=${litCells(vm)} looping=${loopingCells()} plays=${audio.plays.size} stops=${audio.stoppedVoices} " +
            "silences=${audio.silences.get()} streamStops=${audio.stops.get()} streamStarts=${audio.starts.get()} " +
            "top=${screen.device.currentPackageName}")
    }

    private fun log(message: String) = Log.i(TAG, message)

    private companion object {
        const val TAG = "CALL_PROBE"
    }
}
