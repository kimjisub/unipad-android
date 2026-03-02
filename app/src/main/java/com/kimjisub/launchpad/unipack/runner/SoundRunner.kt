package com.kimjisub.launchpad.unipack.runner

import android.media.AudioAttributes
import android.media.SoundPool
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SoundRunner(
	private val unipack: UniPack,
	private val chain: ChainObserver,
	private val loadingListener: LoadingListener,
	private val scope: CoroutineScope,
) {

	private var soundPool: SoundPool
	private var stopID: Array<Array<Array<Int>>>

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

		val audioAttributes = AudioAttributes.Builder()
			.setUsage(AudioAttributes.USAGE_GAME)
			.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
			.build()
		Log.play("soundCount: $soundCount")
		soundPool = SoundPool.Builder()
			.setMaxStreams(soundCount.coerceAtLeast(1))
			.setAudioAttributes(audioAttributes)
			.build()
		stopID = Array(unipack.chain) {
			Array(unipack.buttonX) {
				Array(unipack.buttonY) {
					0
				}
			}
		}

		loadingListener.onStart(soundCount)

		scope.launch(Dispatchers.IO) {
			try {
				for (i in 0 until unipack.chain) {
					for (j in 0 until unipack.buttonX) {
						for (k in 0 until unipack.buttonY) {
							val sounds = table?.get(i)?.get(j)?.get(k)
							if (sounds != null) {
								for (sound in sounds) {
									sound.id = soundPool.load(sound.file.path, 1)
									loadingListener.onProgressTick()
								}
							}
						}
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
		soundPool.stop(stopID[chain.value][x][y])
		val sound: Sound? = unipack.soundGet(chain.value, x, y)
		if (sound != null) {
			stopID[chain.value][x][y] = soundPool.play(sound.id, 1.0f, 1.0f, 0, sound.loop, 1.0f)
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
			soundPool.stop(stopID[chain.value][x][y])
	}

	fun destroy() {
		unipack.soundTable?.let { table ->
			for (i in table)
				for (j in i)
					for (arrayList in j) {
						if (arrayList != null) {
							for (sound in arrayList) {
								try {
									soundPool.unload(sound.id)
								} catch (e: RuntimeException) {
									Log.err("Sound unload failed", e)
								}
							}
						}
					}
		}
		soundPool.release()
	}
}