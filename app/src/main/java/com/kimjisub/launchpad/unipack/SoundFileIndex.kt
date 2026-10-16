package com.kimjisub.launchpad.unipack

import java.io.File

/**
 * Finds the file a keySound line names under `sounds/`: the file stored under exactly that path, else
 * one whose whole path under `sounds/` differs only in upper and lower case (official store packs list
 * `End.wav` for a file stored as `END.wav`). A folder is never a sound.
 *
 * The names are matched against the stored names instead of asking the disk, so a pack finds the same
 * sounds on case-sensitive internal storage as on case-insensitive shared storage, and each sound
 * carries the name it is stored under.
 */
internal class SoundFileIndex(soundsDir: File) {
	private val byPath: Map<String, File>
	private val byCaseFoldedPath: Map<String, File>

	init {
		// Sorted so that, of several files differing only in case, the same one is chosen every time.
		val files = soundsDir.walkTopDown()
			.filter { it.isFile }
			.map { it.relativeTo(soundsDir).invariantSeparatorsPath to it }
			.sortedBy { it.first }
			.toList()
		byPath = files.toMap()
		byCaseFoldedPath = HashMap<String, File>().apply {
			for ((path, file) in files) putIfAbsent(path.lowercase(), file)
		}
	}

	fun find(name: String): File? = byPath[name] ?: byCaseFoldedPath[name.lowercase()]
}
