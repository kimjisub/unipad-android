package com.kimjisub.launchpad.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kimjisub.launchpad.tool.PrimaryButtonWindowCallback

/*
 * The app's dialogs. Each is its own window, outside the activity's right and middle click filter,
 * so these apply the same filter to the dialog window: a right click must not confirm a deletion.
 */

@Composable
fun AppAlertDialog(
	onDismissRequest: () -> Unit,
	confirmButton: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	dismissButton: (@Composable () -> Unit)? = null,
	title: (@Composable () -> Unit)? = null,
	text: (@Composable () -> Unit)? = null,
	containerColor: Color = AlertDialogDefaults.containerColor,
	titleContentColor: Color = AlertDialogDefaults.titleContentColor,
) {
	AlertDialog(
		onDismissRequest = onDismissRequest,
		confirmButton = {
			PrimaryButtonOnlyInThisWindow()
			confirmButton()
		},
		modifier = modifier,
		dismissButton = dismissButton,
		title = title,
		text = text,
		containerColor = containerColor,
		titleContentColor = titleContentColor,
	)
}

@Composable
fun AppDialog(
	onDismissRequest: () -> Unit,
	properties: DialogProperties = DialogProperties(),
	content: @Composable () -> Unit,
) {
	Dialog(onDismissRequest = onDismissRequest, properties = properties) {
		PrimaryButtonOnlyInThisWindow()
		content()
	}
}

@Composable
private fun PrimaryButtonOnlyInThisWindow() {
	val window = (LocalView.current.parent as? DialogWindowProvider)?.window
	DisposableEffect(window) {
		window?.let(PrimaryButtonWindowCallback::install)
		onDispose { window?.let(PrimaryButtonWindowCallback::uninstall) }
	}
}
