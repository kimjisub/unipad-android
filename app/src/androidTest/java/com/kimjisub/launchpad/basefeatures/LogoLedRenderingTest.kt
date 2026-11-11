package com.kimjisub.launchpad.basefeatures

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.ui.compose.LogoLedImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LogoLedRenderingTest : PlaybackScreenTest() {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun blinkingRecolorsOnlyTheLogoWithoutRecomposingOrMeasuringItsParent() {
        val scenario = screen.openPlay()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val image = bitmap.asImageBitmap()
        val color = mutableIntStateOf(Color.Red.toArgb())
        val compositions = AtomicInteger()
        val measurements = AtomicInteger()
        try {
            scenario.onActivity { activity: PlayActivity ->
                activity.setContent {
                    SideEffect { compositions.incrementAndGet() }
                    Layout(content = { LogoLedImage(image) { color.intValue } }, modifier = Modifier.size(64.dp)) { measurables, constraints ->
                        measurements.incrementAndGet()
                        val logo = measurables.single().measure(constraints)
                        layout(logo.width, logo.height) { logo.place(0, 0) }
                    }
                }
            }
            compose.waitForIdle()
            val initialCompositions = compositions.get()
            val initialMeasurements = measurements.get()
            for (next in listOf(Color.Blue, Color(0xFF333333), Color.Red)) {
                compose.runOnIdle { color.intValue = next.toArgb() }
                compose.waitForIdle()
                val pixels = compose.onNodeWithTag("play_logo_led").captureToImage().toPixelMap()
                assertEquals(next.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
                assertEquals("Blink recomposed the parent", initialCompositions, compositions.get())
                assertEquals("Blink measured the parent", initialMeasurements, measurements.get())
            }
        } finally {
            scenario.close()
            bitmap.recycle()
        }
    }
}
