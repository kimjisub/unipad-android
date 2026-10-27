package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.audio.AudioFileInspector
import com.kimjisub.launchpad.audio.AudioInspection
import com.kimjisub.launchpad.audio.AudioExclusion
import com.kimjisub.launchpad.audio.AudioReference
import com.kimjisub.launchpad.audio.AudioPackBounds
import com.kimjisub.launchpad.audio.AudioSelector
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File

class SoundRunner(
	private val unipack: UniPack,
	private val chain: ChainObserver,
	private val loadingListener: LoadingListener,
	private val scope: CoroutineScope,
	private val engine: Engine = OboeEngine,
) {

	private data class PendingSound(val sound: Sound, val chain: Int, val x: Int, val y: Int, val line: Int)

	private var stopKey: Array<Array<Array<Int>>>

	// Stop key of the infinite loop the press of each held pad (x, y) started, 0 when none. Its release
	// stops that voice even when the chain or the pad's next sound changed while it was held.
	private val heldLoop = Array(unipack.buttonX) { IntArray(unipack.buttonY) }
	@Volatile
	private var engineStarted = false

	// The loader thread and destroy() both change the process-wide engine. Every engine start,
	// sound load and release happens under this lock, so once destroy() has run nothing more is
	// loaded and no id is freed twice.
	private val engineLock = Any()
	private var destroyed = false // guarded by engineLock
	private var streamPaused = false // guarded by engineLock
	private val loadJob: Job

	class NoPlayableSoundsException(val report: LoadReport? = null) : IllegalStateException("This pack has no playable sound files")

	enum class FailureReason { LIMIT, UNREADABLE, DECODE, MEMORY }
	data class FileFailure(val file: File, val reason: FailureReason, val exclusion: AudioExclusion? = null)
	data class LoadReport(val usedFiles: Int, val loadedFiles: Int, val failures: List<FileFailure>, val reservedCost: Long) {
		val allFailed: Boolean get() = usedFiles > 0 && loadedFiles == 0
		val compatibilityFailed: Boolean get() = failures.any { it.reason == FailureReason.DECODE || it.reason == FailureReason.MEMORY }
	}

	class AudioOutputUnavailableException : RuntimeException("Failed to open audio output")

	private val wormholeLock = Any()
	private val wormholeJobs = mutableSetOf<Job>()
	private var applyingWormhole = false // guarded by wormholeLock
	private val cancelWormholesOnChainSelection: (Int, Int) -> Unit = { _, _ ->
		synchronized(wormholeLock) {
			// A scheduled wormhole must not cancel the other wormholes in the same chord.
			if (!applyingWormhole) cancelPendingWormholes()
		}
	}

	interface LoadingListener {
		fun onStart(soundCount: Int)
		fun onProgressTick()
		fun onEnd()
		fun onReport(report: LoadReport) {}
		fun onException(throwable: Throwable)
	}

	/** The part of the audio engine this runner drives; lets loading run in unit tests without the native library. */
	interface Engine {
		fun start(): Boolean
		fun stop()
		fun inspect(file: File): AudioInspection = AudioFileInspector.inspect(file)
		fun decode(file: File): OboeAudioEngine.DecodedAudio?
		fun load(decoded: OboeAudioEngine.DecodedAudio): Int
		fun unloadSound(soundId: Int)
		fun unloadAll()
		fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int
		fun stopVoice(stopKey: Int)
		fun stopAllVoices()
	}

	internal object OboeEngine : Engine {
		override fun start() = OboeAudioEngine.start()
		override fun stop() = OboeAudioEngine.stop()
		override fun decode(file: File) = OboeAudioEngine.decodeOnly(file)
		override fun load(decoded: OboeAudioEngine.DecodedAudio) = OboeAudioEngine.loadDecoded(decoded)
		override fun unloadSound(soundId: Int) = OboeAudioEngine.unloadSound(soundId)
		override fun unloadAll() = OboeAudioEngine.unloadAll()
		override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) =
			OboeAudioEngine.play(soundId, volumeL, volumeR, loop)
		override fun stopVoice(stopKey: Int) = OboeAudioEngine.stopVoice(stopKey)
		override fun stopAllVoices() = OboeAudioEngine.stopAllVoices()
	}

	init {
		chain.addSelectionObserver(cancelWormholesOnChainSelection)
		val table = unipack.soundTable
		// Take references before playback can rotate a pad's mapping during asynchronous loading.
		val pending = mutableListOf<PendingSound>()
		if (table != null) {
			for (i in 0 until unipack.chain)
				for (j in 0 until unipack.buttonX)
					for (k in 0 until unipack.buttonY)
						for ((line, sound) in (table[i][j][k] ?: continue).withIndex()) {
							pending.add(PendingSound(sound, i, j, k, line))
						}
		}
		val soundsToLoad = pending.map { it.sound }
		val soundCount = soundsToLoad.size

		Log.play("soundCount: $soundCount")

		stopKey = Array(unipack.chain) {
			Array(unipack.buttonX) {
				Array(unipack.buttonY) {
					0
				}
			}
		}

		loadingListener.onStart(soundCount)

		loadJob = scope.launch(Dispatchers.IO) {
			try {
				val started = synchronized(engineLock) {
					if (destroyed) return@launch
					// start() also creates the native engine the sounds load into, so it runs even when
					// the screen was hidden first; the stream is then closed again until resumeStream().
					engine.start().also {
						engineStarted = it
						if (it && streamPaused) engine.stop()
					}
				}
				if (!started) {
					throw AudioOutputUnavailableException()
				}

				val references = pending.map { AudioReference(it.chain, it.x, it.y, it.line, it.sound.file.absolutePath) }
				val uniqueFiles = soundsToLoad.groupBy { it.file.absolutePath }
				// Inspect and reserve the complete plan before any decoder can run. Neither a decoder's
				// completion order nor an allocation failure can change a later admission decision.
				val plan = AudioSelector.select(references, AudioPackBounds(unipack.chain, unipack.buttonX, unipack.buttonY)) {
					ensureActive()
					engine.inspect(File(it))
				}
				val failures = mutableListOf<FileFailure>()
				var loadedFiles = 0
				// One decoder at a time keeps PCM growth, down-mixing and the native copy from overlapping
				// across files. Real-device peak-memory verification remains necessary.
				for (selection in plan.files) {
					ensureActive()
					val file = File(selection.identity)
					val sounds = uniqueFiles.getValue(selection.identity)
					var reason: FailureReason? = selection.exclusion?.let {
						when (it) {
							AudioExclusion.INVALID_FORMAT, AudioExclusion.READ_FAILURE -> FailureReason.UNREADABLE
							else -> FailureReason.LIMIT
						}
					}
					var soundId = Sound.UNDECODABLE
					if (reason == null) {
						try {
							val decoded = try {
								engine.decode(file)
							} catch (e: CancellationException) {
								throw e
							} catch (e: Exception) {
								Log.err("Sound decode failed: ${file.name}", e)
								null
							}
							ensureActive()
							soundId = synchronized(engineLock) {
								if (destroyed) return@launch
								decoded?.let(engine::load) ?: Sound.UNDECODABLE
							}
							reason = if (soundId < 0) FailureReason.DECODE else null
						} catch (e: CancellationException) {
							throw e
						} catch (_: OutOfMemoryError) {
							reason = FailureReason.MEMORY
						}
					}
					synchronized(engineLock) {
						if (destroyed) return@launch
						sounds.forEach { it.id = if (reason == null) soundId else Sound.UNDECODABLE }
					}
					if (reason == null) loadedFiles++ else failures.add(FileFailure(file, reason, selection.exclusion))
					repeat(sounds.size) { loadingListener.onProgressTick() }
				}
				ensureActive()
				val report = LoadReport(plan.files.size, loadedFiles, failures.toList(), plan.reservedCost)
				loadingListener.onReport(report)
				if (report.allFailed) throw NoPlayableSoundsException(report)
				loadingListener.onEnd()
			} catch (e: CancellationException) {
				// destroy() cancelled the load; this is not a loading failure to report.
				throw e
			} catch (e: Throwable) {
				// OutOfMemoryError and UnsatisfiedLinkError are Errors, not RuntimeExceptions, and
				// used to take the process down instead of reaching onException.
				Log.err("[08] doInBackground", e)
				loadingListener.onException(e)
			}
		}
	}

	fun soundOn(x: Int, y: Int, followWormhole: Boolean = true) {
		val restarted = stopKey[chain.value][x][y]
		engine.stopVoice(restarted)
		// A press made on another chain while the pad is still held replaces its key below; stop
		// that earlier loop now, as a press on the same chain does, or nothing could stop it.
		if (heldLoop[x][y] != restarted) soundOff(x, y)
		heldLoop[x][y] = 0
		val sound: Sound = unipack.soundGet(chain.value, x, y) ?: return
		// An unavailable sound keeps only its own turn silent, including during loading: the pad
		// still moves on to its next sound and still follows the sound's chain move.
		if (sound.id >= 0) {
			val key = engine.play(
				soundId = sound.id,
				volumeL = 1.0f,
				volumeR = 1.0f,
				// The native engine uses an Int; every negative count except -1 plays once.
				loop = sound.loop.coerceAtLeast(Int.MIN_VALUE.toLong()).toInt(),
			)
			stopKey[chain.value][x][y] = key
			heldLoop[x][y] = if (sound.loop == -1L) key else 0
		}
		unipack.soundPush(chain.value, x, y)
		// Auto play has its own chain commands; live input alone schedules the delayed move.
		if (followWormhole && sound.wormhole != Sound.NO_WORMHOLE) {
			val move = scope.launch(Dispatchers.Main, start = CoroutineStart.LAZY) {
				delay(100)
				synchronized(wormholeLock) {
					ensureActive()
					applyingWormhole = true
					try {
						chain.value = sound.wormhole
					} finally {
						applyingWormhole = false
					}
				}
			}
			synchronized(wormholeLock) { wormholeJobs.add(move) }
			move.invokeOnCompletion { synchronized(wormholeLock) { wormholeJobs.remove(move) } }
			move.start()
		}
	}

	fun soundOff(x: Int, y: Int) {
		val key = heldLoop[x][y]
		if (key == 0) return
		heldLoop[x][y] = 0
		engine.stopVoice(key)
	}

	/** Silences every voice, including infinite loops, while keeping the stream and sounds loaded. */
	fun stopAll() {
		cancelPendingWormholes()
		if (engineStarted) engine.stopAllVoices()
	}

	/**
	 * Closes the output stream while the screen is hidden. Android 17 mutes an open stream of an app
	 * nobody can see, and with strict audio hardening aborts the process from the stream's callback
	 * thread. Loaded sounds stay in the engine, so [resumeStream] plays again without reloading.
	 */
	fun pauseStream() {
		cancelPendingWormholes()
		synchronized(engineLock) {
			if (destroyed || streamPaused) return
			streamPaused = true
			if (engineStarted) engine.stop()
		}
	}

	fun resumeStream() {
		synchronized(engineLock) {
			if (destroyed || !streamPaused) return
			streamPaused = false
			if (engineStarted && !engine.start()) Log.err("Failed to reopen the audio stream")
		}
	}

	/** Drops chain moves whose originating playback or chain selection is no longer current. */
	fun cancelPendingWormholes() {
		synchronized(wormholeLock) {
			val pending = wormholeJobs.toList()
			wormholeJobs.clear()
			pending.forEach { it.cancel() }
		}
	}

	fun destroy() {
		cancelPendingWormholes()
		chain.removeSelectionObserver(cancelWormholesOnChainSelection)
		loadJob.cancel()
		synchronized(engineLock) {
			if (destroyed) return
			destroyed = true
			// Collect unique sound IDs to avoid double-unload
			val unloadedIds = mutableSetOf<Int>()
			unipack.soundTable?.let { table ->
				for (i in table)
					for (j in i)
						for (arrayList in j) {
							if (arrayList != null) {
								for (sound in arrayList) {
									if (sound.id >= 0 && unloadedIds.add(sound.id)) {
										try {
											engine.unloadSound(sound.id)
										} catch (e: RuntimeException) {
											Log.err("Sound unload failed", e)
										}
									}
									// The engine reuses freed slots for the next pack's sounds.
									sound.id = Sound.NOT_LOADED
								}
							}
						}
			}
			if (engineStarted) {
				engine.unloadAll()
				engine.stop()
			}
		}
	}
}
