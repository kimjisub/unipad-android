package com.kimjisub.launchpad.activity

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kimjisub.launchpad.midi.Mk2Layout
import com.kimjisub.launchpad.midi.PeripheralDevice
import com.kimjisub.launchpad.midi.UsbPeripheralConnection
import com.kimjisub.launchpad.ui.theme.UniPadTheme

/**
 * The phone as a MIDI pad for a computer over USB. Opens without packs, storage, sound or autoplay:
 * presses go out, lights come in, and nothing else in the app is involved.
 */
class ComputerPadActivity : AppCompatActivity() {
	companion object {
		/** Device tests replace the system USB endpoint with a test peer. */
		@VisibleForTesting
		internal var connectionFactory: (Context) -> UsbPeripheralConnection = UsbPeripheralConnection::create

		/** Declared optional in the manifest, so it is checked when the screen opens. */
		@VisibleForTesting
		internal var midiSupported: (Context) -> Boolean = { context ->
			context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI) &&
				context.getSystemService(Context.MIDI_SERVICE) != null
		}
	}

	private lateinit var connection: UsbPeripheralConnection
	private lateinit var controller: ComputerPadController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		connection = connectionFactory(applicationContext)
		controller = ComputerPadController(connection, midiSupported(this))
		setContent {
			UniPadTheme {
				ComputerPadScreen(controller = controller, onBack = { finish() })
			}
		}
	}

	override fun onStart() {
		super.onStart()
		connection.refresh()
	}

	/** Locking, Home, Back or another app covering the screen: release what is held and close the ports. */
	override fun onStop() {
		controller.leave()
		super.onStop()
	}

	/** Rotation or a resized window moves the pads under the fingers, so held pads are released. */
	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		controller.input.cancel()
	}

	override fun onDestroy() {
		controller.input.cancel()
		connection.close()
		super.onDestroy()
	}
}

/** Screen state that is not the connection's: the user's choices and what is drawn on the pads. */
internal class ComputerPadController(
	val connection: UsbPeripheralConnection,
	private val midiSupported: Boolean,
	private val clock: () -> Long = System::nanoTime,
) {
	data class Connected(val device: String, val pressPort: String, val lightPort: String?)

	val colors = mutableStateListOf<Int>().apply { repeat(Mk2Layout.SIZE) { add(0) } }
	val pressed = mutableStateListOf<Boolean>().apply { repeat(Mk2Layout.SIZE) { add(false) } }
	var deviceId by mutableStateOf<Int?>(null)
	var pressPort by mutableStateOf<Int?>(null)
	/** The chosen lights port; null with [receiveLights] false means the user chose not to receive lights. */
	var lightPort by mutableStateOf<Int?>(null)
	var receiveLights by mutableStateOf(true)
	var connected by mutableStateOf<Connected?>(null)
		private set
	private var lightsRequested by mutableStateOf(false)
	private var withoutLights by mutableStateOf(false)
	private var paused by mutableStateOf(false)
	private var dismissedAttempt by mutableStateOf<Long?>(null)
	/** Written only when it changes, so an idle screen does not recompose. */
	var lightsRecent by mutableStateOf(false)
		private set

	val input = ComputerPadInput(connection::send) { index, down -> pressed[index] = down }

	fun phase(): ComputerPadPhase = ComputerPadRules.phase(
		ComputerPadRules.Inputs(
			midiSupported = midiSupported,
			state = connection.state.value,
			devices = connection.devices.value,
			discoveryFailure = connection.discoveryFailure.value,
			portsClosing = connection.portsClosing.value,
			lightsRequested = lightsRequested,
			withoutLights = withoutLights,
			paused = paused,
			dismissedAttempt = dismissedAttempt,
			lightsRecent = lightsRecent,
		),
	)

	/** The device and ports shown as chosen; a choice that disappeared falls back to the first one. */
	fun selection(devices: List<PeripheralDevice>): Triple<PeripheralDevice, PeripheralDevice.Port?, PeripheralDevice.Port?>? {
		val device = devices.firstOrNull { it.id == deviceId } ?: devices.firstOrNull() ?: return null
		val press = device.inputs.firstOrNull { it.number == pressPort } ?: device.inputs.firstOrNull()
		val light = if (!receiveLights) null else device.outputs.firstOrNull { it.number == lightPort } ?: device.outputs.firstOrNull()
		return Triple(device, press, light)
	}

	fun search() {
		acknowledge()
		connection.refresh()
	}

	fun connect(devices: List<PeripheralDevice>, names: (PeripheralDevice.Port) -> String) {
		val (device, press, light) = selection(devices) ?: return
		press ?: return
		// Remembered so that connecting again after leaving uses the same choice.
		deviceId = device.id
		pressPort = press.number
		if (light != null) lightPort = light.number
		acknowledge()
		withoutLights = false
		lightsRequested = light != null
		connected = Connected(device.name, names(press), light?.let(names))
		connection.connect(device, press.number, light?.number)
	}

	/** After leaving: connect the same choice again if it is still there, otherwise show what was found. */
	fun reconnect(names: (PeripheralDevice.Port) -> String) {
		val devices = connection.devices.value
		if (devices.any { it.id == deviceId }) connect(devices, names) else search()
	}

	fun useWithoutLights() { withoutLights = true }

	fun disconnect() {
		input.cancel()
		connection.disconnect()
	}

	fun leave() {
		input.cancel()
		if (ComputerPadRules.pausesOnLeave(phase())) {
			paused = true
			connection.disconnect()
		}
	}

	/** Called often while the screen is shown: copies only changed lights and a changed recent flag. */
	fun pollLights() {
		for ((index, color) in connection.lights.drainChanges()) if (colors[index] != color) colors[index] = color
		val recent = ComputerPadRules.recent(connection.lights.lastSignalNanos, clock())
		if (recent != lightsRecent) lightsRecent = recent
	}

	private fun acknowledge() {
		paused = false
		(connection.state.value as? UsbPeripheralConnection.State.Failed)?.let { dismissedAttempt = it.attempt }
	}
}
