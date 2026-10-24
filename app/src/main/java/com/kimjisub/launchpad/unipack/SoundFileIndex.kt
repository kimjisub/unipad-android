package com.kimjisub.launchpad.unipack

import java.io.File
import java.text.Normalizer

/**
 * Finds a sound by its relative path: exact spelling first, then canonical Unicode equivalence,
 * then canonical equivalence ignoring case. A folder is never a sound.
 *
 * The names are matched against the stored names instead of asking the disk, so a pack finds the same
 * sounds on case-sensitive internal storage as on case-insensitive shared storage, and each sound
 * carries the name it is stored under.
 */
internal class SoundFileIndex(soundDirs: List<File>) {
	constructor(soundsDir: File) : this(listOf(soundsDir))
	private val byPath: Map<String, File>
	private val byCanonicalPath: Map<String, File>
	private val byCaseFoldedPath: Map<String, File>

	init {
		// Sorted so that, of several files differing only in case, the same one is chosen every time.
		val files = soundDirs.sortedBy { it.name }.asSequence().flatMap { dir ->
			dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath to it }
		}
			.sortedBy { it.first }
			.toList()
		byPath = HashMap<String, File>().apply {
			for ((path, file) in files) putIfAbsent(path, file)
		}
		byCanonicalPath = HashMap<String, File>().apply {
			for ((path, file) in files) putIfAbsent(canonicalName(path), file)
		}
		byCaseFoldedPath = HashMap<String, File>().apply {
			for ((path, file) in files) putIfAbsent(canonicalName(path).lowercase(), file)
		}
	}

	fun find(name: String): File? {
		val components = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
		// References stay relative to sounds/, including a leading slash. Never walk outside it.
		if (components.isEmpty() || ".." in components) return null
		val path = components.joinToString("/")
		return byPath[path] ?: canonicalName(path).let {
			byCanonicalPath[it] ?: byCaseFoldedPath[it.lowercase()]
		}
	}

	private fun canonicalName(path: String): String = Normalizer.normalize(path, Normalizer.Form.NFC)
}
