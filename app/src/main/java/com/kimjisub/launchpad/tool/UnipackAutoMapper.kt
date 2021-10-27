package com.kimjisub.launchpad.tool

import android.annotation.SuppressLint
import android.media.MediaPlayer
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.manager.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class UniPackAutoMapper(
	private val unipack: UniPack,
	private var listener: Listener
) {
	private var autoplay1: ArrayList<AutoPlay.Element> = ArrayList()
	private var autoplay2: ArrayList<AutoPlay.Element> = ArrayList()
	private var autoplay3: ArrayList<AutoPlay.Element> = ArrayList()

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

				for (e: AutoPlay.Element in unipack.autoPlayTable!!.elements) {
					when (e) {
						is AutoPlay.Element.On -> autoplay1.add(e)
						is AutoPlay.Element.Off -> {
						}
						is AutoPlay.Element.Chain -> autoplay1.add(e)
						is AutoPlay.Element.Delay -> autoplay1.add(e)
					}
				}

				var prevDelay: AutoPlay.Element.Delay? = AutoPlay.Element.Delay(0, 0)
				for (e: AutoPlay.Element in autoplay1) {
					when (e) {
						is AutoPlay.Element.On -> {
							if (prevDelay != null) {
								autoplay2.add(prevDelay)
								prevDelay = null
							}
							autoplay2.add(e)
						}
						is AutoPlay.Element.Chain -> autoplay2.add(e)
						is AutoPlay.Element.Delay -> if (prevDelay != null) prevDelay.delay += e.delay else prevDelay =
							e
					}
				}

				withContext(Dispatchers.Main) { listener.onGetWorkSize(autoplay2.size) }

				var nextDuration = 1000
				val mplayer = MediaPlayer()
				for ((i, e) in autoplay2.withIndex()) {

					try {
						when (e) {
							is AutoPlay.Element.On -> {
								val num = e.num % unipack.soundTable!![e.currChain][e.x][e.y]!!.size
								nextDuration = FileManager.wavDuration(
									mplayer,
									unipack.soundTable!![e.currChain][e.x][e.y]!![num].file.path
								)
								autoplay3.add(e)
							}
							is AutoPlay.Element.Chain -> autoplay3.add(e)
							is AutoPlay.Element.Delay -> {
								e.delay = nextDuration + Constant.AUTOPLAY_AUTOMAPPING_DELAY_PRESET
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
				for (e: AutoPlay.Element in autoplay3) {
					when (e) {
						is AutoPlay.Element.On -> //int num = e.num % unipack.soundTable[e.currChain][e.x][e.y].size();
							stringBuilder.append("t ").append(e.x + 1).append(" ").append(e.y + 1)
								.append("\n")
						is AutoPlay.Element.Chain -> stringBuilder.append("c ").append(e.c + 1)
							.append("\n")
						is AutoPlay.Element.Delay -> stringBuilder.append("d ").append(e.delay)
							.append("\n")
					}
				}
				try {
					/* todo 수정
					val filePre = File(unipack.F_project, "autoPlay")

					@SuppressLint("SimpleDateFormat") val fileNow = File(
						unipack.F_project,
						"autoPlay_" + SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(Date(System.currentTimeMillis()))
					)
					filePre.renameTo(fileNow)
					val writer =
						BufferedWriter(OutputStreamWriter(FileOutputStream(unipack.F_autoPlay)))
					writer.write(stringBuilder.toString())
					writer.close()*/
				} catch (e: FileNotFoundException) {
					e.printStackTrace()
				} catch (ee: IOException) {
					ee.printStackTrace()
				}

				withContext(Dispatchers.Main) { listener.onDone() }

			} catch (e: Throwable) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { listener.onException(e) }
			}
		}
	}
}