package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.ui.theme.Background1
import com.kimjisub.launchpad.ui.theme.DarkSurface
import com.kimjisub.launchpad.ui.theme.DarkSurfaceHighest
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.ui.theme.Green
import com.kimjisub.launchpad.ui.theme.Orange
import com.kimjisub.launchpad.ui.theme.OverlayLight
import com.kimjisub.launchpad.ui.theme.Red
import com.kimjisub.launchpad.ui.theme.TitleColor
import com.kimjisub.launchpad.ui.theme.White
import com.kimjisub.launchpad.unipack.UniPack
import java.io.File

sealed class ImportResult {
	data class Success(val folder: File, val unipack: UniPack, val byteSize: Long) : ImportResult()
	data class Warning(val message: String) : ImportResult()
	data class Error(val message: String) : ImportResult()
}

@Composable
fun ImportProgressDialog() {
	AlertDialog(
		onDismissRequest = {},
		confirmButton = {},
		title = { Text(stringResource(R.string.importing)) },
		text = {
			Column {
				Text(stringResource(R.string.wait_a_sec))
				Spacer(Modifier.height(16.dp))
				LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
			}
		},
	)
}

private val DialogMaxWidth = 480.dp
private val DialogShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(10.dp)
private val ButtonMinHeight = 44.dp
private val SecondaryButtonMinWidth = 96.dp
private val ButtonGap = 10.dp

/**
 * Result of a UniPack import: status line, pack summary (or the error text) and the actions.
 * The body scrolls on its own so the buttons stay visible on small screens and large font scales.
 */
@Composable
fun ImportResultDialog(
	result: ImportResult,
	onDismiss: () -> Unit,
	onPlayNow: (ImportResult.Success) -> Unit,
) {
	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		val currentOnDismiss by rememberUpdatedState(onDismiss)
		// The margin around the card belongs to the dialog content, so the platform does not treat it
		// as "outside". This full-window layer dismisses on those taps; the card swallows its own.
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.fillMaxSize()
				.pointerInput(Unit) { detectTapGestures { currentOnDismiss() } }
				.padding(horizontal = 24.dp, vertical = 16.dp),
		) {
			Column(
				modifier = Modifier
					.widthIn(max = DialogMaxWidth)
					.fillMaxWidth()
					.pointerInput(Unit) { detectTapGestures {} }
					.background(DarkSurface, DialogShape)
					.border(1.dp, White.copy(alpha = 0.06f), DialogShape),
			) {
				val scrollState = rememberScrollState()
				Box(Modifier.weight(1f, fill = false)) {
					Column(
						modifier = Modifier
							.verticalScroll(scrollState)
							.padding(start = 20.dp, top = 20.dp, end = 20.dp),
					) {
						StatusLine(result)
						when (result) {
							is ImportResult.Success -> PackSummary(result)
							is ImportResult.Warning -> MessageBox(result.message)
							is ImportResult.Error -> MessageBox(result.message)
						}
					}
					if (scrollState.canScrollForward) {
						Box(
							Modifier
								.align(Alignment.BottomCenter)
								.fillMaxWidth()
								.height(28.dp)
								.background(Brush.verticalGradient(listOf(DarkSurface.copy(alpha = 0f), DarkSurface))),
						)
					}
				}

				val okText = stringResource(android.R.string.ok)
				Box(Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 20.dp)) {
					if (result is ImportResult.Success) {
						ActionButtons(
							secondary = { SecondaryButton(text = okText, onClick = onDismiss) },
							primary = {
								PrimaryButton(
									text = stringResource(R.string.importPlayNow),
									icon = Icons.Filled.PlayArrow,
									onClick = { onPlayNow(result) },
								)
							},
						)
					} else {
						SecondaryButton(text = okText, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
					}
				}
			}
		}
	}
}

@Composable
private fun StatusLine(result: ImportResult) {
	val (icon, color, text) = when (result) {
		is ImportResult.Success -> Triple(Icons.Filled.CheckCircle, Green, stringResource(R.string.importComplete))
		is ImportResult.Warning -> Triple(Icons.Filled.Warning, Orange, stringResource(R.string.warning))
		is ImportResult.Error -> Triple(Icons.Filled.Error, Red, stringResource(R.string.importFailed))
	}
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		modifier = Modifier.semantics(mergeDescendants = true) { heading() },
	) {
		Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(textSized(18.sp)))
		Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
	}
}

@Composable
private fun PackSummary(result: ImportResult.Success) {
	val unipack = result.unipack
	Text(
		text = unipack.title,
		color = White,
		fontSize = 20.sp,
		lineHeight = 24.sp,
		fontWeight = FontWeight.Bold,
		maxLines = 2,
		overflow = TextOverflow.Ellipsis,
		modifier = Modifier.padding(top = 10.dp),
	)
	if (unipack.producerName.isNotEmpty()) {
		Text(
			text = unipack.producerName,
			color = Gray1,
			fontSize = 13.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(top = 2.dp),
		)
	}

	val padSize = "${unipack.buttonX} × ${unipack.buttonY}"
	val chainLabel = stringResource(R.string.MPP_chain)
	val autoPlayLabel = stringResource(R.string.autoPlay)
	val fileSize = "${FileManager.byteToMB(result.byteSize)} MB"
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
		modifier = Modifier.padding(top = 14.dp),
	) {
		InfoChip(
			icon = R.drawable.ic_pad_24dp,
			text = padSize,
			description = "${stringResource(R.string.padSize)} $padSize",
		)
		InfoChip(
			icon = R.drawable.ic_chain_24dp,
			text = "$chainLabel ${unipack.chain}",
		)
		if (unipack.keyLedExist) {
			InfoChip(icon = R.drawable.ic_led_event_24dp, text = "LED")
		}
		if (unipack.autoPlayExist) {
			InfoChip(icon = R.drawable.ic_music_note_24dp, text = autoPlayLabel)
		}
		InfoChip(
			icon = R.drawable.ic_storage,
			text = fileSize,
			description = "${stringResource(R.string.fileSize)} $fileSize",
		)
	}
}

@Composable
private fun InfoChip(icon: Int, text: String, description: String = text) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(5.dp),
		modifier = Modifier
			.heightIn(min = 24.dp)
			.background(DarkSurfaceHighest, CircleShape)
			.padding(horizontal = 10.dp, vertical = 3.dp)
			.clearAndSetSemantics { contentDescription = description },
	) {
		Icon(
			painter = painterResource(icon),
			contentDescription = null,
			tint = TitleColor,
			modifier = Modifier.size(textSized(12.sp)),
		)
		Text(text = text, color = Gray1, fontSize = 12.sp, maxLines = 1)
	}
}

/** Icons next to text grow with the user's font scale, like the text itself. */
@Composable
private fun textSized(size: TextUnit) = with(LocalDensity.current) { size.toDp() }

@Composable
private fun MessageBox(message: String) {
	Text(
		text = message,
		color = Gray1,
		fontSize = 13.sp,
		lineHeight = 18.sp,
		modifier = Modifier
			.padding(top = 12.dp)
			.fillMaxWidth()
			.background(OverlayLight, RoundedCornerShape(10.dp))
			.padding(horizontal = 12.dp, vertical = 10.dp),
	)
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
	Button(
		onClick = onClick,
		shape = ButtonShape,
		colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceHighest, contentColor = Gray1),
		contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
		modifier = modifier.heightIn(min = ButtonMinHeight).widthIn(min = SecondaryButtonMinWidth),
	) {
		Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
	}
}

@Composable
private fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		shape = ButtonShape,
		colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Background1),
		contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
		modifier = Modifier.heightIn(min = ButtonMinHeight),
	) {
		Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(textSized(18.sp)))
		Spacer(Modifier.size(8.dp))
		Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
	}
}

/**
 * Places [secondary] at its natural width and lets [primary] fill the rest of the row.
 * When the primary label would not fit on one line beside it, the buttons stack at full width
 * with [primary] on top, so button labels are never cut.
 */
@Composable
private fun ActionButtons(
	secondary: @Composable () -> Unit,
	primary: @Composable () -> Unit,
) {
	Layout(
		contents = listOf(secondary, primary),
		modifier = Modifier.fillMaxWidth(),
	) { (secondaryMeasurables, primaryMeasurables), constraints ->
		val secondaryMeasurable = secondaryMeasurables.first()
		val primaryMeasurable = primaryMeasurables.first()
		val width = constraints.maxWidth
		val gap = ButtonGap.roundToPx()

		val secondaryWidth = secondaryMeasurable.maxIntrinsicWidth(constraints.maxHeight)
		val primaryWidth = primaryMeasurable.maxIntrinsicWidth(constraints.maxHeight)
		val fitsInRow = secondaryWidth + gap + primaryWidth <= width

		if (fitsInRow) {
			val secondaryPlaceable = secondaryMeasurable.measure(Constraints.fixedWidth(secondaryWidth))
			val primaryPlaceable = primaryMeasurable.measure(Constraints.fixedWidth(width - secondaryWidth - gap))
			val height = maxOf(secondaryPlaceable.height, primaryPlaceable.height)
			layout(width, height) {
				secondaryPlaceable.place(0, (height - secondaryPlaceable.height) / 2)
				primaryPlaceable.place(secondaryWidth + gap, (height - primaryPlaceable.height) / 2)
			}
		} else {
			val primaryPlaceable = primaryMeasurable.measure(Constraints.fixedWidth(width))
			val secondaryPlaceable = secondaryMeasurable.measure(Constraints.fixedWidth(width))
			layout(width, primaryPlaceable.height + gap + secondaryPlaceable.height) {
				primaryPlaceable.place(0, 0)
				secondaryPlaceable.place(0, primaryPlaceable.height + gap)
			}
		}
	}
}
