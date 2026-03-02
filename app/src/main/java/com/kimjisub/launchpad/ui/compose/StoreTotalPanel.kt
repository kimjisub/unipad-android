package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun StoreTotalPanelContent(
	version: String,
	storeCount: Int,
	downloadedCount: Int,
) {
	Surface(
		modifier = Modifier.fillMaxSize(),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surface,
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			// Store icon
			Icon(
				painter = painterResource(R.drawable.baseline_shopping_basket_white_24),
				contentDescription = null,
				modifier = Modifier.size(48.dp),
				tint = Color(0xFF00BCDA),
			)

			Spacer(Modifier.height(8.dp))

			Text(
				text = stringResource(R.string.store),
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold,
				color = Color.White,
			)

			Text(
				text = version,
				fontSize = 10.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			Spacer(Modifier.height(16.dp))

			// Stats
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
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.STP_count),
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = storeCount.toString(),
						fontSize = 14.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.STP_downloadedCount),
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = downloadedCount.toString(),
						fontSize = 14.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
			}
		}
	}
}
