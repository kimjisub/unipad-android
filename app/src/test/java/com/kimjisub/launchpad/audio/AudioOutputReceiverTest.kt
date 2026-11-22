package com.kimjisub.launchpad.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AudioOutputReceiverTest {
    private val context = mockk<Context>(relaxed = true)
    private var interruptions = 0
    private val receiver = AudioOutputReceiver { interruptions++ }

    @Before fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(context, receiver, any(), ContextCompat.RECEIVER_NOT_EXPORTED) } returns null
    }

    @After fun tearDown() = unmockkStatic(ContextCompat::class)

    private fun deliver(action: String?) {
        val intent = mockk<Intent>()
        every { intent.action } returns action
        receiver.onReceive(context, intent)
    }

    @Test fun speakerFallbackInterruptsOnlyWhileStarted() {
        deliver(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        assertEquals(0, interruptions)
        receiver.start(context)
        deliver(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        assertEquals(1, interruptions)
        receiver.stop(context)
        deliver(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        assertEquals(1, interruptions)
        receiver.start(context)
        deliver(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        assertEquals(2, interruptions)
    }

    @Test fun unrelatedConnectionAndDeviceEventsDoNotInterrupt() {
        receiver.start(context)
        for (action in listOf(null, AudioManager.ACTION_HEADSET_PLUG,
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
            "android.hardware.usb.action.USB_DEVICE_DETACHED", "unknown")) {
            deliver(action)
        }
        assertEquals(0, interruptions)
    }

    @Test fun repeatedStartAndStopRegisterAndUnregisterOnlyOnce() {
        receiver.start(context)
        receiver.start(context)
        receiver.stop(context)
        receiver.stop(context)
        verify(exactly = 1) {
            ContextCompat.registerReceiver(context, receiver, any(), ContextCompat.RECEIVER_NOT_EXPORTED)
            context.unregisterReceiver(receiver)
        }
    }

    @Test fun duplicateNotificationsAreSafeEvenWhenAlreadyPaused() {
        var playing = true
        var silences = 0
        val policy = AudioFocusPolicy(object : AudioFocusPolicy.Target {
            override fun pauseAutoPlay() { playing = false }
            override fun silence() { silences++ }
        })
        val receiver = AudioOutputReceiver { policy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS) }
        every { ContextCompat.registerReceiver(context, receiver, any(), ContextCompat.RECEIVER_NOT_EXPORTED) } returns null
        receiver.start(context)
        val intent = mockk<Intent>()
        every { intent.action } returns AudioManager.ACTION_AUDIO_BECOMING_NOISY
        repeat(2) { receiver.onReceive(context, intent) }
        policy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(false, playing)
        assertEquals(2, silences)
    }
}
