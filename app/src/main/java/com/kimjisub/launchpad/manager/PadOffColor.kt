package com.kimjisub.launchpad.manager

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.R

/** An opaque, alpha-weighted RGB mean; invisible pixels have no weight. */
internal fun averageOpaqueColor(pixels: Sequence<Int>): Int? {
    var alpha = 0L
    var red = 0L
    var green = 0L
    var blue = 0L
    for (pixel in pixels) {
        val weight = (pixel ushr 24).toLong()
        alpha += weight
        red += ((pixel ushr 16) and 255) * weight
        green += ((pixel ushr 8) and 255) * weight
        blue += (pixel and 255) * weight
    }
    if (alpha == 0L) return null
    return (0xFF shl 24) or ((red / alpha).toInt() shl 16) or
        ((green / alpha).toInt() shl 8) or (blue / alpha).toInt()
}

/** All pad image loaders produce BitmapDrawables. Read one row at a time to bound extra memory. */
private fun Drawable?.padColor(): Int? {
    val bitmap = (this as? BitmapDrawable)?.bitmap ?: return null
    return averageOpaqueColor(sequence {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (pixel in row) yield(pixel)
        }
    })
}

/** Called once during full theme loading, after the pad's existing missing/damaged fallback. */
internal fun loadPadOffColor(context: Context, pad: Drawable?): Int =
    pad.padColor() ?: requireNotNull(
        ResourcesCompat.getDrawable(context.resources, R.drawable.btn, null).padColor(),
    ) { "The default pad image must contain visible pixels" }
