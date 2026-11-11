package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/** Read the changing LED state here so blinking never invalidates the play screen's layout. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LogoLedImage(bitmap: ImageBitmap, color: () -> Int) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(Color(color())),
        modifier = Modifier.testTag("play_logo_led").semantics { testTagsAsResourceId = true },
    )
}
