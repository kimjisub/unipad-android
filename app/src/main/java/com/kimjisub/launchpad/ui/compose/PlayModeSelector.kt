package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PlayModeOption(val label: String, val color: Color, val selected: Boolean, val onSelect: () -> Unit)

object PlayModeSelectorTags {
	const val SELECTOR = "play_mode_selector"
}

private val OuterPadding = 3.dp
private val Gap = 2.dp
private const val SIDE_BY_SIDE_MAX_LINES = 2

/**
 * The play screen's mode choice. The options sit side by side while every name fits its third
 * of the width in at most two lines without breaking a word; otherwise (a long word, or larger
 * text) they are stacked so each name keeps the full width and is shown whole.
 * Screen readers hear the options as one group of radio buttons, with the chosen mode selected.
 */
@Composable
fun PlayModeSelector(options: List<PlayModeOption>, textColor: Color, fillColor: Color, modifier: Modifier = Modifier) {
	BoxWithConstraints(modifier.fillMaxWidth()) {
		// Keep the inherited line height and letter spacing of the existing mode labels.
		val labelStyle = LocalTextStyle.current.copy(fontSize = 12.sp, textAlign = TextAlign.Center)
		val measurer = rememberTextMeasurer()
		val cellWidth = with(LocalDensity.current) {
			playModeCellWidth(constraints.maxWidth, options.size)
		}
		// Measured bold, as a selected name is drawn, so selecting a mode never changes the layout.
		val sideBySide = options.all { fitsSideBySide(measurer, it.label, cellWidth, labelStyle) }
		val container = Modifier
			.fillMaxWidth()
			.testTag(PlayModeSelectorTags.SELECTOR)
			.selectableGroup()
			.background(fillColor, RoundedCornerShape(10.dp))
			.padding(OuterPadding)
		if (sideBySide) {
			Row(container.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
				options.forEach { Option(it, textColor, Modifier.weight(1f).fillMaxHeight(), SIDE_BY_SIDE_MAX_LINES, labelStyle) }
			}
		} else {
			Column(container, verticalArrangement = Arrangement.spacedBy(Gap)) {
				options.forEach { Option(it, textColor, Modifier.fillMaxWidth(), Int.MAX_VALUE, labelStyle) }
			}
		}
	}
}

@Composable
private fun Option(option: PlayModeOption, textColor: Color, modifier: Modifier, maxLines: Int, labelStyle: TextStyle) {
	Box(
		modifier = modifier
			.background(if (option.selected) option.color else Color.Transparent, RoundedCornerShape(8.dp))
			.focusRing()
			.selectable(selected = option.selected, role = Role.RadioButton, onClick = option.onSelect)
			.padding(vertical = 10.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = option.label,
			color = if (option.selected) Color.Black else textColor,
			style = labelStyle,
			fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal,
			maxLines = maxLines,
		)
	}
}

private fun fitsSideBySide(measurer: TextMeasurer, label: String, width: Int, labelStyle: TextStyle): Boolean {
	val layout = measurer.measure(
		text = label,
		style = labelStyle.copy(fontWeight = FontWeight.Bold),
		maxLines = SIDE_BY_SIDE_MAX_LINES,
		constraints = Constraints(maxWidth = width),
	)
	if (layout.hasVisualOverflow) return false
	// A word wider than the cell is broken inside the word, which is not an overflow.
	return (0 until layout.lineCount).all { line ->
		val end = layout.getLineEnd(line)
		end >= label.length || label[end].isWhitespace() || label[end - 1].isWhitespace()
	}
}

/** The width used to decide whether every weighted option can show its full label. */
internal fun Density.playModeCellWidth(widthPx: Int, optionCount: Int): Int =
	// Padding and gaps are rounded individually by the Row. A weighted cell may get
	// one pixel less than its siblings, so measure against the smallest cell.
	((widthPx - 2 * OuterPadding.roundToPx() - (optionCount - 1) * Gap.roundToPx()) / optionCount).coerceAtLeast(0)
