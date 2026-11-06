package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/*
 * A loaded pack in the corpus's normalized parse form. Shared by the host harness and the device
 * file observations, so both report a parse case the same way.
 */

internal fun hex8(value: Int) = "%08x".format(value)

/** The pack's sounds folder as it is on disk; the parser finds it whatever its letter case. */
internal fun soundsDirOf(root: File): File =
	root.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("sounds", ignoreCase = true) } ?: File(root, "sounds")

internal fun parseResult(pack: UniPack, root: File): JsonElement {
	val soundsDir = soundsDirOf(root)
	return buildJsonObject {
		put("loaded", true)
		putJsonObject("info") {
			put("title", pack.title)
			put("producerName", pack.producerName)
			put("buttonX", pack.buttonX)
			put("buttonY", pack.buttonY)
			put("chain", pack.chain)
			put("squareButton", pack.squareButton)
			pack.website?.takeIf { it.isNotEmpty() }?.let { put("website", it) }
		}
		putJsonArray("sounds") {
			val table = pack.soundTable ?: return@putJsonArray
			for (c in 0 until pack.chain) for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) {
				val queue = table[c][x][y]?.takeIf { it.isNotEmpty() } ?: continue
				add(buildJsonObject {
					put("c", c); put("x", x); put("y", y)
					putJsonArray("queue") {
						for (s in queue) add(buildJsonObject {
							put("file", s.file.relativeTo(soundsDir).path.replace(File.separatorChar, '/'))
							put("loop", s.loop)
							put("wormhole", s.wormhole)
						})
					}
				})
			}
		}
		put("keyLedExist", pack.keyLedExist)
		putJsonArray("leds") {
			val table = pack.ledAnimationTable ?: return@putJsonArray
			for (c in 0 until pack.chain) for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) {
				val queue = table[c][x][y]?.takeIf { it.isNotEmpty() } ?: continue
				add(buildJsonObject {
					put("c", c); put("x", x); put("y", y)
					putJsonArray("queue") {
						for (a in queue) add(buildJsonObject {
							put("loop", a.loop)
							putJsonArray("events") { a.ledEvents.forEach { add(ledEventJson(it)) } }
						})
					}
				})
			}
		}
		val autoPlay = pack.autoPlayTable
		if (autoPlay == null) put("autoPlay", JsonNull)
		else putJsonArray("autoPlay") { autoPlay.elements.forEach { add(autoPlayElementJson(it)) } }
		putJsonArray("errors") { pack.errorDetail?.lines()?.forEach { add(JsonPrimitive(normalizeError(it))) } }
	}
}

private fun ledEventJson(e: LedEvent): JsonElement = buildJsonArray {
	when (e) {
		is LedEvent.On -> { add(JsonPrimitive("on")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(hex8(e.color))); add(JsonPrimitive(e.velocity)) }
		is LedEvent.Off -> { add(JsonPrimitive("off")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)) }
		is LedEvent.Delay -> { add(JsonPrimitive("delay")); add(JsonPrimitive(e.delay)) }
		is LedEvent.Chain -> { add(JsonPrimitive("chain")); add(JsonPrimitive(e.chain)) }
	}
}

private fun autoPlayElementJson(e: AutoPlay.Element): JsonElement = buildJsonArray {
	when (e) {
		is AutoPlay.Element.On -> { add(JsonPrimitive("on")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(e.currChain)); add(JsonPrimitive(e.num)) }
		is AutoPlay.Element.Off -> { add(JsonPrimitive("off")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(e.currChain)) }
		is AutoPlay.Element.Chain -> { add(JsonPrimitive("chain")); add(JsonPrimitive(e.c)) }
		is AutoPlay.Element.Delay -> { add(JsonPrimitive("delay")); add(JsonPrimitive(e.delay)) }
	}
}
