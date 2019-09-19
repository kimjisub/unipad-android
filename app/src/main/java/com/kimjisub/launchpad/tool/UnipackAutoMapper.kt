package com.kimjisub.launchpad.tool

import android.annotation.SuppressLint
import android.media.MediaPlayer
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.Unipack
import com.kimjisub.manager.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class UnipackAutoMapper(
	private val unipack: Unipack,
	private var listener: Listener
) {
	private var autoplay1: ArrayList<Unipack.AutoPlay> = ArrayList()
	private var autoplay2: ArrayList<Unipack.AutoPlay> = ArrayList()
	private var autoplay3: ArrayList<Unipack.AutoPlay> = ArrayList()

	interface Listener {
		fun onStart()
		fun onGetWorkSize(size: Int)
		fun onProgress(progress: Int)
		fun onDone()

		fun onException(throwable: Throwable)
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { listener.onStart() }

				for (e: Unipack.AutoPlay in unipack.autoPlayTable) {
					when (e.func) {
						Unipack.AutoPlay.ON -> autoplay1.add(e)
						Unipack.AutoPlay.OFF -> {
						}
						Unipack.AutoPlay.CHAIN -> autoplay1.add(e)
						Unipack.AutoPlay.DELAY -> autoplay1.add(e)
					}
				}

				var prevDelay: Unipack.AutoPlay? = Unipack.AutoPlay(0, 0)
				for (e: Unipack.AutoPlay in autoplay1) {
					when (e.func) {
						Unipack.AutoPlay.ON -> {
							if (prevDelay != null) {
								autoplay2.add(prevDelay)
								prevDelay = null
							}
							autoplay2.add(e)
						}
						Unipack.AutoPlay.CHAIN -> autoplay2.add(e)
						Unipack.AutoPlay.DELAY -> if (prevDelay != null) prevDelay.d += e.d else prevDelay = e
					}
				}

				withContext(Dispatchers.Main) { listener.onGetWorkSize(autoplay2.size) }

				var nextDuration = 1000
				val mplayer = MediaPlayer()
				for ((i, e) in autoplay2.withIndex()) {

					try {
						when (e.func) {
							Unipack.AutoPlay.ON -> {
								val num = e.num % unipack.soundTable[e.currChain][e.x][e.y].size
								nextDuration = FileManager.wavDuration(mplayer, unipack.soundTable[e.currChain][e.x][e.y][num].file.path)
								autoplay3.add(e)
							}
							Unipack.AutoPlay.CHAIN -> autoplay3.add(e)
							Unipack.AutoPlay.DELAY -> {
								e.d = nextDuration + Constant.AUTOPLAY_AUTOMAPPING_DELAY_PRESET
								autoplay3.add(e)
							}
						}
					} catch (ee: Exception) {
						ee.printStackTrace()
					}
					withContext(Dispatchers.Main) { listener.onProgress(i) }
				}
				mplayer.release()
				val stringBuilder = StringBuilder()
				for (e: Unipack.AutoPlay in autoplay3) {
					when (e.func) {
						Unipack.AutoPlay.ON -> //int num = e.num % unipack.soundTable[e.currChain][e.x][e.y].size();
							stringBuilder.append("t ").append(e.x + 1).append(" ").append(e.y + 1).append("\n")
						Unipack.AutoPlay.CHAIN -> stringBuilder.append("c ").append(e.c + 1).append("\n")
						Unipack.AutoPlay.DELAY -> stringBuilder.append("d ").append(e.d).append("\n")
					}
				}
				try {
					val filePre = File(unipack.F_project, "autoPlay")
					@SuppressLint("SimpleDateFormat") val fileNow = File(
						unipack.F_project,
						"autoPlay_" + SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(Date(System.currentTimeMillis()))
					)
					filePre.renameTo(fileNow)
					val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(unipack.F_autoPlay)))
					writer.write(stringBuilder.toString())
					writer.close()
				} catch (e: FileNotFoundException) {
					e.printStackTrace()
				} catch (ee: IOException) {
					ee.printStackTrace()
				}







				withContext(Dispatchers.Main) { listener.onDone() }

			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { listener.onException(e) }
			}
		}
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)
}