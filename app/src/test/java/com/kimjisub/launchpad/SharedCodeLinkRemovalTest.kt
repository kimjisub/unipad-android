package com.kimjisub.launchpad

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** A retired share link must not resolve to any app activity or alias. */
class SharedCodeLinkRemovalTest {
	@Test
	fun manifestHasNoSharedCodeLinkHandler() {
		val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
		val doc = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
		val data = doc.getElementsByTagName("data")
		val handlers = (0 until data.length).map { data.item(it) }
			.filter {
				it.attributes.getNamedItemNS(ANDROID_NS, "scheme")?.nodeValue == "unipad" &&
					it.attributes.getNamedItemNS(ANDROID_NS, "host")?.nodeValue in listOf(null, "unipack", "*")
			}
		assertTrue("Retired unipad://unipack links still resolve in the manifest", handlers.isEmpty())
	}

	private companion object {
		const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
	}
}
