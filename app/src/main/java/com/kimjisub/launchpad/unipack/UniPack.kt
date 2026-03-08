@file:Suppress("EmptyMethod") // internal set generates empty setter bytecode - idiomatic Kotlin

package com.kimjisub.launchpad.unipack

import android.content.Context
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.FileManager.byteToMB
import com.kimjisub.launchpad.tool.Log.err
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.Sound


abstract class UniPack {
	private val errors = mutableListOf<String>()
	val errorDetail: String?
		get() = if (errors.isEmpty()) null else errors.joinToString("\n")
	var criticalError = false
		internal set

	abstract val id: String


	var title: String = ""
		internal set
	var producerName: String = ""
		internal set
	var buttonX = 0
		internal set
	var buttonY = 0
		internal set
	var chain = 0
		internal set
	var squareButton = true
		internal set
	var website: String? = null
		internal set

	var soundCount = 0
		internal set
	var ledTableCount = 0
		internal set

	var soundTable: Array<Array<Array<ArrayDeque<Sound>?>>>? = null
		internal set
	var ledAnimationTable: Array<Array<Array<ArrayDeque<LedAnimation>?>>>? = null
		internal set
	var autoPlayTable: AutoPlay? = null
		internal set

	open val keyLedExist: Boolean = false
	open val autoPlayExist: Boolean = false


	abstract fun lastModified(): Long

	var detailLoaded: Boolean = false
		internal set
	abstract fun loadInfo(): UniPack
	abstract fun loadDetail(): UniPack

	/**
	 * Load detail with progress callback.
	 * @param onPhase called with (phaseName, phaseIndex, totalPhases) before each phase starts.
	 */
	open fun loadDetailWithProgress(onPhase: (String, Int, Int) -> Unit): UniPack {
		return loadDetail()
	}

	// Validates the file structure to detect a valid unipack.
	abstract fun checkFile()


	abstract fun delete()

	abstract fun getPathString(): String

	var loaded: Boolean = false
		private set
	fun load(): UniPack {
		if (!loaded)
			checkFile()
		loadInfo()
		loaded = true

		return this
	}

	// Circular Queue

	fun soundGet(c: Int, x: Int, y: Int): Sound? {
		return try {
			val sounds = soundTable?.get(c)?.get(x)?.get(y) ?: return null
			sounds[0]
		} catch (e: IndexOutOfBoundsException) {
			err("soundGet ($c, $x, $y)")
			null
		}
	}

	fun soundGet(c: Int, x: Int, y: Int, num: Int): Sound? {
		return try {
			val sounds = soundTable?.get(c)?.get(x)?.get(y) ?: return null
			sounds[num % sounds.size]
		} catch (e: IndexOutOfBoundsException) {
			err("soundGet ($c, $x, $y)")
			null
		}
	}

	fun soundPush(c: Int, x: Int, y: Int) {
		try {
			val sounds = soundTable?.get(c)?.get(x)?.get(y) ?: return
			val item = sounds.removeFirst()
			sounds.addLast(item)
		} catch (e: IndexOutOfBoundsException) {
			err("soundPush ($c, $x, $y)")
		}
	}

	fun soundPush(c: Int, x: Int, y: Int, num: Int) {
		try {
			val sounds = soundTable?.get(c)?.get(x)?.get(y) ?: return
			val targetNum = num % sounds.size
			if (sounds[0].num != targetNum)
				while (true) {
					val item = sounds.removeFirst()
					sounds.addLast(item)
					if (sounds[0].num == targetNum)
						break
				}
		} catch (e: IndexOutOfBoundsException) {
			err("soundPush ($c, $x, $y, $num)")
		} catch (e: ArithmeticException) {
			err("ArithmeticException : soundPush ($c, $x, $y, $num)")
		}
	}


	fun ledGet(c: Int, x: Int, y: Int): LedAnimation? {
		return try {
			val leds = ledAnimationTable?.get(c)?.get(x)?.get(y) ?: return null
			leds[0]
		} catch (e: IndexOutOfBoundsException) {
			err("ledGet ($c, $x, $y)")
			null
		}
	}

	fun ledPush(c: Int, x: Int, y: Int) {
		try {
			val leds = ledAnimationTable?.get(c)?.get(x)?.get(y) ?: return
			val item = leds.removeFirst()
			leds.addLast(item)
		} catch (e: IndexOutOfBoundsException) {
			err("ledPush ($c, $x, $y)")
		}
	}

	fun ledPush(c: Int, x: Int, y: Int, num: Int) {
		try {
			val leds = ledAnimationTable?.get(c)?.get(x)?.get(y) ?: return
			val targetNum = num % leds.size
			if (leds[0].num != targetNum)
				while (true) {
					val item = leds.removeFirst()
					leds.addLast(item)
					if (leds[0].num == targetNum) break
				}
		} catch (e: IndexOutOfBoundsException) {
			err("ledPush ($c, $x, $y, $num)")
		}
	}

	// etc


	fun addErr(content: String) {
		errors.add(content)
	}

	fun infoToString(context: Context): String {
		val res = context.resources
		return "${res.getString(string.title)} : $title\n" +
				"${res.getString(string.producerName)} : $producerName\n" +
				"${res.getString(string.padSize)} : $buttonX x $buttonY\n" +
				"${res.getString(string.numChain)} : $chain\n" +
				"${res.getString(string.fileSize)} : ${byteToMB(getByteSize())} MB"
	}

	abstract fun getByteSize(): Long

	override fun equals(other: Any?): Boolean {
		if (other !is UniPack)
			return false
		return id == other.id
	}

	override fun hashCode(): Int = id.hashCode()

	override fun toString(): String {
		return "UniPack(id=$id)"
	}
}