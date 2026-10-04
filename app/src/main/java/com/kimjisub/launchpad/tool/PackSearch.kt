package com.kimjisub.launchpad.tool

import java.text.Normalizer

/**
 * Filters an already-loaded pack list by a typed query.
 *
 * The query and each field are NFC-normalized and trimmed, then matched as a case-insensitive
 * substring, so a decomposed "Noé" on disk is found by a composed "Noé" typed on the keyboard.
 * A blank query keeps every item.
 */
object PackSearch {

	fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).trim()

	fun matches(query: String, fields: Iterable<String>): Boolean {
		val needle = normalize(query)
		if (needle.isEmpty()) return true
		return fields.any { normalize(it).contains(needle, ignoreCase = true) }
	}

	fun <T> filter(items: List<T>, query: String, fields: (T) -> List<String>): List<T> {
		if (normalize(query).isEmpty()) return items
		return items.filter { matches(query, fields(it)) }
	}
}
