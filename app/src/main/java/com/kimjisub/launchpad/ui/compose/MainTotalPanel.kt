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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.ui.theme.Blue
import com.kimjisub.launchpad.ui.theme.Orange
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel

/** Update states keep one footer; large text retains scrollable, readable information. */
@Composable
fun MainTotalPanelScreen(
	vm: MainTotalPanelViewModel,
	onSettingsClick: () -> Unit = {},
	updateNotice: (@Composable () -> Unit)? = null,
	onCheckUpdateClick: (() -> Unit)? = null,
	checkingUpdate: Boolean = false,
) {
	val largeText = LocalDensity.current.fontScale > 1f
	Surface(
		modifier = Modifier.fillMaxSize(),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surface,
	) {
		Box(modifier = Modifier.fillMaxSize()) {
			if (largeText) {
				Column(
					modifier = Modifier.fillMaxSize()
						.verticalScroll(rememberScrollState())
						.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						LogoAndVersion(vm)
						Spacer(Modifier.weight(1f))
						SettingsButton(onSettingsClick)
					}
					Spacer(Modifier.height(12.dp))
					Stats(vm)
				}
			} else Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(16.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				LogoAndVersion(vm)
				Spacer(Modifier.height(12.dp))
				Stats(vm)
			}

			if (updateNotice != null) {
				Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 64.dp, bottom = 8.dp)) {
					updateNotice()
				}
			} else if (onCheckUpdateClick != null) {
				Row(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(end = 64.dp)
						.focusRing()
						.clickable(enabled = !checkingUpdate) { onCheckUpdateClick() }
						.padding(12.dp)
						.testTag("app_update_check"),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.update_check),
						color = Blue,
						fontSize = 11.sp,
						maxLines = 1,
						overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
					)
					if (checkingUpdate) {
						Spacer(Modifier.width(6.dp))
						CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Blue)
					}
				}
			}

			if (!largeText) SettingsButton(
				onSettingsClick,
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(8.dp),
			)
		}
	}
}

@Composable
private fun LogoAndVersion(vm: MainTotalPanelViewModel) {
	val logo: @Composable () -> Unit = {
		Image(
			painter = painterResource(R.drawable.custom_logo),
			contentDescription = stringResource(R.string.cd_logo),
			modifier = Modifier
				.width(130.dp)
				.height(50.dp),
			contentScale = ContentScale.Inside,
		)
	}
	val version: @Composable () -> Unit = {
		Text(
			text = vm.version,
			fontSize = 10.sp,
			color = if (vm.premium) Orange else MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
	if (LocalDensity.current.fontScale > 1f) {
		Column(horizontalAlignment = Alignment.End) {
			logo()
			version()
		}
	} else {
		Box(contentAlignment = Alignment.BottomEnd) {
			logo()
			version()
		}
	}
}

@Composable
private fun Stats(vm: MainTotalPanelViewModel) {
	val openCount by vm.openCount.observeAsState(0)
	val unipackCount = vm.unipackCount
	val unipackCapacity = vm.unipackCapacity
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

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
	IconButton(
		onClick = onClick,
		modifier = modifier.focusRing(CircleShape),
	) {
		Icon(
			painter = painterResource(R.drawable.baseline_settings_white_24),
			contentDescription = stringResource(R.string.setting),
			modifier = Modifier.size(20.dp),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun StatRow(
	label: String,
	value: String,
) {
	val content: @Composable () -> Unit = {
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
	if (LocalDensity.current.fontScale > 1f) {
		Column(modifier = Modifier.fillMaxWidth()) { content() }
	} else {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) { content() }
	}
}
