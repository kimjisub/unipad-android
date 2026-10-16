package com.kimjisub.launchpad.ledtiming

import com.kimjisub.launchpad.unipack.UniPack
import net.lingala.zip4j.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Measures store packs, which stay out of the repository. Run only when given a folder of pack zips
 * (or unpacked packs), for example the store's `https://unipack.unipad.io/store/<code>.zip` files:
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*StorePackLedTimingTest*' -PledTimingPacks=/path/to/zips \
 *         -PledTimingMeasure=_Some_Pack,_Other_Pack
 *
 * [rankPacks] lists every pack with autoplay and keyLED by LED commands a second at peak.
 * [measurePacks] plays each pack named in ledTimingMeasure in real time (as long as its autoplay) and
 * fails when the driver does not receive exactly what LedRunner handed over, or receives the pack's
 * commands reordered, incomplete or with extra ones. Reports go to build/reports/led-timing/.
 */
class StorePackLedTimingTest {

	private val packs = File(checkNotNull(System.getProperty("ledTiming.packs")) { "set -PledTimingPacks" })
	private val measure = System.getProperty("ledTiming.measure").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
	private val reports = File("build/reports/led-timing").apply { mkdirs() }

	private class Ranked(val name: String, val title: String, val timeline: IntendedTimeline)

	@Test
	fun rankPacks() {
		val ranked = ArrayList<Ranked>()
		val skipped = ArrayList<String>()
		for (source in packSources()) {
			withPack(source) { root ->
				val pack = loadPack(root)
				if (!pack.autoPlayExist || !pack.keyLedExist) {
					skipped += "${source.nameWithoutExtension} (autoplay ${pack.autoPlayExist}, keyLED ${pack.keyLedExist})"
				} else {
					ranked += Ranked(source.nameWithoutExtension, pack.title, timelineFor(pack))
				}
			}
		}
		ranked.sortByDescending { it.timeline.peakPerSecond() }

		val byAverage = ranked.sortedBy { it.timeline.averagePerSecond() }
		val median = byAverage.getOrNull(byAverage.size / 2)?.name
		val text = buildString {
			appendLine("| # | pack | title | commands | play ms | per second (average) | per second (peak 1 s) | endless without delay |")
			appendLine("|---|---|---|---|---|---|---|---|")
			ranked.forEachIndexed { i, r ->
				val t = r.timeline
				appendLine("| ${i + 1} | ${r.name}${if (r.name == median) " (median average)" else ""} | ${r.title} | ${t.commands.size} | " +
					"${t.lastT0} | ${"%.0f".format(t.averagePerSecond())} | ${t.peakPerSecond()} | ${t.delaylessEndlessAnimations} |")
			}
			appendLine()
			appendLine("Without autoplay or keyLED: ${skipped.joinToString()}")
		}
		File(reports, "ranking.md").writeText(text)
		println(text)
		assertTrue("no pack with autoplay and keyLED in $packs", ranked.isNotEmpty())
	}

	@Test
	fun measurePacks() {
		val failed = ArrayList<String>()
		for (name in measure) {
			val source = packSources().firstOrNull { it.nameWithoutExtension == name } ?: error("$name is not in $packs")
			withPack(source) { root ->
				val timeline = timelineFor(loadPack(root))
				val releaseAll = timeline.autoPlayLengthMs + TAIL_MS
				val recording = LedPathRun(loadPack(root)).run(releaseAllAtMs = releaseAll, giveUpAtMs = timeline.lastT0 + GIVE_UP_AFTER_MS)
				val measurement = LedTimingMeasurement(name, timeline, recording)
				val text = measurement.describe()
				File(reports, "$name.txt").writeText(text)
				println(text)
				if (!measurement.deliveredAsHandedOver || !measurement.packToDriver.orderedAndComplete) failed += name
			}
		}
		assertTrue("reordered, missing or extra commands: $failed", failed.isEmpty())
	}

	/** The timeline [LedPathRun] is compared with: every pad released [TAIL_MS] after autoplay ends. */
	private fun timelineFor(pack: UniPack): IntendedTimeline =
		intendedTimeline(pack, releaseAllAtMs = pack.autoPlayLengthMs() + TAIL_MS)

	private fun packSources(): List<File> =
		(packs.listFiles() ?: emptyArray()).filter { it.isDirectory || it.extension.equals("zip", true) }.sortedBy { it.name }

	/** Unpacks a zip without its sounds, which the LED timing does not need, and runs [block] on the pack folder. */
	private fun withPack(source: File, block: (File) -> Unit) {
		if (source.isDirectory) return block(packRoot(source))
		val dir = Files.createTempDirectory("led-timing").toFile()
		try {
			ZipFile(source).use { zip ->
				for (header in zip.fileHeaders) {
					if (header.fileName.split('/').dropLast(1).any { it.equals("sounds", true) }) continue
					zip.extractFile(header, dir.path)
				}
			}
			block(packRoot(dir))
		} finally {
			dir.deleteRecursively()
		}
	}

	/** The shallowest folder holding an info file; store zips nest the pack in one or more folders. */
	private fun packRoot(dir: File): File =
		dir.walkTopDown().filter { it.isDirectory && it.listFiles()?.any { f -> f.isFile && f.name.equals("info", true) } == true }
			.minByOrNull { it.path.count { c -> c == File.separatorChar } } ?: error("no info file in $dir")

	private companion object {
		/** Autoplay ends with the song; trailing animations get this long before every pad is released. */
		const val TAIL_MS = 3_000L
		const val GIVE_UP_AFTER_MS = 30_000L
	}
}
