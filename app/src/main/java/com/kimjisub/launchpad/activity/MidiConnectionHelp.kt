package com.kimjisub.launchpad.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.midi.MidiConnectionStatus
import com.kimjisub.launchpad.midi.MidiConnectionStatus.Phase

/** Both web rows can be omitted together if the guide is still unavailable at release. */
internal object MidiHelpLinks {
	const val enabled = true
	const val guide = "https://unipad.io/docs/guide#launchpad"
	const val firmware = "https://unipad.io/docs/guide#custom-firmware"
}

@Composable
internal fun MidiStatusPanel(status: MidiConnectionStatus, onAllow: () -> Unit) {
	val title = when (status.phase) {
		Phase.NONE -> stringResource(R.string.midi_status_none)
		Phase.PERMISSION -> stringResource(R.string.midi_status_permission)
		Phase.DISCONNECTED -> stringResource(R.string.midi_status_disconnected)
		else -> stringResource(R.string.midi_status_connected, status.deviceName)
	}
	val detail = when {
		status.phase == Phase.NONE -> R.string.midi_status_none_detail
		status.phase == Phase.PERMISSION -> R.string.midi_status_permission_detail
		status.phase == Phase.DISCONNECTED -> R.string.midi_status_disconnected_detail
		status.phase == Phase.INPUT -> R.string.midi_status_input
		status.modelUnknown -> R.string.midi_status_unknown_detail
		status.phase == Phase.WAITING_LONG -> R.string.midi_status_wait_long
		else -> R.string.midi_status_wait
	}
	Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
		Column(Modifier.semantics(mergeDescendants = true) {
			traversalIndex = 0f
			if (status.phase == Phase.INPUT || status.phase == Phase.DISCONNECTED) liveRegion = LiveRegionMode.Polite
		}) {
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(8.dp).background(
					when {
						status.phase == Phase.INPUT -> Color(0xFF66BB6A)
						status.modelUnknown || status.phase == Phase.PERMISSION -> Color(0xFFFFA726)
						else -> MaterialTheme.colorScheme.onSurfaceVariant
					}, CircleShape))
				Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall.copy(lineBreak = guidanceLineBreak))
			}
			Text(stringResource(detail), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(lineBreak = guidanceLineBreak))
			if (status.connected && status.modelUnknown) Text(stringResource(R.string.midi_status_unknown), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
			if (status.connected) status.firmware?.let {
				Text(stringResource(R.string.midi_status_firmware, it), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
			}
		}
		if (status.phase == Phase.PERMISSION) TextButton(onClick = onAllow) { Text(stringResource(R.string.midi_status_allow)) }
	}
}

private val guidanceLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

internal val usbHelpQuestions = listOf(
	R.string.midi_help_find to R.string.midi_help_find_body,
	R.string.midi_help_press to R.string.midi_help_press_body,
	R.string.midi_help_lights to R.string.midi_help_lights_body,
	R.string.midi_help_wrong to R.string.midi_help_wrong_body,
	R.string.midi_help_chain to R.string.midi_help_chain_body,
	R.string.midi_help_firmware to R.string.midi_help_firmware_body,
	R.string.midi_help_crash to R.string.midi_help_crash_body,
)

@Composable
internal fun MidiConnectionHelp(
	expanded: Int,
	onExpand: (Int) -> Unit,
	onClose: () -> Unit,
	onBrowse: (String) -> Unit,
	questions: List<Pair<Int, Int>> = usbHelpQuestions,
) {
	val expandedDescription = stringResource(R.string.midi_help_expanded)
	val collapsedDescription = stringResource(R.string.midi_help_collapsed)
	Column(Modifier.fillMaxWidth().semantics { isTraversalGroup = true; traversalIndex = 2f }
		.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(stringResource(R.string.midi_help_title), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium,
			modifier = Modifier.semantics { heading(); traversalIndex = -3f })
		TextButton(onClick = onClose, modifier = Modifier.semantics { traversalIndex = -2f }) { Text(stringResource(R.string.midi_help_close)) }
		questions.forEachIndexed { index, (question, body) ->
			TextButton(onClick = { onExpand(index) }, modifier = Modifier.fillMaxWidth().semantics {
				traversalIndex = if (expanded == index) 0f else index + 1f
				stateDescription = if (expanded == index) expandedDescription else collapsedDescription
			}) { Text(stringResource(question), modifier = Modifier.fillMaxWidth()) }
			if (expanded == index) {
				Text(stringResource(body), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium.copy(lineBreak = guidanceLineBreak), modifier = Modifier.padding(horizontal = 12.dp).semantics { traversalIndex = 0.1f })
				if (question == R.string.midi_help_firmware && MidiHelpLinks.enabled) TextButton(onClick = { onBrowse(MidiHelpLinks.firmware) }, modifier = Modifier.semantics { traversalIndex = 0.2f }) {
					Text(stringResource(R.string.midi_help_firmware_link))
				}
			}
		}
		if (MidiHelpLinks.enabled) TextButton(onClick = { onBrowse(MidiHelpLinks.guide) }, modifier = Modifier.semantics { traversalIndex = 9f }) {
			Text(stringResource(R.string.midi_help_web_link))
		}
	}
}
