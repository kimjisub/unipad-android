package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
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
		val outer = OUTER_WIDTH.toPx()
		// Both strokes share one centre line, leaving a black edge on each side of the white ring.
		val shift = outset.toPx() - outer / 2
		val ringSize = Size(size.width + shift * 2, size.height + shift * 2)
		translate(-shift, -shift) {
			val outline = shape.createOutline(ringSize, layoutDirection, this)
			drawOutline(outline, Color.Black, style = Stroke(outer))
			drawOutline(outline, Color.White, style = Stroke(INNER_WIDTH.toPx()))
		}
	}

	private companion object {
		val OUTER_WIDTH = 4.dp
		val INNER_WIDTH = 2.dp
	}
}
