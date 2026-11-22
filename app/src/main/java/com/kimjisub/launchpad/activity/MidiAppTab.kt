package com.kimjisub.launchpad.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.VirtualMidiPicker.Lights
import com.kimjisub.launchpad.midi.VirtualMidiConnection.Failure
import com.kimjisub.launchpad.midi.VirtualMidiConnection.State
import com.kimjisub.launchpad.midi.VirtualMidiDevice

/** The picks and actions of the "apps on this phone" tab; [VirtualMidiPicker] implements them. */
internal interface MidiAppActions {
	fun search()
	fun cancelSearch()
	fun select(id: Int)
	fun choosePerformancePort(number: Int)
	fun chooseLightPort(number: Int?)
	fun chooseModel(index: Int)
	fun connect()
	fun cancelConnect()
	fun continueWithoutLights()
	fun disconnect()
}

internal val appHelpQuestions = listOf(
	R.string.midi_help_apps_play to R.string.midi_help_apps_play_body,
	R.string.midi_help_apps_missing to R.string.midi_help_apps_missing_body,
	R.string.midi_help_lights to R.string.midi_help_lights_body,
	R.string.midi_help_wrong to R.string.midi_help_wrong_body,
	R.string.midi_help_crash to R.string.midi_help_crash_body,
)

/** Below this width the list and the settings share one scrolling column, list first. */
private val twoPaneMinWidth = 600.dp
private val wrapping = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

@Composable
internal fun MidiAppTab(
	ui: VirtualMidiPicker.Ui,
	modelNames: List<String>,
	providerName: (String) -> String,
	actions: MidiAppActions,
	onBrowse: (String) -> Unit,
	bottomPadding: androidx.compose.ui.unit.Dp,
) {
	var helpOpen by rememberSaveable { mutableStateOf(false) }
	var expandedQuestion by rememberSaveable { mutableIntStateOf(0) }
	BackHandler(enabled = helpOpen) { helpOpen = false }
	val help: @Composable () -> Unit = {
		MidiConnectionHelp(expandedQuestion, { expandedQuestion = if (expandedQuestion == it) -1 else it },
			{ helpOpen = false }, onBrowse, appHelpQuestions)
	}
	val helpButton: @Composable () -> Unit = {
		val expanded = stringResource(R.string.midi_help_expanded)
		val collapsed = stringResource(R.string.midi_help_collapsed)
		TextButton(onClick = { helpOpen = !helpOpen }, modifier = Modifier.heightIn(min = 48.dp)
			.semantics { stateDescription = if (helpOpen) expanded else collapsed }) {
			Text(stringResource(R.string.midi_help_title), color = Color(0xFFFFA726))
		}
	}
	val list: @Composable ColumnScope.() -> Unit = { AppDeviceList(ui, providerName, actions) }
	val settings: @Composable ColumnScope.() -> Unit = {
		AppStatus(ui, actions)
		helpButton()
		AppSettings(ui, modelNames, actions)
	}
	BoxWithConstraints(Modifier.fillMaxSize().testTag("midiAppTab")) {
		if (maxWidth >= twoPaneMinWidth) Row(Modifier.fillMaxSize()) {
			Column(Modifier.weight(0.35f).fillMaxHeight().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomPadding)) {
				Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = settings)
				Spacer(Modifier.height(8.dp))
				AppPrimaryButton(ui, actions)
			}
			Box(Modifier.weight(0.65f).fillMaxHeight().padding(16.dp)) {
				if (helpOpen) help()
				else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding), content = list)
			}
		} else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = bottomPadding),
			verticalArrangement = Arrangement.spacedBy(8.dp)) {
			if (helpOpen) help()
			else {
				list()
				HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
				settings()
				AppPrimaryButton(ui, actions)
			}
		}
	}
}

@Composable
private fun AppDeviceList(ui: VirtualMidiPicker.Ui, providerName: (String) -> String, actions: MidiAppActions) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(stringResource(R.string.midi_tab_apps), Modifier.weight(1f).semantics { heading() },
				color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
			if (ui.available && !ui.scanning) OutlinedButton(onClick = actions::search, enabled = !ui.busy,
				modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.midi_apps_search)) }
		}
		when {
			!ui.available -> Notice(stringResource(R.string.midi_apps_unavailable))
			ui.scanning -> Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
				verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				CircularProgressIndicator(Modifier.size(24.dp))
				Text(stringResource(R.string.midi_apps_searching), Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground,
					style = MaterialTheme.typography.bodyMedium.copy(lineBreak = wrapping))
				OutlinedButton(onClick = actions::cancelSearch, modifier = Modifier.heightIn(min = 48.dp)) {
					Text(stringResource(R.string.midi_apps_search_cancel))
				}
			}
			ui.listFailed -> Notice(stringResource(R.string.midi_apps_list_failed))
			ui.devices.isEmpty() && ui.missing == null -> {
				Text(stringResource(R.string.midi_apps_empty), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall)
				Notice(stringResource(R.string.midi_apps_empty_detail))
			}
		}
		if (ui.available && !ui.scanning) {
			val ordinals = deviceOrdinals(ui.devices)
			ui.devices.forEach { device ->
				AppDeviceRow(device, ordinals[device.id], providerName(device.provider),
					selected = ui.selected?.id == device.id,
					connected = ui.connected && ui.connecting?.id == device.id,
					enabled = !ui.locked, onClick = { actions.select(device.id) })
			}
			ui.missing?.let { AppDeviceRow(it, null, providerName(it.provider), selected = false, connected = false, enabled = false, missing = true) { } }
		}
	}
}

/** Devices whose name, product, app and ports are all equal get a number, so each can be told apart. */
internal fun deviceOrdinals(devices: List<VirtualMidiDevice>): Map<Int, Int> {
	val groups = devices.groupBy { listOf(it.name, it.product, it.provider, it.performancePorts, it.lightPorts) }
	return groups.values.filter { it.size > 1 }.flatMap { same -> same.mapIndexed { index, device -> device.id to index + 1 } }.toMap()
}

@Composable
private fun AppDeviceRow(
	device: VirtualMidiDevice,
	ordinal: Int?,
	provider: String,
	selected: Boolean,
	connected: Boolean,
	enabled: Boolean,
	missing: Boolean = false,
	onClick: () -> Unit,
) {
	val shape = RoundedCornerShape(12.dp)
	val border = when {
		connected -> Color(0xFF66BB6A)
		selected -> MaterialTheme.colorScheme.primary
		else -> Color.Transparent
	}
	Column(
		Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (missing) 0.5f else 1f)
			.background(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest, shape)
			.border(2.dp, border, shape)
			.selectable(selected = selected, enabled = enabled && !missing, role = Role.RadioButton, onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
	) {
		val name = device.name.ifBlank { device.product }
		Text(if (ordinal != null) "$name · #$ordinal" else name, color = MaterialTheme.colorScheme.onSurface,
			style = MaterialTheme.typography.titleSmall.copy(lineBreak = wrapping))
		if (device.product.isNotBlank() && device.product != name) Text(device.product, color = MaterialTheme.colorScheme.onSurfaceVariant,
			style = MaterialTheme.typography.bodySmall.copy(lineBreak = wrapping))
		if (provider.isNotBlank()) Text(stringResource(R.string.midi_apps_provider, provider), color = MaterialTheme.colorScheme.onSurfaceVariant,
			style = MaterialTheme.typography.bodySmall.copy(lineBreak = wrapping))
		Text(stringResource(R.string.midi_apps_device_ports, device.performancePorts.size, device.lightPorts.size),
			color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
		val badge = when {
			missing -> R.string.midi_apps_missing
			connected -> R.string.midi_apps_connected_badge
			selected -> R.string.midi_apps_selected
			else -> null
		}
		badge?.let {
			Text(stringResource(it), color = if (connected) Color(0xFF66BB6A) else MaterialTheme.colorScheme.primary,
				style = MaterialTheme.typography.labelMedium)
		}
	}
}

@Composable
private fun AppStatus(ui: VirtualMidiPicker.Ui, actions: MidiAppActions) {
	val name = ui.connecting?.name.orEmpty()
	val state = ui.connection
	val (title, details) = when {
		state == State.Connecting -> stringResource(R.string.midi_apps_connecting, name) to emptyList()
		state == State.LightUnavailable -> stringResource(R.string.midi_apps_failed) to listOf(stringResource(R.string.midi_apps_lights_failed))
		state is State.Connected -> stringResource(R.string.midi_apps_connected, name) to listOf(
			stringResource(R.string.midi_apps_status_input),
			stringResource(if (state.lights) R.string.midi_apps_status_lights_on else R.string.midi_apps_status_lights_off))
		ui.lost -> stringResource(R.string.midi_apps_lost) to listOf(stringResource(R.string.midi_apps_lost_detail, name))
		state is State.Failed -> stringResource(R.string.midi_apps_failed) to listOf(stringResource(failureText(state.reason), name))
		else -> stringResource(R.string.midi_apps_idle) to emptyList()
	}
	Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
		Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				if (state == State.Connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
				else Box(Modifier.size(8.dp).background(when {
					state is State.Connected -> Color(0xFF66BB6A)
					state is State.Failed || state == State.LightUnavailable -> Color(0xFFFFA726)
					else -> MaterialTheme.colorScheme.onSurfaceVariant
				}, RoundedCornerShape(4.dp)))
				Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall.copy(lineBreak = wrapping))
			}
			details.forEach {
				Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(lineBreak = wrapping))
			}
			if (ui.usbInUse && !ui.locked) Text(stringResource(R.string.midi_apps_usb_in_use), color = MaterialTheme.colorScheme.tertiary,
				style = MaterialTheme.typography.bodySmall.copy(lineBreak = wrapping))
		}
		if (state == State.LightUnavailable) TextButton(onClick = actions::cancelConnect, modifier = Modifier.heightIn(min = 48.dp)) {
			Text(stringResource(R.string.midi_apps_connect_cancel))
		}
	}
}

private fun failureText(reason: Failure): Int = when (reason) {
	Failure.USB_BUSY -> R.string.midi_apps_failed_usb
	Failure.INVALID_PORT -> R.string.midi_apps_failed_port
	Failure.INPUT_PORT -> R.string.midi_apps_failed_input
	Failure.TIMEOUT -> R.string.midi_apps_failed_timeout
	Failure.UNAVAILABLE -> R.string.midi_apps_failed_unavailable
	Failure.DISCONNECTED, Failure.IO, Failure.OVERFLOW -> R.string.midi_apps_lost_detail
}

@Composable
private fun AppSettings(ui: VirtualMidiPicker.Ui, modelNames: List<String>, actions: MidiAppActions) {
	val device = ui.selected
	Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
		if (device == null) {
			if (ui.available) Notice(stringResource(R.string.midi_apps_pick_device))
			return@Column
		}
		Text(device.name.ifBlank { device.product }, color = MaterialTheme.colorScheme.onBackground,
			style = MaterialTheme.typography.titleMedium.copy(lineBreak = wrapping))
		if (device.performancePorts.isEmpty()) {
			Notice(stringResource(R.string.midi_apps_no_input))
			return@Column
		}
		SectionTitle(R.string.midi_apps_input_title, R.string.midi_apps_input_direction)
		device.performancePorts.forEach { port ->
			Choice(stringResource(R.string.midi_apps_port, port.number + 1, port.name), ui.performancePort == port.number, !ui.locked) {
				actions.choosePerformancePort(port.number)
			}
		}
		SectionTitle(R.string.midi_apps_lights_title, R.string.midi_apps_lights_direction)
		if (ui.lights == Lights.NONE) Notice(stringResource(R.string.midi_apps_lights_none))
		else {
			device.lightPorts.forEach { port ->
				Choice(stringResource(R.string.midi_apps_port, port.number + 1, port.name), ui.lights == Lights.ON && ui.lightPort == port.number, !ui.locked) {
					actions.chooseLightPort(port.number)
				}
			}
			Choice(stringResource(R.string.midi_apps_lights_off), ui.lights == Lights.OFF, !ui.locked) { actions.chooseLightPort(null) }
		}
		Text(stringResource(R.string.midi_apps_model), Modifier.padding(top = 8.dp).semantics { heading() },
			color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall)
		ModelPicker(ui, modelNames, actions)
	}
}

@Composable
private fun ModelPicker(ui: VirtualMidiPicker.Ui, modelNames: List<String>, actions: MidiAppActions) {
	var open by remember { mutableStateOf(false) }
	val recommendedLabel = ui.recommended?.let { stringResource(R.string.midi_apps_model_recommended, modelNames[it]) }
	fun label(index: Int) = if (index == ui.recommended) recommendedLabel.orEmpty() else modelNames[index]
	val current = ui.model?.let { label(it) } ?: stringResource(R.string.midi_apps_model_choose)
	if (ui.model == null) Notice(stringResource(R.string.midi_apps_model_unknown))
	Box {
		OutlinedButton(onClick = { open = true }, enabled = !ui.locked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
			Text(current, style = MaterialTheme.typography.bodyMedium.copy(lineBreak = wrapping))
		}
		DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
			modelNames.indices.forEach { index ->
				DropdownMenuItem(
					text = { Text(label(index)) },
					onClick = { open = false; actions.chooseModel(index) },
					modifier = Modifier.heightIn(min = 48.dp),
				)
			}
		}
	}
}

@Composable
private fun AppPrimaryButton(ui: VirtualMidiPicker.Ui, actions: MidiAppActions) {
	val state = ui.connection
	val (label, action, enabled) = when {
		state == State.Connecting -> Triple(R.string.midi_apps_connect_cancel, actions::cancelConnect, true)
		state == State.LightUnavailable -> Triple(R.string.midi_apps_without_lights, actions::continueWithoutLights, true)
		state is State.Connected -> Triple(R.string.midi_apps_disconnect, actions::disconnect, true)
		ui.lost -> Triple(R.string.midi_apps_search, actions::search, ui.available && !ui.scanning)
		state is State.Failed -> Triple(R.string.midi_apps_retry, actions::connect, ui.canConnect)
		else -> Triple(R.string.midi_apps_connect, actions::connect, ui.canConnect)
	}
	Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
		Text(stringResource(label))
	}
}

@Composable
private fun SectionTitle(title: Int, direction: Int) {
	Column(Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { heading() }) {
		Text(stringResource(title), Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground,
			style = MaterialTheme.typography.titleSmall.copy(lineBreak = wrapping))
		Text(stringResource(direction), Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant,
			style = MaterialTheme.typography.bodySmall.copy(lineBreak = wrapping))
	}
}

@Composable
private fun Choice(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
	Row(
		Modifier.fillMaxWidth().heightIn(min = 48.dp)
			.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
			.padding(horizontal = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		RadioButton(selected = selected, onClick = null, enabled = enabled)
		Spacer(Modifier.size(8.dp))
		Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground,
			style = MaterialTheme.typography.bodyMedium.copy(lineBreak = wrapping))
	}
}

@Composable
private fun Notice(text: String) {
	Text(text, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(lineBreak = wrapping))
}
