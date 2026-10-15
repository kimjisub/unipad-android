package com.kimjisub.launchpad.guard

import com.kimjisub.launchpad.BaseApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.jar.JarFile

/**
 * Keeps fragments out of the app: every screen is an activity hosting Compose.
 *
 * When Android restores a process it re-creates fragments through their no-argument constructor,
 * so a fragment that takes constructor arguments closes the app on return from the background.
 * Lint's ValidFragment check only looks at `android.app.Fragment` and the support-library
 * fragment, not `androidx.fragment.app.Fragment`, so it does not catch this. The test inspects
 * the compiled main classes instead, which also covers subclasses such as DialogFragment and
 * PreferenceFragmentCompat.
 */
class NoFragmentGuardTest {

	@Suppress("DEPRECATION")
	private val fragmentTypes = listOf(androidx.fragment.app.Fragment::class.java, android.app.Fragment::class.java)

	/** Class names in the app's main output, which reaches unit tests as a jar or a directory. */
	private fun mainClassNames(): List<String> {
		val root = File(checkNotNull(BaseApplication::class.java.protectionDomain).codeSource.location.toURI())
		val paths = if (root.isDirectory) {
			root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
		} else {
			JarFile(root).use { jar -> jar.entries().asSequence().map { it.name }.toList() }
		}
		return paths.filter { it.endsWith(".class") && it != "module-info.class" }
			.map { it.removeSuffix(".class").replace('/', '.') }
	}

	@Test
	fun appDefinesNoFragments() {
		val names = mainClassNames()
		assertTrue(
			"Scan did not reach SettingsActivity; class output moved?",
			"com.kimjisub.launchpad.activity.SettingsActivity" in names,
		)
		val loader = javaClass.classLoader
		val fragments = names.filter { name ->
			val type = Class.forName(name, false, loader)
			fragmentTypes.any { it.isAssignableFrom(type) }
		}
		assertTrue(
			"Fragments are re-created without constructor arguments after process death; " +
				"build the screen with Compose in an activity instead:\n" + fragments.joinToString("\n"),
			fragments.isEmpty(),
		)
	}
}
