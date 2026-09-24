package com.kimjisub.launchpad.audio

import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioFocusPolicyTest {

	private class FakeTarget(override var isAutoPlayPlaying: Boolean) : AudioFocusPolicy.Target {
		val events = mutableListOf<String>()
		override fun silence() { events += "silence" }
		override fun pauseAutoPlay() { events += "pause"; isAutoPlayPlaying = false }
		override fun resumeAutoPlay() { events += "resume"; isAutoPlayPlaying = true }
	}

	private lateinit var target: FakeTarget
	private lateinit var policy: AudioFocusPolicy

	@Before
	fun setUp() {
		target = FakeTarget(isAutoPlayPlaying = true)
		policy = AudioFocusPolicy(target)
	}

	@Test
	fun transientLoss_pausesAndSilences_thenGainResumes() {
		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT)
		assertEquals(listOf("pause", "silence"), target.events)
		assertFalse(target.isAutoPlayPlaying)

		policy.onFocusChange(AUDIOFOCUS_GAIN)
		assertEquals(listOf("pause", "silence", "resume"), target.events)
		assertTrue(target.isAutoPlayPlaying)
	}

	@Test
	fun transientLoss_withoutAutoPlay_silencesOnly_andGainDoesNotStartAutoPlay() {
		target.isAutoPlayPlaying = false

		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT)
		policy.onFocusChange(AUDIOFOCUS_GAIN)

		assertEquals(listOf("silence"), target.events)
		assertFalse(target.isAutoPlayPlaying)
	}

	@Test
	fun repeatedTransientLoss_stillResumesOnGain() {
		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT)
		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT)
		policy.onFocusChange(AUDIOFOCUS_GAIN)

		assertEquals(listOf("pause", "silence", "silence", "resume"), target.events)
	}

	@Test
	fun permanentLoss_pausesAndSilences_andGainDoesNotResume() {
		policy.onFocusChange(AUDIOFOCUS_LOSS)
		policy.onFocusChange(AUDIOFOCUS_GAIN)

		assertEquals(listOf("pause", "silence"), target.events)
		assertFalse(target.isAutoPlayPlaying)
	}

	@Test
	fun permanentLoss_afterTransientLoss_cancelsPendingResume() {
		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT)
		policy.onFocusChange(AUDIOFOCUS_LOSS)
		policy.onFocusChange(AUDIOFOCUS_GAIN)

		assertEquals(listOf("pause", "silence", "silence"), target.events)
	}

	@Test
	fun gainTwice_resumesOnlyOnce() {
		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT)
		policy.onFocusChange(AUDIOFOCUS_GAIN)
		policy.onFocusChange(AUDIOFOCUS_GAIN)

		assertEquals(listOf("pause", "silence", "resume"), target.events)
	}

	@Test
	fun duckLoss_isLeftToTheSystem() {
		policy.onFocusChange(AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

		assertTrue(target.events.isEmpty())
		assertTrue(target.isAutoPlayPlaying)
	}
}
