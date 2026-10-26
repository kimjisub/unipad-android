package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.stateDescription
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

/** A single small notice in the existing update-check position, with a full spoken state. */
@Composable
fun AppUpdateBadge(card: Card?, enabled: Boolean, onClick: () -> Unit, checking: Boolean = false) {
	val title = stringResource(when (card) {
		null -> R.string.update_check
		Card.Offer -> R.string.update_offer_title
		is Card.Downloading -> R.string.update_downloading_title
		Card.Ready -> R.string.update_ready_title
		Card.Failed -> R.string.update_failed
	})
	TextButton(
		onClick = onClick,
		enabled = enabled && !checking,
		modifier = Modifier
			.widthIn(max = 220.dp)
			.heightIn(min = 48.dp)
			.focusRing()
			.testTag("app_update_notice")
			// The text alone names the notice; only download progress adds a state.
			.semantics {
				if (card is Card.Downloading && card.fraction != null) {
					stateDescription = "${(card.fraction * 100).toInt()}%"
				}
			},
	) {
		Text(title, modifier = Modifier.weight(1f, fill = false), fontSize = 11.sp,
			maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
		if (checking) {
			Spacer(Modifier.width(6.dp))
			CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
		}
	}
}

/** The existing choices, shown only after the person opens the compact notice. */
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppUpdateDialog(dialog: Dialog, actions: AppUpdateActions, card: Card? = null) {
	if (dialog == Dialog.Details) {
		if (card != null) AppAlertDialog(
			onDismissRequest = actions.dismissDialog,
			modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("app_update_dialog"),
			text = {
				AppUpdateCard(card, enabled = true, actions = actions,
					modifier = Modifier.verticalScroll(rememberScrollState()))
			},
			confirmButton = {},
			dismissButton = {
				TextButton(onClick = actions.dismissDialog, modifier = Modifier.testTag("app_update_dialog_dismiss")) {
					Text(stringResource(R.string.update_close))
				}
			},
		)
		return
	}
	val (message, confirm) = when (dialog) {
		Dialog.Details -> return
		Dialog.NoUpdate -> R.string.update_none to null
		Dialog.CheckFailed -> R.string.update_check_failed to (R.string.update_check_again to actions.checkAgain)
		Dialog.Unsupported -> R.string.update_unsupported to (R.string.update_check_in_store to actions.askStore)
		Dialog.StoreNotice -> R.string.update_store_notice to (R.string.update_store_open to actions.openStore)
		Dialog.StoreUnavailable -> R.string.update_store_unavailable to null
	}
	val dismissText = if (dialog == Dialog.StoreNotice) R.string.cancel else R.string.update_close
	AppAlertDialog(
		onDismissRequest = actions.dismissDialog,
		modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("app_update_dialog"),
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
