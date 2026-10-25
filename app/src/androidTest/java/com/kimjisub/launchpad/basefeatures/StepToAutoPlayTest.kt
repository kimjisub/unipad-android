package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The guide shows an unplayed chord; choosing autoplay must sound that chord immediately. */
@RunWith(AndroidJUnit4::class)
class StepToAutoPlayTest : PlaybackScreenTest() {
    @Test fun switchingFromAnUnplayedStepPlaysThePendingChord() {
        File(screen.pack(), "autoPlay").writeText("delay 500\ntouch 1 1\ntouch 2 2\ndelay 20000\ntouch 3 3\ndelay 60000\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.chooseOption(R.string.stepPractice)
        screen.await("The pending chord was not guided") {
            screen.onMain {
                vm.channelManager.get(0, 0)?.channel == Channel.GUIDE &&
                    vm.channelManager.get(1, 1)?.channel == Channel.GUIDE
            }
        }
        screen.onMain { vm.autoPlayPause() }
        screen.capture("step-to-autoplay-pending-chord")
        SystemClock.sleep(300)
        screen.chooseOption(R.string.autoPlay)
        SystemClock.sleep(200)
        screen.capture("step-to-autoplay-resumed-chord")
        assertEquals("Autoplay must immediately sound both guided pads", 2, audio.plays.size)
    }
}
