package com.kimjisub.launchpad.activity

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.ui.theme.Background1
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadPRO
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.MasterKeyboard
import com.kimjisub.launchpad.midi.driver.Matrix
import com.kimjisub.launchpad.midi.driver.MidiFighter
import com.kimjisub.launchpad.tool.AutorunTimer
import kotlin.math.absoluteValue
import kotlin.reflect.KClass

class MidiSelectActivity : BaseActivity() {
	companion object {
		private const val AUTORUN_TIMER_DURATION_MS = 60000L
	}

	private val timerText = mutableStateOf<String?>(null)
	private val showError = mutableStateOf(false)
	private val logText = mutableStateOf("")
	private val currentMode = mutableIntStateOf(0)
	private val targetScrollPage = mutableIntStateOf(-1)

	private val autorunTimer: AutorunTimer = AutorunTimer(object : AutorunTimer.OnListener {
		override fun onEverySec(leftTime: Long, elapsedTime: Long) {
			timerText.value = getString(R.string.countdown_format, leftTime / 1000 - 1)
		}

		override fun onTimeOut() {
			finish()
		}

		override fun onCanceled() {
			timerText.value = null
		}
	}, AUTORUN_TIMER_DURATION_MS)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		autorunTimer.start()

		MidiConnection.listener = object : MidiConnection.Listener {
			override fun onConnectedListener() {
				showError.value = false
			}

			override fun onChangeDriver(driverRef: DriverRef) {
				for ((i, device) in midiDevices.withIndex()) {
					if (device.driverClass == driverRef::class) {
						targetScrollPage.intValue = i
						break
					}
				}
			}

			override fun onChangeMode(mode: Int) {
				currentMode.intValue = mode
				p.launchpadConnectMethod = mode
			}

			override fun onUiLog(log: String) {
				logText.value += log + "\n"
			}
		}
		MidiConnection.mode = p.launchpadConnectMethod

		val service = getSystemService(USB_SERVICE) as UsbManager
		MidiConnection.initConnection(intent, service)

		setContent {
			MidiSelectScreen(
				timerText = timerText.value,
				showError = showError.value,
				logText = logText.value,
				currentMode = currentMode.intValue,
				targetScrollPage = targetScrollPage.intValue,
				onDeviceChanged = { index ->
					val device = midiDevices[index]
					val driver = device.driverClass.java.getDeclaredConstructor()
						.newInstance() as? DriverRef ?: return@MidiSelectScreen
					MidiConnection.driver = driver
				},
				onModeSelect = { mode ->
					MidiConnection.mode = mode
				},
				onUserInteract = { autorunTimer.cancel() },
			)
		}
	}

	override fun onDestroy() {
		MidiConnection.listener = null
		super.onDestroy()
	}
}

private data class MidiDeviceData(
	val iconResId: Int,
	val nameResId: Int,
	val driverClass: KClass<*>,
)

private val midiDevices = listOf(
	MidiDeviceData(R.drawable.midi_lp_s, R.string.midi_lp_s, LaunchpadS::class),
	MidiDeviceData(R.drawable.midi_lp_mk2, R.string.midi_lp_mk2, LaunchpadMK2::class),
	MidiDeviceData(R.drawable.midi_lp_pro, R.string.midi_lp_pro, LaunchpadPRO::class),
	MidiDeviceData(R.drawable.midi_lp_x, R.string.midi_lp_x, LaunchpadX::class),
	MidiDeviceData(R.drawable.midi_lp_mk3, R.string.midi_lp_mk3, LaunchpadMK3::class),
	MidiDeviceData(R.drawable.midi_midifighter, R.string.midi_midi_fighter, MidiFighter::class),
	MidiDeviceData(R.drawable.midi_matrix, R.string.midi_matrix, Matrix::class),
	MidiDeviceData(R.drawable.midi_master_keyboard, R.string.midi_master_keyboard, MasterKeyboard::class),
)

private val grayColor = Gray1
private val bgColor = Background1

@Composable
private fun MidiSelectScreen(
	timerText: String?,
	showError: Boolean,
	logText: String,
	currentMode: Int,
	targetScrollPage: Int,
	onDeviceChanged: (Int) -> Unit,
	onModeSelect: (Int) -> Unit,
	onUserInteract: () -> Unit,
) {
	val pagerState = rememberPagerState { midiDevices.size }

	LaunchedEffect(targetScrollPage) {
		if (targetScrollPage >= 0) {
			pagerState.scrollToPage(targetScrollPage)
		}
	}

	LaunchedEffect(pagerState) {
		snapshotFlow { pagerState.currentPage }.collect { page ->
			onDeviceChanged(page)
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.clickable { onUserInteract() },
	) {
		Column(
			modifier = Modifier.fillMaxSize(),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = stringResource(R.string.launchpadConnecting),
				color = grayColor,
				fontSize = 30.sp,
				modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
			)

			if (timerText != null) {
				Text(
					text = timerText,
					color = grayColor,
					fontSize = 30.sp,
					modifier = Modifier.padding(bottom = 10.dp),
				)
			}

			HorizontalPager(
				state = pagerState,
				modifier = Modifier.weight(1f),
				contentPadding = PaddingValues(horizontal = 80.dp),
			) { page ->
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.Center,
					modifier = Modifier
						.fillMaxSize()
						.graphicsLayer {
							val pageOffset = ((pagerState.currentPage - page) +
								pagerState.currentPageOffsetFraction).absoluteValue
							val scale = 1f - 0.3f * pageOffset.coerceIn(0f, 1f)
							scaleX = scale
							scaleY = scale
						},
				) {
					val device = midiDevices[page]
					Image(
						painter = painterResource(device.iconResId),
						contentDescription = stringResource(device.nameResId),
						modifier = Modifier.size(150.dp),
					)
					Text(
						text = stringResource(device.nameResId),
						color = Color.White,
						modifier = Modifier.padding(top = 5.dp),
					)
				}
			}
		}

		// Mode selection buttons (bottom-end)
		Column(
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(10.dp),
		) {
			ModeButton(
				text = stringResource(R.string.signal_SpeedFirst),
				isSelected = currentMode == 0,
				onClick = {
					onUserInteract()
					onModeSelect(0)
				},
			)
			ModeButton(
				text = stringResource(R.string.signal_avoidAfterimage),
				isSelected = currentMode == 1,
				onClick = {
					onUserInteract()
					onModeSelect(1)
				},
			)
		}

		// Log text (top-start)
		if (logText.isNotEmpty()) {
			Text(
				text = logText,
				color = grayColor,
				fontSize = 12.sp,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(4.dp),
			)
		}

		// Error overlay
		if (showError) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = stringResource(R.string.midiDevicesNotDetected),
					color = grayColor,
					fontSize = 30.sp,
				)
			}
		}
	}
}

@Composable
private fun ModeButton(
	text: String,
	isSelected: Boolean,
	onClick: () -> Unit,
) {
	val textColor = if (isSelected) bgColor else grayColor

	Row(
		modifier = Modifier
			.clickable { onClick() }
			.padding(10.dp),
	) {
		Text(
			text = text,
			color = textColor,
			fontSize = 15.sp,
		)
	}
}
