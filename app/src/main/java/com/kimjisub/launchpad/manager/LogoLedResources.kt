package com.kimjisub.launchpad.manager

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import com.kimjisub.launchpad.R
import java.io.IOException
import java.io.InputStream

/** Decode a bundled theme's optional alpha mask once, sharing the ZIP image extension order. */
internal fun loadLogoLed(context: Context, open: (String) -> InputStream): Drawable {
	for (extension in ZipThemeResources.imageExtensions) {
		val stream = try { open("logo_led.$extension") } catch (_: IOException) { continue }
		val bitmap = try { stream.use { BitmapFactory.decodeStream(it) } } catch (_: IOException) { null }
		if (bitmap != null) return bitmap.toDrawable(context.resources)
		// The first existing candidate wins, just as for other ZIP images.
		break
	}
	return requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.logo_led, null))
}
