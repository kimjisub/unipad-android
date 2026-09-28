package com.kimjisub.launchpad.guard

/**
 * Finds uses of the Material3 text-field containers that crash on this app's dependency set.
 *
 * play-services-oss-licenses 17.5.1 lifts material3 from the BOM's 1.4.0 to 1.5.0-alpha17,
 * which was built against compose-foundation 1.11.0-beta02 while the app runs 1.12.0. The
 * alpha's TextFieldDefaults/OutlinedTextFieldDefaults.Container style lambdas only implement
 * the old `applyStyle` signature, so composing any API below throws while the field attaches
 * (ClassCastException in R8 release builds, AbstractMethodError in debug). This is what closed
 * Settings → Storage → Transfer in 4.1.6/4.1.7 before its search box moved to BasicTextField.
 *
 * The scan only reports direct references to the names listed in [BANNED_FUNCTIONS] and
 * [BANNED_MEMBERS]; it does not follow call paths. Only the TextField/OutlinedTextField crash
 * was reproduced on a device. The other entries reach the Container lambdas in the alpha17
 * bytecode, but crashes through them were not reproduced. A source that passes is not proven
 * safe.
 *
 * The scan is textual: comments and string literals are ignored, imports (including `as`
 * aliases and `material3.*`) and fully qualified references are resolved. It does not catch:
 * - the deprecated `SearchBar(query = …)` / `DockedSearchBar(query = …)` overloads. Their
 *   alpha17 bytecode calls SearchBarDefaults.InputField internally (a crash through that path
 *   was not reproduced). Their names match the `inputField` overloads, so they are left
 *   unbanned rather than blocking both;
 * - `typealias` of the owner objects or `with(TextFieldDefaults) { DecorationBox() }` receivers;
 * - reflection, Java callers, or other libraries (oss-licenses itself included) that compose
 *   these fields internally;
 * - Material3 APIs added after alpha17 that use the same Container.
 */
object Material3TextFieldScanner {

	private const val PACKAGE = "androidx.compose.material3"

	/** Top-level composables reported when referenced directly; not every caller of Container. */
	val BANNED_FUNCTIONS = setOf(
		"TextField",
		"OutlinedTextField",
		"SecureTextField",
		"OutlinedSecureTextField",
		"DatePicker",
		"DateRangePicker",
		"TimeInput",
	)

	/** Object members reported when referenced directly, keyed by owner. */
	val BANNED_MEMBERS = mapOf(
		"TextFieldDefaults" to setOf("Container", "ContainerBox", "DecorationBox", "decorator"),
		"OutlinedTextFieldDefaults" to setOf("Container", "ContainerBox", "DecorationBox", "decorator"),
		"SearchBarDefaults" to setOf("InputField"),
	)

	data class Violation(val line: Int, val reference: String)

	private val importRegex = Regex("""^\s*import\s+(\w+(?:\.\w+)*(?:\.\*)?)(?:\s+as\s+(\w+))?""", RegexOption.MULTILINE)

	private val bannedQualifiedNames: Set<String> =
		BANNED_FUNCTIONS + BANNED_MEMBERS.flatMap { (owner, members) -> members.map { "$owner.$it" } }

	fun scan(source: String): List<Violation> {
		val code = stripCommentsAndStrings(source)
		val violations = mutableListOf<Violation>()
		// Local name -> material3 name, for imported banned functions and member owners.
		val importedOwners = mutableMapOf<String, String>()
		var wildcard = false

		for (match in importRegex.findAll(code)) {
			val path = match.groupValues[1]
			if (!path.startsWith("$PACKAGE.")) continue
			val name = path.removePrefix("$PACKAGE.")
			val alias = match.groupValues[2].ifEmpty { null }
			when {
				name == "*" -> wildcard = true
				name in bannedQualifiedNames -> violations += Violation(lineOf(code, match.range.first), path)
				name in BANNED_MEMBERS -> importedOwners[alias ?: name] = name
			}
		}
		if (wildcard) BANNED_MEMBERS.keys.forEach { importedOwners.putIfAbsent(it, it) }

		val body = code.replace(importRegex) { " ".repeat(it.value.length) }

		for (name in bannedQualifiedNames) {
			val qualified = Regex("""\b${Regex.escape(PACKAGE)}\s*\.\s*${name.replace(".", """\s*\.\s*""")}\b""")
			qualified.findAll(body).forEach { violations += Violation(lineOf(body, it.range.first), "$PACKAGE.$name") }
		}
		if (wildcard) {
			for (name in BANNED_FUNCTIONS) {
				val call = Regex("""(?<![\w.])$name\s*[({]|::$name\b""")
				call.findAll(body).forEach { violations += Violation(lineOf(body, it.range.first), "$PACKAGE.$name") }
			}
		}
		for ((local, owner) in importedOwners) {
			for (member in BANNED_MEMBERS.getValue(owner)) {
				val access = Regex("""(?<![\w.])$local\s*\.\s*$member\b""")
				access.findAll(body).forEach { violations += Violation(lineOf(body, it.range.first), "$PACKAGE.$owner.$member") }
			}
		}
		return violations.distinct().sortedBy { it.line }
	}

	private fun lineOf(text: String, index: Int): Int = text.subSequence(0, index).count { it == '\n' } + 1

	/** Replaces comments and string/char literals with spaces, keeping line breaks for line numbers. */
	internal fun stripCommentsAndStrings(source: String): String {
		val out = StringBuilder(source.length)
		var i = 0
		fun blank(until: Int) {
			while (i < until && i < source.length) {
				out.append(if (source[i] == '\n') '\n' else ' ')
				i++
			}
		}
		while (i < source.length) {
			when {
				source.startsWith("//", i) -> blank(source.indexOf('\n', i).let { if (it < 0) source.length else it })
				source.startsWith("/*", i) -> {
					var depth = 0
					var j = i
					while (j < source.length) {
						if (source.startsWith("/*", j)) { depth++; j += 2 }
						else if (source.startsWith("*/", j)) { depth--; j += 2; if (depth == 0) break }
						else j++
					}
					blank(j)
				}
				source.startsWith("\"\"\"", i) -> {
					val end = source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it + 3 }
					blank(end)
				}
				source[i] == '"' || source[i] == '\'' -> {
					val quote = source[i]
					var j = i + 1
					while (j < source.length && source[j] != quote && source[j] != '\n') {
						if (source[j] == '\\') j++
						j++
					}
					blank(minOf(j + 1, source.length))
				}
				else -> out.append(source[i++])
			}
		}
		return out.toString()
	}
}
