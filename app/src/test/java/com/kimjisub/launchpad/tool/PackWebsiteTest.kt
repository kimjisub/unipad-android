package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackWebsiteTest {
	@Test fun webSchemes_areNormalized() {
		assertEquals("https://unipad.io/path", packWebsiteAddress(" HTTPS://unipad.io/path "))
		assertEquals("http://unipad.io/path", packWebsiteAddress("Http://unipad.io/path"))
	}

	@Test fun bareAddresses_includePortsAndIpv6() {
		for ((written, expected) in listOf(
			"unipad.io/path" to "https://unipad.io/path",
			"unipad.io:8080/path" to "https://unipad.io:8080/path",
			"localhost:3000" to "https://localhost:3000/",
			"[::1]:8080/path" to "https://[::1]:8080/path",
			"//unipad.io/path" to "https://unipad.io/path",
		)) assertEquals(written, expected, packWebsiteAddress(written))
	}

	@Test fun unsupportedOrUnusableAddresses_haveNoLink() {
		for (written in listOf(
			"", "   ", "javascript:x", "javascript:123", "file:///tmp/a", "intent://x", "mailto:x@y",
			"https://", "http://?x", "/path", "unipad.io:99999/path", "unipad.io:abc/path", "not a link",
		)) assertNull(written, packWebsiteAddress(written))
	}
}
