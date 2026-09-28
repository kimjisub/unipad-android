package com.kimjisub.launchpad

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The tap-order numbers only appear while the play screen's Trace Log switch is on, so the
 * Settings row has to say what it draws and point at that switch in every language.
 */
class TraceLogClassicStringsTest {

	private val resDir = File("src/main/res")

	private fun localeStrings(): Map<String, Map<String, String>> =
		resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
			.mapNotNull { dir -> File(dir, "strings.xml").takeIf { it.exists() }?.let { dir.name to parse(it) } }
			.toMap()

	private fun parse(file: File): Map<String, String> {
		val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
		val nodes = doc.getElementsByTagName("string")
		return (0 until nodes.length).associate { i ->
			val node = nodes.item(i)
			node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
		}
	}

	@Test
	fun everyLocaleDefinesTitleAndDescription() {
		val missing = localeStrings().filter { (_, s) ->
			s["trace_log_classic"].isNullOrBlank() || s["trace_log_classic_desc"].isNullOrBlank()
		}.keys
		assertTrue("Missing trace_log_classic strings in $missing", missing.isEmpty())
	}

	@Test
	fun descriptionNamesThePlayScreenTraceLogSwitch() {
		val notLinked = localeStrings().filter { (_, s) ->
			val switchName = s["traceLog"] ?: return@filter false
			s["trace_log_classic_desc"]?.contains(switchName, ignoreCase = true) != true
		}.keys
		assertTrue("Description does not mention the Trace Log switch in $notLinked", notLinked.isEmpty())
	}

	@Test
	fun titleStaysShortEnoughForOneSettingsRow() {
		val tooLong = localeStrings().filter { (_, s) -> (s["trace_log_classic"]?.length ?: 0) > MAX_TITLE_LENGTH }
			.mapValues { it.value["trace_log_classic"] }
		assertTrue("Titles longer than $MAX_TITLE_LENGTH chars: $tooLong", tooLong.isEmpty())
	}

	@Test
	fun titleSaysItShowsNumbers() {
		val strings = localeStrings()
		assertTrue(strings.getValue("values").getValue("trace_log_classic").contains("number", ignoreCase = true))
		assertTrue(strings.getValue("values-ko").getValue("trace_log_classic").contains("숫자"))
	}

	private companion object {
		const val MAX_TITLE_LENGTH = 40
	}
}
