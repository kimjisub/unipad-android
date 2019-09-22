package com.kimjisub.launchpad.unipack.runner

import android.app.ProgressDialog
import android.media.AudioManager
import android.media.SoundPool
import android.os.AsyncTask
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.struct.Sound
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class SoundRunner(unipack: Unipack, listener: Listener) {

	private var soundPool: SoundPool? = null
	private var stopID: Array<Array<Array<Int>>>? = null

	interface Listener {
		fun onStart(soundCount:Int, soundPool: SoundPool, stopID: Array<Array<Array<Int>>>)
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

			listener.onStart(soundCount, soundPool!!, stopID!!)

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
}