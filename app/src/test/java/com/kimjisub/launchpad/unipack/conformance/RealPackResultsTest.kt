package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Reads real pack folders with the same parser call and normalized result as the conformance cases,
 * for unipad.io meta/unipack-conformance/real-packs to compare with iOS and the web. It runs only when
 * given a folder; an ordinary test run skips it.
 *
 * - UNIPACK_REAL_PACKS: a folder holding one folder per pack, named by its id.
 * - UNIPACK_REAL_PACKS_OUT: where `android/<id>.json` and `android.run.json` are written
 *   (default build/unipack-real-packs).
 * - UNIPACK_REAL_PACKS_ONLY: comma separated ids to read; every pack folder without it.
 */
class RealPackResultsTest {

	@Test
	fun readEveryPack() {
		val packsDir = System.getenv("UNIPACK_REAL_PACKS")?.let(::File)
		assumeTrue("UNIPACK_REAL_PACKS is not set", packsDir != null)
		val out = File(System.getenv("UNIPACK_REAL_PACKS_OUT") ?: "build/unipack-real-packs")
		val only = System.getenv("UNIPACK_REAL_PACKS_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

		val packs = realPackFolders(checkNotNull(packsDir), only)
		val dir = File(out, PLATFORM).apply { mkdirs() }
		for (folder in packs) {
			val line = buildJsonObject {
				put("platform", PLATFORM)
				put("id", folder.name)
				runCatching { readPack(folder) }.fold({ put("actual", it) }, { put("error", it.stackTraceToString()) })
			}
			File(dir, "${folder.name}.json").writeText("$line\n")
			println("UNIPACK-REAL-PACK ${folder.name} ${if ("error" in line) "error" else "ok"}")
		}
		File(out, "$PLATFORM.run.json").writeText(buildJsonObject {
			put("platform", PLATFORM)
			put("generatedAt", Instant.now().toString())
			putJsonArray("packs") { packs.forEach { add(JsonPrimitive(it.name)) } }
		}.toString() + "\n")
	}

	@Test
	fun soundsBelowACapitalisedSoundsFolderAreNamedFromInsideIt() {
		val root = Files.createTempDirectory("real-pack").toFile()
		try {
			File(root, "info").writeText("title=t\nproducerName=p\nbuttonX=2\nbuttonY=2\nchain=1")
			File(root, "keySound").writeText("1 1 1 a.wav")
			File(root, "Sounds").mkdirs()
			File(root, "Sounds/a.wav").writeBytes(ByteArray(4))
			val sounds = readPack(root).jsonObject.getValue("sounds").jsonArray
			assertEquals("a.wav", sounds.single().jsonObject.getValue("queue").jsonArray.single().jsonObject.getValue("file").jsonPrimitive.content)
		} finally {
			root.deleteRecursively()
		}
	}

	private fun readPack(folder: File): JsonElement {
		val pack = Deps().loadPack(folder)
		return if (pack.criticalError) buildJsonObject { put("loaded", false) } else parseResult(pack, folder)
	}
}

private val ID_PATTERN = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*$")

/** The pack folders under [dir] in id order, the same selection as real-packs/packs.mjs listPacks. */
internal fun realPackFolders(dir: File, only: Set<String>?): List<File> {
	val all = dir.listFiles().orEmpty().filter { it.isDirectory && ID_PATTERN.matches(it.name) }.sortedBy { it.name }
	if (only == null) return all
	val missing = only - all.map { it.name }.toSet()
	check(missing.isEmpty()) { "no pack folder for ${missing.joinToString()} in $dir" }
	return all.filter { it.name in only }
}
