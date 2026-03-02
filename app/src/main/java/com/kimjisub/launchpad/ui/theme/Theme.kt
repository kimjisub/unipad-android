package com.kimjisub.launchpad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val UniPadColorScheme = darkColorScheme(
	primary = Blue,
	onPrimary = White,
	secondary = SkyBlue,
	onSecondary = White,
	tertiary = Orange,
	onTertiary = White,
	background = Background1,
	onBackground = Gray1,
	surface = DarkSurface,
	onSurface = White,
	surfaceVariant = Background2,
	onSurfaceVariant = Gray1,
	surfaceContainerHighest = DarkSurfaceHighest,
	error = Red,
	onError = White,
)

@Composable
fun UniPadTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = UniPadColorScheme,
		typography = UniPadTypography,
		content = content,
	)
}
