package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SoundRunner(
	private val unipack: UniPack,
	private val chain: ChainObserver,
	private val loadingListener: LoadingListener,
	private val scope: CoroutineScope,
) {

	private var stopKey: Array<Array<Array<Int>>>
	private var engineStarted = false

	interface LoadingListener {
		fun onStart(soundCount: Int)
		fun onProgressTick()
		fun onEnd()
		fun onException(throwable: Throwable)
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

		scope.launch(Dispatchers.IO) {
			try {
				engineStarted = OboeAudioEngine.start()
				if (!engineStarted) {
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

				// Phase 2: Parallel decode unique files
				val decodedCache = ConcurrentHashMap<String, OboeAudioEngine.DecodedAudio>()
				val progress = AtomicInteger(0)
				val semaphore = Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))

				val decodeJobs = uniqueFiles.keys.map { filePath ->
					async(Dispatchers.IO) {
						semaphore.withPermit {
							val decoded = OboeAudioEngine.decodeOnly(java.io.File(filePath))
							if (decoded != null) {
								decodedCache[filePath] = decoded
							} else {
								Log.err("Failed to decode: $filePath")
							}
							// Report progress for all sounds sharing this file
							val count = uniqueFiles[filePath]?.size ?: 1
							val newProgress = progress.addAndGet(count)
							repeat(count) { loadingListener.onProgressTick() }
						}
					}
				}
				decodeJobs.awaitAll()

				// Phase 3: Load decoded PCM into native engine (sequential, fast)
				for ((filePath, sounds) in uniqueFiles) {
					val decoded = decodedCache[filePath] ?: continue
					val soundId = OboeAudioEngine.loadDecoded(decoded)
					if (soundId < 0) {
						Log.err("Failed to load into engine: $filePath")
						continue
					}
					for (sound in sounds) {
						sound.id = soundId
					}
				}

				loadingListener.onEnd()
			} catch (e: RuntimeException) {
				Log.err("[08] doInBackground", e)
				loadingListener.onException(e)
			}
		}
	}

	fun soundOn(x: Int, y: Int) {
		OboeAudioEngine.stopVoice(stopKey[chain.value][x][y])
		val sound: Sound? = unipack.soundGet(chain.value, x, y)
		if (sound != null && sound.id >= 0) {
			stopKey[chain.value][x][y] = OboeAudioEngine.play(
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
			OboeAudioEngine.stopVoice(stopKey[chain.value][x][y])
	}

	fun destroy() {
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
										OboeAudioEngine.unloadSound(sound.id)
									} catch (e: RuntimeException) {
										Log.err("Sound unload failed", e)
									}
								}
							}
						}
					}
		}
		if (engineStarted) {
			OboeAudioEngine.stop()
		}
	}
}
