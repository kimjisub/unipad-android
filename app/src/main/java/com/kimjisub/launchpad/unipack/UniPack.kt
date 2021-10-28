package com.kimjisub.launchpad.unipack

import android.content.Context
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.FileManager.byteToMB
import com.kimjisub.launchpad.tool.Log.err
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.Sound


abstract class UniPack {
	var errorDetail: String? = null
	var criticalError = false

	var id: String = ""


	var title: String = ""
	var producerName: String = ""
	var buttonX = 0
	var buttonY = 0
	var chain = 0
	var squareButton = true
	var website: String? = null

	var soundCount = 0
	var ledTableCount = 0

	var soundTable: Array<Array<Array<ArrayList<Sound>?>>>? = null
	var ledAnimationTable: Array<Array<Array<ArrayList<LedAnimation>?>>>? = null
	var autoPlayTable: AutoPlay? = null

	open val infoExist: Boolean = false
	open val soundExist: Boolean = false
	open val keySoundExist: Boolean = false
	open val keyLedExist: Boolean = false
	open val autoPlayExist: Boolean = false


	abstract fun lastModified(): Long

	var detailLoaded: Boolean = false
	abstract fun loadInfo(): UniPack
	abstract fun loadDetail(): UniPack

	// 파일 구조를 확인하여 정상적인 유니팩인지 감지합니다.
	abstract fun checkFile()


	abstract fun delete()

	abstract fun getPathString(): String

	init {
	}

	var loaded : Boolean = false
	fun load(): UniPack {
		if(!loaded)
		checkFile()
		loadInfo()
		loaded = true

		return this
	}

	// Circular Queue /////////////////////////////////////////////////////////////////////////////////////////

	fun Sound_get(c: Int, x: Int, y: Int): Sound? {
		return try {
			soundTable!![c][x][y]!![0]
		} catch (e: NullPointerException) {
			null
		} catch (ee: IndexOutOfBoundsException) {
			err("Sound_get ($c, $x, $y)")
			null
		}
	}

	fun Sound_get(c: Int, x: Int, y: Int, num: Int): Sound? {
		return try {
			val sound = soundTable!![c][x][y]
			soundTable!![c][x][y]!![num % sound!!.size]
		} catch (e: NullPointerException) {
			null
		} catch (e: IndexOutOfBoundsException) {
			err("Sound_get ($c, $x, $y)")
			null
		}
	}

	fun Sound_push(c: Int, x: Int, y: Int) {
		try {
			val item = soundTable!![c][x][y]!!.removeAt(0)
			soundTable!![c][x][y]!!.add(item)
		} catch (e: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			err("Sound_push ($c, $x, $y)")
		}
	}

	fun Sound_push(c: Int, x: Int, y: Int, num: Int) {
		try {
			val e = soundTable!![c][x][y]
			if (soundTable!![c][x][y]!![0].num != num)
				while (true) {
					val tmp = e!![0]
					e.removeAt(0)
					e.add(tmp)
					if (e[0].num == num % e.size)
						break
				}
		} catch (e: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			err("Sound_push ($c, $x, $y, $num)")
		} catch (ee: ArithmeticException) {
			err("ArithmeticException : Sound_push ($c, $x, $y, $num)")
		}
	}


	fun led_get(c: Int, x: Int, y: Int): LedAnimation? {
		return try {
			ledAnimationTable!![c][x][y]!![0]
		} catch (e: NullPointerException) {
			null
		} catch (ee: IndexOutOfBoundsException) {
			err("LED_get ($c, $x, $y)")
			null
		}
	}

	fun led_push(c: Int, x: Int, y: Int) {
		try {
			val item = ledAnimationTable!![c][x][y]!!.removeAt(0)
			ledAnimationTable!![c][x][y]!!.add(item)
		} catch (e: NullPointerException) {
		} catch (e: IndexOutOfBoundsException) {
			err("LED_push ($c, $x, $y)")
		}
	}

	fun led_push(c: Int, x: Int, y: Int, num: Int) {
		try {
			val e: ArrayList<LedAnimation>? = ledAnimationTable!![c][x][y]
			if (e!![0].num != num)
				while (true) {
					val item = e.removeAt(0)
					e.add(item)
					if (e[0].num == num % e.size) break
				}
		} catch (e: NullPointerException) {
		} catch (ee: IndexOutOfBoundsException) {
			err("LED_push ($c, $x, $y, $num)")
		}
	}

	// etc /////////////////////////////////////////////////////////////////////////////////////////


	fun addErr(content: String) {
		if (errorDetail == null)
			errorDetail = content
		else
			errorDetail += "\n" + content
	}

	fun infoToString(context: Context): String {
		return context.resources.getString(string.title) + " : " + title + "\n" +
				context.resources.getString(string.producerName) + " : " + producerName + "\n" +
				context.resources.getString(string.padSize) + " : " + buttonX.toString() + " x " + buttonY.toString() + "\n" +
				context.resources.getString(string.numChain) + " : " + chain.toString() + "\n" +
				context.resources.getString(string.fileSize) + " : " + byteToMB(getByteSize()) + " MB"
	}

	abstract fun getByteSize(): Long

	override fun equals(other: Any?): Boolean {
		if (other !is UniPack)
			return false
		val otherUniPack = other as UniPack
		return toString() == otherUniPack.toString()
	}

	override fun toString(): String {
		return "UniPack()"
	}


}