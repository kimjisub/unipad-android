package com.kimjisub.launchpad.guard

/**
 * Finds drawables that exist only in density-qualified folders (`drawable-hdpi`, `drawable-480dpi`, …).
 *
 * Play splits the app bundle by screen density: such files leave the base APK and ship only in the
 * density splits. An install without those splits (base APK alone, app cloners, virtual phones)
 * then has no entry for the drawable, and the first lookup throws Resources.NotFoundException.
 * This closed the app on the main screen for the settings icon in 4.1.3 and 4.1.8. A drawable is
 * safe once any folder without a density qualifier (`drawable`, `drawable-nodpi`,
 * `drawable-anydpi`, `drawable-night-v24`, …) also provides it.
 *
 * Mipmap folders are not scanned: bundletool keeps every mipmap density in the base APK.
 */
object DensityOnlyDrawableScanner {

	private val DENSITY_QUALIFIER = Regex("(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|\\d+dpi)")

	/** Whether a resource folder name (`drawable-hdpi-v21`) is a density-split drawable folder. */
	fun isDensitySplit(folderName: String): Boolean {
		val parts = folderName.split('-')
		return parts.first() == "drawable" && parts.drop(1).any { DENSITY_QUALIFIER.matches(it) }
	}

	/**
	 * @param folders resource folder name → file names inside it, from every scanned `res` root.
	 * @return drawable names (without extension) found only in density-split folders, sorted.
	 */
	fun scan(folders: List<Pair<String, Collection<String>>>): List<String> {
		val drawables = folders.filter { (folder, _) -> folder.split('-').first() == "drawable" }
		val (densityOnly, baseFolders) = drawables.partition { (folder, _) -> isDensitySplit(folder) }
		val inBase = baseFolders.flatMap { (_, files) -> files.map(::resourceName) }.toSet()
		return densityOnly.flatMap { (_, files) -> files.map(::resourceName) }
			.filterNot { it in inBase }
			.distinct()
			.sorted()
	}

	/** `name.9.png` and `name.png` both define the resource `name`. */
	private fun resourceName(fileName: String): String = fileName.substringBefore('.')
}
