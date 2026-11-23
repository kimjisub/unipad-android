package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 9×9 square: eight round buttons on top, an 8×8 pad block below them and eight round buttons on
 * its right, with the top-right corner left empty. Cell indices follow the launchpad layout order:
 * 0–63 pads row by row from the top left, 64–71 the top buttons from the left, 72–79 the right
 * buttons from the top.
 */
object PadGridGeometry {
	const val CELLS = 9
	const val SIZE = 80
	/** Share of a cell's pitch left as the gap around the drawn pad. */
	const val GAP_FRACTION = 0.1f

	fun index(row: Int, column: Int): Int? = when {
		row !in 0 until CELLS || column !in 0 until CELLS -> null
		row == 0 -> if (column < 8) 64 + column else null
		column == 8 -> 72 + row - 1
		else -> (row - 1) * 8 + column
	}

	/** The cell under a point of a square of [side] pixels; the whole pitch counts, so the gaps do not release a finger. */
	fun indexAt(x: Float, y: Float, side: Float): Int? {
		if (side <= 0f || x < 0f || y < 0f || x >= side || y >= side) return null
		val pitch = side / CELLS
		return index((y / pitch).toInt(), (x / pitch).toInt())
	}

	/** (row, column) of a cell in the 9×9 square. */
	fun position(index: Int): Pair<Int, Int> = when (index) {
		in 0..63 -> (index / 8 + 1) to (index % 8)
		in 64..71 -> 0 to (index - 64)
		in 72..79 -> (index - 72 + 1) to 8
		else -> throw IllegalArgumentException("No cell $index")
	}

	fun isEdgeButton(index: Int) = index >= 64

	/** The drawn pad size for a square of [side]. */
	fun padSize(side: Dp): Dp = side / CELLS * (1f - GAP_FRACTION)
}

private val PadOff = Color(0xFF2A3648)
private val EdgeOff = Color(0xFF34435A)
private val PressedBorder = Color.White

/**
 * Draws the grid only: the caller owns touches (on this composable's square bounds, see
 * [PadGridGeometry.indexAt]), connections and what a cell's semantics say and do.
 *
 * @param colors cell colors, [Color.Unspecified] for an unlit cell.
 * @param pressed cells a finger is holding; drawn with a white border, separate from their color.
 */
@Composable
fun LaunchpadPadGrid(
	colors: (Int) -> Color,
	pressed: (Int) -> Boolean,
	label: (Int) -> String,
	enabled: Boolean,
	modifier: Modifier = Modifier,
	cellSemantics: (Int) -> (SemanticsPropertyReceiver.() -> Unit) = { {} },
) {
	BoxWithConstraints(modifier.aspectRatio(1f)) {
		val pitch = minOf(maxWidth, maxHeight) / PadGridGeometry.CELLS
		val gap = pitch * PadGridGeometry.GAP_FRACTION
		// Sized from the pad, not the user's font scale: the number has to fit inside it.
		val labelSize = with(LocalDensity.current) { (pitch * 0.3f).toSp() }
		for (index in 0 until PadGridGeometry.SIZE) {
			val (row, column) = PadGridGeometry.position(index)
			val lit = colors(index)
			val edge = PadGridGeometry.isEdgeButton(index)
			val fill = if (enabled && lit != Color.Unspecified) lit else if (edge) EdgeOff else PadOff
			val shape = if (edge) CircleShape else RoundedCornerShape(pitch * 0.12f)
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.offset(x = pitch * column, y = pitch * row)
					.size(pitch)
					.padding(gap / 2)
					.clearAndSetSemantics(cellSemantics(index))
					.alpha(if (enabled) 1f else 0.55f)
					.background(fill, shape)
					.then(if (pressed(index)) Modifier.border(pitch * 0.06f, PressedBorder, shape) else Modifier),
			) {
				Text(
					text = label(index),
					fontSize = labelSize,
					fontWeight = FontWeight.Medium,
					color = if (fill.luminance() > 0.45f) Color(0xFF111825) else Color(0xFFC9D2DE),
					maxLines = 1,
					softWrap = false,
				)
			}
		}
	}
}
