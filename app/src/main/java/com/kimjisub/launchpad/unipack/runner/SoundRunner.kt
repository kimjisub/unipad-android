package com.kimjisub.launchpad.unipack.runner

import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.struct.Sound
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class SoundRunner(
	private val unipack: Unipack,
	private val chain: ChainObserver,
	private val listener: Listener
) {

	private var soundPool: SoundPool? = null
	private var stopID: Array<Array<Array<Int>>>? = null

	interface Listener {
		fun onStart(soundCount: Int)
		fun onProgressTick()
		fun onEnd()
		fun onException(throwable: Throwable)
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			var soundCount = 0
			for (i in 0 until unipack.chain)
				for (j in 0 until unipack.buttonX)
					for (k in 0 until unipack.buttonY)
						if (unipack.soundTable!![i][j][k] != null)
							soundCount += unipack.soundTable!![i][j][k]!!.size
			soundPool = SoundPool(soundCount, AudioManager.STREAM_MUSIC, 0)
			stopID = Array(unipack.chain) {
				Array(unipack.buttonX) {
					Array(unipack.buttonY) {
						0
					}
				}
			}

			listener.onStart(soundCount)

			try {
				for (i in 0 until unipack.chain) {
					for (j in 0 until unipack.buttonX) {
						for (k in 0 until unipack.buttonY) {
							val arrayList: ArrayList<*>? = unipack.soundTable!![i][j][k]
							if (arrayList != null) {
								for (l in arrayList.indices) {
									val e: Sound = unipack.soundTable!![i][j][k]!![l]
									e.id = soundPool!!.load(e.file.path, 1)
									listener.onProgressTick()
								}
							}
						}
					}
				}
				listener.onEnd()

			} catch (e: Exception) {
				Log.err("[08] doInBackground")
				e.printStackTrace()
				listener.onException(e)
			}
		}
	}

	fun soundOn(x: Int, y: Int) {
		soundPool!!.stop(stopID!![chain.value][x][y])
		val e: Sound? = unipack.Sound_get(chain.value, x, y)
		if (e != null) {
			stopID!![chain.value][x][y] = soundPool!!.play(e.id, 1.0f, 1.0f, 0, e.loop, 1.0f)
			unipack.Sound_push(chain.value, x, y)
			if (e.wormhole != -1)
				Handler().postDelayed({ chain.value = e.wormhole }, 100)
		}
	}

	fun soundOff(x: Int, y: Int){
		if (unipack.Sound_get(chain.value, x, y)!!.loop == -1)
			soundPool!!.stop(stopID!![chain.value][x][y])
	}

	fun destroy(){
		if (soundPool != null) {
			for (i in unipack.soundTable!!)
				for(j in i)
					for(arrayList in j){
						if (arrayList != null) {
							for (sound in arrayList) {
								try {
									soundPool!!.unload(sound.id)
								} catch (e: Exception) {
									e.printStackTrace()
								}
							}
						}
					}
			soundPool!!.release()
			soundPool = null
		}
	}
}