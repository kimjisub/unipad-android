package com.kimjisub.launchpad.manager

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LogoLedResourcesTest {
    @Test fun zipMaskRetainsAlphaAndAspectAndIsDecodedOnlyWhenLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "logo-resource-fixture")
        assertFalse("Fixture directory already exists", folder.exists())
        folder.mkdirs()
        try {
            File(folder, "theme.json").writeText("""{"name":"Mask","author":"Tester"}""")
            val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
            bitmap.setPixel(32, 64, Color.argb(128, 0, 255, 0))
            val image = File(folder, "logo_led.webp")
            // Decoding uses the contents; also exercises the alternate extension lookup.
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val theme = ZipThemeResources(context, folder, fullLoad = true)
            val drawable = requireNotNull(theme.logoLed)
            val decoded = drawable.toBitmap()
            assertEquals(64, decoded.width)
            assertEquals(128, decoded.height)
            assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
            assertEquals(128, Color.alpha(decoded.getPixel(32, 64)))
            assertTrue(image.delete())
            repeat(5) { assertSame(drawable, theme.logoLed) }
            bitmap.recycle()
        } finally { folder.deleteRecursively() }
    }
}
