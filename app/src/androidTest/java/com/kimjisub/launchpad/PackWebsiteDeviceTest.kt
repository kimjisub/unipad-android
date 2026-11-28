package com.kimjisub.launchpad

import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.tool.splitties.browsePackWebsite
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackWebsiteDeviceTest {
	private val opened = mutableListOf<Intent>()
	private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
		override fun startActivity(intent: Intent) { opened += intent }
	}
	@Test fun bareAddress_opensHttps() {
		context.browsePackWebsite("unipad.io/path")
		assertEquals(listOf("https://unipad.io/path?no_analytics=1"), opened.map { it.data.toString() })
	}
	@Test fun webSchemes_areKept() {
		val urls = listOf("http://unipad.io/path", "https://unipad.io/path")
		urls.forEach { context.browsePackWebsite(it) }
		assertEquals(urls.map { "$it?no_analytics=1" }, opened.map { it.data.toString() })
	}
	@Test fun uppercaseSchemes_openWithTheNormalizedScheme() {
		for (url in listOf("HTTPS://unipad.io/path", "Https://unipad.io/path", "HTTP://unipad.io/path")) context.browsePackWebsite(url)
		assertEquals(listOf("https://unipad.io/path?no_analytics=1", "https://unipad.io/path?no_analytics=1", "http://unipad.io/path?no_analytics=1"), opened.map { it.data.toString() })
	}
	@Test fun bareAddressWithPort_opensHttps() {
		context.browsePackWebsite("unipad.io:8080/path")
		context.browsePackWebsite("localhost:3000/path")
		assertEquals(listOf("https://unipad.io:8080/path?no_analytics=1", "https://localhost:3000/path"), opened.map { it.data.toString() })
	}
	@Test fun otherSchemes_doNotOpenAnything() {
		for (url in listOf("javascript:x", "file:///tmp/a", "intent://x", "mailto:x@y")) context.browsePackWebsite(url)
		assertEquals(emptyList<Intent>(), opened)
	}
}
