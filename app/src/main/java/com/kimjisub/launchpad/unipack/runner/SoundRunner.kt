package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
	@Volatile
	private var engineStarted = false

	// The loader thread and destroy() both change the process-wide engine. Every engine start,
	// sound load and release happens under this lock, so once destroy() has run nothing more is
	// loaded and no id is freed twice.
	private val engineLock = Any()
	private var destroyed = false // guarded by engineLock
	private val loadJob: Job

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

	private object OboeEngine : Engine {
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
		val table = unipack.soundTable
		var soundCount = 0
		if (table != null) {
			for (i in 0 until unipack.chain)
				for (j in 0 until unipack.buttonX)
					for (k in 0 until unipack.buttonY)
						if (table[i][j][k] != null)
							soundCount += table[i][j][k]?.size ?: 0
		}

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
					engine.start().also { engineStarted = it }
				}
				if (!started) {
					throw RuntimeException("Failed to start Oboe audio engine")
				}

				// Phase 1: Collect all unique files and map sounds to them
				val allSounds = mutableListOf<Sound>()
				val uniqueFiles = LinkedHashMap<String, MutableList<Sound>>()
				if (table != null) {
					for (i in 0 until unipack.chain)
						for (j in 0 until unipack.buttonX)
							for (k in 0 until unipack.buttonY) {
								val sounds = table[i][j][k] ?: continue
								for (sound in sounds) {
									allSounds.add(sound)
									val key = sound.file.absolutePath
									uniqueFiles.getOrPut(key) { mutableListOf() }.add(sound)
								}
							}
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
								if (decoded == null) {
									Log.err("Failed to decode: $filePath")
								} else {
									ensureActive()
									synchronized(engineLock) {
										if (destroyed) {
											Log.play("SoundRunner destroyed while loading; skipped $filePath")
											return@withPermit
										}
										val soundId = engine.load(decoded)
										if (soundId < 0) {
											Log.err("Failed to load into engine: $filePath")
										} else {
											for (sound in sounds) {
												sound.id = soundId
											}
										}
									}
								}
								// Report progress for all sounds sharing this file
								repeat(sounds.size) { loadingListener.onProgressTick() }
							}
						}
					}.awaitAll()
				}

				ensureActive()
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

	fun soundOn(x: Int, y: Int) {
		engine.stopVoice(stopKey[chain.value][x][y])
		val sound: Sound? = unipack.soundGet(chain.value, x, y)
		if (sound != null && sound.id >= 0) {
			stopKey[chain.value][x][y] = engine.play(
				soundId = sound.id,
				volumeL = 1.0f,
				volumeR = 1.0f,
				loop = sound.loop,
			)
			unipack.soundPush(chain.value, x, y)
			if (sound.wormhole != Sound.NO_WORMHOLE)
				scope.launch(Dispatchers.Main) {
					delay(100)
					chain.value = sound.wormhole
				}
		}
	}

	fun soundOff(x: Int, y: Int) {
		val sound = unipack.soundGet(chain.value, x, y)
		if (sound != null && sound.loop == -1)
			engine.stopVoice(stopKey[chain.value][x][y])
	}

	/** Silences every voice, including infinite loops, while keeping the stream and sounds loaded. */
	fun stopAll() {
		if (engineStarted) engine.stopAllVoices()
	}

	fun destroy() {
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
									sound.id = -1
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
