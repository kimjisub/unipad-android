package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

/** Real resource providers; Android decoding is stubbed to report an unreadable image. */
class LogoLedThemeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val resources = mockk<Resources>()
    private val assets = mockk<AssetManager>()
    private val fallback = mockk<Drawable>()
    private val metadata = """{"name":"Test","author":"Tester","version":"1"}"""
    private var damagedAsset = false

    @Before fun setUp() {
        mockkStatic(ResourcesCompat::class, BitmapFactory::class)
        every { context.resources } returns resources
        every { context.assets } returns assets
        every { resources.getString(any()) } returns "Test"
        every { ResourcesCompat.getDrawable(resources, any(), null) } returns mockk<Drawable>()
        every { ResourcesCompat.getDrawable(resources, R.drawable.logo_led, null) } returns fallback
        every { ResourcesCompat.getColor(resources, any(), null) } returns 0
        every { BitmapFactory.decodeStream(any()) } returns null
        every { BitmapFactory.decodeFile(any()) } returns null
        every { assets.open(any()) } answers {
            when (val path = firstArg<String>()) {
                "themes/test/theme.json" -> ByteArrayInputStream(metadata.toByteArray())
                "themes/test/logo_led.png" -> if (damagedAsset) ByteArrayInputStream(byteArrayOf(1, 2, 3)) else throw FileNotFoundException(path)
                else -> throw FileNotFoundException(path)
            }
        }
    }
    @After fun tearDown() = unmockkAll()

    @Test fun defaultThemeUsesUnipadMark() {
        assertSame(fallback, DefaultThemeResources(context, fullLoad = true).logoLed)
    }
    @Test fun bundledThemeWithoutLogoUsesUnipadMark() {
        assertSame(fallback, AssetThemeResources(context, "themes/test", fullLoad = true).logoLed)
    }
    @Test fun bundledThemeWithDamagedLogoUsesUnipadMark() {
        damagedAsset = true
        val theme = AssetThemeResources(context, "themes/test", fullLoad = true)
        repeat(5) { assertSame(fallback, theme.logoLed) }
        verify(exactly = 1) { BitmapFactory.decodeStream(any()) }
    }
    @Test fun zipThemeWithoutLogoUsesUnipadMark() {
        assertSame(fallback, ZipThemeResources(context, zipTheme(), fullLoad = true).logoLed)
    }
    @Test fun zipThemeWithDamagedLogoUsesUnipadMark() {
        val folder = zipTheme()
        File(folder, "logo_led.png").writeBytes(byteArrayOf(1, 2, 3))
        assertSame(fallback, ZipThemeResources(context, folder, fullLoad = true).logoLed)
    }
    @Test fun zipLogoUsesTheSameFirstExistingCandidateAsOtherImages() {
        val folder = zipTheme()
        val png = File(folder, "logo_led.png").apply { mkdir() }
        val webp = File(folder, "logo_led.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertSame(fallback, ZipThemeResources(context, folder, fullLoad = true).logoLed)
        verify(exactly = 1) { BitmapFactory.decodeFile(png.absolutePath) }
        verify(exactly = 0) { BitmapFactory.decodeFile(webp.absolutePath) }
        verify(exactly = 0) { BitmapFactory.decodeStream(any()) }
    }
    private fun zipTheme(): File = temporary.newFolder().also {
        File(it, "theme.json").writeText(metadata)
    }
}
