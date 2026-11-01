package com.kimjisub.launchpad.diagnostics

/** Only these coarse categories may leave the device; never log the installer or lookup error. */
internal object InstallSource {
	fun classify(installerPackageName: String?): String = when (installerPackageName) {
		"com.android.vending" -> "play"
		"com.sec.android.app.samsungapps" -> "samsung"
		"com.uptodown" -> "uptodown"
		"com.apkpure.aegon" -> "apkpure"
		null, "" -> "unknown"
		else -> "other"
	}

	fun read(readInstaller: () -> String?): String = try {
		classify(readInstaller())
	} catch (_: Exception) {
		// The system may withhold install information. Its error can contain a package name.
		"unknown"
	}
}
