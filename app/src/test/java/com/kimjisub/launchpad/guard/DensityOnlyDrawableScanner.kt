package com.kimjisub.launchpad.guard

/**
 * Finds drawables that exist only in density-qualified folders (`drawable-hdpi`, `drawable-480dpi`, …).
 *
 * Play splits the app bundle by screen density: such files leave the base APK and ship only in the
 * density splits. An install without those splits (base APK alone, app cloners, virtual phones)
 * then has no entry for the drawable, and the first lookup throws Resources.NotFoundException.
 * This closed the app on the main screen for the settings icon in 4.1.3 and 4.1.8. A drawable is
 * safe only once a folder that every device configuration can read (`drawable`, `drawable-nodpi`,
 * `drawable-anydpi`, optionally with a `-vNN` API qualifier) also provides it; a copy in a folder
 * such as `drawable-night` or `drawable-land` still leaves other configurations without one.
 *
 * Mipmap folders are not scanned: bundletool keeps every mipmap density in the base APK.
 */
object DensityOnlyDrawableScanner {

	private val DENSITY_QUALIFIER = Regex("(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|\\d+dpi)")
	private val BASE_FOLDER = Regex("drawable(-nodpi|-anydpi)?(-v\\d+)?")

	/** Whether a resource folder name (`drawable-hdpi-v21`) is a density-split drawable folder. */
	fun isDensitySplit(folderName: String): Boolean {
		val parts = folderName.split('-')
		return parts.first() == "drawable" && parts.drop(1).any { DENSITY_QUALIFIER.matches(it) }
	}

	/**
	 * @param folders resource folder name → file names inside it, from every scanned `res` root.
	 * @return drawable names (without extension) found in density-split folders but in no base folder, sorted.
	 */
	fun scan(folders: List<Pair<String, Collection<String>>>): List<String> {
		val inBase = folders.filter { (folder, _) -> BASE_FOLDER.matches(folder) }
			.flatMap { (_, files) -> resourceNames(files) }
			.toSet()
		return folders.filter { (folder, _) -> isDensitySplit(folder) }
			.flatMap { (_, files) -> resourceNames(files) }
			.filterNot { it in inBase }
			.distinct()
			.sorted()
	}

	/**
	 * `name.9.png` and `name.png` both define the resource `name`. Hidden files (`.DS_Store`,
	 * `.gitkeep`) are ignored, as the resource merger ignores them.
	 */
	private fun resourceNames(fileNames: Collection<String>): List<String> =
		fileNames.filterNot { it.startsWith('.') }.map { it.substringBefore('.') }
}
