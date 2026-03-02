package com.kimjisub.launchpad.ui.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.viewmodel.MainPackPanelViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatDate(date: Date?): String =
	if (date != null) dateFormat.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) else "-"

@Composable
fun MainPackPanelScreen(vm: MainPackPanelViewModel) {
	val unipackEnt by vm.unipackEnt.observeAsState()
	val soundCount = vm.soundCount
	val ledCount = vm.ledCount
	val fileSize = vm.fileSize
	val unipack = vm.unipack

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
			// Header: bookmark + action buttons
			HeaderRow(
				isBookmarked = unipackEnt?.bookmark == true,
				onBookmarkToggle = { vm.bookmarkToggle() },
				onDelete = { vm.delete() },
			)

			// Title (marquee)
			Text(
				text = unipack.title,
				fontSize = 16.sp,
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
					text = unipack.producerName,
					fontSize = 12.sp,
					maxLines = 1,
					overflow = TextOverflow.Clip,
					color = Color(0xFF666666),
					modifier = Modifier
						.weight(1f)
						.basicMarquee(),
				)
				IconButton(onClick = { vm.youtubeClick() }, modifier = Modifier.size(32.dp)) {
					Icon(painterResource(R.drawable.ic_youtube_24dp), contentDescription = stringResource(R.string.cd_youtube), tint = Color(0xFF555555))
				}
				if (unipack.website != null) {
					IconButton(onClick = { vm.websiteClick() }, modifier = Modifier.size(32.dp)) {
						Icon(painterResource(R.drawable.ic_web_24dp), contentDescription = stringResource(R.string.cd_website), tint = Color(0xFF555555))
					}
				}
			}

			Spacer(Modifier.height(8.dp))

			// Stats: play count + dates
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				// Play Count
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Text(
						text = stringResource(R.string.MPT_playCount),
						fontSize = 10.sp,
						color = Color(0xFF888888),
					)
					Text(
						text = (unipackEnt?.openCount ?: 0).toString(),
						fontWeight = FontWeight.Bold,
						color = Color(0xFF1A1A1A),
					)
				}

				Spacer(Modifier.weight(1f))

				// Dates
				Column(horizontalAlignment = Alignment.End) {
					DateRow(
						label = stringResource(R.string.MPP_downloadedDate),
						value = formatDate(unipackEnt?.createdAt),
					)
					DateRow(
						label = stringResource(R.string.MPP_lastPlayed),
						value = formatDate(unipackEnt?.lastOpenedAt),
					)
				}
			}

			Spacer(Modifier.height(8.dp))

			// 2x2 property grid
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
				Row(modifier = Modifier
					.fillMaxWidth()) {
					PropertyBlock(
						icon = R.drawable.ic_pad_24dp,
						title = stringResource(R.string.MPP_padSize),
						value = "${unipack.buttonX} \u00D7 ${unipack.buttonY}",
						modifier = Modifier.weight(1f),
					)
					PropertyBlock(
						icon = R.drawable.ic_chain_24dp,
						title = stringResource(R.string.MPP_chain),
						value = unipack.chain.toString(),
						modifier = Modifier.weight(1f),
					)
				}
				Row(modifier = Modifier
					.fillMaxWidth()) {
					PropertyBlock(
						icon = R.drawable.ic_music_note_24dp,
						title = stringResource(R.string.MPP_soundFiles),
						value = soundCount?.toString() ?: stringResource(R.string.measuring),
						modifier = Modifier.weight(1f),
					)
					PropertyBlock(
						icon = R.drawable.ic_led_event_24dp,
						title = stringResource(R.string.MPP_ledEvents),
						value = ledCount?.toString() ?: stringResource(R.string.measuring),
						modifier = Modifier.weight(1f),
					)
				}
			}

			// Path + file size
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = unipack.getPathString(),
					fontSize = 10.sp,
					maxLines = 1,
					overflow = TextOverflow.Clip,
					color = Color(0xFF888888),
					modifier = Modifier
						.weight(1f)
						.basicMarquee(),
				)
				Spacer(Modifier.width(4.dp))
				Text(
					text = fileSize ?: "",
					fontSize = 10.sp,
					color = Color(0xFF888888),
				)
			}
		}
	}
}

@Composable
fun HeaderRow(
	isBookmarked: Boolean,
	onBookmarkToggle: () -> Unit,
	onDelete: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(40.dp)) {
			Icon(
				painter = painterResource(
					if (isBookmarked) R.drawable.ic_bookmark_on else R.drawable.ic_bookmark_off
				),
				contentDescription = stringResource(R.string.cd_bookmark),
				tint = Color(0xFF555555),
			)
		}
		Spacer(Modifier.weight(1f))
		IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
			Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.cd_delete), tint = Color(0xFF555555))
		}
	}
}

@Composable
fun DateRow(label: String, value: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = label,
			fontSize = 10.sp,
			color = Color(0xFF888888),
		)
		Spacer(Modifier.width(8.dp))
		Text(
			text = value,
			fontSize = 10.sp,
			color = Color(0xFF333333),
		)
	}
}

@Composable
fun PropertyBlock(
	icon: Int,
	title: String,
	value: String,
	modifier: Modifier = Modifier,
) {
	Surface(
		modifier = modifier.padding(2.dp),
		shape = RoundedCornerShape(4.dp),
		color = Color(0xFFF2F2F2),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				painter = painterResource(icon),
				contentDescription = title,
				modifier = Modifier.size(20.dp),
				tint = Color(0xFF888888),
			)
			Spacer(Modifier.width(8.dp))
			Column {
				Text(
					text = title,
					fontSize = 10.sp,
					color = Color(0xFF888888),
					lineHeight = 12.sp,
				)
				Text(
					text = value,
					fontWeight = FontWeight.Bold,
					fontSize = 14.sp,
					color = Color(0xFF1A1A1A),
					lineHeight = 16.sp,
				)
			}
		}
	}
}
