package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.AppUpdatePrompter.Card
import com.kimjisub.launchpad.manager.AppUpdatePrompter.Dialog

class AppUpdateActions(
	val download: () -> Unit,
	val later: () -> Unit,
	val install: () -> Unit,
	val retry: () -> Unit,
	val askStore: () -> Unit,
	val openStore: () -> Unit,
	val checkAgain: () -> Unit,
	val dismissDialog: () -> Unit,
)

/** The new-version card in the list's side panel. [enabled] is false while the list is busy. */
@Composable
fun AppUpdateCard(
	card: Card,
	enabled: Boolean,
	actions: AppUpdateActions,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
			.padding(14.dp)
			.testTag("app_update_card"),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		when (card) {
			Card.Offer -> {
				Title(R.string.update_offer_title)
				Body(R.string.update_offer_body)
				Buttons {
					Primary(R.string.update_download, enabled, actions.download, "app_update_download")
					Secondary(R.string.update_later, enabled, actions.later, "app_update_later")
				}
			}

			is Card.Downloading -> {
				Title(R.string.update_downloading_title)
				val fraction = card.fraction
				if (fraction != null) {
					LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
					Text(
						text = "${(fraction * 100).toInt()}%",
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				} else {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
				}
			}

			Card.Ready -> {
				Title(R.string.update_ready_title)
				Body(R.string.update_ready_body)
				Buttons {
					Primary(R.string.update_install, enabled, actions.install, "app_update_install")
					Secondary(R.string.update_later, enabled, actions.later, "app_update_later")
				}
			}

			Card.Failed -> {
				Title(R.string.update_failed)
				Buttons {
					Primary(R.string.update_retry, enabled, actions.retry, "app_update_retry")
					Secondary(R.string.update_check_in_store, enabled, actions.askStore, "app_update_store")
					Secondary(R.string.update_later, enabled, actions.later, "app_update_later")
				}
			}
		}
	}
}

@Composable
fun AppUpdateDialog(dialog: Dialog, actions: AppUpdateActions) {
	val (message, confirm) = when (dialog) {
		Dialog.NoUpdate -> R.string.update_none to null
		Dialog.CheckFailed -> R.string.update_check_failed to (R.string.update_check_again to actions.checkAgain)
		Dialog.Unsupported -> R.string.update_unsupported to (R.string.update_check_in_store to actions.askStore)
		Dialog.StoreNotice -> R.string.update_store_notice to (R.string.update_store_open to actions.openStore)
		Dialog.StoreUnavailable -> R.string.update_store_unavailable to null
	}
	val dismissText = if (dialog == Dialog.StoreNotice) R.string.cancel else R.string.update_close
	AlertDialog(
		onDismissRequest = actions.dismissDialog,
		modifier = Modifier.testTag("app_update_dialog"),
		text = { Text(stringResource(message)) },
		confirmButton = {
			if (confirm != null) {
				TextButton(onClick = confirm.second, modifier = Modifier.testTag("app_update_dialog_confirm")) {
					Text(stringResource(confirm.first))
				}
			}
		},
		dismissButton = {
			TextButton(onClick = actions.dismissDialog, modifier = Modifier.testTag("app_update_dialog_dismiss")) {
				Text(stringResource(dismissText))
			}
		},
	)
}

@Composable
private fun Title(text: Int) {
	Text(
		text = stringResource(text),
		fontSize = 15.sp,
		fontWeight = FontWeight.Bold,
		color = MaterialTheme.colorScheme.onSurface,
		modifier = Modifier.semantics { heading() },
	)
}

@Composable
private fun Body(text: Int) {
	Text(
		text = stringResource(text),
		fontSize = 12.sp,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

// Wraps instead of clipping when the panel is narrow or the font is large.
@Composable
private fun Buttons(content: @Composable () -> Unit) {
	FlowRow(
		modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		content()
	}
}

@Composable
private fun Primary(text: Int, enabled: Boolean, onClick: () -> Unit, tag: String) {
	Button(onClick = onClick, enabled = enabled, modifier = Modifier.focusRing().testTag(tag)) {
		Text(stringResource(text))
	}
}

@Composable
private fun Secondary(text: Int, enabled: Boolean, onClick: () -> Unit, tag: String) {
	TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.focusRing().testTag(tag)) {
		Text(stringResource(text))
	}
}
