package com.kimjisub.launchpad.tool

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object BrowserLinks {
	// The instrumented runner sets this only in its own process, never in saved app state.
	@Volatile var isTestRun = false

	fun address(link: String, testRun: Boolean = isTestRun): String {
		if (!testRun) return link
		val url = link.toHttpUrlOrNull() ?: return link
		if (url.host != "unipad.io" || "no_analytics" in url.queryParameterNames) return link

		// Parse for the host and query keys only; retain the caller's spelling and escaping.
		val page = link.substringBefore('#')
		val fragment = link.substring(page.length)
		val separator = when {
			'?' !in page -> "?"
			page.endsWith('?') || page.endsWith('&') -> ""
			else -> "&"
		}
		return "${page}${separator}no_analytics=1$fragment"
	}
}
