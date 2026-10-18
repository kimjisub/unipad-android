package com.kimjisub.launchpad.chainrelease

import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Pad
import java.io.File

/**
 * Gives an unpacked corpus pack one keyLED animation per keySound line, on the test side: the corpus
 * packs have no lights and their files stay as they are. Each animation lights a cell of its own for one
 * sample length per pass and passes as often as its sound plays, endlessly for an infinite loop, so the
 * cells lit at a step are the cells of the playbacks the corpus says are active.
 */
class ChainReleaseLights private constructor(private val cells: Map<Key, Cell>) {
	private data class Key(val chain: Int, val pad: Pad, val file: String)

	private class Cell(val pad: Pad, val endless: Boolean)

	/** Every cell an animation lights. */
	val all: Set<Pad> = cells.values.map { it.pad }.toSet()

	/** The cells of the endless animations. */
	val endless: Set<Pad> = cells.values.filter { it.endless }.map { it.pad }.toSet()

	/** The cell of the animation a press of [pad] on [chain] starts along with [file]. */
	fun cellOf(chain: Int, pad: Pad, file: String): Pad =
		checkNotNull(cells[Key(chain, pad, file)]) { "No light for $file on chain ${chain + 1} pad $pad" }.pad

	companion object {
		private const val SAMPLE_MS = 100

		/** Writes `keyLED` into [pack], a folder holding a corpus pack's files. */
		fun addTo(pack: File): ChainReleaseLights {
			val lines = File(pack, "keySound").readLines().map { it.trim().split(Regex("\\s+")) }.filter { it.size >= 5 }
			val pads = lines.map { Pad(it[1].toInt() - 1, it[2].toInt() - 1) }.toSet()
			val dir = File(pack, "keyLED").apply { mkdirs() }
			val names = HashMap<Triple<Int, Int, Int>, MutableList<String>>()
			val cells = LinkedHashMap<Key, Cell>()
			lines.forEachIndexed { index, (c, x, y, file, loop) ->
				// The last columns hold no pad of the corpus packs, so a light never shares a cell with a press.
				val cell = Pad(index % 8, 7 - index / 8)
				check(cell !in pads) { "Light cell $cell is a pad of the pack" }
				val key = Key(c.toInt() - 1, Pad(x.toInt() - 1, y.toInt() - 1), file)
				check(cells.put(key, Cell(cell, loop == "0")) == null) { "Two sounds $file on chain $c pad $x $y" }
				val name = "$c $x $y $loop $index"
				names.getOrPut(Triple(key.chain, key.pad.x, key.pad.y)) { mutableListOf() } += name
				val on = "o ${cell.x + 1} ${cell.y + 1} a ${index + 1}\nd $SAMPLE_MS\n"
				File(dir, name).writeText(if (loop == "0") on else on + "f ${cell.x + 1} ${cell.y + 1}\n")
			}
			// The pack loader orders a pad's animations by file name; they must take turns like its sounds.
			for (list in names.values) check(list == list.sortedBy { it.lowercase() }) { "Lights $list would not follow the sound order" }
			return ChainReleaseLights(cells)
		}
	}
}
