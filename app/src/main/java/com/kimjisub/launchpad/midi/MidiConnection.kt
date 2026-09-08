package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadPRO
import com.kimjisub.launchpad.midi.driver.LaunchpadPROCFW
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.MasterKeyboard
import com.kimjisub.launchpad.midi.driver.Matrix
import com.kimjisub.launchpad.midi.driver.MidiFighter
import com.kimjisub.launchpad.midi.driver.Noting
import com.kimjisub.launchpad.tool.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


object MidiConnection {

	private data class DriverEntry(
		val name: String,
		val factory: () -> DriverRef,
		val interfaceNum: Int = 0,
		val productNameMatcher: ((String?) -> Boolean)? = null,
	)

	private val driverRegistryByName: List<DriverEntry> = listOf(
		DriverEntry("Launchpad Pro MK2 (CFW)", ::LaunchpadPROCFW, productNameMatcher = {
			it?.contains("Launchpad Open", ignoreCase = true) == true
		})
	)

	// Exact PID matches (non-Novation devices or single-PID devices)
	private val driverRegistryExact: Map<Int, DriverEntry> = mapOf(
		8 to DriverEntry("MidiFighter", ::MidiFighter),
		8211 to DriverEntry("LX 61 piano", ::MasterKeyboard),
		32822 to DriverEntry("Arduino Leonardo midi", ::LaunchpadPRO, interfaceNum = 3),
	)

	// Novation Launchpad PID ranges (Device ID 1~16 -> base PID + 0..15)
	private data class DriverRange(
		val pidStart: Int,
		val pidEnd: Int,
		val entry: DriverEntry,
	)

	private val driverRegistryRanges: List<DriverRange> = listOf(
		DriverRange(0x0020, 0x002F, DriverEntry("Launchpad S", ::LaunchpadS)),           // 32~47
		DriverRange(0x0036, 0x0036, DriverEntry("Launchpad Mini", ::LaunchpadS)),         // 54 (single)
		DriverRange(0x0051, 0x0060, DriverEntry("Launchpad Pro", ::LaunchpadPRO)),        // 81~96
		DriverRange(0x0069, 0x0078, DriverEntry("Launchpad MK2", ::LaunchpadMK2)),        // 105~120
		DriverRange(0x0103, 0x0112, DriverEntry("Launchpad X", ::LaunchpadX)),            // 259~274
		DriverRange(0x0113, 0x0122, DriverEntry("Launchpad Mini MK3", ::LaunchpadMiniMK3)), // 275~290
		DriverRange(0x0123, 0x0132, DriverEntry("Launchpad Pro MK3", ::LaunchpadMK3)),    // 291~306
	)

	private const val MATRIX_PRODUCT_ID_MASK = 0xFFC0
	private const val MATRIX_PRODUCT_ID_BASE = 0x1040

	private var ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private const val USB_MIDI_PACKET_SIZE = 4
	private const val USB_BULK_TIMEOUT_MS = 50

	// ---------------------------------------------------------------------
	// DeviceSession holds everything that used to be a single top-level var
	// (USB connection, endpoints, driver instance, send/receive loops, MIDI
	// API state). One instance per physically connected Launchpad-like device.
	// ---------------------------------------------------------------------
	private class DeviceSession(val usbDevice: UsbDevice) {
		var usbInterface: UsbInterface? = null
		var usbEndpointIn: UsbEndpoint? = null
		var usbEndpointOut: UsbEndpoint? = null
		@Volatile var usbDeviceConnection: UsbDeviceConnection? = null

		// Android MIDI API for SysEx delivery (used before USB interface claim)
		var midiDevice: MidiDevice? = null
		// Written from the init coroutine (IO) and closeMidiApi (main), read from
		// sendRawBuffer (IO) - ConcurrentHashMap for that cross-thread access.
		val midiInputPorts = ConcurrentHashMap<Int, MidiInputPort>()
		var pendingInitJob: Job? = null
		@Volatile var initSysExSent = false

		// Deferred USB claim - stored for later use after MIDI API SysEx
		var pendingUsbClaim: (() -> Unit)? = null

		var driver: DriverRef = Noting()

		// Non-blocking ordered send queue: this session's own USB endpoint
		val sendChannel = Channel<ByteArray>(Channel.UNLIMITED)
		var sendJob: Job? = null
		@Volatile var receiveJob: Job? = null
		@Volatile var isRun = false

		var name: String = "Unknown"
	}

	// Keyed by UsbDevice.deviceId (stable while the device stays attached).
	// ConcurrentHashMap because sessions are mutated on the main thread (initDevice(),
	// disconnect cleanup) but iterated by onSendSignal/onSendRaw, which can run off-main
	// (e.g. LED animation loops) - a plain map here caused ConcurrentModificationException.
	private val sessions = ConcurrentHashMap<Int, DeviceSession>()

	private fun startSendLoop(session: DeviceSession) {
		if (session.sendJob?.isActive == true) return
		session.sendJob = ioScope.launch {
			// Batch buffer: maxPacketSize (64) fits 16 MIDI packets
			val batchBuffer = ByteArray(64)

			for (first in session.sendChannel) {
				try {
					first.copyInto(batchBuffer, 0)
					var offset = USB_MIDI_PACKET_SIZE

					while (offset + USB_MIDI_PACKET_SIZE <= batchBuffer.size) {
						val next = session.sendChannel.tryReceive().getOrNull() ?: break
						next.copyInto(batchBuffer, offset)
						offset += USB_MIDI_PACKET_SIZE
					}

					// Read both fields once: teardownSession() nulls them in sequence from
					// another thread.
					val conn = session.usbDeviceConnection
					val ep = session.usbEndpointOut
					if (conn != null && ep != null) {
						val written = conn.bulkTransfer(ep, batchBuffer, offset, USB_BULK_TIMEOUT_MS)
						if (written < 0) Log.midiDetail("USB TX failed ($written) on ${session.name}, ${offset / USB_MIDI_PACKET_SIZE} packets dropped")
					}
				} catch (_: RuntimeException) {
					// Device may be disconnected
				}
			}
		}
	}

	private var usbManager: UsbManager? = null
	private var midiManager: MidiManager? = null
	// Cached for dualPadModeEnabled's persistence (see below) - set whenever initConnection()
	// is called with a real context, which happens on every USB attach and app launch.
	private var appContext: Context? = null

	private var onCycleListener: DriverRef.OnCycleListener? = null
	private var onReceiveSignalListener: DriverRef.OnReceiveSignalListener? = null
	private var onSendSignalListener: DriverRef.OnSendSignalListener? = null

	// deviceId of whichever session is currently "primary". Tracked explicitly (rather than
	// relying on identity against the public `driver` property) because once a second pad
	// connects, `driver` becomes a MultiplexDriver wrapper rather than a real per-device
	// driver instance - see below.
	@Volatile
	private var primarySessionId: Int? = null

	// Session-only (not persisted). Off: a second connected pad shows an exact copy of the
	// primary's grid. On: the second pad's grid is horizontally flipped (y -> 7 - y), both
	// for what lights up and for which logical pad a physical press maps to - so two units
	// facing each other show/feel like a mirror image instead of a duplicate.
	@Volatile
	var reflectedModeEnabled: Boolean = false

	// Which pad is "primary" (the unflipped reference side) is decided by connection order
	// (whichever connects first), which may not match how the pads are physically placed.
	// If Reflected feels backwards, flip this instead of the connection order.
	@Volatile
	var reflectedSwapSides: Boolean = false

	private fun isFlippedForReflection(session: DeviceSession): Boolean {
		if (!reflectedModeEnabled) return false
		val isPrimary = session.usbDevice.deviceId == primarySessionId
		return if (reflectedSwapSides) isPrimary else !isPrimary
	}

	// Builds a send listener scoped to a single session - it only ever delivers that
	// session's own already-encoded output to that session's own USB/MIDI connection.
	// Fan-out across multiple connected pads is handled one level up, by MultiplexDriver
	// calling each session's own sendPadLed()/etc directly (with per-session coordinates) -
	// NOT by relaying raw bytes here, since raw bytes are already encoded for one specific
	// model and can't be corrected for a differently-encoding second device after the fact.
	private fun makeSendListener(originSession: DeviceSession): DriverRef.OnSendSignalListener =
		object : DriverRef.OnSendSignalListener {
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
				if (originSession.usbDeviceConnection != null) {
					originSession.sendChannel.trySend(byteArrayOf(cmd, sig, note, velocity))
				}
			}

			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
				if (originSession.usbDeviceConnection != null) {
					ioScope.launch { sendRawBuffer(originSession, messages, cableNumber) }
				}
			}
		}

	// Builds a receive listener scoped to a single session. Pad touches from the primary
	// session pass straight through. Touches from a non-primary session get their y flipped
	// (7 - y) when reflectedModeEnabled is on, so pressing the pad that's visually lit on the
	// reflected device triggers the same logical pad the primary shows it at.
	private fun makeReceiveListener(session: DeviceSession): DriverRef.OnReceiveSignalListener =
		object : DriverRef.OnReceiveSignalListener {
			override fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
				controller?.onUnknownEvent(cmd, sig, note, velocity)
			}

			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
				val mappedY = if (isFlippedForReflection(session)) 7 - y else y
				controller?.onPadTouch(x, mappedY, upDown, velocity)
			}

			override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
				controller?.onFunctionKeyTouch(f, upDown)
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {
				controller?.onChainTouch(c, upDown)
			}

			override fun onReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
				controller?.onUnknownEvent(cmd, sig, note, velocity)
			}
		}

	// Exposed as `driver` (see below) once a second pad connects. Fans each write out to
	// every connected session's OWN driver instance, so each device encodes correctly for
	// its own hardware - this is what makes mixed-model pairs (not just identical ones) work,
	// and it's the hook point for reflection (y gets flipped per-session, before encoding).
	private class MultiplexDriver : DriverRef() {
		override fun sendPadLed(x: Int, y: Int, velocity: Int) {
			for (session in sessions.values) {
				val localY = if (isFlippedForReflection(session)) 7 - y else y
				session.driver.sendPadLed(x, localY, velocity)
			}
		}

		override fun sendChainLed(c: Int, velocity: Int) {
			for (session in sessions.values) session.driver.sendChainLed(c, velocity)
		}

		override fun sendFunctionKeyLed(f: Int, velocity: Int) {
			for (session in sessions.values) session.driver.sendFunctionKeyLed(f, velocity)
		}

		override fun sendClearLed() {
			for (session in sessions.values) session.driver.sendClearLed()
		}
	}

	@Volatile
	var connectedDevice: ConnectedDeviceSnapshot? = null
		private set

	/** Full list of currently connected devices (e.g. for a "2 Launchpads connected" banner). */
	val connectedDevices: List<ConnectedDeviceSnapshot>
		get() = sessions.values.map { ConnectedDeviceSnapshot(it.name, 0L) }

	@Volatile
	var connectionObserver: ConnectionObserver? = null

	// Dual-pad support is opt-in. Off (default) preserves the original single-device
	// behavior exactly: only ever the first device found gets opened, matching pre-refactor
	// UniPad. On, additional devices connecting are accepted as extra mirrored sessions.
	// Flip this from wherever the user picks their devices, BEFORE plugging them in.
	//
	// Persisted via SharedPreferences (not just @Volatile in-memory) - a plain in-memory flag
	// silently reset on every cold start, which in practice happens on every USB attach event
	// (UsbMidiHandlerActivity can be the process's first Activity), making the toggle
	// effectively useless. Falls back to in-memory-only behavior if appContext isn't set yet
	// (e.g. dualPadModeEnabled is read/written before initConnection() has ever run).
	private const val PREFS_NAME = "midi_connection_prefs"
	private const val KEY_DUAL_PAD_MODE = "dual_pad_mode_enabled"
	private var dualPadModeEnabledFallback = false

	var dualPadModeEnabled: Boolean
		get() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			?.getBoolean(KEY_DUAL_PAD_MODE, dualPadModeEnabledFallback)
			?: dualPadModeEnabledFallback
		set(value) {
			dualPadModeEnabledFallback = value
			appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				?.edit()?.putBoolean(KEY_DUAL_PAD_MODE, value)?.apply()
		}

	// `driver` represents the PRIMARY (first-connected) device's driver. PlayActivity /
	// ChannelManager keep reading/writing this exactly as before (e.g. driver.sendPadLed(...)).
	// The mirroring happens underneath: onSendSignalListener broadcasts the resulting bytes to
	// every connected session, so a second (or third) Launchpad lights up in step automatically.
	@Volatile
	private var _driver: DriverRef = Noting()

	var driver: DriverRef
		get() = _driver
		set(value) {
			// A manual pick from MidiSelectActivity while more than one pad is connected
			// must NOT replace the MultiplexDriver dispatcher (that's what was causing only
			// one pad to light up after picking a model). Route it to the primary session
			// specifically via setDriverForSession() instead, and leave the dispatcher in
			// place. To target the secondary pad specifically, call
			// setDriverForSession(sessionId, ...) directly with its session id.
			if (value !is MultiplexDriver && sessions.size > 1 && _driver is MultiplexDriver) {
				primarySessionId?.let { setDriverForSession(it, value) }
				return
			}

			val oldDriver = _driver
			oldDriver.sendClearLed()
			oldDriver.onDisconnected()

			// Write-through only applies to real per-device driver instances (a manual
			// override, or the very first primary assignment) - never to the internal
			// MultiplexDriver wrapper, which must never become a session's own `driver`
			// reference (that would break its receive loop's decoding).
			if (value !is MultiplexDriver) {
				for (session in sessions.values) {
					if (session.driver === oldDriver) {
						session.driver = value
					}
				}
			}

			try {
				_driver = value
				// Cycle listener must be wired regardless of driver type - skipping it for
				// MultiplexDriver was the root cause of onConnected()/onDisconnected() (and
				// therefore controller?.onAttach()/onDetach(), which redraws LEDs) becoming
				// silent no-ops the moment a second pad connects.
				value.setOnCycleListener(onCycleListener)
				if (value !is MultiplexDriver) {
					val ownerSession = sessions.values.firstOrNull { it.driver === value }
					value.setOnGetSignalListener(ownerSession?.let { makeReceiveListener(it) } ?: onReceiveSignalListener)
					value.setOnSendSignalListener(ownerSession?.let { makeSendListener(it) } ?: onSendSignalListener)
				}
				value.initialize()
				if (sessions.isNotEmpty())
					value.onConnected()
			} catch (e: RuntimeException) {
				// initialize() sends the driver's init SysEx; a send failure must not take the
				// caller (MidiSelectActivity's click, UsbMidiHandlerActivity.onCreate) down with it.
				Log.err("Driver set failed", e)
			}

			listener?.onChangeDriver(value)
		}

	@Volatile
	var controller: MidiController? = null

	@Volatile
	internal var listener: Listener? = null
		set(value) {
			field = value
			if (field != null) {
				field?.onChangeDriver(driver)
			}
		}

	fun initConnection(intent: Intent, usbManager: UsbManager, context: Context? = null) {
		this.usbManager = usbManager

		// Initialize Android MIDI API for SysEx support
		if (context != null) {
			appContext = context.applicationContext
			midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
			Log.midiDetail("MidiManager available: ${midiManager != null}")
		}

		val usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
		} else {
			@Suppress("DEPRECATION")
			intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
		}

		if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action) {
			initDevice(usbDevice)
		} else {
			// App-launch / manual scan path. Opens every attached, already-permitted device
			// that isn't already connected and looks like a MIDI controller, so both
			// Launchpads get picked up even if they were plugged in before app launch. Only a
			// device we already hold permission for is worth trying - the first entry of
			// deviceList used to be a hub or a mouse and ended up published as
			// "Master Keyboard" before this permission check existed.
			try {
				for (device in requireNotNull(usbManager).deviceList.values) {
					if (!sessions.containsKey(device.deviceId)
						&& usbManager.hasPermission(device)
						&& looksLikeMidiDevice(device)
					) {
						initDevice(device)
					}
				}
			} catch (e: RuntimeException) {
				Log.err("USB enumeration failed", e)
			}
		}

		onCycleListener = object : DriverRef.OnCycleListener {
			override fun onConnected() {
				controller?.onAttach()
			}

			override fun onDisconnected() {
				controller?.onDetach()
			}
		}

		onSendSignalListener = object : DriverRef.OnSendSignalListener {
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
				// Fallback only - normal wiring uses makeSendListener(session) instead,
				// which is origin-aware and model-guarded. This just avoids a null listener
				// in edge cases (e.g. setDriverListener() called externally with defaults).
				// Matches by driver INSTANCE identity against the currently active _driver,
				// not by class - matching by class broke in two ways: it silently dropped
				// everything when _driver was a MultiplexDriver (no session's driver is ever
				// that class), and it double-sent when two identical-model pads were
				// connected (both sessions' drivers share the same class).
				val ownerSession = sessions.values.firstOrNull { it.driver === _driver }
				if (ownerSession?.usbDeviceConnection != null) {
					ownerSession.sendChannel.trySend(byteArrayOf(cmd, sig, note, velocity))
				}
			}

			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
				val ownerSession = sessions.values.firstOrNull { it.driver === _driver }
				if (ownerSession?.usbDeviceConnection != null) {
					ioScope.launch {
						sendRawBuffer(ownerSession, messages, cableNumber)
					}
				}
			}
		}

		onReceiveSignalListener = object : DriverRef.OnReceiveSignalListener {
			override fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
				controller?.onUnknownEvent(cmd, sig, note, velocity)
			}

			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
				controller?.onPadTouch(x, y, upDown, velocity)
			}

			override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
				controller?.onFunctionKeyTouch(f, upDown)
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {
				controller?.onChainTouch(c, upDown)
			}

			override fun onReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
				controller?.onUnknownEvent(cmd, sig, note, velocity)
			}
		}

		// Re-wire whichever session(s) were created above now that the listeners exist
		// (mirrors the original ordering, where setDriverListener() was called again at
		// the end of initConnection to finish wiring the driver created inside initDevice()).
		for (session in sessions.values) {
			setDriverListener(session.driver, makeSendListener(session), makeReceiveListener(session))
		}
	}

	// Used to filter both the app-launch scan loop above and to decide whether a freshly
	// attached device is worth ever calling initDevice() on.
	private fun looksLikeMidiDevice(device: UsbDevice): Boolean {
		val pid = device.productId
		if (driverRegistryExact.containsKey(pid)) return true
		if (driverRegistryRanges.any { pid in it.pidStart..it.pidEnd }) return true
		if (pid and MATRIX_PRODUCT_ID_MASK == MATRIX_PRODUCT_ID_BASE) return true
		for (i in 0 until device.interfaceCount) {
			val ui = device.getInterface(i)
			if (ui.interfaceClass == UsbConstants.USB_CLASS_AUDIO && ui.interfaceSubclass == 3) return true
		}
		return false
	}

	private fun initDevice(device: UsbDevice?) {
		if (device == null) {
			Log.midiDetail("USB 에러 : device == null")
			return
		}
		if (sessions.containsKey(device.deviceId)) {
			Log.midiDetail("Device ${device.deviceId} already connected, skipping")
			return
		}
		if (!dualPadModeEnabled && sessions.isNotEmpty()) {
			Log.midiDetail("Dual pad mode is off - ignoring additional device (${device.deviceName})")
			listener?.onUiLog("Dual pad mode is off - ignoring ${device.deviceName}")
			return
		}

		val session = DeviceSession(device)
		var interfaceNum = 0

		try {
			Log.midiDetail("DeviceName : ${device.deviceName}")
			Log.midiDetail("DeviceClass : ${device.deviceClass}")
			Log.midiDetail("DeviceId : ${device.deviceId}")
			Log.midiDetail("DeviceProtocol : ${device.deviceProtocol}")
			Log.midiDetail("DeviceSubclass : ${device.deviceSubclass}")
			Log.midiDetail("InterfaceCount : ${device.interfaceCount}")
			Log.midiDetail("VendorId : ${device.vendorId}")
		} catch (e: SecurityException) {
			Log.err("USB device info read failed", e)
		}

		try {
			Log.midiDetail("ProductId : ${device.productId}")
			listener?.onUiLog("ProductId : ${device.productId}")

			val pid = device.productId
			val productName = device.productName
			val nameEntry = driverRegistryByName.firstOrNull { it.productNameMatcher?.invoke(productName) == true }
			val exactEntry = driverRegistryExact[pid]
			val rangeEntry = driverRegistryRanges.firstOrNull { pid in it.pidStart..it.pidEnd }?.entry

			val entry = nameEntry ?: exactEntry ?: rangeEntry

			if (entry != null) {
				val deviceId = if (rangeEntry != null) {
					val range = driverRegistryRanges.first { pid in it.pidStart..it.pidEnd }
					pid - range.pidStart + 1
				} else null
				val idStr = if (deviceId != null) " (Device ID $deviceId)" else ""
				listener?.onUiLog("prediction : ${entry.name}$idStr")
				Log.midiDetail("Driver: ${entry.name}$idStr (PID=0x${"%04X".format(pid)})")
				interfaceNum = entry.interfaceNum
				session.driver = entry.factory()
				session.name = entry.name
			} else if (pid and MATRIX_PRODUCT_ID_MASK == MATRIX_PRODUCT_ID_BASE) {
				listener?.onUiLog("prediction : 203 Matrix")
				session.driver = Matrix()
				session.name = "Matrix"
			} else {
				listener?.onUiLog("prediction : unknown (PID=$pid)")
				session.driver = MasterKeyboard()
				session.name = "Master Keyboard"
			}
		} catch (e: SecurityException) {
			Log.err("USB driver selection failed", e)
		}

		// Log all interfaces
		for (i in 0 until device.interfaceCount) {
			val ui = device.getInterface(i)
			Log.midiDetail("Interface[$i]: class=${ui.interfaceClass}, subclass=${ui.interfaceSubclass}, endpoints=${ui.endpointCount}")
		}

		// Find MIDI Streaming interface (class=1, subclass=3)
		for (i in interfaceNum until device.interfaceCount) {
			val ui = device.getInterface(i)
			if (ui.endpointCount > 0 && ui.interfaceClass == UsbConstants.USB_CLASS_AUDIO && ui.interfaceSubclass == 3) {
				session.usbInterface = ui
				listener?.onUiLog("Interface MIDI : (${i + 1}/${device.interfaceCount})")
				break
			}
		}
		// Fallback: first interface with endpoints
		if (session.usbInterface == null) {
			for (i in interfaceNum until device.interfaceCount) {
				val ui = device.getInterface(i)
				if (ui.endpointCount > 0) {
					session.usbInterface = ui
					listener?.onUiLog("Interface : (${i + 1}/${device.interfaceCount})")
					break
				}
			}
		}
		val usbIf = session.usbInterface ?: run {
			Log.midiDetail("USB 에러 : usbInterface == null")
			return
		}
		for (i in 0 until usbIf.endpointCount) {
			val ep = usbIf.getEndpoint(i)
			val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
			val info = "EP[$i] dir=$dir type=${ep.type} addr=0x${"%02X".format(ep.address)} maxPkt=${ep.maxPacketSize}"
			Log.midiDetail(info)
			listener?.onUiLog(info)
			when (ep.direction) {
				UsbConstants.USB_DIR_IN -> session.usbEndpointIn = ep
				UsbConstants.USB_DIR_OUT -> session.usbEndpointOut = ep
			}
		}
		val endpointIn = session.usbEndpointIn ?: run {
			Log.midiDetail("USB 에러 : usbEndpointIn == null")
			return
		}
		val manager = usbManager ?: run {
			Log.midiDetail("USB 에러 : usbManager == null")
			return
		}
		// openDevice throws SecurityException for a device we were not granted (only the device
		// that fired the attach intent is auto-granted).
		val connection = try {
			if (manager.hasPermission(device)) manager.openDevice(device) else {
				Log.midiDetail("USB 에러 : no permission for ${device.deviceName}")
				null
			}
		} catch (e: SecurityException) {
			Log.err("USB openDevice failed", e)
			null
		}
		if (connection == null) {
			Log.midiDetail("USB 에러 : usbDeviceConnection == null")
			return
		}
		session.usbDeviceConnection = connection

		// Defer USB interface claim - MIDI API needs the interface first for SysEx
		session.pendingUsbClaim = {
			Log.midiDetail("USB: Claiming interface for MIDI communication (${session.name})")
			if (session.usbDeviceConnection !== connection) {
				Log.midiDetail("USB: device changed before the claim, skipping (${session.name})")
			} else if (connection.claimInterface(usbIf, true)) {
				startReceiveLoop(session, connection, endpointIn)
			} else {
				// Announcing "connected" while owning no interface left the UI streaming LEDs
				// to a device that never answered; report the disconnect instead.
				Log.midiDetail("USB 에러 : claimInterface failed (${session.name})")
				teardownSession(session, notify = true)
			}
		}

		sessions[device.deviceId] = session
		setDriverListener(session.driver, makeSendListener(session), makeReceiveListener(session))

		// First device to connect becomes "primary". PlayActivity/ChannelManager keep
		// reading/writing `driver` exactly as before. With only one pad connected, `driver`
		// is that pad's own real driver instance - zero overhead/behavior change for the
		// vast majority of users who never use dual-pad mode. Once a second pad connects,
		// `driver` becomes a MultiplexDriver that fans writes out to every session's own
		// driver (each encoding correctly for its own hardware, flipping y per-session when
		// Reflected mode is on).
		if (sessions.size == 1) {
			primarySessionId = device.deviceId
			// Bypass the public `driver` setter here deliberately: it would call
			// initialize()/onConnected() before the connection is actually claimed and ready
			// - both happen properly once the MIDI API / bulk-transfer path below finishes.
			// listener?.onChangeDriver is re-added manually so the UI still updates.
			_driver = session.driver
			listener?.onChangeDriver(_driver)
		} else if (sessions.size == 2) {
			driver = MultiplexDriver()
		}
		// Every connecting device updates the banner now, not just the primary - previously a
		// second pad connecting left the banner still showing the first pad's name.
		publishConnectedDevice(session.name)

		listener?.onConnectedListener()
		connectedDevice?.let { connectionObserver?.onConnected(it) }

		// Try MIDI API for SysEx first, then claim USB interface
		initMidiApiDevice(session, device)
	}

	private fun initMidiApiDevice(session: DeviceSession, usbDevice: UsbDevice?) {
		val manager = midiManager
		if (manager == null || usbDevice == null) {
			Log.midiDetail("MIDI API not available, claiming USB interface directly (${session.name})")
			claimUsbAndStart(session)
			return
		}

		// Every call below is a binder round-trip into the MIDI service that produced the
		// Android 16 NullPointerExceptions (#41/#49); openDevice was guarded, enumeration was not.
		val targetInfo = try {
			@Suppress("DEPRECATION")
			val deviceInfos = manager.devices
			Log.midiDetail("MIDI API: ${deviceInfos.size} device(s) found")
			deviceInfos.firstOrNull { info ->
				val props = info.properties
				Log.midiDetail("MIDI API device: name=${props.getString(MidiDeviceInfo.PROPERTY_NAME)}, " +
					"manufacturer=${props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)}, " +
					"product=${props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)}, " +
					"inputPorts=${info.inputPortCount}, outputPorts=${info.outputPortCount}")
				info.inputPortCount > 0
			}
		} catch (e: RuntimeException) {
			Log.err("MIDI API: enumeration failed, claiming USB interface directly", e)
			claimUsbAndStart(session)
			return
		}

		if (targetInfo == null) {
			Log.midiDetail("MIDI API: No matching MIDI device found, claiming USB interface directly")
			claimUsbAndStart(session)
			return
		}

		try {
			Log.midiDetail("MIDI API: Opening device (inputPorts=${targetInfo.inputPortCount}, outputPorts=${targetInfo.outputPortCount})")
			for (port in targetInfo.ports) {
				val dir = if (port.type == MidiDeviceInfo.PortInfo.TYPE_INPUT) "INPUT" else "OUTPUT"
				Log.midiDetail("  MIDI API Port[${port.portNumber}]: $dir name=${port.name}")
			}
			manager.openDevice(targetInfo, { device ->
				// Runs later on the main Handler, outside the try below: a framework exception here
				// (Crashlytics 9b99a871: SecurityException from openInputPort on Android 16) must fall
				// back the same way a null device does.
				try {
					onMidiApiDeviceOpened(session, device, targetInfo)
				} catch (e: RuntimeException) {
					Log.err("MIDI API: device callback failed, claiming USB interface directly", e)
					closeMidiApi(session)
					claimUsbAndStart(session)
				}
			}, Handler(Looper.getMainLooper()))
		} catch (e: RuntimeException) {
			Log.err("MIDI API: openDevice failed, claiming USB interface directly", e)
			claimUsbAndStart(session)
		}
	}

	private fun onMidiApiDeviceOpened(session: DeviceSession, device: MidiDevice?, targetInfo: MidiDeviceInfo) {
		if (device == null) {
			Log.midiDetail("MIDI API: Failed to open device, claiming USB interface directly (${session.name})")
			claimUsbAndStart(session)
			return
		}
		session.midiDevice = device
		Log.midiDetail("MIDI API: Device opened successfully (${session.name})")

		// Port opening and the SysEx sends (blocking port.send + 50 ms sleeps per message, per
		// port) used to run on the main thread inside the attach path; a Pro MK3 with several
		// ports froze the UI. The job is owned per-session so teardownSession() can cancel it
		// on an early unplug of that specific device.
		val connectionAtOpen = session.usbDeviceConnection
		session.pendingInitJob?.cancel()
		session.pendingInitJob = ioScope.launch {
			try {
				try {
					for (portInfo in targetInfo.ports) {
						if (portInfo.type == MidiDeviceInfo.PortInfo.TYPE_INPUT) {
							val port = device.openInputPort(portInfo.portNumber)
							if (port != null) {
								session.midiInputPorts[portInfo.portNumber] = port
								Log.midiDetail("MIDI API: Opened input port ${portInfo.portNumber} (${portInfo.name}) [${session.name}]")
							}
						}
					}

					// Send SysEx to ALL input ports (port names are empty, we don't know which is DAW)
					val initData = session.driver.getInitSysEx()
					if (initData != null && session.midiInputPorts.isNotEmpty()) {
						val (messages, _) = initData
						var sent = false
						for (portNum in session.midiInputPorts.keys) {
							Log.midiDetail("MIDI API: Sending init SysEx (${messages.size} messages) to port $portNum [${session.name}]")
							if (sendViaMidiApi(session, messages, portNum)) sent = true
						}
						if (sent) session.initSysExSent = true
					}
				} catch (e: RuntimeException) {
					// Crashlytics 9b99a871: SecurityException from openInputPort on Android 16.
					Log.err("MIDI API: init over MIDI API failed, falling back to USB (${session.name})", e)
				}
				// Let the SysEx flush before the ports are closed
				delay(500)
			} finally {
				withContext(NonCancellable + Dispatchers.Main) {
					closeMidiApi(session)
					// A device that was torn down while we slept must not have its stale
					// interface claimed for whatever is plugged in now.
					if (connectionAtOpen != null && session.usbDeviceConnection === connectionAtOpen) claimUsbAndStart(session)
				}
			}
		}
	}

	private fun closeMidiApi(session: DeviceSession) {
		Log.midiDetail("MIDI API: Closing ports and device (${session.name})")
		for ((portNum, port) in session.midiInputPorts) {
			try {
				port.close()
				Log.midiDetail("MIDI API: Closed input port $portNum")
			} catch (e: Exception) {
				Log.err("MIDI API: Failed to close port $portNum", e)
			}
		}
		session.midiInputPorts.clear()
		try {
			session.midiDevice?.close()
			session.midiDevice = null
			Log.midiDetail("MIDI API: Device closed (${session.name})")
		} catch (e: Exception) {
			Log.err("MIDI API: Failed to close device", e)
		}
	}

	private fun claimUsbAndStart(session: DeviceSession) {
		session.pendingUsbClaim?.invoke()
		session.pendingUsbClaim = null
		if (session.usbDeviceConnection == null) return
		startSendLoop(session)
		if (!session.initSysExSent) {
			// The MIDI API path did not deliver the init SysEx (no MidiManager, no input port,
			// or the open failed). driver.initialize() at assignment time was dropped because
			// the connection did not exist yet, so without this the launchpad stays in its
			// default layout and every pad lands on the wrong coordinate. It now goes over the
			// bulk endpoint.
			Log.midiDetail("USB: sending init SysEx over bulk transfer (${session.name})")
			session.driver.initialize()
		}
	}

	private fun sendViaMidiApi(session: DeviceSession, messages: List<ByteArray>, cableNumber: Int): Boolean {
		val port = session.midiInputPorts[cableNumber] ?: return false
		try {
			for ((index, msg) in messages.withIndex()) {
				val hex = msg.joinToString(" ") { "%02X".format(it) }
				Log.midiDetail("MIDI API TX (port=$cableNumber): $hex")
				port.send(msg, 0, msg.size)
				// Delay between SysEx messages to allow device mode transitions
				if (index < messages.size - 1) {
					Thread.sleep(50)
				}
			}
			return true
		} catch (e: Exception) {
			Log.err("MIDI API send failed", e)
			return false
		}
	}

	private fun sendRawBuffer(session: DeviceSession, messages: List<ByteArray>, cableNumber: Int = 0) {
		// Try Android MIDI API first (handles SysEx properly)
		if (sendViaMidiApi(session, messages, cableNumber)) {
			Log.midiDetail("SysEx sent via MIDI API (port=$cableNumber) [${session.name}]")
			return
		}

		// Fallback: USB bulk transfer with manual SysEx encoding
		Log.midiDetail("MIDI API not available for port=$cableNumber, falling back to USB bulk transfer [${session.name}]")
		try {
			for ((index, msg) in messages.withIndex()) {
				val encoded = encodeSysEx(msg, cableNumber)
				Log.midiDetail("TX SysEx (USB): ${msg.joinToString(" ") { "%02X".format(it) }} (cable=$cableNumber) [${session.name}]")

				val conn = session.usbDeviceConnection
				val ep = session.usbEndpointOut
				if (conn == null || ep == null) {
					Log.midiDetail("TX SysEx (USB): no connection, message dropped [${session.name}]")
					return
				}
				var offset = 0
				while (offset < encoded.size) {
					val chunk = minOf(64, encoded.size - offset)
					val written = conn.bulkTransfer(ep, encoded, offset, chunk, USB_BULK_TIMEOUT_MS)
					if (written < 0) {
						Log.midiDetail("TX SysEx (USB): bulkTransfer failed ($written) at offset $offset [${session.name}]")
						return
					}
					offset += chunk
				}
				// Delay between SysEx messages to allow device mode transitions
				if (index < messages.size - 1) {
					Thread.sleep(50)
				}
			}
		} catch (e: RuntimeException) {
			Log.err("sendRawBuffer failed", e)
		}
	}

	private fun encodeSysEx(sysex: ByteArray, cableNumber: Int = 0): ByteArray {
		val cablePrefix = (cableNumber shl 4).toByte()
		val packets = mutableListOf<Byte>()
		var i = 0
		while (i < sysex.size) {
			val remaining = sysex.size - i
			if (remaining >= 3 && sysex[i + 2] != 0xF7.toByte()) {
				// SysEx start or continue: CIN = 0x04
				packets.add((cablePrefix + 0x04).toByte())
				packets.add(sysex[i])
				packets.add(sysex[i + 1])
				packets.add(sysex[i + 2])
				i += 3
			} else if (remaining == 1) {
				// SysEx end with 1 byte: CIN = 0x05
				packets.add((cablePrefix + 0x05).toByte())
				packets.add(sysex[i])
				packets.add(0x00)
				packets.add(0x00)
				i += 1
			} else if (remaining == 2) {
				// SysEx end with 2 bytes: CIN = 0x06
				packets.add((cablePrefix + 0x06).toByte())
				packets.add(sysex[i])
				packets.add(sysex[i + 1])
				packets.add(0x00)
				i += 2
			} else {
				// SysEx end with 3 bytes: CIN = 0x07
				packets.add((cablePrefix + 0x07).toByte())
				packets.add(sysex[i])
				packets.add(sysex[i + 1])
				packets.add(sysex[i + 2])
				i += 3
			}
		}
		return packets.toByteArray()
	}

	private fun startReceiveLoop(session: DeviceSession, conn: UsbDeviceConnection, endpointIn: UsbEndpoint) {
		val previous = session.receiveJob
		session.receiveJob = ioScope.launch {
			// cancel() alone cannot interrupt a blocking bulkTransfer; wait for the old loop so
			// two loops never poll the same endpoint. The old loop sees a foreign connection in
			// its finally and stays silent, so the new device's "connected" is not followed by a
			// bogus "disconnected".
			previous?.cancelAndJoin()
			if (session.usbDeviceConnection !== conn) return@launch

			session.isRun = true
			withContext(Dispatchers.Main) {
				session.driver.onConnected()
			}
			Log.midiDetail("USB 시작 (${session.name})")

			val byteArray = ByteArray(endpointIn.maxPacketSize)
			// Flat int array: [cmd0,sig0,note0,vel0, cmd1,sig1,note1,vel1, ...]
			val eventBuf = IntArray(endpointIn.maxPacketSize)
			var fastFailures = 0

			try {
				while (isActive && session.usbDeviceConnection === conn) {
					val started = SystemClock.elapsedRealtime()
					val length = conn.bulkTransfer(
						endpointIn,
						byteArray,
						byteArray.size,
						1000
					)
					if (length >= 4) {
						fastFailures = 0
						var eventCount = 0
						var i = 0
						// Whole 4-byte packets only: an interrupt endpoint with maxPacketSize 9
						// can return a length that is not a multiple of 4.
						while (i + 3 < length) {
							val b1 = byteArray[i + 1].toInt() and 0xFF
							if (b1 == 0xF8) { // Skip MIDI Clock
								i += 4
								continue
							}
							val base = eventCount * 4
							eventBuf[base] = byteArray[i].toInt()
							eventBuf[base + 1] = byteArray[i + 1].toInt()
							eventBuf[base + 2] = byteArray[i + 2].toInt()
							eventBuf[base + 3] = byteArray[i + 3].toInt()
							eventCount++
							i += 4
						}
						if (eventCount > 0) {
							// Copy to snapshot for safe Main thread dispatch
							val snapshot = eventBuf.copyOf(eventCount * 4)
							val n = eventCount
							withContext(Dispatchers.Main) {
								for (j in 0 until n) {
									val base = j * 4
									session.driver.getSignal(snapshot[base], snapshot[base + 1], snapshot[base + 2], snapshot[base + 3])
								}
							}
						}
					} else if (length < 0) {
						// An idle device also returns -1, but only after the full 1000 ms
						// timeout; a detached device fails within a few milliseconds. Counting
						// only the fast failures tells the two apart without depending on the
						// same-millisecond coincidence the old heuristic needed (on ROMs where a
						// dead fd takes >= 1 ms to fail it never fired and the loop spun forever).
						if (SystemClock.elapsedRealtime() - started < 50) {
							if (++fastFailures > 10) break
						} else {
							fastFailures = 0
						}
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: RuntimeException) {
				Log.err("MIDI receive loop error (${session.name})", e)
			} finally {
				Log.midiDetail("USB 끝 (${session.name})")
				withContext(NonCancellable + Dispatchers.Main) {
					// Only the loop that still owns the connection reports the disconnect;
					// a superseded loop's device has already been torn down by initDevice.
					if (session.usbDeviceConnection === conn) teardownSession(session, notify = true)
				}
			}
		}
	}

	private fun publishConnectedDevice(name: String) {
		connectedDevice = ConnectedDeviceSnapshot(
			name = name,
			eventId = SystemClock.elapsedRealtime()
		)
	}

	/**
	 * Releases everything one session was holding - MIDI API ports/device, USB interface
	 * claim, USB connection fd, and its send/init coroutines - then removes it from
	 * `sessions`. When notify is true (a real disconnect, not a superseded/replaced attempt),
	 * also demotes/promotes primary and MultiplexDriver state and fires the UI callbacks.
	 */
	private fun teardownSession(session: DeviceSession, notify: Boolean) {
		session.pendingInitJob?.cancel()
		session.pendingInitJob = null
		session.receiveJob?.cancel()
		session.pendingUsbClaim = null
		session.initSysExSent = false
		closeMidiApi(session)

		// Null the connection first: the receive loop and the send path compare against it.
		val conn = session.usbDeviceConnection
		session.usbDeviceConnection = null
		val iface = session.usbInterface
		session.usbInterface = null
		session.usbEndpointIn = null
		session.usbEndpointOut = null
		if (conn != null) {
			try {
				if (iface != null) conn.releaseInterface(iface)
			} catch (e: RuntimeException) {
				Log.err("USB releaseInterface failed (${session.name})", e)
			}
			try {
				conn.close()
			} catch (e: RuntimeException) {
				Log.err("USB close failed (${session.name})", e)
			}
		}
		session.sendJob?.cancel()
		session.sendChannel.close()

		session.isRun = false
		sessions.remove(session.usbDevice.deviceId)

		if (notify) {
			session.driver.onDisconnected()

			val wasPrimary = session.usbDevice.deviceId == primarySessionId
			if (wasPrimary) {
				primarySessionId = sessions.values.firstOrNull()?.usbDevice?.deviceId
			}

			when {
				sessions.isEmpty() -> {
					_driver = Noting()
				}
				sessions.size == 1 && _driver is MultiplexDriver -> {
					// Back down to one pad - drop the multiplex wrapper and talk to
					// that pad's own driver directly again.
					_driver = sessions.values.first().driver
				}
				wasPrimary && _driver !is MultiplexDriver -> {
					// Single-pad mode, and the connected pad just disconnected -
					// promote whichever other pad is left, if any.
					_driver = sessions.values.firstOrNull()?.driver ?: Noting()
				}
				// else: MultiplexDriver stays in place (2+ pads still connected), or a
				// non-primary pad disconnected without affecting the primary - nothing
				// else to update.
			}

			connectedDevice = sessions.values.firstOrNull()?.let {
				ConnectedDeviceSnapshot(it.name, SystemClock.elapsedRealtime())
			}

			if (sessions.isEmpty()) {
				connectionObserver?.onDisconnected()
			}
		}
	}

	// Driver

	fun setDriverListener(
		target: DriverRef = driver,
		sendListener: DriverRef.OnSendSignalListener? = onSendSignalListener,
		receiveListener: DriverRef.OnReceiveSignalListener? = onReceiveSignalListener,
	) {
		target.setOnCycleListener(onCycleListener)
		target.setOnGetSignalListener(receiveListener)
		target.setOnSendSignalListener(sendListener)
	}

	// Read-only snapshot of every currently connected pad, for UI that wants to let the
	// person pick a model per physical device (rather than only ever targeting the primary).
	data class SessionSummary(
		val sessionId: Int,
		val deviceName: String,
		val driverClass: KClass<out DriverRef>,
		val isPrimary: Boolean,
	)

	val connectedSessions: List<SessionSummary>
		get() = sessions.values.map {
			SessionSummary(
				sessionId = it.usbDevice.deviceId,
				deviceName = it.name,
				driverClass = it.driver::class,
				isPrimary = it.usbDevice.deviceId == primarySessionId,
			)
		}

	// Sets the model/driver for one specific connected pad, identified by SessionSummary.sessionId.
	// Unlike assigning `driver` directly, this always targets exactly the requested physical
	// device and never disturbs the MultiplexDriver dispatcher other pads rely on - this is
	// the one to use from a per-device picker UI.
	fun setDriverForSession(sessionId: Int, value: DriverRef) {
		val target = sessions[sessionId] ?: return

		val oldTargetDriver = target.driver
		oldTargetDriver.sendClearLed()
		oldTargetDriver.onDisconnected()

		target.driver = value
		setDriverListener(value, makeSendListener(target), makeReceiveListener(target))
		try {
			value.initialize()
			value.onConnected()
		} catch (e: RuntimeException) {
			// Matches the top-level `driver` setter: a bad SysEx send must not crash the
			// caller (MidiSelectActivity's click handler).
			Log.err("Driver set failed", e)
		}

		// Keep the public `driver` property in sync when there's only one pad connected
		// (no MultiplexDriver in play) and this is that pad.
		if (sessionId == primarySessionId && _driver !is MultiplexDriver) {
			_driver = value
		}

		listener?.onChangeDriver(value)
	}

	// Controller

	fun removeController(target: MidiController) {
		if (controller != null && controller === target)
			controller = null
	}

	interface Listener {
		fun onConnectedListener()

		fun onChangeDriver(driverRef: DriverRef)

		fun onUiLog(log: String)
	}

	interface ConnectionObserver {
		fun onConnected(snapshot: ConnectedDeviceSnapshot)
		fun onDisconnected()
	}

	data class ConnectedDeviceSnapshot(
		val name: String,
		val eventId: Long,
	)
}
