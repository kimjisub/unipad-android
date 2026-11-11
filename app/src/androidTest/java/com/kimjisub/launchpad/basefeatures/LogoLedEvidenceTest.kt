package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.midi.driver.DriverRef
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Captures real rendered masks and exercises the pack parser/runner, including display resizing. */
@RunWith(AndroidJUnit4::class)
class LogoLedEvidenceTest : PlaybackScreenTest() {
    private val logo = By.res("play_logo_led")
    private val off = 0xFF222222.toInt()

    @Test fun packCommandsAndThemeMasksAcrossPhoneAndTabletLayouts() {
        val prefs = PreferenceManager(screen.context)
        val originalTheme = prefs.selectedTheme
        val originalWatermark = prefs.watermark
        prefs.watermark = false
        val originalSize = overrideValue(screen.device.executeShellCommand("wm size"))
        val originalDensity = overrideValue(screen.device.executeShellCommand("wm density"))
        val themeDir = File(screen.context.getExternalFilesDir(null), "themes/logo-led-example")
        assertFalse("Fixture folder already exists", themeDir.exists())
        val noLogoFolder = File(screen.context.cacheDir, "logo-led-without-commands")
        assertFalse("Fixture folder already exists", noLogoFolder.exists())
        val pack = screen.pack()
        File(pack, "keyLED").listFiles().orEmpty().forEach { it.delete() }
        File(pack, "keyLED/1 1 1 1").writeText("o l a 5\nd 60000\n")
        File(pack, "keyLED/1 1 2 1").writeText("o l a 45\nd 60000\n")
        File(pack, "keyLED/1 1 3 1").writeText("f l\n")
        File(pack, "keyLED/1 1 4 1").writeText("o mc 8 a 5\nd 60000\n")
        try {
            assertTrue(TestCaptures.save(screen.device, "logo-led/commands-pack.zip") { it.writeBytes(screen.zip()); true })
            prefs.selectedTheme = screen.context.packageName
            val scenario = screen.openPlay()
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            var restoreColor = false
            for ((name, size, orientation) in listOf(
                Triple("phone-portrait", "720x1280", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
                Triple("tablet-portrait", "1600x2560", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
                Triple("tablet-landscape", "1600x2560", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
            )) {
                screen.device.executeShellCommand("wm density 320")
                screen.device.executeShellCommand("wm size $size")
                screen.onMain { screen.resumed().requestedOrientation = orientation }
                val expectedWidth = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) size.substringBefore("x").toInt() else size.substringAfter("x").toInt()
                val expectedOrientation = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                screen.await("Display did not settle for $name") {
                    screen.onMain { screen.resumed().resources.configuration.orientation == expectedOrientation &&
                        screen.bounds(screen.resumed().window.decorView).width() == expectedWidth &&
                        screen.resumed().resources.displayMetrics.densityDpi == 320 &&
                        screen.vm().uiLoaded && screen.views(screen.resumed().window.decorView).filterIsInstance<ChainView>().count { it.visibility == View.VISIBLE } == 32 }
                }
                screen.instrumentation.waitForIdleSync()
                screen.node(logo)
                if (restoreColor) captureColor("$name-current-red-after-resize", palette(5))
                screen.onMain { screen.vm().ledInit() }
                captureColor("$name-default-off", off)
                command(0, 5)
                captureColor("$name-default-red", palette(5))
                command(1, 45)
                captureColor("$name-default-blue", palette(45))
                screen.tapPad(2)
                awaitChannel(null)
                captureColor("$name-default-off-command", off)
                command(0, 5)
                restoreColor = true
                val metrics = screen.onMain { screen.resumed().resources.displayMetrics }
                println("Logo evidence: $name ${metrics.widthPixels}x${metrics.heightPixels}, densityDpi=${metrics.densityDpi}, widthDp=${screen.onMain { screen.resumed().resources.configuration.screenWidthDp }}, theme=${prefs.selectedTheme}, pack=${pack.name}, commands=o l a 5; o l a 45; f l, emulator display override, watermark=false")
            }
            command(0, 5)
            screen.onMain { screen.vm().scbHideUI.setChecked(true) }
            screen.await("Hide UI did not hide the menu") { !screen.has(By.desc(screen.text(R.string.menu))) }
            captureColor("hidden-controls-red", palette(5))
            screen.onMain {
                screen.vm().scbHideUI.setChecked(false)
                screen.vm().scbProLightMode.setChecked(false)
            }
            screen.await("Normal mode kept the indicator") { !screen.has(logo) }
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            captureColor("mode-restored-red", palette(5))
            screen.onMain { screen.vm().scbLed.setChecked(false) }
            captureColor("led-disabled", off)
            screen.onMain { screen.vm().scbLed.setChecked(true) }
            command(0, 5)
            captureColor("led-enabled-red", palette(5))
            val chainBounds = screen.onMain {
                val views = screen.views(screen.resumed().window.decorView)
                val padRight = views.filterIsInstance<PadView>().maxOf { screen.bounds(it).right }
                views.filterIsInstance<ChainView>().map(screen::bounds).filter { it.left >= padRight }.sortedBy { it.top }
            }
            assertEquals(8, chainBounds.size)
            screen.device.click(chainBounds[1].centerX(), chainBounds[1].centerY())
            screen.await("Second chain button did not work") { screen.onMain { screen.vm().chain.value == 1 } }
            screen.device.click(chainBounds[0].centerX(), chainBounds[0].centerY())
            screen.await("First chain button did not work") { screen.onMain { screen.vm().chain.value == 0 } }
            screen.tapPad(3)
            screen.await("Last top-row LED did not light") { screen.onMain { screen.vm().channelManager.get(-1, 7)?.color == palette(5) } }
            screen.capture("top-row-last-and-logo-red")
            val plays = audio.plays.size
            val point = screen.node(logo).visibleCenter
            screen.device.click(point.x, point.y)
            assertEquals("Logo tap generated a sound", plays, audio.plays.size)
            assertEquals("Logo tap changed the chain", 0, screen.onMain { screen.vm().chain.value })
            scenario.recreate()
            screen.await("Recreated screen did not load") { screen.onMain { screen.vm().uiLoaded && !screen.vm().unipackLoading } }
            // Leaving/recreating already clears pack LEDs; the screen must reflect that current state.
            captureColor("recreated-current-off", off)
            scenario.close()

            themeDir.mkdirs()
            File(themeDir, "theme.json").writeText("""{"name":"Logo Diamond","author":"UniPad Theme Example","version":"1.0"}""")
            val mask = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            Canvas(mask).drawPath(Path().apply { moveTo(64f, 0f); lineTo(128f, 64f); lineTo(64f, 128f); lineTo(0f, 64f); close() }, Paint().apply { color = Color.GREEN; isAntiAlias = true })
            File(themeDir, "logo_led.png").outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(themeDir, "theme_ic.png").outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
            mask.recycle()
            assertTrue(TestCaptures.save(screen.device, "logo-led/diamond-theme.zip") { zipFolder(themeDir, it); true })
            prefs.selectedTheme = "zip://logo-led-example"
            val themed = screen.openPlay()
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            captureColor("theme-diamond-off", off)
            command(0, 5)
            captureColor("theme-diamond-red", palette(5))
            val bounds = screen.node(logo).visibleBounds
            val screenshot = screen.instrumentation.uiAutomation.takeScreenshot()!!
            try {
                assertNotEquals("Transparent corner became a solid color rectangle", palette(5), screenshot.getPixel(bounds.left + 1, bounds.top + 1))
            } finally { screenshot.recycle() }
            themed.close()
            File(themeDir, "logo_led.png").writeText("damaged image")
            val damaged = screen.openPlay()
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            captureColor("theme-damaged-fallback", off)
            damaged.close()
            assertTrue(File(themeDir, "logo_led.png").delete())
            val absent = screen.openPlay()
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            captureColor("theme-absent-fallback", off)
            absent.close()
            prefs.selectedTheme = "asset://sskin"
            val bundled = screen.openPlay()
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            captureColor("theme-bundled-off", off)
            command(0, 5)
            captureColor("theme-bundled-red", palette(5))
            bundled.close()
            prefs.selectedTheme = screen.context.packageName
            pack.copyRecursively(noLogoFolder)
            File(noLogoFolder, "keyLED").deleteRecursively()
            val noLogoPack = screen.openPlay(noLogoFolder)
            screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
            captureColor("new-pack-without-logo-commands", off)
            noLogoPack.close()
        } finally {
            prefs.selectedTheme = originalTheme
            prefs.watermark = originalWatermark
            themeDir.deleteRecursively()
            noLogoFolder.deleteRecursively()
            screen.repo.delete(noLogoFolder.name)
            screen.device.executeShellCommand("wm size ${originalSize ?: "reset"}")
            screen.device.executeShellCommand("wm density ${originalDensity ?: "reset"}")
        }
    }

    private fun palette(code: Int) = LaunchpadColor.ARGB[code].toInt()
    private fun command(pad: Int, code: Int) {
        screen.tapPad(pad)
        awaitChannel(palette(code))
    }
    private fun awaitChannel(color: Int?) = screen.await("Logo channel did not become $color") {
        screen.onMain { screen.vm().channelManager.get(-1, DriverRef.LOGO_FUNCTION_KEY)?.color == color }
    }
    private fun captureColor(name: String, expected: Int) {
        screen.await("Rendered logo did not become $expected for $name") {
            if (!screen.has(logo)) false else {
                val b = screen.node(logo).visibleBounds
                val shot = screen.instrumentation.uiAutomation.takeScreenshot() ?: return@await false
                try {
                    var count = 0
                    for (x in b.left until b.right) for (y in b.top until b.bottom) if (shot.getPixel(x, y) == expected) count++
                    count >= 4
                } finally { shot.recycle() }
            }
        }
        screen.capture(name)
    }
    private fun overrideValue(output: String) = Regex("Override (?:size|density): (.+)").find(output)?.groupValues?.get(1)?.trim()
    private fun zipFolder(folder: File, target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            folder.listFiles().orEmpty().sortedBy { it.name }.forEach {
                zip.putNextEntry(ZipEntry(it.name)); zip.write(it.readBytes()); zip.closeEntry()
            }
        }
    }
}
