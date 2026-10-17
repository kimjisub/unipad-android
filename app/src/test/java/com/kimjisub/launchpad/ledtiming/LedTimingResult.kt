package com.kimjisub.launchpad.ledtiming

import java.util.IdentityHashMap
import kotlin.math.ceil

/**
 * Commands that arrived, set against the ones expected: the pack's intent against the driver, or
 * LedRunner's output against the driver. Each LED is compared on its own: its sequence of codes as
 * they arrived against the sequence expected, matched by longest common subsequence. An expected code
 * left unmatched but arriving elsewhere in that LED's sequence counts as reordered; otherwise it is
 * missing. A code that arrived and is left unmatched after that is extra.
 */
class LedTimingResult(
	val expected: Int,
	val arrived: Int,
	val matched: Int,
	val reordered: Int,
	val missing: Int,
	val extra: Int,
	/** Matched commands that arrived after one expected more than [OVERTAKE_MS] later, on any LED. */
	val overtaken: Int,
	/** Arrival minus expected time of each matched command, in expected order. */
	val latencies: List<Latency>,
	/** The first few LEDs whose sequence differs, for reproducing it. */
	val differences: List<String>,
) {
	class Latency(val t0: Double, val ms: Double)

	val orderedAndComplete: Boolean get() = reordered == 0 && missing == 0 && extra == 0

	private val sorted by lazy { latencies.map { it.ms }.sorted() }

	fun percentile(p: Double): Double = percentileOf(sorted, p)

	val median get() = percentile(50.0)
	val p95 get() = percentile(95.0)
	val max get() = sorted.lastOrNull() ?: Double.NaN

	/** Least-squares slope of latency over expected time, in ms per minute of play: above 0 means it builds up. */
	fun buildUpPerMinute(): Double {
		if (latencies.size < 2) return 0.0
		val meanT = latencies.sumOf { it.t0 } / latencies.size
		val meanL = latencies.sumOf { it.ms } / latencies.size
		var num = 0.0
		var den = 0.0
		for (l in latencies) {
			num += (l.t0 - meanT) * (l.ms - meanL)
			den += (l.t0 - meanT) * (l.t0 - meanT)
		}
		return if (den == 0.0) 0.0 else num / den * 60_000
	}

	/** Median latency of the matched commands expected in each tenth of the play time. */
	fun medianByTenth(): List<Double> {
		if (latencies.isEmpty()) return emptyList()
		val end = latencies.maxOf { it.t0 } + 1
		return (0 until 10).map { i ->
			percentileOf(latencies.filter { (it.t0 * 10 / end).toInt() == i }.map { it.ms }.sorted(), 50.0)
		}
	}

	companion object {
		/** Two LedRunner ticks: closer than this, two commands can share a tick and leave in either order. */
		const val OVERTAKE_MS = 8L

		private const val MAX_DIFF_CELLS = 50_000_000L
		private const val MAX_REPORTED_DIFFERENCES = 10

		fun compare(expected: List<LedCommand>, arrived: List<LedCommand>): LedTimingResult {
			val intendedByLed = expected.groupBy { it.target }
			val sentByLed = arrived.groupBy { it.target }
			var matched = 0
			var reordered = 0
			var missing = 0
			var extra = 0
			val latencies = ArrayList<Latency>()
			val matchOf = IdentityHashMap<LedCommand, LedCommand>()
			val differences = ArrayList<String>()

			for (led in intendedByLed.keys + sentByLed.keys) {
				val want = intendedByLed[led].orEmpty()
				val got = sentByLed[led].orEmpty()
				val pairs = commonSubsequence(want.map { it.code }, got.map { it.code })
				matched += pairs.size
				for ((w, g) in pairs) {
					latencies += Latency(want[w].ms, got[g].ms - want[w].ms)
					matchOf[got[g]] = want[w]
				}
				if (pairs.size == want.size && pairs.size == got.size) continue

				val unmatchedWant = want.indices.toSet() - pairs.map { it.first }.toSet()
				val unmatchedGot = (got.indices.toSet() - pairs.map { it.second }.toSet()).map { got[it].code }.toMutableList()
				for (w in unmatchedWant) {
					if (unmatchedGot.remove(want[w].code)) reordered++ else missing++
				}
				extra += unmatchedGot.size
				if (differences.size < MAX_REPORTED_DIFFERENCES) differences += describe(led, want, got)
			}

			var overtaken = 0
			var latestIntended = Double.NEGATIVE_INFINITY
			for (command in arrived) {
				val t0 = matchOf[command]?.ms ?: continue
				if (t0 + OVERTAKE_MS < latestIntended) overtaken++
				latestIntended = maxOf(latestIntended, t0)
			}

			return LedTimingResult(
				expected = expected.size,
				arrived = arrived.size,
				matched = matched,
				reordered = reordered,
				missing = missing,
				extra = extra,
				overtaken = overtaken,
				latencies = latencies.sortedBy { it.t0 },
				differences = differences,
			)
		}

		/**
		 * Index pairs of a longest common subsequence. The sequences are usually equal or nearly so, so
		 * the shared head and tail are matched directly and only the part between them is compared cell
		 * by cell; a part too large for that is left unmatched.
		 */
		private fun commonSubsequence(a: List<Int>, b: List<Int>): List<Pair<Int, Int>> {
			var head = 0
			while (head < a.size && head < b.size && a[head] == b[head]) head++
			var tail = 0
			while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++
			val pairs = ArrayList<Pair<Int, Int>>()
			for (i in 0 until head) pairs += i to i
			val n = a.size - head - tail
			val m = b.size - head - tail
			if (n > 0 && m > 0 && n.toLong() * m <= MAX_DIFF_CELLS) {
				val table = Array(n + 1) { IntArray(m + 1) }
				for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
					table[i][j] = if (a[head + i] == b[head + j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
				}
				var i = 0
				var j = 0
				while (i < n && j < m) {
					when {
						a[head + i] == b[head + j] -> { pairs += (head + i) to (head + j); i++; j++ }
						table[i + 1][j] >= table[i][j + 1] -> i++
						else -> j++
					}
				}
			}
			for (k in 0 until tail) pairs += (a.size - tail + k) to (b.size - tail + k)
			return pairs
		}

		private fun describe(led: LedTarget, want: List<LedCommand>, got: List<LedCommand>): String {
			var first = 0
			while (first < want.size && first < got.size && want[first].code == got[first].code) first++
			val from = (first - 2).coerceAtLeast(0)
			fun window(commands: List<LedCommand>) =
				commands.subList(from, minOf(commands.size, first + 4)).joinToString { "${it.code}@${"%.1f".format(it.ms)}" }
			return "$led: ${want.size} expected, ${got.size} arrived; from #$from expected [${window(want)}] arrived [${window(got)}]"
		}
	}
}

/** The nearest-rank [p]th percentile of ascending [sorted]. */
fun percentileOf(sorted: List<Double>, p: Double): Double =
	if (sorted.isEmpty()) Double.NaN else sorted[(ceil(p / 100 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
