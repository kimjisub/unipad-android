package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.ui.theme.Orange
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel

@Composable
fun MainTotalPanelScreen(
	vm: MainTotalPanelViewModel,
	onSettingsClick: () -> Unit = {},
) {
	val version = vm.version
	val premium = vm.premium
	val openCount by vm.openCount.observeAsState(0)
	val unipackCount = vm.unipackCount
	val unipackCapacity = vm.unipackCapacity

	Surface(
		modifier = Modifier.fillMaxSize(),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surface,
	) {
		Box(modifier = Modifier.fillMaxSize()) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(16.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				// Logo + version
				Box(contentAlignment = Alignment.BottomEnd) {
					Image(
						painter = painterResource(R.drawable.custom_logo),
						contentDescription = stringResource(R.string.cd_logo),
						modifier = Modifier
							.width(130.dp)
							.height(50.dp),
						contentScale = ContentScale.Inside,
					)
					Text(
						text = version,
						fontSize = 10.sp,
						color = if (premium) Orange else MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}

				Spacer(Modifier.height(12.dp))

				// Stats list
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.background(
							MaterialTheme.colorScheme.surfaceContainerHighest,
							RoundedCornerShape(8.dp),
						)
						.padding(12.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					StatRow(
						label = stringResource(R.string.MPT_playCount),
						value = openCount.toString(),
					)
					StatRow(
						label = stringResource(R.string.MTP_count),
						value = unipackCount?.toString() ?: "-",
					)
					StatRow(
						label = stringResource(R.string.MTP_size),
						value = if (unipackCapacity != null) "$unipackCapacity ${stringResource(R.string.mb)}" else "-",
					)
				}
			}

			// Settings icon at bottom-right
			IconButton(
				onClick = onSettingsClick,
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(8.dp),
			) {
				Icon(
					painter = painterResource(R.drawable.baseline_settings_white_24),
					contentDescription = stringResource(R.string.setting),
					modifier = Modifier.size(20.dp),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

@Composable
private fun StatRow(
	label: String,
	value: String,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = label,
			fontSize = 12.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = value,
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}
