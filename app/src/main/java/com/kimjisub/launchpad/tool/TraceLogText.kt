package com.kimjisub.launchpad.tool

/** Text for the classic trace log, where each pad shows the order of every tap on it. */
object TraceLogText {
	/**
	 * Returns one string per pad ([buttonX][buttonY]): the 1-based index of every tap on that pad,
	 * each followed by a space ("1 5 "), which is the format the pre-4.1 trace log used.
	 * Taps outside the grid are ignored.
	 */
	fun perPad(sequence: List<Pair<Int, Int>>, buttonX: Int, buttonY: Int): Array<Array<String>> {
		val builders = Array(buttonX) { Array(buttonY) { StringBuilder() } }
		sequence.forEachIndexed { index, (x, y) ->
			if (x in 0 until buttonX && y in 0 until buttonY) builders[x][y].append(index + 1).append(' ')
		}
		return Array(buttonX) { x -> Array(buttonY) { y -> builders[x][y].toString() } }
	}
}
