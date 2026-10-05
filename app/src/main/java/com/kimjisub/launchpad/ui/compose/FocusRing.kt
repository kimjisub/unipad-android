package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp

/**
 * Outlines the element while it holds keyboard focus, so Tab navigation shows where it is.
 * The ring is white inside black, readable on the light list and on the dark play screen.
 * Place it before the element's clickable (or the button's own modifier): it follows the focus
 * of the first focusable element after it.
 */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(8.dp)): Modifier = this then FocusRingElement(shape)

private data class FocusRingElement(val shape: Shape) : ModifierNodeElement<FocusRingNode>() {
	override fun create() = FocusRingNode(shape)

	override fun update(node: FocusRingNode) {
		node.shape = shape
		node.invalidateDraw()
	}

	override fun InspectorInfo.inspectableProperties() {
		name = "focusRing"
		properties["shape"] = shape
	}
}

private class FocusRingNode(var shape: Shape) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
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
		inset(outer / 2) {
			val outline = shape.createOutline(size, layoutDirection, this)
			drawOutline(outline, Color.Black, style = Stroke(outer))
			drawOutline(outline, Color.White, style = Stroke(INNER_WIDTH.toPx()))
		}
	}

	private companion object {
		val OUTER_WIDTH = 4.dp
		val INNER_WIDTH = 2.dp
	}
}
