package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Outlines the element while it holds keyboard focus, so Tab navigation shows where it is.
 * The ring is white inside black, readable on the light list and on the dark play screen.
 * Place it before the element's clickable (or the button's own modifier): it follows the focus
 * of the first focusable element after it.
 * [outset] draws the ring that far outside the element, for small icons the ring would otherwise
 * cover, without changing the element's size or the layout around it.
 */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(8.dp), outset: Dp = 0.dp): Modifier =
	this then FocusRingElement(shape, outset)

private data class FocusRingElement(val shape: Shape, val outset: Dp) : ModifierNodeElement<FocusRingNode>() {
	override fun create() = FocusRingNode(shape, outset)

	override fun update(node: FocusRingNode) {
		node.shape = shape
		node.outset = outset
		node.invalidateDraw()
	}

	override fun InspectorInfo.inspectableProperties() {
		name = "focusRing"
		properties["shape"] = shape
		properties["outset"] = outset
	}
}

private class FocusRingNode(var shape: Shape, var outset: Dp) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
	private var focused = false

	override fun onFocusEvent(focusState: FocusState) {
		if (focused != focusState.isFocused) {
			focused = focusState.isFocused
			invalidateDraw()
		}
	}

	override fun ContentDrawScope.draw() {
		drawContent()
		if (!focused) return
		val ring = focusRingGeometry(shape, size, outset, layoutDirection)
		translate(-ring.shift.x, -ring.shift.y) {
			drawOutline(ring.outline, Color.Black, style = Stroke(FOCUS_RING_OUTER_WIDTH.toPx()))
			drawOutline(ring.outline, Color.White, style = Stroke(FOCUS_RING_INNER_WIDTH.toPx()))
		}
	}
}

private val FOCUS_RING_OUTER_WIDTH = 4.dp
private val FOCUS_RING_INNER_WIDTH = 2.dp

/** Where the ring's centre line runs: [shift] outside the element's top-left corner, then [outline]. */
internal class FocusRingGeometry(val shift: Offset, val outline: Outline)

internal fun Density.focusRingGeometry(shape: Shape, elementSize: Size, outset: Dp, layoutDirection: LayoutDirection): FocusRingGeometry {
	// Both strokes share one centre line, leaving a black edge on each side of the white ring.
	val shift = outset.toPx() - FOCUS_RING_OUTER_WIDTH.toPx() / 2
	// An element thinner than the inward shift would give the shape a negative size, which it rejects;
	// such an element gets its ring collapsed onto its centre instead.
	val shiftX = shift.coerceAtLeast(-elementSize.width / 2)
	val shiftY = shift.coerceAtLeast(-elementSize.height / 2)
	val ringSize = Size(elementSize.width + shiftX * 2, elementSize.height + shiftY * 2)
	return FocusRingGeometry(Offset(shiftX, shiftY), shape.createOutline(ringSize, layoutDirection, this))
}
