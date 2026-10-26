package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

/**
 * The system bars and the display cutout, without the keyboard: a screen padded by these is covered
 * by the keyboard rather than squeezed by it. Lists that must scroll their last rows above the
 * keyboard use [WindowInsets.Companion.safeDrawing], which adds it.
 *
 * From API 35 every window is edge-to-edge: the navigation bar sits over the bottom (gesture handle)
 * or a side (3-button bar in landscape), and a cutout can take a side as well. Older windows can
 * opt into this layout too. Windows that do not opt in are laid out between the bars and receive
 * zero insets for the space already excluded.
 */
val WindowInsets.Companion.barsAndCutout: WindowInsets
	@Composable
	@NonRestartableComposable
	get() = systemBars.union(displayCutout)
