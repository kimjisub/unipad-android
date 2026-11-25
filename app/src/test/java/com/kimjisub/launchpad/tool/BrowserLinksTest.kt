package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrowserLinksTest {
	private val official = listOf(
		"https://unipad.io", "https://unipad.io/docs/get-started",
		"https://unipad.io/docs/theme", "https://unipad.io/docs/guide#launchpad",
		"https://unipad.io/docs/guide#custom-firmware",
	)

	@Test fun defaultSessionDoesNotMarkAddresses() {
		assertFalse(BrowserLinks.isTestRun)
		for (link in official) assertEquals(link, BrowserLinks.address(link))
	}

	@Test fun ordinaryRunsKeepEveryByte() {
		for (link in official + listOf("HTTPS://UNIPAD.IO:443/a?x=%2f+#part", "https://unipad.io?no_analytics=1#keep", "invalid address")) {
			assertEquals(link, BrowserLinks.address(link, testRun = false))
		}
	}

	@Test fun testRunsMarkOfficialPages() {
		for (link in official) {
			val page = link.substringBefore('#')
			val fragment = link.removePrefix(page)
			assertEquals("$page?no_analytics=1$fragment", BrowserLinks.address(link, testRun = true))
		}
	}

	@Test fun queryAndFragmentStayVerbatim() {
		assertEquals("https://unipad.io/a?x=%2f+%20&x=2&no_analytics=1#part?keep",
			BrowserLinks.address("https://unipad.io/a?x=%2f+%20&x=2#part?keep", true))
		assertEquals("https://unipad.io/a?no_analytics=1#",
			BrowserLinks.address("https://unipad.io/a?#", true))
	}

	@Test fun trailingQuerySeparatorAndEncodedPathStayVerbatim() {
		assertEquals("https://unipad.io/a%2fb?x=%25&no_analytics=1#part%20two",
			BrowserLinks.address("https://unipad.io/a%2fb?x=%25&#part%20two", true))
	}

	@Test fun existingMarkerIsNotDuplicated() {
		for (query in listOf("no_analytics=1", "x=1&no_analytics=1", "no_analytics=0", "no_analytics", "no%5Fanalytics=1")) {
			val link = "https://unipad.io/docs/theme?$query#part"
			assertEquals(link, BrowserLinks.address(link, true))
		}
		val once = BrowserLinks.address("https://unipad.io", true)
		assertEquals(once, BrowserLinks.address(once, true))
	}

	@Test fun unrelatedAddressesStayVerbatim() {
		for (link in listOf("https://example.com/?next=https://unipad.io", "https://api.unipad.io/a",
			"https://unipad.io.example.com", "https://unipad.io@example.com", "mailto:hello@unipad.io",
			"invalid address", "https://example.com/#no_analytics=1")) {
			assertEquals(link, BrowserLinks.address(link, true))
		}
	}

	@Test fun onlyQueryParametersCountAsAnExistingMarker() {
		assertEquals("http://UNIPAD.IO:8080/a?no_analytics=1#no_analytics=1",
			BrowserLinks.address("http://UNIPAD.IO:8080/a#no_analytics=1", true))
	}
}
