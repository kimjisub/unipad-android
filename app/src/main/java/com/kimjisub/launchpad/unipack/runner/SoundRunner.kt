package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

class SoundRunner(
	private val unipack: UniPack,
	private val chain: ChainObserver,
	private val loadingListener: LoadingListener,
	private val scope: CoroutineScope,
	private val engine: Engine = OboeEngine,
) {

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

	class NoPlayableSoundsException : IllegalStateException("This pack has no playable sound files")

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
		fun onException(throwable: Throwable)
	}

	/** The part of the audio engine this runner drives; lets loading run in unit tests without the native library. */
	interface Engine {
		fun start(): Boolean
		fun stop()
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
		val soundsToLoad = mutableListOf<Sound>()
		if (table != null) {
			for (i in 0 until unipack.chain)
				for (j in 0 until unipack.buttonX)
					for (k in 0 until unipack.buttonY)
						for (sound in table[i][j][k] ?: continue) soundsToLoad.add(sound)
		}
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

				// Phase 1: Collect all unique files and map sounds to them
				val allSounds = soundsToLoad
				val uniqueFiles = LinkedHashMap<String, MutableList<Sound>>()
				for (sound in allSounds) {
					val key = sound.file.absolutePath
					uniqueFiles.getOrPut(key) { mutableListOf() }.add(sound)
				}

				Log.play("uniqueFiles: ${uniqueFiles.size} / totalSounds: ${allSounds.size}")

				// Phase 2: Decode unique files in parallel and hand each one to the native engine as
				// soon as it is decoded. The engine keeps its own copy, so only the files currently
				// being decoded sit on the Java heap instead of the PCM of the whole pack.
				// coroutineScope keeps a failing file inside this load: it cancels the other files,
				// waits for them to stop and rethrows here, so the failure reaches onException below
				// instead of failing the caller's scope.
				val semaphore = Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))

				coroutineScope {
					uniqueFiles.map { (filePath, sounds) ->
						async(Dispatchers.IO) {
							semaphore.withPermit {
								val decoded = engine.decode(File(filePath))
								if (decoded == null) Log.err("Failed to decode: $filePath")
								ensureActive()
								synchronized(engineLock) {
									if (destroyed) {
										Log.play("SoundRunner destroyed while loading; skipped $filePath")
										return@withPermit
									}
									val soundId = decoded?.let(engine::load) ?: Sound.UNDECODABLE
									if (decoded != null && soundId < 0) Log.err("Failed to load into engine: $filePath")
									for (sound in sounds) {
										sound.id = if (soundId < 0) Sound.UNDECODABLE else soundId
									}
								}
								// Report progress for all sounds sharing this file
								repeat(sounds.size) { loadingListener.onProgressTick() }
							}
						}
					}.awaitAll()
				}

				ensureActive()
				// Light-only packs have no audio to fail; reject actual files only when none loaded.
				if (allSounds.isNotEmpty() && allSounds.none { it.id >= 0 }) throw NoPlayableSoundsException()
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
