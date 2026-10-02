package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R

private val TextPrimary = Color(0xFFE8ECF2)
private val TextSecondary = Color(0xFF8A96A8)
private val Accent = Color(0xFF4283E6)
private val DividerColor = Color(0xFF2A3648)

// Built on BasicTextField rather than Material3 OutlinedTextField: play-services-oss-licenses
// 17.5.1 resolves material3 to 1.5.0-alpha17, whose text field styles do not match
// compose-foundation 1.12.0: release builds crash with "LayoutNode should be attached to an
// owner" and debug builds with AbstractMethodError while the field attaches.
@Composable
fun SearchField(
	query: String,
	onQueryChange: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(4.dp)

	BasicTextField(
		value = query,
		onValueChange = onQueryChange,
		singleLine = true,
		textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
		cursorBrush = SolidColor(Accent),
		interactionSource = interactionSource,
		modifier = modifier,
		decorationBox = { innerTextField ->
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.border(if (focused) 2.dp else 1.dp, if (focused) Accent else DividerColor, shape)
					.heightIn(min = 56.dp)
					.padding(horizontal = 12.dp),
			) {
				Icon(
					imageVector = Icons.Default.Search,
					contentDescription = null,
					tint = TextSecondary,
					modifier = Modifier.size(20.dp),
				)
				Box(
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 12.dp),
				) {
					if (query.isEmpty()) {
						Text(
							text = stringResource(R.string.transfer_search),
							color = TextSecondary,
							fontSize = 13.sp,
						)
					}
					innerTextField()
				}
				if (query.isNotEmpty()) {
					IconButton(onClick = { onQueryChange("") }) {
						Icon(
							imageVector = Icons.Default.Clear,
							contentDescription = null,
							tint = TextSecondary,
							modifier = Modifier.size(20.dp),
						)
					}
				}
			}
		},
	)
}
