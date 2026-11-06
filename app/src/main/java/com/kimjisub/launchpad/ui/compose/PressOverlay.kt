package com.kimjisub.launchpad.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

/**
 * Press feedback drawn by Compose alone: a flat shape in the content colour while pressed, faded out
 * frame by frame after release. Material's ripple is drawn on Android by a framework RippleDrawable,
 * which hands its animations to the render thread (RenderNodeAnimator); this one never does.
 *
 * [maxHeight] keeps the overlay to the drawn container when the node is taller, as a Material button
 * is around its touch target.
 */
class PressOverlayIndication(
	private val shape: Shape = RectangleShape,
	private val maxHeight: Dp = Dp.Infinity,
) : IndicationNodeFactory {
	override fun create(interactionSource: InteractionSource): DelegatableNode =
		PressOverlayNode(interactionSource, shape, maxHeight)

	override fun equals(other: Any?): Boolean =
		other is PressOverlayIndication && other.shape == shape && other.maxHeight == maxHeight

	override fun hashCode(): Int = 31 * shape.hashCode() + maxHeight.hashCode()

	companion object {
		/** Material 3's pressed state layer opacity, the strength of the ripple it replaces. */
		const val PRESSED_ALPHA = 0.1f
		const val FADE_OUT_MS = 150
	}
}

private class PressOverlayNode(
	private val interactionSource: InteractionSource,
	private val shape: Shape,
	private val maxHeight: Dp,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
	private val alpha = Animatable(0f)

	override fun onAttach() {
		coroutineScope.launch {
			val presses = mutableSetOf<PressInteraction.Press>()
			interactionSource.interactions.collect { interaction ->
				when (interaction) {
					is PressInteraction.Press -> presses += interaction
					is PressInteraction.Release -> presses -= interaction.press
					is PressInteraction.Cancel -> presses -= interaction.press
					else -> return@collect
				}
				launch {
					if (presses.isNotEmpty()) alpha.snapTo(PressOverlayIndication.PRESSED_ALPHA)
					else alpha.animateTo(0f, tween(PressOverlayIndication.FADE_OUT_MS))
				}
			}
		}
	}

	override fun ContentDrawScope.draw() {
		drawContent()
		val strength = alpha.value
		if (strength <= 0f) return
		val height = minOf(size.height, maxHeight.toPx())
		val color = currentValueOf(LocalContentColor).copy(alpha = strength)
		translate(top = (size.height - height) / 2) {
			drawOutline(shape.createOutline(Size(size.width, height), layoutDirection, this), color)
		}
	}
}

/**
 * Gives every press under [content] the [PressOverlayIndication] and turns Material's ripple off.
 * Material buttons draw no press feedback of their own then; use [PressOverlayIconButton] and
 * [PressOverlayTextButton] for them.
 */
@Composable
fun WithoutRipple(content: @Composable () -> Unit) {
	CompositionLocalProvider(
		LocalIndication provides PressOverlayIndication(),
		LocalRippleConfiguration provides null,
		content = content,
	)
}

/** The overlay fills the drawn circle: the button's own node is as large as its touch target. */
@Composable
fun PressOverlayIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	val interactionSource = remember { MutableInteractionSource() }
	IconButton(onClick = onClick, modifier = modifier, interactionSource = interactionSource) {
		Box(
			modifier = Modifier.fillMaxSize().indication(interactionSource, PressOverlayIndication(CircleShape)),
			contentAlignment = Alignment.Center,
		) { content() }
	}
}

@Composable
fun PressOverlayTextButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	content: @Composable RowScope.() -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	TextButton(
		onClick = onClick,
		modifier = modifier.indication(interactionSource, PressOverlayIndication(ButtonDefaults.textShape, ButtonDefaults.MinHeight)),
		enabled = enabled,
		interactionSource = interactionSource,
		content = content,
	)
}
