package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.unipack.UniPack

sealed class ImportResult {
	data class Success(val unipack: UniPack) : ImportResult()
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

@Composable
fun ImportResultDialog(
	result: ImportResult,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = {
			Text(
				text = when (result) {
					is ImportResult.Success -> stringResource(R.string.importComplete)
					is ImportResult.Warning -> stringResource(R.string.warning)
					is ImportResult.Error -> stringResource(R.string.importFailed)
				},
			)
		},
		text = {
			when (result) {
				is ImportResult.Success -> SuccessContent(result.unipack)
				is ImportResult.Warning -> Text(result.message)
				is ImportResult.Error -> Text(result.message)
			}
		},
	)
}

@Composable
private fun SuccessContent(unipack: UniPack) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		// Title + Producer
		Column {
			Text(
				text = unipack.title,
				fontWeight = FontWeight.Bold,
				fontSize = 18.sp,
				maxLines = 1,
				modifier = Modifier.basicMarquee(),
			)
			if (unipack.producerName.isNotEmpty()) {
				Text(
					text = unipack.producerName,
					fontSize = 13.sp,
					color = Color(0xFF888888),
				)
			}
		}

		// 2x2 property grid
		Row(modifier = Modifier.fillMaxWidth()) {
			PropertyBlock(
				icon = R.drawable.ic_pad_24dp,
				title = stringResource(R.string.padSize),
				value = "${unipack.buttonX} \u00D7 ${unipack.buttonY}",
				modifier = Modifier.weight(1f),
			)
			PropertyBlock(
				icon = R.drawable.ic_chain_24dp,
				title = stringResource(R.string.numChain),
				value = unipack.chain.toString(),
				modifier = Modifier.weight(1f),
			)
		}
		Row(modifier = Modifier.fillMaxWidth()) {
			PropertyBlock(
				icon = R.drawable.ic_led_event_24dp,
				title = "LED",
				value = if (unipack.keyLedExist) "ON" else "OFF",
				modifier = Modifier.weight(1f),
			)
			PropertyBlock(
				icon = R.drawable.ic_music_note_24dp,
				title = "Autoplay",
				value = if (unipack.autoPlayExist) "ON" else "OFF",
				modifier = Modifier.weight(1f),
			)
		}

		// File size
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Icon(
				painter = painterResource(R.drawable.ic_storage),
				contentDescription = null,
				modifier = Modifier.size(16.dp),
				tint = Color(0xFF888888),
			)
			Text(
				text = "${FileManager.byteToMB(unipack.getByteSize())} MB",
				fontSize = 13.sp,
				color = Color(0xFF888888),
			)
		}
	}
}
