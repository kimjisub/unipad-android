package com.kimjisub.launchpad.tool

import android.media.MediaPlayer
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UniPackAutoMapper(
	private val unipack: UniPack,
	private val listener: Listener,
	scope: CoroutineScope,
) {
	private val autoplay1: MutableList<AutoPlay.Element> = mutableListOf()
	private val autoplay2: MutableList<AutoPlay.Element> = mutableListOf()
	private val autoplay3: MutableList<AutoPlay.Element> = mutableListOf()

	interface Listener {
		fun onStart()
		fun onGetWorkSize(size: Int)
		fun onProgress(progress: Int)
		fun onDone()

		fun onException(throwable: Throwable)
	}

	init {
		scope.launch(Dispatchers.IO) {
			try {
				withContext(Dispatchers.Main) { listener.onStart() }

				val autoPlayTable = unipack.autoPlayTable ?: return@launch
			for (e: AutoPlay.Element in autoPlayTable.elements) {
					when (e) {
						is AutoPlay.Element.On -> autoplay1.add(e)
						is AutoPlay.Element.Off -> {
						}

						is AutoPlay.Element.Chain -> autoplay1.add(e)
						is AutoPlay.Element.Delay -> autoplay1.add(e)
					}
				}

				var prevDelay: AutoPlay.Element.Delay? = AutoPlay.Element.Delay(0)
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
						else -> {}
					}
				}

				withContext(Dispatchers.Main) { listener.onGetWorkSize(autoplay2.size) }

				var nextDuration = 1000
				val mplayer = MediaPlayer()
				try {
					for ((i, e) in autoplay2.withIndex()) {

						try {
							when (e) {
								is AutoPlay.Element.On -> {
									val sounds = unipack.soundTable?.get(e.currChain)?.get(e.x)?.get(e.y)
										?: continue
									val num = e.num % sounds.size
									nextDuration = FileManager.wavDuration(
										mplayer,
										sounds[num].file.path
									)
									autoplay3.add(e)
								}

								is AutoPlay.Element.Chain -> autoplay3.add(e)
								is AutoPlay.Element.Delay -> {
									e.delay = nextDuration + Constant.AUTOPLAY_AUTOMAPPING_DELAY_PRESET
									autoplay3.add(e)
								}
								else -> {}
							}
						} catch (ee: Exception) {
							Log.err("AutoMapper element processing failed", ee)
						}
						withContext(Dispatchers.Main) { listener.onProgress(i) }
					}
				} finally {
					mplayer.release()
				}

				withContext(Dispatchers.Main) { listener.onDone() }

			} catch (e: Throwable) {
				Log.err("AutoMapper failed", e)
				withContext(Dispatchers.Main) { listener.onException(e) }
			}
		}
	}
}
