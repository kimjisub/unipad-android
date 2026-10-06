package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRingTest {

	private val density = Density(2.75f)

	private fun geometry(element: Dp, outset: Dp = 0.dp, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) =
		with(density) {
			val px = element.toPx()
			focusRingGeometry(shape, Size(px, px), outset, LayoutDirection.Ltr)
		}

	@Test
	fun elementsSmallerThanTheRingStillGetAnOutline() {
		for (shape in listOf(RoundedCornerShape(8.dp), RoundedCornerShape(5.dp), CircleShape, RectangleShape)) {
			for (element in listOf(0.dp, 2.dp, 3.9.dp)) {
				val ring = geometry(element, shape = shape)
				val bounds = ring.outline.bounds
				assertTrue("$shape at $element", bounds.width >= 0f && bounds.height >= 0f)
			}
		}
	}

	@Test
	fun aTooSmallElementHasItsRingOnItsCentre() {
		val ring = geometry(2.dp)
		val half = with(density) { 1.dp.toPx() }
		assertEquals(Offset(-half, -half), ring.shift)
		assertEquals(0f, ring.outline.bounds.width, 0.001f)
	}

	@Test
	fun theRingSitsJustInsideAnOrdinaryElementAndOutsideWithAnOutset() {
		with(density) {
			val inside = geometry(40.dp)
			assertEquals(Offset(-2.dp.toPx(), -2.dp.toPx()), inside.shift)
			assertEquals(36.dp.toPx(), inside.outline.bounds.width, 0.001f)

			val outside = geometry(24.dp, outset = 6.dp, shape = CircleShape)
			assertEquals(Offset(4.dp.toPx(), 4.dp.toPx()), outside.shift)
			assertEquals(32.dp.toPx(), outside.outline.bounds.width, 0.001f)
		}
	}
}
