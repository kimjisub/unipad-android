package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.ui.theme.Green
import com.kimjisub.launchpad.ui.theme.Pink
import com.kimjisub.launchpad.ui.theme.SubtitleColor
import com.kimjisub.launchpad.ui.theme.TitleColor

/**
 * Shared list item composable used by both MainActivity and FBStoreActivity.
 *
 * Layout: 60dp height, left flag area + right white content area with title/subtitle and LED/AutoPlay indicators.
 *
 * @param title Primary text (unipack title or error message)
 * @param subtitle Secondary text (producer name or path)
 * @param hasLed Whether LED data exists
 * @param hasAutoPlay Whether AutoPlay data exists
 * @param indicatorFontSize Font size for LED/AutoPlay indicators
 * @param indicatorOnColor Color for enabled indicators
 * @param indicatorOffColor Color for disabled indicators
 * @param isBookmarked Whether to show bookmark icon
 * @param flagColor Background color of the flag area
 * @param flagWidth Width of the flag area
 * @param contentStartPadding Start padding of the content area (used for slide-to-reveal effect)
 * @param flagClickable Whether the flag area is clickable
 * @param onFlagClick Callback when flag is clicked
 * @param flagContent Composable slot for flag area content
 * @param onClick Callback when the entire item is clicked
 */
@Composable
fun UnipackListItem(
	title: String,
	subtitle: String,
	hasLed: Boolean,
	hasAutoPlay: Boolean,
	indicatorFontSize: TextUnit = 9.sp,
	indicatorOnColor: Color = Green,
	indicatorOffColor: Color = Pink,
	isBookmarked: Boolean = false,
	flagColor: Color,
	flagWidth: Dp,
	contentStartPadding: Dp = 0.dp,
	flagClickable: Boolean = false,
	onFlagClick: () -> Unit = {},
	flagContent: @Composable () -> Unit = {},
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(60.dp)
			.padding(horizontal = 16.dp)
			.clickable { onClick() },
	) {
		// Bottom layer: Flag area
		Box(
			modifier = Modifier
				.width(flagWidth)
				.fillMaxHeight()
				.background(flagColor, RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
				.then(if (flagClickable) Modifier.clickable { onFlagClick() } else Modifier),
			contentAlignment = Alignment.Center,
		) {
			flagContent()
		}

		// Top layer: Content area
		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = contentStartPadding),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Row(
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight()
					.background(
						Color.White,
						RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp),
					),
				verticalAlignment = Alignment.CenterVertically,
			) {
				// Bookmark indicator
				if (isBookmarked) {
					Icon(
						painter = painterResource(R.drawable.ic_bookmark_on),
						contentDescription = stringResource(R.string.cd_bookmark),
						modifier = Modifier
							.size(20.dp)
							.padding(start = 4.dp),
						tint = Green,
					)
				}

				// Title + subtitle
				Column(
					modifier = Modifier
						.weight(1f)
						.padding(start = if (isBookmarked) 4.dp else 15.dp),
				) {
					Text(
						text = title,
						fontSize = 14.sp,
						color = TitleColor,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						text = subtitle,
						fontSize = 12.sp,
						color = SubtitleColor,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.padding(top = 3.dp),
					)
				}

				// Option indicators (LED, AutoPlay)
				Column(
					modifier = Modifier.padding(end = 15.dp),
					horizontalAlignment = Alignment.End,
				) {
					Text(
						text = stringResource(
							R.string.option_indicator_format,
							stringResource(R.string.led).uppercase(),
						),
						fontSize = indicatorFontSize,
						color = if (hasLed) indicatorOnColor else indicatorOffColor,
						maxLines = 1,
					)
					Text(
						text = stringResource(
							R.string.option_indicator_format,
							stringResource(R.string.autoPlay).uppercase(),
						),
						fontSize = indicatorFontSize,
						color = if (hasAutoPlay) indicatorOnColor else indicatorOffColor,
						maxLines = 1,
						modifier = Modifier.padding(top = 1.dp),
					)
				}
			}
		}
	}
}
