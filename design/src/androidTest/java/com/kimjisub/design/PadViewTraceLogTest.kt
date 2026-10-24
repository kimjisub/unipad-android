package com.kimjisub.design

import android.graphics.Color
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.design.view.PadView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PadViewTraceLogTest {
	@Test
	fun settingSkinColorClearsPreviousNumberShadow() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val pad = PadView(instrumentation.targetContext)
			val text = pad.findViewById<TextView>(R.id.traceLog)
			text.setShadowLayer(2f, 0f, 1f, Color.BLACK)
			pad.setTraceLogTextColor(Color.BLUE)
			assertEquals(Color.BLUE, text.currentTextColor)
			assertEquals(0f, text.shadowRadius, 0f)
			assertEquals(0f, text.shadowDx, 0f)
			assertEquals(0f, text.shadowDy, 0f)
			assertEquals(Color.TRANSPARENT, text.shadowColor)
		}
	}
}
