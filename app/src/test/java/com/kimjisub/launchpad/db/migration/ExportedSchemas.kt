package com.kimjisub.launchpad.db.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** The schema JSON files Room exports to app/schemas. Unit tests run with the app module as working directory. */
object ExportedSchemas {
	val dir = File("schemas/com.kimjisub.launchpad.db.AppDatabase")

	fun versions(): List<Int> =
		dir.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

	/** The `database` object of `<version>.json`. */
	fun load(version: Int): JsonObject =
		Json.parseToJsonElement(File(dir, "$version.json").readText()).jsonObject.getValue("database").jsonObject
}
