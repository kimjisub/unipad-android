package com.kimjisub.launchpad.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R

@Composable
fun StorePackPanelContent(
	title: String,
	subtitle: String,
	downloadCount: String,
	isLED: Boolean,
	isAutoPlay: Boolean,
	isDownloaded: Boolean,
	isDownloading: Boolean,
	downloadProgress: Float,
	downloadStatusText: String,
	onDownloadClick: () -> Unit,
	onYoutubeClick: () -> Unit,
	onWebsiteClick: () -> Unit,
) {
	Surface(
		modifier = Modifier.fillMaxSize(),
		shape = RoundedCornerShape(8.dp),
		color = Color.White,
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(16.dp),
		) {
			// Title (marquee)
			Text(
				text = title,
				fontSize = 16.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Clip,
				color = Color(0xFF1A1A1A),
				modifier = Modifier
					.fillMaxWidth()
					.basicMarquee(),
			)

			// Subtitle + YouTube/Website buttons
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = subtitle,
					fontSize = 12.sp,
					maxLines = 1,
					overflow = TextOverflow.Clip,
					color = Color(0xFF666666),
					modifier = Modifier
						.weight(1f)
						.basicMarquee(),
				)
				IconButton(onClick = onYoutubeClick, modifier = Modifier.size(32.dp)) {
					Icon(painterResource(R.drawable.ic_youtube_24dp), contentDescription = stringResource(R.string.cd_youtube), tint = Color(0xFF555555))
				}
				IconButton(onClick = onWebsiteClick, modifier = Modifier.size(32.dp)) {
					Icon(painterResource(R.drawable.ic_web_24dp), contentDescription = stringResource(R.string.cd_website), tint = Color(0xFF555555))
				}
			}

			Spacer(Modifier.height(12.dp))

			// Feature badges (LED, AutoPlay)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				FeatureBadge(
					label = stringResource(R.string.led).uppercase(),
					enabled = isLED,
				)
				FeatureBadge(
					label = stringResource(R.string.autoPlay).uppercase(),
					enabled = isAutoPlay,
				)
			}

			Spacer(Modifier.height(12.dp))

			// Download count
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(4.dp),
				color = Color(0xFFF2F2F2),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.SPP_downloadCount),
						fontSize = 12.sp,
						color = Color(0xFF888888),
					)
					Spacer(Modifier.weight(1f))
					Text(
						text = downloadCount,
						fontSize = 14.sp,
						fontWeight = FontWeight.Bold,
						color = Color(0xFF1A1A1A),
					)
				}
			}

			Spacer(Modifier.weight(1f))

			// Bottom: download status area
			when {
				isDownloading -> {
					DownloadProgressSection(
						progress = downloadProgress,
						statusText = downloadStatusText,
					)
				}
				isDownloaded -> {
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(4.dp),
						color = Color(0xFFE8F5E9),
					) {
						Text(
							text = stringResource(R.string.downloaded),
							fontSize = 12.sp,
							fontWeight = FontWeight.Medium,
							color = Color(0xFF4CAF50),
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						)
					}
				}
				else -> {
					Button(
						onClick = onDownloadClick,
						modifier = Modifier.fillMaxWidth().height(40.dp),
						shape = RoundedCornerShape(8.dp),
						colors = ButtonDefaults.buttonColors(
							containerColor = Color(0xFF4283E6),
						),
					) {
						Text(
							text = stringResource(R.string.download),
							fontSize = 14.sp,
							fontWeight = FontWeight.Medium,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun DownloadProgressSection(
	progress: Float,
	statusText: String,
) {
	val animatedProgress by animateFloatAsState(
		targetValue = progress,
		animationSpec = tween(300),
		label = "downloadProgress",
	)

	Column(modifier = Modifier.fillMaxWidth()) {
		LinearProgressIndicator(
			progress = { if (animatedProgress > 0f) animatedProgress else 0f },
			modifier = Modifier
				.fillMaxWidth()
				.height(6.dp)
				.clip(RoundedCornerShape(3.dp)),
			color = Color(0xFF4283E6),
			trackColor = Color(0xFFE0E0E0),
		)
		Spacer(Modifier.height(4.dp))
		Text(
			text = statusText,
			fontSize = 11.sp,
			color = Color(0xFF888888),
		)
	}
}

@Composable
private fun FeatureBadge(label: String, enabled: Boolean) {
	val bgColor = if (enabled) Color(0xFFE8F5E9) else Color(0xFFFCE4EC)
	val dotColor = if (enabled) Color(0xFF4CAF50) else Color(0xFFE91E63)
	val textColor = if (enabled) Color(0xFF388E3C) else Color(0xFFC2185B)

	Surface(
		shape = RoundedCornerShape(4.dp),
		color = bgColor,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.size(6.dp)
					.clip(CircleShape)
					.background(dotColor),
			)
			Spacer(Modifier.width(4.dp))
			Text(
				text = label,
				fontSize = 10.sp,
				fontWeight = FontWeight.Medium,
				color = textColor,
			)
		}
	}
}
