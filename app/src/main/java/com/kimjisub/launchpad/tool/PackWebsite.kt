package com.kimjisub.launchpad.tool

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val WEBSITE_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/** The same HTTP(S) validation is used when offering a pack link and when opening it. */
internal fun packWebsiteAddress(link: String): String? {
	val written = link.trim()
	if (written.isEmpty()) return null
	written.toHttpUrlOrNull()?.let { return it.toString() }
	if (WEBSITE_SCHEME.containsMatchIn(written)) {
		// A dotted host or localhost followed by a numeric port is a bare web address,
		// even though URI parsers treat the text before its colon as a scheme.
		val authority = written.substringBefore('/').substringBefore('?').substringBefore('#')
		val host = authority.substringBeforeLast(':')
		val port = authority.substringAfterLast(':')
		if ((host != "localhost" && '.' !in host) || port.isEmpty() || port.any { it !in '0'..'9' }) return null
	}
	val address = when {
		written.startsWith("//") -> "https:$written"
		written.startsWith('/') -> return null
		else -> "https://$written"
	}
	return address.toHttpUrlOrNull()?.toString()
}
