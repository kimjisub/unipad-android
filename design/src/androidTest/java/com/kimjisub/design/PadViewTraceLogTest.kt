package com.kimjisub.design

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.design.view.PadView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PadViewTraceLogTest {
	@Test
	fun settingSkinColorClearsPreviousNumberShadow() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val pad = PadView(instrumentation.targetContext)
			pad.setTraceLogText("1 ")
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

	@Test
	fun deferredNumberKeepsXmlTextSize() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val context = instrumentation.targetContext
			val original = LayoutInflater.from(context).inflate(R.layout.view_pad, FrameLayout(context), false)
				.findViewById<TextView>(R.id.traceLog)
			val pad = PadView(context).setTraceLogText("1 2 3 ")
			val deferred = pad.findViewById<TextView>(R.id.traceLog)
			assertEquals("XML and deferred number size in pixels", original.textSize, deferred.textSize, 0f)
		}
	}

	@Test
	fun unusedOverlaysAreNotCreatedForAnEmptyPad() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val pad = PadView(instrumentation.targetContext)
			pad.setTraceLogTextColor(Color.BLUE, Color.BLACK)
			pad.setTraceLogText("")
			pad.appendTraceLog("")
			pad.setPhantomImageDrawable(null)
			pad.setPhantomRotation(90f)
			pad.stopGuideAnimation()
			assertNull(pad.findViewById<View>(R.id.traceLog))
			assertNull(pad.findViewById<View>(R.id.guideTiming))
			assertNull(pad.findViewById<View>(R.id.phantom))
			assertEquals(3, pad.childCount)
		}
	}

	@Test
	fun textCreatedAfterStyleKeepsColorAndShadow() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val pad = PadView(instrumentation.targetContext)
			pad.setTraceLogTextColor(Color.BLUE, Color.BLACK)
			pad.appendTraceLog("1 ")
			val text = pad.findViewById<TextView>(R.id.traceLog)
			val density = pad.resources.displayMetrics.density
			assertEquals("1 ", text.text.toString())
			assertEquals(Color.BLUE, text.currentTextColor)
			assertEquals(density, text.shadowRadius, 0f)
			assertEquals(0f, text.shadowDx, 0f)
			assertEquals(density / 2f, text.shadowDy, 0f)
			assertEquals(Color.BLACK, text.shadowColor)
		}
	}

	@Test
	fun overlaysKeepTheirDrawOrderWhenCreatedInReverse() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val pad = PadView(instrumentation.targetContext)
			pad.setTraceLogText("1 ")
			pad.startGuideAnimation(SystemClock.elapsedRealtime() + 1000)
			pad.setPhantomRotation(90f)
			pad.setPhantomImageDrawable(ColorDrawable(Color.WHITE))
			assertEquals(
				listOf(R.id.background, R.id.led, R.id.phantom, R.id.guideTiming, R.id.traceLog, R.id.touchSpace),
				(0 until pad.childCount).map { pad.getChildAt(it).id },
			)
			val phantom = pad.findViewById<ImageView>(R.id.phantom)
			assertEquals(90f, phantom.rotation, 0f)
			assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, phantom.importantForAccessibility)
			pad.setPhantomImageDrawable(null)
			assertNull(phantom.drawable)
			pad.stopGuideAnimation()
			assertEquals(View.GONE, pad.findViewById<View>(R.id.guideTiming).visibility)
			val touch = pad.findViewById<View>(R.id.touchSpace)
			var clicked = false
			pad.setOnClickListener { clicked = true }
			touch.performClick()
			assertTrue(clicked)
			assertTrue(touch.isClickable)
			assertEquals(false, touch.isFocusable)
		}
	}

	@Test
	fun latestStyleBeforeTextWinsAndClearingReusesTheTextView() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val pad = PadView(instrumentation.targetContext)
			pad.setTraceLogTextColor(Color.BLUE, Color.BLACK)
			pad.setTraceLogTextColor(Color.WHITE)
			pad.setTraceLogText("1 ")
			val text = pad.findViewById<TextView>(R.id.traceLog)
			assertEquals(Color.WHITE, text.currentTextColor)
			assertEquals(0f, text.shadowRadius, 0f)
			pad.setTraceLogText(null)
			assertEquals("", text.text.toString())
			pad.appendTraceLog("2 ")
			assertEquals("2 ", text.text.toString())
			assertEquals(text, pad.findViewById<TextView>(R.id.traceLog))
		}
	}
}
