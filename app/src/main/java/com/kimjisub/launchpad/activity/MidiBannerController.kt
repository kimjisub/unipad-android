package com.kimjisub.launchpad.activity

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.ui.theme.Background2
import com.kimjisub.launchpad.ui.theme.Blue
import com.kimjisub.launchpad.ui.theme.Green
import com.kimjisub.launchpad.ui.theme.Red
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import com.kimjisub.launchpad.ui.theme.White
import kotlinx.coroutines.delay

private const val BANNER_DURATION_MS = 4000L

internal class MidiBannerController(
	private val activity: BaseActivity,
	private val onAction: () -> Unit,
) {
	companion object {
		private var lastShownEventId: Long = -1L
		private var lastDisconnectShown: Boolean = false
	}

	private var observerRegistered = false
	private var composeView: ComposeView? = null

	private var bannerVisible by mutableStateOf(false)
	private var bannerDeviceName by mutableStateOf("")
	private var bannerConnected by mutableStateOf(true)

	private val observer = object : MidiConnection.ConnectionObserver {
		override fun onConnected(snapshot: MidiConnection.ConnectedDeviceSnapshot) {
			activity.runOnUiThread {
				showConnectedBanner(snapshot)
			}
		}

		override fun onDisconnected() {
			activity.runOnUiThread {
				showDisconnectedBanner()
			}
		}
	}

	fun onResume() {
		ensureOverlayAttached()
		registerIfNeeded()
		MidiConnection.connectedDevice?.let { snapshot ->
			if (snapshot.eventId > lastShownEventId) {
				showConnectedBanner(snapshot)
			}
		}
	}

	fun onPause() {
		unregisterIfNeeded()
	}

	fun onDestroy() {
		unregisterIfNeeded()
		removeOverlay()
	}

	private fun registerIfNeeded() {
		if (observerRegistered) return
		MidiConnection.connectionObserver = observer
		observerRegistered = true
	}

	private fun unregisterIfNeeded() {
		if (!observerRegistered) return
		if (MidiConnection.connectionObserver === observer) {
			MidiConnection.connectionObserver = null
		}
		observerRegistered = false
	}

	private fun ensureOverlayAttached() {
		if (composeView != null) return
		val rootFrame = activity.findViewById<FrameLayout>(android.R.id.content) ?: return

		val view = ComposeView(activity).apply {
			setContent {
				UniPadTheme {
					MidiBanner(
						visible = bannerVisible,
						deviceName = bannerDeviceName,
						connected = bannerConnected,
						onClick = {
							bannerVisible = false
							if (bannerConnected) onAction()
						},
						onAutoDismiss = { bannerVisible = false },
					)
				}
			}
		}

		val params = FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT,
		).apply {
			gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
		}

		rootFrame.addView(view, params)
		composeView = view
	}

	private fun removeOverlay() {
		composeView?.let { view ->
			(view.parent as? ViewGroup)?.removeView(view)
		}
		composeView = null
	}

	private fun showConnectedBanner(snapshot: MidiConnection.ConnectedDeviceSnapshot) {
		lastShownEventId = snapshot.eventId
		lastDisconnectShown = false
		bannerDeviceName = snapshot.name
		bannerConnected = true
		bannerVisible = true
	}

	private fun showDisconnectedBanner() {
		if (lastDisconnectShown) return
		lastDisconnectShown = true
		bannerConnected = false
		bannerVisible = true
	}
}

@Composable
private fun MidiBanner(
	visible: Boolean,
	deviceName: String,
	connected: Boolean,
	onClick: () -> Unit,
	onAutoDismiss: () -> Unit,
) {
	LaunchedEffect(visible, connected) {
		if (visible) {
			delay(BANNER_DURATION_MS)
			onAutoDismiss()
		}
	}

	AnimatedVisibility(
		visible = visible,
		enter = slideInVertically { -it } + fadeIn(),
		exit = slideOutVertically { -it } + fadeOut(),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding()
				.padding(top = 8.dp),
			contentAlignment = Alignment.TopCenter,
		) {
			Row(
				modifier = Modifier
					.widthIn(max = 360.dp)
					.clip(RoundedCornerShape(12.dp))
					.background(Background2)
					.clickable { onClick() }
					.padding(horizontal = 16.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(
					painter = painterResource(R.drawable.ic_usb),
					contentDescription = null,
					tint = if (connected) Green else Red,
					modifier = Modifier.size(20.dp),
				)
				Spacer(modifier = Modifier.width(10.dp))
				Text(
					text = "MIDI",
					color = if (connected) Blue else Gray1,
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
				)
				Spacer(modifier = Modifier.width(8.dp))
				if (connected) {
					Text(
						text = deviceName,
						color = White,
						fontSize = 13.sp,
						fontWeight = FontWeight.Medium,
					)
					Spacer(modifier = Modifier.width(12.dp))
					Text(
						text = stringResource(R.string.midi_open_panel),
						color = Blue,
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
					)
				} else {
					Text(
						text = stringResource(R.string.midi_disconnected),
						color = Gray1,
						fontSize = 13.sp,
						fontWeight = FontWeight.Medium,
					)
				}
			}
		}
	}
}
