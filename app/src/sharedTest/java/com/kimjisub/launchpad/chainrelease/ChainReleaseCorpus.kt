package com.kimjisub.launchpad.chainrelease

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * `chain-release-v1`, the corpus every UniPad app checks pad releases against: two small packs and,
 * for each step of six cases, what must have started, stopped and stayed held. The files are copied
 * byte for byte from kimjisub/unipad.io `meta/unipack-conformance/chain-release-v1`; [load] refuses
 * them when they differ from that corpus's manifest.
 *
 * Coordinates and chains are one-based in the files and zero-based here.
 */
class ChainReleaseCorpus private constructor(
	val cases: List<Case>,
	private val packs: Map<String, ByteArray>,
	/** SHA-256 of every file used, by its path in the corpus. */
	val sha256: Map<String, String>,
) {
	data class Pad(val x: Int, val y: Int)

	sealed interface Input {
		data class Press(val inputId: String, val pad: Pad) : Input
		data class Release(val inputId: String, val cancelled: Boolean) : Input
		data class ChainButton(val chain: Int) : Input
		data object Checkpoint : Input
	}

	/** A playback that started; [plays] is null for an infinite loop. */
	data class Start(val playbackId: String, val inputId: String, val pad: Pad, val file: String, val plays: Int?)

	data class Expected(
		val chain: Int,
		val starts: List<Start>,
		val releaseStops: Set<String>,
		val naturalEnds: Set<String>,
		val active: Set<String>,
		val pressedInputs: Set<String>,
	)

	data class Step(val atMs: Long, val input: Input, val expected: Expected)

	data class Case(val id: String, val pack: String, val steps: List<Step>)

	/** The pack's files, ready for a pack folder. */
	fun unpack(case: Case, into: File) {
		ZipInputStream(packs.getValue(case.pack).inputStream()).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				val target = File(into, entry.name)
				check(target.canonicalPath.startsWith(into.canonicalPath)) { "Entry outside the pack: ${entry.name}" }
				if (entry.isDirectory) target.mkdirs()
				else target.apply { parentFile?.mkdirs() }.outputStream().use { zip.copyTo(it) }
			}
		}
	}

	companion object {
		const val SUITE = "chain-release-v1"
		private val PACKS = mapOf("manual" to "packs/manual.uni", "delayed" to "packs/delayed.uni")
		private const val EXPECTATIONS = "expectations.json"

		/** [open] reads a file of the corpus by its path under `chain-release-v1/`. */
		fun load(open: (String) -> InputStream): ChainReleaseCorpus {
			val manifest = Json.parseToJsonElement(read(open, "manifest.json").decodeToString()).jsonObject
			check(manifest.string("suite") == SUITE) { "Not the $SUITE manifest" }
			val files = manifest.getValue("files").jsonObject
			val bytes = (PACKS.values + EXPECTATIONS).associateWith { read(open, it) }
			val sha256 = bytes.mapValues { (path, data) ->
				val expected = files.getValue(path).jsonObject.string("sha256")
				val actual = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
				check(actual == expected) { "$path differs from the $SUITE manifest: $actual, expected $expected" }
				actual
			}
			val root = Json.parseToJsonElement(bytes.getValue(EXPECTATIONS).decodeToString()).jsonObject
			check(root.string("suite") == SUITE) { "Not the $SUITE expectations" }
			val cases = root.getValue("cases").jsonArray.map { it.jsonObject.toCase() }
			return ChainReleaseCorpus(cases, PACKS.mapValues { bytes.getValue(it.value) }, sha256)
		}

		private fun read(open: (String) -> InputStream, path: String) = open("$SUITE/$path").use { it.readBytes() }

		private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
		private fun JsonObject.labels(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.content }.toSet()
		private fun JsonArray.pad() = Pad(this[0].jsonPrimitive.int - 1, this[1].jsonPrimitive.int - 1)

		private fun JsonObject.toCase() = Case(
			id = string("id"),
			pack = string("pack"),
			steps = getValue("steps").jsonArray.map { it.jsonObject.toStep() },
		)

		private fun JsonObject.toStep(): Step {
			val input = getValue("input").jsonObject
			val expected = getValue("expected").jsonObject
			return Step(
				atMs = getValue("atMs").jsonPrimitive.int.toLong(),
				input = when (val kind = input.string("kind")) {
					"press" -> Input.Press(input.string("inputId"), input.getValue("pad").jsonArray.pad())
					"release" -> Input.Release(input.string("inputId"), cancelled = false)
					"cancel" -> Input.Release(input.string("inputId"), cancelled = true)
					"chain-button" -> Input.ChainButton(input.getValue("chain").jsonPrimitive.int - 1)
					"checkpoint" -> Input.Checkpoint
					else -> error("Unknown input kind $kind")
				},
				expected = Expected(
					chain = expected.getValue("chain").jsonPrimitive.int - 1,
					starts = expected.getValue("starts").jsonArray.map { start ->
						val s = start.jsonObject
						Start(
							playbackId = s.string("playbackId"),
							inputId = s.string("inputId"),
							pad = s.getValue("pad").jsonArray.pad(),
							file = s.string("file").substringAfterLast('/'),
							plays = s.getValue("plays").jsonPrimitive.intOrNull,
						)
					},
					releaseStops = expected.labels("releaseStops"),
					naturalEnds = expected.labels("naturalEnds"),
					active = expected.labels("active"),
					pressedInputs = expected.labels("pressedInputs"),
				),
			)
		}
	}
}
