package com.kimjisub.launchpad.manager

/** Read old bundled selections even before the background preference migration has finished. */
internal fun resolveBundledThemeId(themeId: String, isBundled: (String) -> Boolean): String {
	if (!themeId.startsWith("zip://")) return themeId
	val name = themeId.removePrefix("zip://")
	// Bundles are immediate children of assets/themes, never paths inside an external theme.
	if (name.isEmpty() || '/' in name || '\\' in name) return themeId
	return if (isBundled(name)) "asset://$name" else themeId
}
