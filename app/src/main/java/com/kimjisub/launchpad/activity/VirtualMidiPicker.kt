package com.kimjisub.launchpad.activity

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.MainThread
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.VirtualMidiConnection
import com.kimjisub.launchpad.midi.VirtualMidiConnection.Failure
import com.kimjisub.launchpad.midi.VirtualMidiConnection.State
import com.kimjisub.launchpad.midi.VirtualMidiDevice
import com.kimjisub.launchpad.midi.VirtualMidiSelection
import com.kimjisub.launchpad.midi.driver.DriverRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the "apps on this phone" tab shows and does. It lists devices only when asked (the tab
 * opening or "Search again"), never connects by itself, and outlives the screen so a connection
 * stays while the person moves around the app. Main thread only.
 */
internal class VirtualMidiPicker(
	private val connection: VirtualMidiConnection,
	private val scope: CoroutineScope,
	/** False when the phone has no MIDI service: the tab explains it and stays locked. */
	private val available: Boolean,
	private val usbInUse: () -> Boolean,
	/** The model index recommended for a device, or null when UniPad cannot tell. */
	private val recommend: (VirtualMidiDevice) -> Int?,
	private val driverFor: (Int) -> DriverRef,
) : MidiAppActions {
	enum class Lights { CHOOSE, ON, OFF, NONE }

	data class Ui(
		val available: Boolean = true,
		val scanning: Boolean = false,
		val listFailed: Boolean = false,
		val devices: List<VirtualMidiDevice> = emptyList(),
		/** The device that was connected and is gone; shown dimmed until it is found again. */
		val missing: VirtualMidiDevice? = null,
		val selected: VirtualMidiDevice? = null,
		val performancePort: Int? = null,
		val lights: Lights = Lights.CHOOSE,
		val lightPort: Int? = null,
		val model: Int? = null,
		val recommended: Int? = null,
		val connection: State = State.Idle,
		/** The device of the current or last connection attempt. */
		val connecting: VirtualMidiDevice? = null,
		val usbInUse: Boolean = false,
	) {
		val busy: Boolean get() = connection == State.Connecting || connection == State.LightUnavailable
		val connected: Boolean get() = connection is State.Connected
		/** While connecting or connected nothing that changes the connection can be picked. */
		val locked: Boolean get() = busy || connected
		val lost: Boolean get() = (connection as? State.Failed)?.reason?.let { it in LOST } == true
		val canConnect: Boolean get() = available && !locked && !scanning && !usbInUse && selected != null &&
			performancePort != null && model != null && lights != Lights.CHOOSE
	}

	private val _ui = MutableStateFlow(Ui(available = available, usbInUse = usbInUse()))
	val ui: StateFlow<Ui> = _ui
	private var scan: Job? = null

	init {
		connection.onStateChanged = { state ->
			_ui.update { current ->
				val gone = current.connecting?.takeIf { state is State.Failed && state.reason in LOST }
				if (gone == null) current.copy(connection = state)
				// A lost device stays visible but dimmed, and is picked again only after a new search.
				else current.copy(connection = state, missing = gone, selected = null, performancePort = null,
					lights = Lights.CHOOSE, lightPort = null, model = null, recommended = null,
					devices = current.devices.filter { it.id != gone.id })
			}
		}
		_ui.update { it.copy(connection = connection.state) }
	}

	/** The tab opened: read the list once, unless a read is already running. */
	@MainThread
	fun open() {
		refreshUsb()
		if (scan == null) search()
	}

	@MainThread
	fun refreshUsb() = _ui.update { it.copy(usbInUse = usbInUse()) }

	@MainThread
	override fun search() {
		val current = _ui.value
		if (!current.available || current.busy || scan != null) return
		_ui.update { it.copy(scanning = true) }
		lateinit var job: Job
		job = scope.launch {
			val result = connection.devices()
			if (scan !== job) return@launch
			scan = null
			_ui.update { ui ->
				val devices = result.getOrNull()
				if (devices == null) ui.copy(scanning = false, listFailed = true)
				else {
					// A device in the list keeps its picks only while it still offers the picked ports.
					val stillSelected = ui.selected?.let { old -> devices.firstOrNull { it.id == old.id && it == old } }
					val missing = ui.missing?.takeIf { gone -> devices.none { it.id == gone.id } }
					val connectedId = ui.connecting?.id?.takeIf { ui.connected }
					// The search answers "Search again" after a lost device: the list now shows what is there.
					val shown = if (ui.lost) State.Idle else ui.connection
					val base = ui.copy(scanning = false, listFailed = false, devices = devices, missing = missing, connection = shown)
					when {
						stillSelected != null || (connectedId != null && ui.selected?.id == connectedId) -> base
						else -> base.copy(selected = null, performancePort = null, lights = Lights.CHOOSE,
							lightPort = null, model = null, recommended = null)
					}
				}
			}
		}
		scan = job
	}

	/** Back to the list shown before the search. */
	@MainThread
	override fun cancelSearch() {
		scan?.cancel()
		scan = null
		_ui.update { it.copy(scanning = false) }
	}

	@MainThread
	override fun select(id: Int) {
		val current = _ui.value
		if (current.locked) return
		val device = current.devices.firstOrNull { it.id == id } ?: return
		val recommended = recommend(device)
		_ui.update {
			it.copy(
				// A new pick starts over: an earlier failure no longer describes it.
				connection = if (it.connection is State.Failed) State.Idle else it.connection,
				selected = device,
				performancePort = device.performancePorts.singleOrNull()?.number,
				lights = when (device.lightPorts.size) {
					0 -> Lights.NONE
					1 -> Lights.ON
					else -> Lights.CHOOSE
				},
				lightPort = device.lightPorts.singleOrNull()?.number,
				model = recommended,
				recommended = recommended,
			)
		}
	}

	@MainThread
	override fun choosePerformancePort(number: Int) {
		val current = _ui.value
		if (current.locked || current.selected?.performancePorts?.none { it.number == number } != false) return
		_ui.update { it.copy(performancePort = number) }
	}

	/** A light port number, or null for "Off". A device without one keeps "no light output". */
	@MainThread
	override fun chooseLightPort(number: Int?) {
		val current = _ui.value
		val device = current.selected ?: return
		if (current.locked || device.lightPorts.isEmpty()) return
		if (number == null) _ui.update { it.copy(lights = Lights.OFF, lightPort = null) }
		else if (device.lightPorts.any { it.number == number }) _ui.update { it.copy(lights = Lights.ON, lightPort = number) }
	}

	@MainThread
	override fun chooseModel(index: Int) {
		if (_ui.value.locked || _ui.value.selected == null) return
		_ui.update { it.copy(model = index) }
	}

	@MainThread
	override fun connect() {
		refreshUsb()
		val current = _ui.value
		if (!current.canConnect) return
		val device = current.selected ?: return
		val selection = VirtualMidiSelection(device, current.performancePort ?: return,
			if (current.lights == Lights.ON) current.lightPort else null)
		_ui.update { it.copy(connecting = device, missing = null) }
		connection.connect(selection, driverFor(current.model ?: return))
	}

	/** Cancels a connection being opened, or declines to continue without lights. */
	@MainThread
	override fun cancelConnect() {
		if (_ui.value.busy) connection.disconnect()
	}

	@MainThread
	override fun continueWithoutLights() = connection.continueWithoutLights()

	@MainThread
	override fun disconnect() {
		if (_ui.value.connected) connection.disconnect()
	}

	companion object {
		/** Reasons that mean the device or its app went away: shown as "Disconnected" with "Search again". */
		val LOST = setOf(Failure.DISCONNECTED, Failure.IO, Failure.OVERFLOW)

		private var shared: VirtualMidiPicker? = null

		/** One per process, so the connection is kept while the person moves between screens. */
		@MainThread
		fun shared(context: Context): VirtualMidiPicker = shared ?: VirtualMidiPicker(
			MidiConnection.virtualConnection(context),
			MainScope(),
			context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI) &&
				context.getSystemService(Context.MIDI_SERVICE) != null,
			{ MidiConnection.connectedSessions.isNotEmpty() },
			{ device -> if (device.recommendsMatrix) midiModelIndex(com.kimjisub.launchpad.midi.driver.Matrix::class) else null },
			{ index -> midiModelDriver(index) },
		).also { shared = it }
	}
}
