package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.tool.SoundDurationReader
import com.kimjisub.launchpad.tool.UniPackAutoMapper
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files

private fun durations(case: CorpusCase, corpus: Corpus): JsonObject =
    Json.parseToJsonElement(case.files.single { it.path == "operation.json" }.bytes(corpus).decodeToString())
        .jsonObject.getValue("durations").jsonObject

internal fun observeAutoMap(case: CorpusCase, corpus: Corpus): JsonElement {
    val root = Files.createTempDirectory("conformance-mapper").toFile()
    try {
        case.writeTo(root, corpus)
        val lengths = durations(case, corpus)
        val pack = UniPackFolder(root).apply { load(); loadDetail() }
        val reader = object : SoundDurationReader {
            override fun durationMs(file: File): Double? = lengths.getValue(file.name).jsonPrimitive.doubleOrNull
            override fun close() = Unit
        }
        runBlocking {
            UniPackAutoMapper(pack, { reader }).run(object : UniPackAutoMapper.Listener {
                override fun onGetWorkSize(size: Int) = Unit
                override fun onProgress(progress: Int) = Unit
            })
        }
        // Reopen the file actually written by the mapper, using the real parser.
        val mapped = UniPackFolder(root).apply { load(); loadDetail() }
        var time = 0L
        val events = buildJsonArray {
            for (e in checkNotNull(mapped.autoPlayTable).elements) {
                if (e is AutoPlay.Element.Delay) { time += e.delay; continue }
                add(buildJsonArray {
                    when (e) {
                        is AutoPlay.Element.On -> {
                            add(JsonPrimitive("on")); add(JsonPrimitive(time)); add(JsonPrimitive(e.currChain))
                            add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(e.num))
                        }
                        is AutoPlay.Element.Off -> {
                            add(JsonPrimitive("off")); add(JsonPrimitive(time)); add(JsonPrimitive(e.currChain))
                            add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y))
                        }
                        is AutoPlay.Element.Chain -> {
                            add(JsonPrimitive("chain")); add(JsonPrimitive(time)); add(JsonPrimitive(e.c))
                        }
                        is AutoPlay.Element.Delay -> error("delay handled above")
                    }
                })
            }
        }
        return buildJsonObject { put("events", events) }
    } finally { root.deleteRecursively() }
}
