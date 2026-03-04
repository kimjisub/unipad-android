package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.manager.LaunchpadColor.ARGB
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.Sound
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class UniPackFolder(private val rootFolder: File) : UniPack() {

	private var infoFile: File? = null
	private var soundsDir: File? = null
	private var keySoundFile: File? = null
	private var keyLedDir: File? = null
	private var autoPlayFile: File? = null
	override val id: String
		get() = rootFolder.name

	override val keyLedExist
		get() = keyLedDir != null
	override val autoPlayExist
		get() = autoPlayFile != null

	override fun lastModified(): Long {
		return FileManager.getInnerFileLastModified(rootFolder)
	}

	override fun loadInfo(): UniPack {
		if (!criticalError) {
			info()
		}

		return this
	}

	override fun loadDetail(): UniPack {
		if (!criticalError) {
			if (!detailLoaded) {
				keySound()
				keyLed()
				autoPlay()
				detailLoaded = true
			}
		}

		return this
	}

	override fun toString(): String {
		return "UniPackFolder(folderName=${rootFolder.name})"
	}

	override fun checkFile() {
		rootFolder.listFiles()?.forEach {
			when (it.name.lowercase()) {
				"info" -> infoFile = if (it.isFile) it else null
				"sounds" -> soundsDir = if (it.isDirectory) it else null
				"keysound" -> keySoundFile = if (it.isFile) it else null
				"keyled" -> keyLedDir = if (it.isDirectory) it else null
				"autoplay" -> autoPlayFile = if (it.isFile) it else null
			}
		}

		if (infoFile == null) addErr("info doesn't exist")
		if (keySoundFile == null) addErr("keySound doesn't exist")
		if (infoFile == null && keySoundFile == null) addErr("It does not seem to be UniPack.")

		if (infoFile == null || keySoundFile == null)
			criticalError = true
	}

	override fun delete() {
		FileManager.deleteDirectory(rootFolder)
	}

	override fun getPathString(): String {
		return rootFolder.path
	}

	private fun info() {
		val file = infoFile ?: return
		BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
			while (true) {
				val s = reader.readLine()?.trim() ?: break
				if (s.isEmpty()) continue
				try {
					val split = s.split("=", limit = 2)
					val key = split[0].trim()
					val value = split[1].trim()
					when (key) {
						"title" -> title = value
						"producerName" -> producerName = value
						"buttonX" -> buttonX = value.toInt()
						"buttonY" -> buttonY = value.toInt()
						"chain" -> chain = value.toInt()
						"squareButton" -> squareButton = value == "true"
						"website" -> website = value
					}
				} catch (e: IndexOutOfBoundsException) {
					addErr("info : [$s] format is not found")
				}
			}
		}
		if (title.isEmpty()) addErr("info : title was missing")
		if (producerName.isEmpty()) addErr("info : producerName was missing")
		if (buttonX == 0) addErr("info : buttonX was missing")
		if (buttonY == 0) addErr("info : buttonY was missing")
		if (chain == 0) addErr("info : chain was missing")
		if (chain !in 1..24) {
			addErr("info : chain out of range")
			criticalError = true
		}
	}

	private fun keySound() {
		val keySoundFile = keySoundFile ?: return
		val soundsDir = soundsDir
		val table = Array(chain) {
			Array(buttonX) {
				arrayOfNulls<ArrayDeque<Sound>>(buttonY)
			}
		}
		soundTable = table
		soundCount = 0
		BufferedReader(InputStreamReader(FileInputStream(keySoundFile))).use { reader ->
			while (true) {
				val s = reader.readLine()?.trim() ?: break
				if (s.isEmpty()) continue
				val split = s.trim().split("\\s+".toRegex()).toTypedArray()
				var c: Int
				var x: Int
				var y: Int
				var soundURL: String
				var loop = 0
				var wormhole = Sound.NO_WORMHOLE
				try {
					if (split.size <= 2) continue
					c = split[0].toInt() - 1
					x = split[1].toInt() - 1
					y = split[2].toInt() - 1
					soundURL = split[3]
					if (split.size >= 5) loop = split[4].toInt() - 1
					if (split.size >= 6) {
						loop = split[4].toInt() - 1
						wormhole = split[5].toInt() - 1
					}
				} catch (e: NumberFormatException) {
					addErr("keySound : [$s] format is incorrect")
					continue
				} catch (e: IndexOutOfBoundsException) {
					addErr("keySound : [$s] format is incorrect")
					continue
				}
				if (c < 0 || c >= chain)
					addErr("keySound : [$s] chain is incorrect")
				else if (x < 0 || x >= buttonX)
					addErr("keySound : [$s] x is incorrect")
				else if (y < 0 || y >= buttonY) addErr(
					"keySound : [$s] y is incorrect"
				) else {
					try {
						if (soundsDir == null) {
							addErr("keySound : [$s] sounds directory not found")
							continue
						}
						val soundFile = File(soundsDir, soundURL)
						val sound = Sound(soundFile, loop, wormhole)
						if (!sound.file.isFile) {
							addErr("keySound : [$s] sound was not found")
							continue
						}
						if (table[c][x][y] == null)
							table[c][x][y] = ArrayDeque()
						sound.num = table[c][x][y]?.size ?: 0
						table[c][x][y]?.addLast(sound)
						soundCount++
					} catch (e: Exception) {
						Log.err("keySound parse error: [$s]", e)
						addErr("keySound : [$s] sound was not found")
						continue
					}
				}
			}
		}
	}

	private fun keyLed() {
		val keyLedDir = keyLedDir ?: return
		val table = Array(chain) {
			Array(buttonX) {
				arrayOfNulls<ArrayDeque<LedAnimation>?>(buttonY)
			}
		}
		ledAnimationTable = table
		ledTableCount = 0
		run {
			val fileList = (keyLedDir.listFiles() ?: return).sortedBy { it.name.lowercase() }
			for (file in fileList) {
				if (file.isFile) {
					val fileName: String = file.name.trim()
					val split1 = fileName.trim().split("\\s+".toRegex()).toTypedArray()
					var c: Int
					var x: Int
					var y: Int
					var loop = 1
					try {
						if (split1.size <= 2) continue
						c = split1[0].toInt() - 1
						x = split1[1].toInt() - 1
						y = split1[2].toInt() - 1
						if (split1.size >= 4) loop = split1[3].toInt()
						if (c < 0 || c >= chain) {
							addErr("keyLed : [$fileName] chain is incorrect")
							continue
						} else if (x < 0 || x >= buttonX) {
							addErr("keyLed : [$fileName] x is incorrect")
							continue
						} else if (y < 0 || y >= buttonY) {
							addErr("keyLed : [$fileName] y is incorrect")
							continue
						} else if (loop < 0) {
							addErr("keyLed : [$fileName] loop is incorrect")
							continue
						}
					} catch (e: NumberFormatException) {
						addErr("keyLed : [$fileName] format is incorrect")
						continue
					} catch (e: IndexOutOfBoundsException) {
						addErr("keyLed : [$fileName] format is incorrect")
						continue
					}
					val ledList = ArrayList<LedAnimation.LedEvent>()
					BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
						loop@ while (true) {
							val s = reader.readLine()?.trim() ?: break
							if (s.isEmpty()) continue@loop
							val split2 = s.trim().split("\\s+".toRegex()).toTypedArray()
							var option: String
							var ledX = -1
							var ledY = -1
							var ledColor = -1
							var ledVelocity = 4
							var ledDelay = -1
							try {
								option = split2[0]
								when (option) {
									"on", "o" -> {
										val xToken = split2[1]
										if (xToken == "*" || xToken == "mc") {
											// Round/chain LED: o * {y} ... or o mc {y} ...
											ledX = -1
											ledY = split2[2].toInt() - 1
										} else if (xToken == "l") {
											// Logo LED: o l ... — not supported, skip
											continue@loop
										} else {
											ledX = xToken.toInt() - 1
											ledY = split2[2].toInt() - 1
										}
										if (split2.size == 4) ledColor =
											split2[3].toInt(16) + -0x1000000 else if (split2.size == 5) {
											if (split2[3] == "auto" || split2[3] == "a") {
												ledVelocity = split2[4].toInt()
												ledColor = ARGB[ledVelocity].toInt()
											} else {
												ledVelocity = split2[4].toInt()
												ledColor = split2[3].toInt(16) + -0x1000000
											}
										} else {
											addErr("keyLed : [$fileName].[$s] format is incorrect")
											continue@loop
										}
									}

									"off", "f" -> {
										val xToken = split2[1]
										if (xToken == "*" || xToken == "mc") {
											ledX = -1
											ledY = split2[2].toInt() - 1
										} else if (xToken == "l") {
											continue@loop
										} else {
											ledX = xToken.toInt() - 1
											ledY = split2[2].toInt() - 1
										}
									}

									"delay", "d" -> ledDelay = split2[1].toInt()
									"chain", "c" -> continue@loop // Unitor extension: chain command in keyLed, skip
									else -> {
										addErr("keyLed : [$fileName].[$s] format is incorrect")
										continue@loop
									}
								}
							} catch (e: NumberFormatException) {
								addErr("keyLed : [$fileName].[$s] format is incorrect")
								continue
							} catch (e: IndexOutOfBoundsException) {
								addErr("keyLed : [$fileName].[$s] format is incorrect")
								continue
							}
							when (option) {
								"on", "o" -> ledList.add(
									LedAnimation.LedEvent.On(
										ledX,
										ledY,
										ledColor,
										ledVelocity
									)
								)

								"off", "f" -> ledList.add(LedAnimation.LedEvent.Off(ledX, ledY))
								"delay", "d" -> ledList.add(LedAnimation.LedEvent.Delay(ledDelay))
							}
						}
					}
					if (table[c][x][y] == null)
						table[c][x][y] = ArrayDeque()
					table[c][x][y]?.addLast(
						LedAnimation(
							ledList,
							loop,
							table[c][x][y]?.size ?: 0
						)
					)
					ledTableCount++
				} else addErr("keyLed : ${file.name} is not file")
			}
		}
	}

	private fun autoPlay() {
		val autoPlayFile = autoPlayFile ?: return
		val autoPlay = AutoPlay(ArrayList())
		autoPlayTable = autoPlay
		val map = Array(buttonX) { IntArray(buttonY) }
		var currChain = 0
		BufferedReader(InputStreamReader(FileInputStream(autoPlayFile))).use { reader ->
			loop@ while (true) {
				val s = reader.readLine()?.trim() ?: break
				if (s.isEmpty()) continue@loop
				val split = s.trim().split("\\s+".toRegex()).toTypedArray()
				var option: String
				var x = -1
				var y = -1
				var chain = -1
				var delay = -1
				try {
					option = split[0]
					when (option) {
						"on", "o" -> {
							x = split[1].toInt() - 1
							y = split[2].toInt() - 1
							if (x < 0 || x >= buttonX) {
								addErr("autoPlay : [$s] x is incorrect")
								continue@loop
							}
							if (y < 0 || y >= buttonY) {
								addErr("autoPlay : [$s] y is incorrect")
								continue@loop
							}
						}

						"off", "f" -> {
							x = split[1].toInt() - 1
							y = split[2].toInt() - 1
							if (x < 0 || x >= buttonX) {
								addErr("autoPlay : [$s] x is incorrect")
								continue@loop
							} else if (y < 0 || y >= buttonY) {
								addErr("autoPlay : [$s] y is incorrect")
								continue@loop
							}
						}

						"touch", "t" -> {
							x = split[1].toInt() - 1
							y = split[2].toInt() - 1
							if (x < 0 || x >= buttonX) {
								addErr("autoPlay : [$s] x is incorrect")
								continue@loop
							} else if (y < 0 || y >= buttonY) {
								addErr("autoPlay : [$s] y is incorrect")
								continue@loop
							}
						}

						"chain", "c" -> {
							chain = split[1].toInt() - 1
							if (chain < 0 || chain >= this.chain) {
								addErr("autoPlay : [$s] chain is incorrect")
								continue@loop
							}
						}

						"delay", "d" -> delay = split[1].toInt()
						else -> {
							addErr("autoPlay : [$s] format is incorrect")
							continue@loop
						}
					}
				} catch (e: NumberFormatException) {
					addErr("autoPlay : [$s] format is incorrect")
					continue
				} catch (e: IndexOutOfBoundsException) {
					addErr("autoPlay : [$s] format is incorrect")
					continue
				}
				when (option) {
					"on", "o" -> {
						autoPlay.elements.add(
							AutoPlay.Element.On(
								x,
								y,
								currChain,
								map[x][y]
							)
						)
						val sound = soundGet(currChain, x, y, map[x][y])
						map[x][y]++
						if (sound != null && sound.wormhole != Sound.NO_WORMHOLE) {
							autoPlay.elements.add(AutoPlay.Element.Chain(sound.wormhole.also {
								currChain = it
							}))
							map.forEach { row -> row.fill(0) }
						}
					}

					"off", "f" -> autoPlay.elements.add(
						AutoPlay.Element.Off(
							x,
							y,
							currChain
						)
					)

					"touch", "t" -> {
						autoPlay.elements.add(
							AutoPlay.Element.On(
								x,
								y,
								currChain,
								map[x][y]
							)
						)
						autoPlay.elements.add(AutoPlay.Element.Off(x, y, currChain))
						map[x][y]++
					}

					"chain", "c" -> {
						autoPlay.elements.add(AutoPlay.Element.Chain(chain.also {
							currChain = it
						}))
						map.forEach { row -> row.fill(0) }
					}

					"delay", "d" -> autoPlay.elements.add(
						AutoPlay.Element.Delay(
							delay
						)
					)
				}
			}
		}
	}

	override fun getByteSize(): Long {
		return getFolderSize(rootFolder)
	}

	private fun getFolderSize(file: File): Long {
		var totalMemory: Long = 0
		if (file.isFile) {
			return file.length()
		} else if (file.isDirectory) {
			val childFileList: Array<out File> = file.listFiles() ?: return 0
			for (childFile in childFileList) totalMemory += getFolderSize(childFile)
			return totalMemory
		} else return 0
	}

}