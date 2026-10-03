package com.kimjisub.launchpad.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kimjisub.launchpad.R

internal const val MINI_MK3_GUIDE_URL =
	"https://userguides.novationmusic.com/hc/en-gb/articles/23731330721682-Launchpad-Mini-MK3-s-Settings-menu"

/** Uses the screen's explicit selection, never an automatically detected Mini driver. */
internal fun midiHelpModelNote(modelNameResId: Int, explicitlySelected: Boolean): Int? =
	when (modelNameResId) {
		R.string.midi_lp_mini_mk3 -> if (explicitlySelected) R.string.midi_help_mini else null
		R.string.midi_lp_pro, R.string.midi_lp_mk3 -> R.string.midi_help_pro
		R.string.midi_lp_s, R.string.midi_lp_mk2, R.string.midi_lp_x -> null
		else -> R.string.midi_help_other
	}

@Composable
internal fun MidiConnectionHelpDialog(
	modelNameResId: Int,
	explicitlySelected: Boolean,
	onClose: () -> Unit,
) {
	val context = LocalContext.current
	val focusRequester = remember { FocusRequester() }
	val modelNote = midiHelpModelNote(modelNameResId, explicitlySelected)
	var linkFailed by rememberSaveable(modelNameResId) { mutableStateOf(false) }
	Dialog(
		onDismissRequest = onClose,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		LaunchedEffect(Unit) { focusRequester.requestFocus() }
		Surface(
			modifier = Modifier
				.padding(12.dp)
				.widthIn(max = 640.dp)
				.fillMaxWidth()
				.fillMaxHeight(0.95f)
				.onPreviewKeyEvent {
					if (it.key == Key.Escape) {
						if (it.type == KeyEventType.KeyDown) onClose()
						true
					} else false
				}
				.focusRequester(focusRequester)
				.focusable(),
			shape = MaterialTheme.shapes.large,
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Text(
					text = stringResource(R.string.midi_help_title),
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.semantics { heading() },
				)
				Text(
					text = stringResource(R.string.midi_help_selected_model, stringResource(modelNameResId)),
					style = MaterialTheme.typography.bodyMedium,
				)
				Column(
					modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(stringResource(R.string.midi_help_common))
					if (modelNote != null) Text(stringResource(modelNote))
					HelpSection(R.string.midi_help_not_listed_title, R.string.midi_help_not_listed_body)
					Text(stringResource(R.string.midi_help_android))
					HelpSection(R.string.midi_help_no_lights_title, R.string.midi_help_no_lights_body)
					HelpSection(R.string.midi_help_crash_title, R.string.midi_help_crash_body)
					if (modelNote == R.string.midi_help_mini) {
						TextButton(onClick = {
							try {
								context.startActivity(Intent(Intent.ACTION_VIEW, MINI_MK3_GUIDE_URL.toUri()))
								linkFailed = false
							} catch (_: ActivityNotFoundException) {
								linkFailed = true
							}
						}) {
							Text(stringResource(R.string.midi_help_manufacturer))
						}
						if (linkFailed) Text(stringResource(R.string.midi_help_link_failed))
					}
				}
				Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
					Text(stringResource(R.string.midi_help_close))
				}
			}
		}
	}
}

@Composable
private fun HelpSection(titleResId: Int, bodyResId: Int) {
	Text(
		text = stringResource(titleResId),
		style = MaterialTheme.typography.titleSmall,
		modifier = Modifier.semantics { heading() },
	)
	Text(stringResource(bodyResId))
}
