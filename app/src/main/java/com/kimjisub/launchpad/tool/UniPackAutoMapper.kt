package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Rewrites a pack's autoPlay so each wait matches the length of the sound pressed before it.
 *
 * [run] belongs to its caller's scope: cancelling it (the play screen is left) stops it before the
 * file is written, and the file is only ever swapped whole (see [AutoPlayFileReplacer]).
 */
class UniPackAutoMapper(
	private val unipack: UniPackFolder,
	private val openDurationReader: () -> SoundDurationReader = ::MediaPlayerDurationReader,
	private val replacer: AutoPlayFileReplacer = AutoPlayFileReplacer(),
) {
	interface Listener {
		fun onGetWorkSize(size: Int)
		fun onProgress(progress: Int)
	}

	/** Calls [listener] in the caller's context; throws when the pack cannot be read or written. */
	suspend fun run(listener: Listener) {
		val autoPlayFile = unipack.autoPlayFile ?: throw FileNotFoundException("autoPlay")
		val autoPlay = unipack.autoPlayTable ?: throw IllegalStateException("autoPlay is not loaded")
		val elements = mergeDelays(autoPlay.elements)
		listener.onGetWorkSize(elements.size)

		val result = ArrayList<AutoPlay.Element>()
		openDurationReader().use { reader ->
			// null: the sound's length is unknown, so the pack's own wait is kept.
			var nextDuration: Int? = FIRST_DELAY_MS
			for ((i, e) in elements.withIndex()) {
				when (e) {
					is AutoPlay.Element.On -> {
						nextDuration = soundFile(e)?.let { withContext(Dispatchers.IO) { reader.durationMs(it) } }
						result.add(e)
					}
					is AutoPlay.Element.Chain -> result.add(e)
					is AutoPlay.Element.Delay ->
						result.add(AutoPlay.Element.Delay(nextDuration?.plus(AUTOMAPPING_DELAY_OFFSET_MS) ?: e.delay))
					is AutoPlay.Element.Off -> {}
				}
				listener.onProgress(i)
			}
		}

		val content = render(result)
		// withContext checks for cancellation before writing; the replacement itself is not interrupted.
		withContext(Dispatchers.IO) { replacer.replace(autoPlayFile, content) }
	}

	private fun soundFile(e: AutoPlay.Element.On): File? {
		val sounds = unipack.soundTable?.getOrNull(e.currChain)?.getOrNull(e.x)?.getOrNull(e.y)
		if (sounds.isNullOrEmpty()) return null
		return sounds.elementAt(e.num % sounds.size).file
	}

	companion object {
		private const val FIRST_DELAY_MS = 1000
		private const val AUTOMAPPING_DELAY_OFFSET_MS = 0

		/** Drops pad releases and joins consecutive waits into one, placed before each press. */
		internal fun mergeDelays(elements: List<AutoPlay.Element>): List<AutoPlay.Element> {
			val merged = ArrayList<AutoPlay.Element>()
			var pendingDelay: Int? = 0
			for (e in elements) {
				when (e) {
					is AutoPlay.Element.On -> {
						pendingDelay?.let { merged.add(AutoPlay.Element.Delay(it)) }
						pendingDelay = null
						merged.add(e)
					}
					is AutoPlay.Element.Chain -> merged.add(e)
					is AutoPlay.Element.Delay -> pendingDelay = (pendingDelay ?: 0) + e.delay
					is AutoPlay.Element.Off -> {}
				}
			}
			return merged
		}

		internal fun render(elements: List<AutoPlay.Element>): String = buildString {
			for (e in elements) {
				when (e) {
					is AutoPlay.Element.On -> append("t ").append(e.x + 1).append(' ').append(e.y + 1).append('\n')
					is AutoPlay.Element.Chain -> append("c ").append(e.c + 1).append('\n')
					is AutoPlay.Element.Delay -> append("d ").append(e.delay).append('\n')
					is AutoPlay.Element.Off -> {}
				}
			}
		}
	}
}
