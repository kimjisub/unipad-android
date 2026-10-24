package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.audio.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

internal val AUDIO_OPERATIONS = setOf("audio-cost", "audio-file", "audio-wave", "audio-selection")

/** Calls the production audio operations. Expectations are read only by the later classifier. */
internal fun observeAudioOperation(case: CorpusCase, corpus: Corpus): JsonElement {
    require(case.observation in AUDIO_OPERATIONS)
    val root = Files.createTempDirectory("conformance-audio").toFile()
    try {
        case.writeTo(root, corpus)
        if (case.observation == "audio-file") {
            val file = case.files.single()
            return inspectionJson(AudioFileInspector.inspect(File(root, file.path)))
        }
        val input = Json.parseToJsonElement(File(root, "operation.json").readText()).jsonObject
        return when (case.observation) {
            "audio-cost" -> costJson(AudioSafety.cost(input.facts()))
            "audio-selection" -> {
                val references = input.getValue("references").jsonArray.map {
                    val r = it.jsonArray
                    AudioReference(r[0].jsonPrimitive.int, r[1].jsonPrimitive.int, r[2].jsonPrimitive.int,
                        r[3].jsonPrimitive.int, r[4].jsonPrimitive.content)
                }
                val bounds = input.getValue("bounds").jsonArray
                val facts = input.getValue("facts").jsonObject
                val plan = AudioSelector.select(references, AudioPackBounds(bounds[0].jsonPrimitive.int,
                    bounds[1].jsonPrimitive.int, bounds[2].jsonPrimitive.int)) { name ->
                    val value = facts.getValue(name)
                    if (value == JsonNull) AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
                    else {
                        val f = value.jsonArray
                        AudioInspection.Readable(AudioFileFacts(f[0].jsonPrimitive.int,
                            f[1].jsonPrimitive.int, f[2].jsonPrimitive.long))
                    }
                }
                buildJsonObject {
                    putJsonArray("order") { plan.files.forEach { add(JsonPrimitive(it.identity)) } }
                    putJsonArray("selected") { plan.files.filter { it.exclusion == null }.forEach { add(JsonPrimitive(it.identity)) } }
                    putJsonArray("exclusions") { plan.files.forEach { add(it.exclusion.json()) } }
                    put("reservedCost", plan.reservedCost.toString())
                }
            }
            "audio-wave" -> {
                // Construct actual PCM bytes, including the physical data length; claimedSeconds
                // is intentionally not a file fact. Sparse zero samples keep large fixtures cheap.
                val file = File(root, "physical.wav")
                val f = input.facts()
                val dataBytes = Math.multiplyExact(Math.multiplyExact(f.frames, f.channels.toLong()), 2)
                require(dataBytes <= Int.MAX_VALUE - 36)
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt((dataBytes + 36).toInt()); put("WAVEfmt ".toByteArray())
                    putInt(16); putShort(1); putShort(f.channels.toShort()); putInt(f.sampleRate)
                    putInt(f.sampleRate * f.channels * 2); putShort((f.channels * 2).toShort()); putShort(16)
                    put("data".toByteArray()); putInt(dataBytes.toInt())
                }.array()
                RandomAccessFile(file, "rw").use { it.write(header); it.setLength(44 + dataBytes) }
                when (val inspected = AudioFileInspector.inspect(file)) {
                    is AudioInspection.Readable -> buildJsonObject {
                        put("frames", inspected.facts.frames.toString())
                        costJson(AudioSafety.cost(inspected.facts)).forEach { (k, v) -> put(k, v) }
                    }
                    is AudioInspection.Rejected -> error("Physical WAV fixture could not be inspected: ${inspected.reason}")
                }
            }
            else -> error("Unregistered audio operation ${case.observation}")
        }
    } finally { root.deleteRecursively() }
}

private fun JsonObject.facts() = AudioFileFacts(getValue("rate").jsonPrimitive.int,
    getValue("channels").jsonPrimitive.int, getValue("frames").jsonPrimitive.long)

private fun AudioExclusion?.json(): JsonElement = this?.let { JsonPrimitive(it.name) } ?: JsonNull

private fun costJson(cost: AudioCost) = buildJsonObject {
    put("cost", cost.value.toString()); put("exclusion", cost.exclusion.json())
}

private fun inspectionJson(inspection: AudioInspection) = buildJsonObject {
    when (inspection) {
        is AudioInspection.Rejected -> put("exclusion", inspection.reason.json())
        is AudioInspection.Readable -> {
            put("exclusion", JsonNull); put("rate", inspection.facts.sampleRate)
            put("channels", inspection.facts.channels); put("frames", inspection.facts.frames.toString())
        }
    }
}
