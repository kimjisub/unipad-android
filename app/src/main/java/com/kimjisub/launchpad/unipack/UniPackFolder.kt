package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.manager.LaunchpadColor.ARGB
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.Sound
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Tokeniser shared by the text tables. `\s` in Java regex is ASCII-only, so a line separated by a
 * no-break space (U+00A0) or an ideographic space (U+3000, Korean IME) was one token and dropped,
 * while iOS and web split it. The class lists the Unicode space separators explicitly.
 */
private val TOKEN_SPLIT = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]+")

/** trim() plus the UTF-8 BOM, which Windows Notepad puts before the first key. */
private fun String.trimLine(): String = trim { it.isWhitespace() || it == '\uFEFF' }

/**
 * A reader that honours a BOM: UTF-16 (FE FF / FF FE) as iOS does, UTF-8 with the BOM skipped,
 * otherwise UTF-8 (Android's platform default). A UTF-16 info used to decode to garbage and reject
 * the pack here while it played on iOS.
 */
private fun bomAwareReader(input: InputStream): Reader {
	val buffered = BufferedInputStream(input)
	buffered.mark(3)
	val b = ByteArray(3)
	val n = buffered.read(b)
	val charset: Charset
	val skip: Int
	when {
		n >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> { charset = StandardCharsets.UTF_16BE; skip = 2 }
		n >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> { charset = StandardCharsets.UTF_16LE; skip = 2 }
		n >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() -> { charset = StandardCharsets.UTF_8; skip = 3 }
		else -> { charset = StandardCharsets.UTF_8; skip = 0 }
	}
	buffered.reset()
	var toSkip = skip.toLong()
	while (toSkip > 0) toSkip -= buffered.skip(toSkip)
	return InputStreamReader(buffered, charset)
}

class UniPackFolder(val rootFolder: File) : UniPack() {
	private companion object {
		const val MAX_GRID_SIZE = 64
		val ROUND_TOKENS = setOf("*", "mc")
		const val LOGO_TOKEN = "l"
		val AUTO_TOKENS = setOf("auto", "a")

		fun rgbColor(token: String): Int {
			// Longer tokens can parse as Int but carry into the alpha byte.
			if (token.length > 6) throw NumberFormatException("RGB color exceeds six characters")
			return token.toInt(16) + -0x1000000
		}

		/**
		 * `mc 1`..`mc 32` as a round LED index. Anything else is dropped without a warning, as the drivers
		 * dropped it before; index 32 would otherwise light the logo.
		 */
		fun roundLedIndex(token: String): Int? =
			(token.toInt() - 1).takeIf { it in 0 until DriverRef.ROUND_BUTTON_COUNT }

		/**
		 * The colour tokens of a logo line, read as iOS reads them: `o l color`, `o l auto velocity`, and the
		 * forms with a placeholder where other lines have y, `o l _ color` and `o l _ color|auto velocity`.
		 */
		fun logoColorTokens(split: Array<String>): List<String> = when (split.size) {
			3 -> listOf(split[2])
			4 -> if (split[2] in AUTO_TOKENS) listOf(split[2], split[3]) else listOf(split[3])
			5 -> listOf(split[3], split[4])
			else -> emptyList()
		}
	}

	private var infoFile: File? = null
	private var soundsDir: File? = null
	private var keySoundFile: File? = null
	private var keyLedDir: File? = null
	var autoPlayFile: File? = null
		private set
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
		return loadDetailWithProgress { _, _, _ -> }
	}

	override fun loadDetailWithProgress(onPhase: (String, Int, Int) -> Unit): UniPack {
		if (!criticalError) {
			if (!detailLoaded) {
				val totalPhases = 3
				onPhase("keySound", 0, totalPhases)
				keySound()
				onPhase("keyLed", 1, totalPhases)
				keyLed()
				onPhase("autoPlay", 2, totalPhases)
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

	override fun delete(): Boolean {
		FileManager.deleteDirectory(rootFolder)
		return !rootFolder.exists()
	}

	override fun getPathString(): String {
		return rootFolder.path
	}

	private fun info() {
		val file = infoFile ?: return
		val inputStream = try {
			FileInputStream(file)
		} catch (e: FileNotFoundException) {
			addErr("info : file was not found")
			criticalError = true
			return
		}
		BufferedReader(bomAwareReader(inputStream)).use { reader ->
			while (true) {
				val s = reader.readLine()?.trimLine() ?: break
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
				} catch (e: NumberFormatException) {
					// Used to propagate out of load(): the pack vanished from the library with no
					// message (WorkspaceManager's catch). iOS/web record it and carry on.
					addErr("info : [$s] format is incorrect")
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
		// Same bound as iOS: a negative value threw NegativeArraySizeException in the table
		// allocation and a huge one allocated chain * x * y cells.
		if (buttonX !in 0..MAX_GRID_SIZE || buttonY !in 0..MAX_GRID_SIZE) {
			addErr("info : buttonX/buttonY out of range")
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
		val inputStream = try {
			FileInputStream(keySoundFile)
		} catch (e: FileNotFoundException) {
			addErr("keySound : file was not found")
			criticalError = true
			return
		}
		BufferedReader(bomAwareReader(inputStream)).use { reader ->
			while (true) {
				val s = reader.readLine()?.trimLine() ?: break
				if (s.isEmpty()) continue
				val split = s.trim().split(TOKEN_SPLIT).toTypedArray()
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
					val split1 = fileName.trim().split(TOKEN_SPLIT).toTypedArray()
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
					val inputStream = try {
						FileInputStream(file)
					} catch (e: FileNotFoundException) {
						addErr("keyLed : [$fileName] file was not found")
						continue
					}
					BufferedReader(bomAwareReader(inputStream)).use { reader ->
						loop@ while (true) {
							val s = reader.readLine()?.trimLine() ?: break
							if (s.isEmpty()) continue@loop
							val split2 = s.trim().split(TOKEN_SPLIT).toTypedArray()
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
										val colorTokens: List<String>
										if (xToken in ROUND_TOKENS) {
											// Round/chain LED: o * {y} ... or o mc {y} ...
											ledX = -1
											ledY = roundLedIndex(split2[2]) ?: continue@loop
											colorTokens = split2.drop(3)
										} else if (xToken == LOGO_TOKEN) {
											ledX = -1
											ledY = DriverRef.LOGO_FUNCTION_KEY
											colorTokens = logoColorTokens(split2)
										} else {
											ledX = xToken.toInt() - 1
											ledY = split2[2].toInt() - 1
											colorTokens = split2.drop(3)
										}
										when (colorTokens.size) {
											1 -> ledColor = rgbColor(colorTokens[0])
											2 -> {
												ledVelocity = colorTokens[1].toInt()
												ledColor = if (colorTokens[0] in AUTO_TOKENS) ARGB[ledVelocity].toInt()
												else rgbColor(colorTokens[0])
											}
											else -> {
												addErr("keyLed : [$fileName].[$s] format is incorrect")
												continue@loop
											}
										}
									}

									"off", "f" -> {
										val xToken = split2[1]
										if (xToken in ROUND_TOKENS) {
											ledX = -1
											ledY = roundLedIndex(split2[2]) ?: continue@loop
										} else if (xToken == LOGO_TOKEN) {
											ledX = -1
											ledY = DriverRef.LOGO_FUNCTION_KEY
										} else {
											ledX = xToken.toInt() - 1
											ledY = split2[2].toInt() - 1
										}
									}

									"delay", "d" -> ledDelay = split2[1].toInt()
									"chain", "c" -> {
										val chainValue = split2[1].toInt() - 1
										ledList.add(LedAnimation.LedEvent.Chain(chainValue))
										continue@loop
									}
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

	fun reloadAutoPlay() {
		autoPlay()
	}

	private fun autoPlay() {
		val autoPlayFile = autoPlayFile ?: return
		val autoPlay = AutoPlay(ArrayList())
		autoPlayTable = autoPlay
		val map = Array(buttonX) { IntArray(buttonY) }
		var currChain = 0
		val inputStream = try {
			FileInputStream(autoPlayFile)
		} catch (e: FileNotFoundException) {
			addErr("autoPlay : file was not found")
			return
		}
		BufferedReader(bomAwareReader(inputStream)).use { reader ->
			loop@ while (true) {
				val s = reader.readLine()?.trimLine() ?: break
				if (s.isEmpty()) continue@loop
				val split = s.trim().split(TOKEN_SPLIT).toTypedArray()
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