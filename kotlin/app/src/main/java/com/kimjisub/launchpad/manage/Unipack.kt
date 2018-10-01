package com.kimjisub.launchpad.manage

import android.content.Context
import com.kimjisub.launchpad.BaseActivity
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manage.Tools.logErr
import java.io.*
import java.util.*

class Unipack(var URL: String, loadDetail: Boolean) {

	var ErrorDetail: String? = null
	var CriticalError = false

	var isInfo = false
	var isSounds = false
	var isKeySound = false
	var isKeyLED = false
	var isAutoPlay = false

	var title: String? = null
	var producerName: String? = null
	var buttonX = 0
	var buttonY = 0
	var chain = 0
	var squareButton = true
	var websiteURL: String? = null

	//========================================================================================== Sound

	var sound: Array<Array<Array<ArrayList<Sound>>>>? = null

	class Sound {
		var URL: String = ""
		var loop = -1
		var wormhole = -1

		var num: Int = 0
		var id = -1

		internal constructor(URL: String, loop: Int, wormhole: Int) {
			this.URL = URL
			this.loop = loop
			this.wormhole = wormhole
		}

		constructor() {}
	}

	fun Sound_push(c: Int, x: Int, y: Int) {
		//log("Sound_push (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			val tmp = sound!![c][x][y][0]
			sound!![c][x][y].removeAt(0)
			sound!![c][x][y].add(tmp)
		} catch (ignored: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			logErr("Sound_push ($c, $x, $y)")
			ee.printStackTrace()
		}

	}

	fun Sound_push(c: Int, x: Int, y: Int, num: Int) {
		//log("Sound_push (" + c + ", " + buttonX + ", " + buttonY + ", " + num + ")");
		try {
			val e = sound!![c][x][y]
			if (sound!![c][x][y][0].num != num)
				while (true) {
					val tmp = e[0]
					e.removeAt(0)
					e.add(tmp)
					if (e[0].num == num % e.size)
						break
				}
		} catch (ignored: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			logErr("Sound_push ($c, $x, $y, $num)")
			ee.printStackTrace()
		} catch (ee: ArithmeticException) {
			logErr("ArithmeticException : Sound_push ($c, $x, $y, $num)")
			ee.printStackTrace()
		}

	}

	fun Sound_get(c: Int, x: Int, y: Int): Sound {
		//log("Sound_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			return sound!![c][x][y][0]
		} catch (ignored: NullPointerException) {
			return Sound()
		} catch (ee: IndexOutOfBoundsException) {
			logErr("Sound_get ($c, $x, $y)")
			ee.printStackTrace()
			return Sound()
		}

	}

	fun Sound_get(c: Int, x: Int, y: Int, num: Int): Sound {
		//log("Sound_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			val e = sound!![c][x][y]
			return sound!![c][x][y][num % e.size]
		} catch (ignored: NullPointerException) {
			return Sound()
		} catch (ee: IndexOutOfBoundsException) {
			logErr("Sound_get ($c, $x, $y)")
			ee.printStackTrace()
			return Sound()
		}

	}

	//==========================================================================================

	var led: Array<Array<Array<ArrayList<LED>>>>? = null

	class LED internal constructor(
		var syntaxs: ArrayList<Syntax>,
		var loop: Int,
		var num: Int
	) {
		class Syntax {
			var func = 0
			var x: Int = 0
			var y: Int = 0
			var color = -1
			var velo = 119
			var delay = -1

			internal constructor(x: Int, y: Int, color: Int, velo: Int) {
				this.func = ON
				this.x = x
				this.y = y
				this.color = color
				this.velo = velo
			}

			internal constructor(x: Int, y: Int) {
				this.func = OFF
				this.x = x
				this.y = y
			}

			internal constructor(d: Int) {
				this.func = DELAY
				this.delay = d
			}

			companion object {
				val ON = 1
				val OFF = 2
				val DELAY = 3
			}
		}
	}

	fun LED_push(c: Int, x: Int, y: Int) {
		//log("LED_push (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			val e = led!![c][x][y][0]
			led!![c][x][y].removeAt(0)
			led!![c][x][y].add(e)
		} catch (ignored: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			logErr("LED_push ($c, $x, $y)")
			ee.printStackTrace()
		}

	}

	fun LED_push(c: Int, x: Int, y: Int, num: Int) {
		//log("LED_push (" + c + ", " + buttonX + ", " + buttonY + ", " + num + ")");
		try {
			val e = led!![c][x][y]
			if (e[0].num != num)
				while (true) {
					val tmp = e[0]
					e.removeAt(0)
					e.add(tmp)
					if (e[0].num == num % e.size)
						break
				}
		} catch (ignored: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			logErr("LED_push ($c, $x, $y, $num)")
			ee.printStackTrace()
		}

	}

	fun LED_get(c: Int, x: Int, y: Int): LED? {
		//log("LED_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			return led!![c][x][y][0]
		} catch (ignored: NullPointerException) {
			return null
		} catch (ee: IndexOutOfBoundsException) {
			logErr("LED_get ($c, $x, $y)")
			ee.printStackTrace()
			return null
		}

	}

	//==========================================================================================

	var autoPlay: ArrayList<AutoPlay>? = null

	internal val mainAutoplay: String?
		get() {
			val fileList = FileManager.sortByName(File(URL).listFiles())
			for (f in fileList) {
				if (f.isFile && f.name.toLowerCase().startsWith("autoplay"))
					return f.path
			}
			return null
		}

	val autoplays: Array<String>
		get() {
			val fileList = FileManager.sortByName(File(URL).listFiles())
			val autoPlays = ArrayList()
			for (f in fileList) {
				if (f.isFile && (f.name.toLowerCase().startsWith("autoplay") || f.name.toLowerCase().startsWith("_autoplay")))
					autoPlays.add(f.path)
			}

			return autoPlays.toTypedArray()
		}


	class AutoPlay {

		var func = 0
		var currChain = 0
		var num = 0
		var x: Int = 0
		var y: Int = 0
		var c: Int = 0
		var d: Int = 0

		internal constructor(x: Int, y: Int, currChain: Int, num: Int) {
			this.func = ON

			this.x = x
			this.y = y
			this.currChain = currChain
			this.num = num
		}

		internal constructor(x: Int, y: Int, currChain: Int) {
			this.func = OFF

			this.x = x
			this.y = y
			this.currChain = currChain
		}

		internal constructor(c: Int) {
			this.func = CHAIN

			this.c = c
		}

		constructor(d: Int, currChain: Int) {
			this.func = DELAY
			this.d = d

			this.currChain = currChain
		}

		companion object {
			val ON = 1
			val OFF = 2
			val CHAIN = 3
			val DELAY = 4
		}
	}

	init {

		try {
			isInfo = File("$URL/info").isFile
			isSounds = File("$URL/sounds").isDirectory
			isKeySound = File("$URL/keySound").isFile
			isKeyLED = File("$URL/keyLED").isDirectory
			isAutoPlay = File("$URL/autoPlay").isFile

			if (!isInfo) addErr("info doesn't exist")
			if (!isKeySound) addErr("keySound doesn't exist")
			if (!isInfo && !isKeySound) addErr("It does not seem to be UniPack.")

			if (!isInfo || !isKeySound)
				CriticalError = true
			else {

				if (isInfo) {
					val reader = BufferedReader(InputStreamReader(FileInputStream("$URL/info")))
					var s: String
					while ((s = reader.readLine()) != null) {

						if (s.length == 0)
							continue

						try {
							val split = s.split("=".toRegex(), 2).toTypedArray()

							val key = split[0]
							val value = split[1]

							when (key) {
								"title" -> title = value
								"producerName" -> producerName = value
								"buttonX" -> buttonX = Integer.parseInt(value)
								"buttonY" -> buttonY = Integer.parseInt(value)
								"chain" -> chain = Integer.parseInt(value)
								"squareButton" -> squareButton = value == "true"
								"websiteURL" -> websiteURL = value
							}
						} catch (e: ArrayIndexOutOfBoundsException) {
							e.printStackTrace()
							addErr("info : [$s] format is not found")
						}

					}

					if (title == null)
						addErr("info : title was missing")
					if (producerName == null)
						addErr("info : producerName was missing")
					if (buttonX == 0)
						addErr("info : buttonX was missing")
					if (buttonY == 0)
						addErr("info : buttonY was missing")
					if (chain == 0)
						addErr("info : chain was missing")
					if (!(1 <= chain && chain <= 24)) {
						addErr("info : chain out of range")
						CriticalError = true
					}

					reader.close()
				}


				if (loadDetail) {
					if (isKeySound) {
						sound = Array<Array<Array<ArrayList<*>>>>(chain) { Array(buttonX) { arrayOfNulls(buttonY) } }
						val reader = BufferedReader(InputStreamReader(FileInputStream("$URL/keySound")))
						var s: String
						while ((s = reader.readLine()) != null) {
							val split = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

							val c: Int
							val x: Int
							val y: Int
							val soundURL: String
							var loop = 0
							var wormhole = -1

							try {
								if (split.size <= 2)
									continue

								c = Integer.parseInt(split[0]) - 1
								x = Integer.parseInt(split[1]) - 1
								y = Integer.parseInt(split[2]) - 1
								soundURL = split[3]

								if (split.size >= 5)
									loop = Integer.parseInt(split[4]) - 1
								if (split.size >= 6) {
									loop = Integer.parseInt(split[4]) - 1
									wormhole = Integer.parseInt(split[5]) - 1
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
							else if (y < 0 || y >= buttonY)
								addErr("keySound : [$s] y is incorrect")
							else {

								val tmp = Sound("$URL/sounds/$soundURL", loop, wormhole)

								if (!File(tmp.URL!!).isFile) {
									addErr("keySound : [$s] sound was not found")
									continue
								}

								if (sound!![c][x][y] == null)
									sound!![c][x][y] = ArrayList()
								tmp.num = sound!![c][x][y].size
								sound!![c][x][y].add(tmp)

							}
						}
						reader.close()
					}


					if (isKeyLED) {
						led = Array<Array<Array<ArrayList<*>>>>(chain) { Array(buttonX) { arrayOfNulls(buttonY) } }
						val fileList = FileManager.sortByName(File("$URL/keyLED").listFiles())
						for (file in fileList) {
							if (file.isFile) {
								val fileName = file.name
								val split1 = fileName.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

								val c: Int
								val x: Int
								val y: Int
								var loop = 1

								try {
									if (split1.size <= 2)
										continue

									c = Integer.parseInt(split1[0]) - 1
									x = Integer.parseInt(split1[1]) - 1
									y = Integer.parseInt(split1[2]) - 1
									if (split1.size >= 4)
										loop = Integer.parseInt(split1[3])

									if (c < 0 || c >= chain) {
										addErr("keyLED : [$fileName] chain is incorrect")
										continue
									} else if (x < 0 || x >= buttonX) {
										addErr("keyLED : [$fileName] x is incorrect")
										continue
									} else if (y < 0 || y >= buttonY) {
										addErr("keyLED : [$fileName] y is incorrect")
										continue
									} else if (loop < 0) {
										addErr("keyLED : [$fileName] loop is incorrect")
										continue
									}


								} catch (e: NumberFormatException) {
									addErr("keyLED : [$fileName] format is incorrect")
									continue
								} catch (e: IndexOutOfBoundsException) {
									addErr("keyLED : [$fileName] format is incorrect")
									continue
								}

								val LEDs = ArrayList<LED.Syntax>()

								val reader = BufferedReader(InputStreamReader(FileInputStream(file)))
								var s: String
								while ((s = reader.readLine()) != null) {
									val split2 = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

									val option: String
									var _x = -1
									var _y = -1
									var _color = -1
									var _velo = 119
									var _delay = -1


									try {
										if (split2[0] == "")
											continue

										option = split2[0]

										when (option) {
											"on", "o" -> {
												try {
													_x = Integer.parseInt(split2[1]) - 1
												} catch (ignore: NumberFormatException) {
												}

												_y = Integer.parseInt(split2[2]) - 1

												if (split2.size == 4)
													_color = Integer.parseInt(split2[3], 16) + -0x1000000
												else if (split2.size == 5) {
													if (split2[3] == "auto" || split2[3] == "a") {
														_velo = Integer.parseInt(split2[4])
														_color = LaunchpadColor.ARGB[_velo] + -0x1000000
													} else {
														_velo = Integer.parseInt(split2[4])
														_color = Integer.parseInt(split2[3], 16) + -0x1000000
													}
												} else {
													addErr("keyLED : [$fileName].[$s] format is incorrect")
													continue
												}
											}
											"off", "f" -> {
												try {
													_x = Integer.parseInt(split2[1]) - 1
												} catch (ignore: NumberFormatException) {
												}

												_y = Integer.parseInt(split2[2]) - 1
											}
											"delay", "d" -> _delay = Integer.parseInt(split2[1])
											else -> {
												addErr("keyLED : [$fileName].[$s] format is incorrect")
												continue
											}
										}

									} catch (e: NumberFormatException) {
										addErr("keyLED : [$fileName].[$s] format is incorrect")
										continue
									} catch (e: IndexOutOfBoundsException) {
										addErr("keyLED : [$fileName].[$s] format is incorrect")
										continue
									}


									when (option) {
										"on", "o" -> LEDs.add(LED.Syntax(_x, _y, _color, _velo))
										"off", "f" -> LEDs.add(LED.Syntax(_x, _y))
										"delay", "d" -> LEDs.add(LED.Syntax(_delay))
									}
								}
								if (led!![c][x][y] == null)
									led!![c][x][y] = ArrayList()
								led!![c][x][y].add(LED(LEDs, loop, led!![c][x][y].size))
								reader.close()
							} else
								addErr("keyLED : " + file.name + " is not file")
						}
					}

					if (isAutoPlay) {
						autoPlay = ArrayList()
						val map = Array(buttonX) { IntArray(buttonY) }

						var currChain = 0

						val reader = BufferedReader(InputStreamReader(FileInputStream(mainAutoplay!!)))
						var s: String
						while ((s = reader.readLine()) != null) {
							val split = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

							val option: String
							var x = -1
							var y = -1
							var chain = -1
							var delay = -1

							try {
								if (split[0] == "")
									continue

								option = split[0]

								when (option) {
									"on", "o" -> {
										x = Integer.parseInt(split[1]) - 1
										y = Integer.parseInt(split[2]) - 1
										if (x < 0 || x >= buttonX) {
											addErr("autoPlay : [$s] x is incorrect")
											continue
										}
										if (y < 0 || y >= buttonY) {
											addErr("autoPlay : [$s] y is incorrect")
											continue
										}
									}
									"off", "f" -> {
										x = Integer.parseInt(split[1]) - 1
										y = Integer.parseInt(split[2]) - 1
										if (x < 0 || x >= buttonX) {
											addErr("autoPlay : [$s] x is incorrect")
											continue
										} else if (y < 0 || y >= buttonY) {
											addErr("autoPlay : [$s] y is incorrect")
											continue
										}
									}
									"touch", "t" -> {
										x = Integer.parseInt(split[1]) - 1
										y = Integer.parseInt(split[2]) - 1
										if (x < 0 || x >= buttonX) {
											addErr("autoPlay : [$s] x is incorrect")
											continue
										} else if (y < 0 || y >= buttonY) {
											addErr("autoPlay : [$s] y is incorrect")
											continue
										}
									}
									"chain", "c" -> {
										chain = Integer.parseInt(split[1]) - 1
										if (chain < 0 || chain >= this.chain) {
											addErr("autoPlay : [$s] chain is incorrect")
											continue
										}
									}
									"delay", "d" -> delay = Integer.parseInt(split[1])
									else -> {
										addErr("autoPlay : [$s] format is incorrect")
										continue
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
									autoPlay!!.add(AutoPlay(x, y, currChain, map[x][y]))
									val sound = Sound_get(currChain, x, y, map[x][y])
									map[x][y]++
									if (sound.wormhole != -1) {
										autoPlay!!.add(AutoPlay(currChain = sound.wormhole))
										for (i in 0 until buttonX)
											for (j in 0 until buttonY)
												map[i][j] = 0
										logErr("shut the fuck up please")
									}
								}
								"off", "f" -> autoPlay!!.add(AutoPlay(x, y, currChain))
								"touch", "t" -> {
									autoPlay!!.add(AutoPlay(x, y, currChain, map[x][y]))
									autoPlay!!.add(AutoPlay(x, y, currChain))
									map[x][y]++
								}
								"chain", "c" -> {
									autoPlay!!.add(AutoPlay(currChain = chain))
									for (i in 0 until buttonX)
										for (j in 0 until buttonY)
											map[i][j] = 0
								}
								"delay", "d" -> autoPlay!!.add(AutoPlay(delay, currChain))
							}
						}
						reader.close()
					}
				}
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}

	}

	private fun addErr(content: String) {
		if (ErrorDetail == null)
			ErrorDetail = content
		else
			ErrorDetail += "\n$content"
	}

	fun getInfoText(context: Context): String {
		val title = BaseActivity.lang(context, R.string.title) + " : " + this.title
		val producerName = BaseActivity.lang(context, R.string.producerName) + " : " + this.producerName
		val scale = BaseActivity.lang(context, R.string.title) + " : " + this.buttonX + " x " + this.buttonY
		val chainCount = BaseActivity.lang(context, R.string.title) + " : " + this.chain
		val capacity = BaseActivity.lang(context, R.string.title) + " : " + FileManager.byteToMB(FileManager.getFolderSize(URL).toFloat()) + " MB"

		return "$title\n$producerName\n$scale\n$chainCount\n$capacity"

	}
}