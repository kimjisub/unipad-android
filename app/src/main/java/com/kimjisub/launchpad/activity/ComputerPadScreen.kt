package com.kimjisub.launchpad.activity

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.midi.Mk2Layout
import com.kimjisub.launchpad.midi.PeripheralDevice
import com.kimjisub.launchpad.midi.UsbPeripheralConnection.Failure
import com.kimjisub.launchpad.ui.compose.AppAlertDialog
import com.kimjisub.launchpad.ui.compose.LaunchpadPadGrid
import com.kimjisub.launchpad.ui.compose.PadGridGeometry
import com.kimjisub.launchpad.ui.compose.barsAndCutout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal object ComputerPadTags {
	const val GRID = "computer_pad_grid"
	const val STATUS = "computer_pad_status"
	const val TOO_SMALL = "computer_pad_too_small"
	/** The window's content area, inside the system bars and the outer padding. */
	const val CONTENT = "computer_pad_content"
}

private val ScreenBg = Color(0xFF161E2B)
private val CardBg = Color(0xFF1E2736)
private val TextPrimary = Color(0xFFE8ECF2)
private val TextSecondary = Color(0xFF8A96A8)
private val Accent = Color(0xFF4283E6)
private val Good = Color(0xFF4CD9A0)
private val Warning = Color(0xFFFFA24E)
private val MinButton = 48.dp
private val MinPad = 32.dp
/** About one display frame; the lights are state, so a burst between two polls shows its final colors. */
private const val LIGHT_POLL_MS = 16L

/** A stopped screen has closed its connection and draws nothing, so it does not poll. */
internal suspend fun pollWhileStarted(lifecycle: Lifecycle, intervalMs: Long, poll: () -> Unit) =
	lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
		while (isActive) {
			poll()
			delay(intervalMs)
		}
	}

@Composable
internal fun ComputerPadScreen(controller: ComputerPadController, onBack: () -> Unit) {
	val connection = controller.connection
	// Subscribing makes these changes recompose; the phase reads their current values.
	connection.state.collectAsState().value
	connection.devices.collectAsState().value
	connection.discoveryFailure.collectAsState().value
	connection.portsClosing.collectAsState().value
	val phase = controller.phase()
	var showHelp by remember { mutableStateOf(false) }

	val lifecycle = LocalLifecycleOwner.current.lifecycle
	LaunchedEffect(lifecycle) { pollWhileStarted(lifecycle, LIGHT_POLL_MS, controller::pollLights) }

	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.background(ScreenBg)
			.windowInsetsPadding(WindowInsets.barsAndCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical))
			.padding(8.dp)
			.testTag(ComputerPadTags.CONTENT),
	) {
		val landscape = maxWidth >= maxHeight
		val gridSide = if (landscape) minOf(maxHeight, maxWidth * 0.6f) else minOf(maxWidth, maxHeight * 0.6f)
		val tooSmall = PadGridGeometry.padSize(gridSide) < MinPad
		// A window too small for 32 dp pads takes no input; held pads are released when it shrinks.
		val generation = (phase as? ComputerPadPhase.Open)?.generation?.takeIf { !tooSmall }
		LaunchedEffect(generation) { controller.input.accept(generation) }
		val panel: @Composable (Modifier) -> Unit = { modifier ->
			GuideAndActions(
				modifier,
				guide = {
					Header(phase, onBack)
					PhaseContent(phase, controller)
				},
				actions = { PhaseActions(phase, controller, onBack = onBack, onHelp = { showHelp = true }) },
			)
		}
		val grid: @Composable () -> Unit = { PadArea(phase, controller, gridSide, tooSmall) }
		if (landscape) {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				panel(Modifier.weight(1f))
				grid()
			}
		} else {
			Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
				panel(Modifier.weight(1f).fillMaxWidth())
				grid()
			}
		}
	}
	if (showHelp) HelpDialog { showHelp = false }
}

/**
 * The guide above the actions. While the actions take at most half the height they stay in view
 * and only the guide scrolls; taller actions (a short window or large text) scroll together with
 * the guide, so neither the way back at its top nor any action is pushed out of reach.
 */
@Composable
private fun GuideAndActions(modifier: Modifier, guide: @Composable ColumnScope.() -> Unit, actions: @Composable ColumnScope.() -> Unit) {
	BoxWithConstraints(modifier) {
		val viewport = constraints.maxHeight
		Layout(
			content = {
				Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp), content = guide)
				Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
			},
			modifier = Modifier.verticalScroll(rememberScrollState()),
		) { (guidePart, actionsPart), constraints ->
			val width = constraints.maxWidth
			val actionsPlaced = actionsPart.measure(Constraints(maxWidth = width))
			val gap = if (actionsPlaced.height > 0) 8.dp.roundToPx() else 0
			val pinned = viewport != Constraints.Infinity && actionsPlaced.height <= viewport / 2
			// Not pinned, the guide gets its whole height, so only the panel as a whole scrolls.
			val guideHeight = if (pinned) (viewport - gap - actionsPlaced.height).coerceAtLeast(0) else guidePart.maxIntrinsicHeight(width)
			val guidePlaced = guidePart.measure(Constraints(maxWidth = width, maxHeight = guideHeight))
			layout(width, guidePlaced.height + gap + actionsPlaced.height) {
				guidePlaced.place(0, 0)
				actionsPlaced.place(0, guidePlaced.height + gap)
			}
		}
	}
}

@Composable
private fun Header(phase: ComputerPadPhase, onBack: () -> Unit) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		IconButton(onClick = onBack, modifier = Modifier.size(MinButton)) {
			Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.computer_pad_back), tint = TextPrimary)
		}
		Text(
			stringResource(R.string.computer_pad_title),
			color = TextPrimary,
			fontSize = 20.sp,
			fontWeight = FontWeight.SemiBold,
			modifier = Modifier.semantics { heading() },
		)
	}
	val (status, color) = statusLine(phase)
	Text(
		status,
		color = color,
		fontSize = 13.sp,
		modifier = Modifier
			.padding(start = 4.dp)
			.testTag(ComputerPadTags.STATUS)
			.semantics { liveRegion = LiveRegionMode.Polite },
	)
}

@Composable
private fun statusLine(phase: ComputerPadPhase): Pair<String, Color> = when (phase) {
	ComputerPadPhase.Unsupported -> stringResource(R.string.computer_pad_unsupported_title) to Warning
	is ComputerPadPhase.NotFound -> stringResource(phase.failure?.let(::failureText) ?: R.string.computer_pad_not_found) to Warning
	is ComputerPadPhase.Ready -> stringResource(R.string.computer_pad_found) to TextSecondary
	ComputerPadPhase.Connecting -> stringResource(R.string.computer_pad_connecting) to TextSecondary
	ComputerPadPhase.LightsUnavailable -> stringResource(R.string.computer_pad_lights_failed) to Warning
	is ComputerPadPhase.Open -> stringResource(R.string.computer_pad_port_open) to Good
	ComputerPadPhase.Closing -> stringResource(R.string.computer_pad_closing) to TextSecondary
	is ComputerPadPhase.Failed -> stringResource(failureTitle(phase.failure)) to Warning
	is ComputerPadPhase.Paused -> stringResource(R.string.computer_pad_paused_title) to Warning
}

private fun failureTitle(failure: Failure): Int = when (ComputerPadRules.kind(failure)) {
	ComputerPadFailureKind.NOT_CONNECTED -> R.string.computer_pad_connect_failed_title
	ComputerPadFailureKind.OTHER_CONNECTION -> R.string.computer_pad_busy_title
	ComputerPadFailureKind.DISCONNECTED -> R.string.computer_pad_disconnected_title
}

/** One sentence for each failure; the screen never shows a failure without its own reason. */
internal fun failureText(failure: Failure): Int = when (failure) {
	Failure.UNAVAILABLE -> R.string.computer_pad_fail_unavailable
	Failure.DISCOVERY -> R.string.computer_pad_fail_discovery
	Failure.BUSY -> R.string.computer_pad_busy_desc
	Failure.OPEN -> R.string.computer_pad_fail_open
	Failure.INPUT_PORT -> R.string.computer_pad_fail_input_port
	Failure.OUTPUT_PORT -> R.string.computer_pad_fail_output_port
	Failure.SEND -> R.string.computer_pad_fail_send
	Failure.RELEASE -> R.string.computer_pad_fail_release
	Failure.CLOSE -> R.string.computer_pad_fail_close
	Failure.OVERFLOW -> R.string.computer_pad_fail_overflow
	Failure.REMOVED -> R.string.computer_pad_fail_removed
	Failure.TIMEOUT -> R.string.computer_pad_fail_timeout
}

@Composable
private fun ColumnScope.PhaseContent(phase: ComputerPadPhase, controller: ComputerPadController) {
	val portName = portNamer()
	when (phase) {
		ComputerPadPhase.Unsupported -> Note(stringResource(R.string.computer_pad_unsupported_desc))
		is ComputerPadPhase.NotFound -> {
			Steps()
			Note(stringResource(R.string.computer_pad_preview))
		}
		is ComputerPadPhase.Ready -> {
			Choices(phase.devices, controller, portName)
			Note(stringResource(R.string.computer_pad_preview))
		}
		ComputerPadPhase.Connecting, ComputerPadPhase.LightsUnavailable, ComputerPadPhase.Closing -> Unit
		is ComputerPadPhase.Open -> {
			val connected = controller.connected
			InfoCard(
				stringResource(R.string.computer_pad_send),
				stringResource(R.string.computer_pad_send_direction),
				listOfNotNull(connected?.device, connected?.pressPort).joinToString(" · ") + " · " + stringResource(R.string.computer_pad_send_ready),
			)
			InfoCard(
				stringResource(R.string.computer_pad_receive),
				stringResource(R.string.computer_pad_receive_direction),
				stringResource(
					when (phase.lights) {
						ComputerPadPhase.Lights.NOT_RECEIVING -> R.string.computer_pad_no_lights
						ComputerPadPhase.Lights.NO_SIGNAL_YET -> R.string.computer_pad_lights_waiting
						ComputerPadPhase.Lights.RECENT -> R.string.computer_pad_lights_recent
					},
				),
			)
			InfoCard(stringResource(R.string.computer_pad_layout), stringResource(R.string.computer_pad_no_sound), null)
			if (phase.lights == ComputerPadPhase.Lights.NO_SIGNAL_YET) Note(stringResource(R.string.computer_pad_set_output))
		}
		is ComputerPadPhase.Failed -> {
			Note(stringResource(failureText(phase.failure)), TextPrimary)
			when (ComputerPadRules.kind(phase.failure)) {
				ComputerPadFailureKind.NOT_CONNECTED -> Unit
				ComputerPadFailureKind.OTHER_CONNECTION -> Note(stringResource(R.string.computer_pad_busy_no_takeover))
				ComputerPadFailureKind.DISCONNECTED -> {
					Note(stringResource(R.string.computer_pad_disconnected_desc))
					Note(stringResource(R.string.computer_pad_stop_sound), TextPrimary)
				}
			}
		}
		is ComputerPadPhase.Paused -> {
			Note(stringResource(R.string.computer_pad_paused_desc))
			Note(stringResource(R.string.computer_pad_stop_sound))
		}
	}
}

/** The actions of each phase; a connect action that waits for the previous ports says why next to it. */
@Composable
private fun PhaseActions(phase: ComputerPadPhase, controller: ComputerPadController, onBack: () -> Unit, onHelp: () -> Unit) {
	val portName = portNamer()
	val previousClosing = when (phase) {
		is ComputerPadPhase.Ready -> phase.previousClosing
		is ComputerPadPhase.Paused -> phase.previousClosing
		else -> false
	}
	if (previousClosing) Note(stringResource(R.string.computer_pad_closing_previous), Warning)
	when (phase) {
		ComputerPadPhase.Unsupported -> Buttons { PrimaryButton(R.string.computer_pad_back, onClick = onBack) }
		is ComputerPadPhase.NotFound -> Buttons {
			PrimaryButton(R.string.computer_pad_search_again) { controller.search() }
			SecondaryButton(R.string.computer_pad_help, onHelp)
		}
		is ComputerPadPhase.Ready -> Buttons {
			PrimaryButton(R.string.computer_pad_connect, enabled = !phase.previousClosing && controller.canConnect(phase.devices)) {
				controller.connect(phase.devices, portName)
			}
			SecondaryButton(R.string.computer_pad_search_again) { controller.search() }
			SecondaryButton(R.string.computer_pad_help, onHelp)
		}
		ComputerPadPhase.Connecting -> Buttons { SecondaryButton(R.string.cancel) { controller.disconnect() } }
		ComputerPadPhase.LightsUnavailable -> Buttons {
			PrimaryButton(R.string.computer_pad_use_without_lights) { controller.useWithoutLights() }
			SecondaryButton(R.string.cancel) { controller.disconnect() }
		}
		is ComputerPadPhase.Open -> Buttons {
			SecondaryButton(R.string.computer_pad_help, onHelp)
			PrimaryButton(R.string.computer_pad_disconnect) { controller.disconnect() }
		}
		ComputerPadPhase.Closing -> Unit
		is ComputerPadPhase.Failed -> when (ComputerPadRules.kind(phase.failure)) {
			ComputerPadFailureKind.NOT_CONNECTED -> Buttons {
				PrimaryButton(R.string.computer_pad_search_again) { controller.search() }
				SecondaryButton(R.string.computer_pad_help, onHelp)
			}
			ComputerPadFailureKind.OTHER_CONNECTION -> Buttons {
				PrimaryButton(R.string.computer_pad_back, onClick = onBack)
				SecondaryButton(R.string.computer_pad_search_again) { controller.search() }
			}
			ComputerPadFailureKind.DISCONNECTED -> Buttons { PrimaryButton(R.string.computer_pad_search_again) { controller.search() } }
		}
		is ComputerPadPhase.Paused -> Buttons {
			PrimaryButton(R.string.computer_pad_reconnect, enabled = !phase.previousClosing) { controller.reconnect(portName) }
		}
	}
}

/** A port without a name is shown by its number. */
@Composable
private fun portNamer(): (PeripheralDevice.Port) -> String {
	val resources = LocalResources.current
	return { port -> port.name.ifBlank { resources.getString(R.string.computer_pad_port_number, port.number + 1) } }
}

@Composable
private fun Steps() {
	InfoCard(stringResource(R.string.computer_pad_step_cable), null, null)
	InfoCard(stringResource(R.string.computer_pad_step_usb), null, null)
	InfoCard(stringResource(R.string.computer_pad_step_app), null, stringResource(R.string.computer_pad_step_app_note))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(devices: List<PeripheralDevice>, controller: ComputerPadController, portName: (PeripheralDevice.Port) -> String) {
	val (device, press, light) = controller.selection(devices) ?: return
	if (devices.size > 1) {
		SectionTitle(stringResource(R.string.computer_pad_device))
		devices.forEachIndexed { i, candidate ->
			Choice("${i + 1}. ${ComputerPadRules.deviceName(candidate)}", candidate.id == device.id) { controller.deviceId = candidate.id }
		}
	} else InfoCard(ComputerPadRules.deviceName(device), null, null)
	SectionTitle(stringResource(R.string.computer_pad_send) + ": " + stringResource(R.string.computer_pad_send_direction))
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		device.inputs.forEach { port -> Choice(portName(port), port.number == press?.number) { controller.pressPort = port.number } }
	}
	if (press == null) Note(stringResource(R.string.computer_pad_no_press_port), Warning)
	SectionTitle(stringResource(R.string.computer_pad_receive) + ": " + stringResource(R.string.computer_pad_receive_direction))
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		device.outputs.forEach { port ->
			Choice(portName(port), port.number == light?.number) { controller.lightPort = port.number; controller.receiveLights = true }
		}
		Choice(stringResource(R.string.computer_pad_no_lights), light == null) { controller.receiveLights = false }
	}
}

@Composable
private fun PadArea(phase: ComputerPadPhase, controller: ComputerPadController, side: Dp, tooSmall: Boolean) {
	val resources = LocalResources.current
	val open = phase is ComputerPadPhase.Open
	val active = open && !tooSmall
	val pressAction = stringResource(R.string.computer_pad_press_action)
	val pressedState = stringResource(R.string.computer_pad_pressed)
	// The lock note stays inside the pads' square: wider, it would take the guide's and actions' width.
	Box(Modifier.size(side), contentAlignment = Alignment.Center) {
		LaunchpadPadGrid(
			colors = { index ->
				val color = controller.colors[index]
				if (open && color != 0) Color(LaunchpadColor.ARGB[color].toInt()) else Color.Unspecified
			},
			pressed = { controller.pressed[it] },
			label = { index -> Mk2Layout.address(index)!!.number.toString() },
			enabled = open,
			modifier = Modifier
				.size(side)
				.testTag(ComputerPadTags.GRID)
				.onSizeChanged { controller.input.cancel() }
				// Fingers are followed even while input is locked, so one held when a session opens presses nothing until it lifts.
				.pointerInput(Unit) {
					try {
						awaitPointerEventScope {
							while (true) {
								val event = awaitPointerEvent()
								for (change in event.changes) {
									val pointer = (change.id.value and 0x3fffffff).toInt()
									if (change.pressed)
										controller.input.move(pointer, PadGridGeometry.indexAt(change.position.x, change.position.y, size.width.toFloat()))
									else controller.input.up(pointer)
									change.consume()
								}
							}
						}
					} finally { controller.input.cancel() }
				},
			cellSemantics = { index ->
				{
					contentDescription = cellDescription(resources, index, controller.colors[index].takeIf { open } ?: 0)
					role = Role.Button
					if (controller.pressed[index]) stateDescription = pressedState
					if (active) onClick(label = pressAction) { controller.input.click(index); true } else disabled()
				}
			},
		)
		if (tooSmall) {
			Text(
				stringResource(R.string.computer_pad_too_small),
				color = TextPrimary,
				fontSize = 13.sp,
				modifier = Modifier
					.testTag(ComputerPadTags.TOO_SMALL)
					.background(Color(0xE6111825), RoundedCornerShape(8.dp))
					.padding(8.dp),
			)
		}
	}
}

/** Row, column, number and light for the screen reader; color is never the only signal. */
internal fun cellDescription(resources: Resources, index: Int, color: Int): String {
	val light = if (color == 0) resources.getString(R.string.computer_pad_light_off) else resources.getString(R.string.computer_pad_light_on, color)
	val number = Mk2Layout.address(index)!!.number
	return when {
		index < 64 -> resources.getString(R.string.computer_pad_cell_pad, index / 8 + 1, index % 8 + 1, number, light)
		index < 72 -> resources.getString(R.string.computer_pad_cell_top, index - 64 + 1, number, light)
		else -> resources.getString(R.string.computer_pad_cell_right, index - 72 + 1, number, light)
	}
}

@Composable
private fun InfoCard(title: String, direction: String?, detail: String?) {
	Column(
		Modifier
			.fillMaxWidth()
			.background(CardBg, RoundedCornerShape(10.dp))
			.padding(horizontal = 12.dp, vertical = 6.dp)
			.semantics(mergeDescendants = true) {},
	) {
		Text(title, color = TextPrimary, fontSize = 14.sp)
		if (direction != null) Text(direction, color = TextSecondary, fontSize = 12.sp)
		if (detail != null) Text(detail, color = TextPrimary, fontSize = 13.sp)
	}
}

@Composable
private fun SectionTitle(text: String) {
	Text(text, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun Note(text: String, color: Color = TextSecondary) {
	Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
	Box(
		contentAlignment = Alignment.CenterStart,
		modifier = Modifier
			.defaultMinSize(minHeight = MinButton)
			.background(if (selected) Color(0xFF253A5C) else CardBg, RoundedCornerShape(10.dp))
			.then(if (selected) Modifier.border(1.5.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
			.clickable(role = Role.RadioButton, onClick = onClick)
			.semantics { this.selected = selected }
			.padding(horizontal = 12.dp, vertical = 8.dp),
	) { Text(text, color = TextPrimary, fontSize = 14.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) {
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun PrimaryButton(text: Int, enabled: Boolean = true, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		enabled = enabled,
		colors = ButtonDefaults.buttonColors(containerColor = Accent),
		modifier = Modifier.defaultMinSize(minHeight = MinButton),
	) { Text(stringResource(text)) }
}

@Composable
private fun SecondaryButton(text: Int, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		colors = ButtonDefaults.buttonColors(containerColor = CardBg, contentColor = TextPrimary),
		modifier = Modifier.defaultMinSize(minHeight = MinButton),
	) { Text(stringResource(text)) }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
	AppAlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = MinButton)) { Text(stringResource(android.R.string.ok)) } },
		title = { Text(stringResource(R.string.computer_pad_help_title)) },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				for (line in listOf(
					R.string.computer_pad_step_cable, R.string.computer_pad_step_usb, R.string.computer_pad_step_app,
					R.string.computer_pad_help_name, R.string.computer_pad_help_numbers, R.string.computer_pad_help_lights,
					R.string.computer_pad_no_sound,
				)) Text(stringResource(line))
			}
		},
	)
}
