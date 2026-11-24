package com.kimjisub.launchpad

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The empty pack list's first-run guide and the update check are shown to everyone on first launch.
 * A language that translates the guide's button names must translate the sentences around them too,
 * or English sentences end up with translated button names inside.
 */
class FirstRunGuideStringsTest {

	private val resDir = File("src/main/res")

	private fun strings(folder: String): Map<String, String> {
		val file = File(resDir, "$folder/strings.xml")
		val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
		val nodes = doc.getElementsByTagName("string")
		return (0 until nodes.length).associate { i ->
			val node = nodes.item(i)
			node.attributes.getNamedItem("name").nodeValue to node.textContent
		}
	}

	private fun placeholders(text: String) = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

	@Test
	fun guideAndUpdateCheckAreTranslatedWithTheSamePlaceholders() {
		val english = strings("values")
		val problems = TRANSLATED.flatMap { folder ->
			val local = strings(folder)
			(GUIDE + UPDATE_CHECK).mapNotNull { key ->
				val text = local[key]
				when {
					text.isNullOrBlank() -> "$folder: $key missing"
					text == english.getValue(key) -> "$folder: $key is still English"
					placeholders(text) != placeholders(english.getValue(key)) ->
						"$folder: $key has ${placeholders(text)}, English has ${placeholders(english.getValue(key))}"
					else -> null
				}
			}
		}
		assertTrue(problems.joinToString("\n"), problems.isEmpty())
	}

	/** Every language that has these keys keeps English's placeholders, so formatting never throws or drops a name. */
	@Test
	fun everyLanguageKeepsTheGuidePlaceholders() {
		val english = strings("values")
		val folders = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
		val problems = folders.flatMap { folder ->
			val local = strings(folder.name)
			(GUIDE + UPDATE_CHECK).mapNotNull { key ->
				val text = local[key] ?: return@mapNotNull null
				if (placeholders(text) == placeholders(english.getValue(key))) null else "${folder.name}: $key"
			}
		}
		assertTrue(problems.joinToString("\n"), problems.isEmpty())
	}

	private companion object {
		val PLACEHOLDER = Regex("%\\d+\\$[sd]")
		val TRANSLATED = listOf("values-in", "values-es", "values-pt-rBR")
		val GUIDE = listOf(
			"guide_empty_title", "guide_empty_step_get", "guide_empty_step_play", "guide_empty_step_pads",
			"guide_empty_link", "unipack_play",
		)
		val UPDATE_CHECK = listOf("update_check", "update_check_failed", "update_check_again", "update_check_in_store")
	}
}
