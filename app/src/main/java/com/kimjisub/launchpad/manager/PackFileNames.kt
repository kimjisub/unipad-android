package com.kimjisub.launchpad.manager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.Normalizer

/** Original child names travel with their directory, including when a pack is unwrapped or moved. */
internal object PackFileNames {
    const val MANIFEST = ".unipad-filenames.json"
    const val ALIAS_PREFIX = ".unipad-entry-"
    data class Entry(val name: String, val file: File)

    fun folded(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC).uppercase().lowercase()

    fun validName(name: String): Boolean = name.isNotEmpty() && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it == '\u0000' }

    private fun names(dir: File): Map<String, String> {
        val manifest = File(dir, MANIFEST)
        return try {
            if (!manifest.isFile || manifest.canonicalFile.parentFile != dir.canonicalFile) return emptyMap()
            val document = Json.parseToJsonElement(manifest.readText()).jsonObject
            if (document["format"]?.jsonPrimitive?.content != "unipad-filenames-1") return emptyMap()
            val names = document.getValue("names").jsonObject.mapValues { it.value.jsonPrimitive.content }
            // The sidecar describes direct children only. It can never redirect a reader outside
            // this directory, even when a downloaded archive contains a forged or broken sidecar.
            if (names.any { !validName(it.key) || !validName(it.value) || folded(it.key) == folded(MANIFEST) } ||
                names.keys.map(::folded).toSet().size != names.size ||
                names.values.toSet().size != names.size) emptyMap() else names
        } catch (_: Exception) { emptyMap() }
    }

    fun write(dir: File, names: Map<String, String>) {
        File(dir, MANIFEST).writeText(JsonObject(mapOf(
            "format" to JsonPrimitive("unipad-filenames-1"),
            "names" to JsonObject(names.mapValues { JsonPrimitive(it.value) }),
        )).toString())
    }

    fun children(dir: File): List<Entry> {
        val names = names(dir)
        val children = dir.listFiles().orEmpty()
        if (names.isEmpty()) return children.map { Entry(it.name, it) }
        val byStoredName = children.associateBy { folded(it.name) }
        val restored = names.mapNotNull { (stored, original) ->
            byStoredName[folded(stored)]?.let { Entry(original, it) }
        }
        val mapped = restored.map { it.file }.toSet()
        // Preserve archive order for keyLED names with equal normalized sort keys, even when
        // another filesystem lists the physical aliases in a different order after a move.
        return restored + children.filter { it !in mapped && folded(it.name) != folded(MANIFEST) }
            .map { Entry(it.name, it) }
    }

    fun originalName(file: File): String = file.parentFile?.let { parent ->
        children(parent).firstOrNull { it.file == file }?.name
    } ?: file.name

    fun files(dir: File): Sequence<Entry> = sequence {
        val pending = ArrayDeque<Entry>()
        val visited = mutableSetOf<File>()
        children(dir).asReversed().forEach { pending.addLast(it) }
        while (pending.isNotEmpty()) {
            val entry = pending.removeLast()
            if (entry.file.isDirectory) {
                if (!visited.add(entry.file.canonicalFile)) continue
                children(entry.file).asReversed().forEach {
                    pending.addLast(Entry("${entry.name}/${it.name}", it.file))
                }
            } else yield(entry)
        }
    }

    fun clear(dir: File) {
        if (names(dir).isNotEmpty()) check(File(dir, MANIFEST).delete())
    }
}
