package com.kimjisub.launchpad.guard

/**
 * Finds drawables that exist only in density-qualified folders (`drawable-hdpi`, `drawable-480dpi`, …).
 *
 * Play splits the app bundle by screen density: such files leave the base APK and ship only in the
 * density splits. An install without those splits (base APK alone, app cloners, virtual phones)
 * then has no entry for the drawable, and the first lookup throws Resources.NotFoundException.
 * This closed the app on the main screen for the settings icon in 4.1.3 and 4.1.8. A drawable is
 * safe only once a folder that every supported device can read (`drawable`, `drawable-nodpi`,
 * `drawable-anydpi`, optionally with a `-vNN` API qualifier no higher than the app's minSdk) also
 * provides it; a copy in a folder such as `drawable-night`, `drawable-land` or `drawable-v26` still
 * leaves other configurations, or older Android versions, without one.
 *
 * Mipmap folders are not scanned: bundletool keeps every mipmap density in the base APK.
 */
object DensityOnlyDrawableScanner {

	private val DENSITY_QUALIFIER = Regex("(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|\\d+dpi)")
	private val BASE_FOLDER = Regex("drawable(?:-nodpi|-anydpi)?(?:-v(\\d+))?")

	/** File names the resource merger skips by default (aapt's ignore-assets pattern). */
	private val IGNORED_FILE = Regex("\\..*|thumbs\\.db|picasa\\.ini|.*\\.scc|.*~", RegexOption.IGNORE_CASE)

	/** Whether a resource folder name (`drawable-hdpi-v21`) is a density-split drawable folder. */
	fun isDensitySplit(folderName: String): Boolean {
		val parts = folderName.split('-')
		return parts.first() == "drawable" && parts.drop(1).any { DENSITY_QUALIFIER.matches(it) }
	}

	/** Whether every device from [minSdk] up reads a drawable from this folder name. */
	fun isBaseFolder(folderName: String, minSdk: Int): Boolean {
		val match = BASE_FOLDER.matchEntire(folderName) ?: return false
		val api = match.groupValues[1].toIntOrNull() ?: return true
		return api <= minSdk
	}

	/**
	 * @param folders resource folder name → file names inside it, from every scanned `res` root.
	 * @param minSdk the lowest Android API level the app installs on.
	 * @return drawable names (without extension) found in density-split folders but in no base folder, sorted.
	 */
	fun scan(folders: List<Pair<String, Collection<String>>>, minSdk: Int): List<String> {
		val inBase = folders.filter { (folder, _) -> isBaseFolder(folder, minSdk) }
			.flatMap { (_, files) -> resourceNames(files) }
			.toSet()
		return folders.filter { (folder, _) -> isDensitySplit(folder) }
			.flatMap { (_, files) -> resourceNames(files) }
			.filterNot { it in inBase }
			.distinct()
			.sorted()
	}

	/** `name.9.png` and `name.png` both define the resource `name`. */
	private fun resourceNames(fileNames: Collection<String>): List<String> =
		fileNames.filterNot { IGNORED_FILE.matches(it) }.map { it.substringBefore('.') }
}
