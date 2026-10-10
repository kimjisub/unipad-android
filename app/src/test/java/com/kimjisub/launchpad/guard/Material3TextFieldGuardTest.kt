package com.kimjisub.launchpad.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the Material3 text fields that crashed the Transfer screen (see [Material3TextFieldScanner])
 * out of the app and design sources. Text inputs go through foundation's BasicTextField instead,
 * like TransferActivity's SearchField. Unit-test sources are not scanned; they never compose UI.
 */
class Material3TextFieldGuardTest {

	private val modules = listOf(File("."), File("../design"))

	private fun productSources(): List<File> = modules.flatMap { module ->
		val src = File(module, "src")
		assertTrue("Missing source root ${src.canonicalPath}", src.isDirectory)
		src.listFiles { dir -> dir.isDirectory && dir.name != "test" }!!
			.flatMap { sourceSet -> sourceSet.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "kts") }.toList() }
	}

	@Test
	fun productSourcesDoNotUseCrashingMaterial3TextFields() {
		val sources = productSources()
		assertTrue(
			"Scanner did not reach TransferActivity; source roots moved?",
			sources.any { it.name == "TransferActivity.kt" },
		)
		val found = sources.flatMap { file ->
			Material3TextFieldScanner.scan(file.readText()).map { "${file.canonicalPath}:${it.line} ${it.reference}" }
		}
		assertTrue(
			"Material3 text fields crashed when a dependency lifted material3 to 1.5.0-alpha17 on " +
				"foundation 1.12.0 and would again with such a prerelease; use BasicTextField instead:\n" +
				found.joinToString("\n"),
			found.isEmpty(),
		)
	}

	@Test
	fun flagsDirectImportAndCall() {
		val source = """
			import androidx.compose.material3.OutlinedTextField
			fun Search() { OutlinedTextField(value = "", onValueChange = {}) }
		""".trimIndent()
		assertEquals(listOf(1), Material3TextFieldScanner.scan(source).map { it.line })
	}

	@Test
	fun flagsAliasedImport() {
		val source = "import androidx.compose.material3.TextField as M3Field\nfun F() { M3Field(\"\", {}) }"
		assertEquals(
			"androidx.compose.material3.TextField",
			Material3TextFieldScanner.scan(source).single().reference,
		)
	}

	@Test
	fun flagsFullyQualifiedCall() {
		val source = "fun F() {\n androidx.compose.material3.SecureTextField(state = s)\n}"
		assertEquals(listOf(2), Material3TextFieldScanner.scan(source).map { it.line })
	}

	@Test
	fun flagsWildcardImportCall() {
		val source = "import androidx.compose.material3.*\nfun F() {\n TextField(\"\", {})\n Text(\"ok\")\n}"
		assertEquals(listOf(3), Material3TextFieldScanner.scan(source).map { it.line })
	}

	@Test
	fun flagsDecorationBoxThroughAliasedOwner() {
		val source = """
			import androidx.compose.material3.OutlinedTextFieldDefaults as Defaults
			import androidx.compose.material3.SearchBarDefaults
			fun F() {
				BasicTextField("", {}, decorationBox = { Defaults.DecorationBox(value = "") })
				SearchBarDefaults.InputField(query = "")
			}
		""".trimIndent()
		assertEquals(listOf(4, 5), Material3TextFieldScanner.scan(source).map { it.line })
	}

	@Test
	fun doesNotReportBasicTextFieldOrUnlistedMaterial3Names() {
		val source = """
			import androidx.compose.foundation.text.BasicTextField
			import androidx.compose.material3.Text
			import androidx.compose.material3.TextFieldDefaults
			import androidx.compose.material3.DatePickerDialog
			fun F() {
				BasicTextField(value = "", onValueChange = {})
				Text(text = "OutlinedTextField")
				val h = TextFieldDefaults.MinHeight
			}
		""".trimIndent()
		assertEquals(emptyList<Material3TextFieldScanner.Violation>(), Material3TextFieldScanner.scan(source))
	}

	/**
	 * Records a known gap, not a safe pattern: these deprecated overloads call
	 * SearchBarDefaults.InputField internally, yet the scan does not report them.
	 */
	@Test
	fun knownGapLegacySearchBarOverloadsAreNotReported() {
		val source = """
			import androidx.compose.material3.SearchBar
			import androidx.compose.material3.DockedSearchBar
			fun F() {
				SearchBar(query = q, onQueryChange = {}, onSearch = {}, active = false, onActiveChange = {}) {}
				DockedSearchBar(query = q, onQueryChange = {}, onSearch = {}, active = false, onActiveChange = {}) {}
			}
		""".trimIndent()
		assertEquals(emptyList<Material3TextFieldScanner.Violation>(), Material3TextFieldScanner.scan(source))
	}

	@Test
	fun ignoresNamesInComments() {
		val source = """
			// Built on BasicTextField rather than Material3 OutlinedTextField.
			/* import androidx.compose.material3.TextField
			   /* nested */ androidx.compose.material3.OutlinedTextField() */
			/** See [androidx.compose.material3.TextFieldDefaults.DecorationBox]. */
			val s = ""${'"'}androidx.compose.material3.TextField(""${'"'}
		""".trimIndent()
		assertEquals(emptyList<Material3TextFieldScanner.Violation>(), Material3TextFieldScanner.scan(source))
	}
}
