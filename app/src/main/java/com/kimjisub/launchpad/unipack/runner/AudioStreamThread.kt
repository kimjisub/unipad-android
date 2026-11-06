package com.kimjisub.launchpad.unipack.runner

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The one thread that opens and closes the audio output and frees a pack's sounds. Opening an output
 * can take seconds on some devices, so the play screen never waits for it. The engine is shared by
 * the whole process: one thread, run in the order asked, lets a pack that is left free its sounds
 * before the next pack opens the output again.
 */
internal object AudioStreamThread : Executor {
	private val thread = Executors.newSingleThreadExecutor { Thread(it, "UniPadAudioStream").apply { isDaemon = true } }

	override fun execute(command: Runnable) = thread.execute(command)
}
