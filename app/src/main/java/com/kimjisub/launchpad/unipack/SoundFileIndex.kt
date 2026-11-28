package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.manager.PackFileNames
import java.io.File
import java.text.Normalizer

/**
 * Finds a sound by its relative path: exact spelling first, then canonical Unicode equivalence,
 * then canonical equivalence ignoring case. A folder is never a sound.
 *
 * The names are matched against original archive names (or unmodified installed names), so a pack finds the same
 * sounds on case-sensitive internal storage as on case-insensitive shared storage, and each sound
 * keeps its own stored bytes.
 */
internal class SoundFileIndex(soundDirs: List<File>) {
	constructor(soundsDir: File) : this(listOf(soundsDir))
	private val byPath: Map<String, File>
	private val byCanonicalPath: Map<String, File>
	private val byCaseFoldedPath: Map<String, File>

	init {
		// Sorted so that, of several files differing only in case, the same one is chosen every time.
		val files = soundDirs.sortedBy { PackFileNames.originalName(it) }.asSequence().flatMap { dir ->
			PackFileNames.files(dir).filter { it.file.isFile }.map { it.name to it.file }
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
