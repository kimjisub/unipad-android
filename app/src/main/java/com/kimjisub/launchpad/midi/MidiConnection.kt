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

	// Novation Launchpad PID ranges (Device ID 1~16 → base PID + 0..15)
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

	// Non-blocking ordered send queue: callers enqueue instantly, single consumer batches and sends
	private val sendChannel = Channel<ByteArray>(Channel.UNLIMITED)
	private var sendJob: Job? = null
	private const val USB_MIDI_PACKET_SIZE = 4
	private const val USB_BULK_TIMEOUT_MS = 50

	private fun startSendLoop() {
		if (sendJob?.isActive == true) return
		sendJob = ioScope.launch {
			// Batch buffer: maxPacketSize (64) fits 16 MIDI packets
			val batchBuffer = ByteArray(64)

			for (first in sendChannel) {
				try {
					// Start batch with the first message
					first.copyInto(batchBuffer, 0)
					var offset = USB_MIDI_PACKET_SIZE

					// Drain all pending messages into the batch (up to 64 bytes)
					while (offset + USB_MIDI_PACKET_SIZE <= batchBuffer.size) {
						val next = sendChannel.tryReceive().getOrNull() ?: break
						next.copyInto(batchBuffer, offset)
						offset += USB_MIDI_PACKET_SIZE
					}

					// Read both fields once: teardown() nulls them in sequence from another thread.
					val conn = usbDeviceConnection
					val ep = usbEndpointOut
					if (conn != null && ep != null) {
						val written = conn.bulkTransfer(ep, batchBuffer, offset, USB_BULK_TIMEOUT_MS)
						if (written < 0) Log.midiDetail("USB TX failed ($written), ${offset / USB_MIDI_PACKET_SIZE} packets dropped")
					}
				} catch (_: RuntimeException) {
					// Device may be disconnected
				}
			}
		}
	}

	private var usbManager: UsbManager? = null
	private var usbInterface: UsbInterface? = null
	private var usbEndpointIn: UsbEndpoint? = null
	private var usbEndpointOut: UsbEndpoint? = null
	private var usbDeviceConnection: UsbDeviceConnection? = null

	// Android MIDI API for SysEx delivery (used before USB interface claim)
	private var midiManager: MidiManager? = null
	private var midiDevice: MidiDevice? = null
	// Written from the init coroutine (IO) and closeMidiApi (main), read from sendRawBuffer (IO).
	private val midiInputPorts = ConcurrentHashMap<Int, MidiInputPort>()
	private var pendingInitJob: Job? = null
	@Volatile
	private var initSysExSent = false

	// Deferred USB claim - stored for later use after MIDI API SysEx
	private var pendingUsbClaim: (() -> Unit)? = null

	private var onCycleListener: DriverRef.OnCycleListener? = null
	private var onReceiveSignalListener: DriverRef.OnReceiveSignalListener? = null
	private var onSendSignalListener: DriverRef.OnSendSignalListener? = null
	@Volatile
	var connectedDevice: ConnectedDeviceSnapshot? = null
		private set
	@Volatile
	var connectionObserver: ConnectionObserver? = null

	@Volatile
	var driver: DriverRef = Noting()
		set(value) {
			field.sendClearLed()
			field.onDisconnected()

			try {
				field = value
				setDriverListener()
				field.initialize()
				if (isRun)
					field.onConnected()
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
	private var receiveJob: Job? = null
	@Volatile
	private var isRun = false

	// Listener


	fun initConnection(intent: Intent, usbManager: UsbManager, context: Context? = null) {
		this.usbManager = usbManager

		// Initialize Android MIDI API for SysEx support
		if (context != null) {
			midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
			Log.midiDetail("MidiManager available: ${midiManager != null}")
		}

		val usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
		} else {
			@Suppress("DEPRECATION")
			intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
		}
		if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action)
			initDevice(usbDevice)
		else {
			// Without an attach intent nothing was granted to us. Only a device we already hold
			// permission for, and that looks like a MIDI device, is worth opening: the first entry
			// of deviceList used to be a hub or a mouse and ended up published as "Master Keyboard".
			val candidate = try {
				usbManager.deviceList.values.firstOrNull { usbManager.hasPermission(it) && looksLikeMidiDevice(it) }
			} catch (e: RuntimeException) {
				Log.err("USB enumeration failed", e)
				null
			}
			if (candidate != null) initDevice(candidate)
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
				if (usbDeviceConnection != null) {
					sendChannel.trySend(byteArrayOf(cmd, sig, note, velocity))
				}
			}

			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
				if (usbDeviceConnection != null) {
					ioScope.launch {
						sendRawBuffer(messages, cableNumber)
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

		setDriverListener()
		// driver.initialize() is called by initMidiApiDevice() after MIDI API is ready
		// (or falls back to USB bulk transfer if MIDI API is not available)
	}

	private fun initDevice(device: UsbDevice?) {
		var interfaceNum = 0

		if (device == null) {
			Log.midiDetail("USB 에러 : device == null")
			return
		}

		// A previous device (replug, or a second attach) must release its fd and stop its
		// receive loop before its fields are overwritten.
		teardown(notify = true)

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
				driver = entry.factory()
				publishConnectedDevice(entry.name)
			} else if (pid and MATRIX_PRODUCT_ID_MASK == MATRIX_PRODUCT_ID_BASE) {
				// Detection stays on the product id. The device renamed itself in
				// firmware (Matrix -> Mystrix) but the PID did not move, so only the
				// name shown to the user needs to follow. iOS and the web match on the
				// announced name and do have to carry both spellings.
				listener?.onUiLog("prediction : 203 Mystrix")
				driver = Matrix()
				publishConnectedDevice("Mystrix")
			} else {
				listener?.onUiLog("prediction : unknown (PID=$pid)")
				driver = MasterKeyboard()
				publishConnectedDevice("Master Keyboard")
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
				usbInterface = ui
				listener?.onUiLog("Interface MIDI : (${i + 1}/${device.interfaceCount})")
				break
			}
		}
		// Fallback: first interface with endpoints
		if (usbInterface == null) {
			for (i in interfaceNum until device.interfaceCount) {
				val ui = device.getInterface(i)
				if (ui.endpointCount > 0) {
					usbInterface = ui
					listener?.onUiLog("Interface : (${i + 1}/${device.interfaceCount})")
					break
				}
			}
		}
		val usbIf = usbInterface ?: run {
			Log.midiDetail("USB 에러 : usbInterface == null")
			connectedDevice = null
			return
		}
		for (i in 0 until usbIf.endpointCount) {
			val ep = usbIf.getEndpoint(i)
			val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
			val info = "EP[$i] dir=$dir type=${ep.type} addr=0x${"%02X".format(ep.address)} maxPkt=${ep.maxPacketSize}"
			Log.midiDetail(info)
			listener?.onUiLog(info)
			when (ep.direction) {
				UsbConstants.USB_DIR_IN -> usbEndpointIn = ep
				UsbConstants.USB_DIR_OUT -> usbEndpointOut = ep
			}
		}
		val endpointIn = usbEndpointIn ?: run {
			Log.midiDetail("USB 에러 : usbEndpointIn == null")
			connectedDevice = null
			return
		}
		val manager = usbManager ?: run {
			Log.midiDetail("USB 에러 : usbManager == null")
			connectedDevice = null
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
			connectedDevice = null
			return
		}
		usbDeviceConnection = connection

		// Defer USB interface claim - MIDI API needs the interface first for SysEx
		pendingUsbClaim = {
			Log.midiDetail("USB: Claiming interface for MIDI communication")
			if (usbDeviceConnection !== connection) {
				Log.midiDetail("USB: device changed before the claim, skipping")
			} else if (connection.claimInterface(usbIf, true)) {
				startReceiveLoop(connection, endpointIn)
			} else {
				// Announcing "connected" while owning no interface left the UI streaming LEDs
				// to a device that never answered; report the disconnect instead.
				Log.midiDetail("USB 에러 : claimInterface failed")
				teardown(notify = true)
			}
		}

		listener?.onConnectedListener()
		connectedDevice?.let { connectionObserver?.onConnected(it) }

		// Try MIDI API for SysEx first, then claim USB interface
		initMidiApiDevice(device)

		return
	}

	private fun initMidiApiDevice(usbDevice: UsbDevice?) {
		val manager = midiManager
		if (manager == null || usbDevice == null) {
			// No MIDI API available, claim USB interface directly
			Log.midiDetail("MIDI API not available, claiming USB interface directly")
			claimUsbAndStart()
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
			claimUsbAndStart()
			return
		}

		if (targetInfo == null) {
			Log.midiDetail("MIDI API: No matching MIDI device found, claiming USB interface directly")
			claimUsbAndStart()
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
					onMidiApiDeviceOpened(device, targetInfo)
				} catch (e: RuntimeException) {
					Log.err("MIDI API: device callback failed, claiming USB interface directly", e)
					closeMidiApi()
					claimUsbAndStart()
				}
			}, Handler(Looper.getMainLooper()))
		} catch (e: RuntimeException) {
			Log.err("MIDI API: openDevice failed, claiming USB interface directly", e)
			claimUsbAndStart()
		}
	}

	private fun onMidiApiDeviceOpened(device: MidiDevice?, targetInfo: MidiDeviceInfo) {
		if (device == null) {
			Log.midiDetail("MIDI API: Failed to open device, claiming USB interface directly")
			claimUsbAndStart()
			return
		}
		midiDevice = device
		Log.midiDetail("MIDI API: Device opened successfully")

		// Port opening and the SysEx sends (blocking port.send + 50 ms sleeps per message, per
		// port) used to run on the main thread inside the attach path; a Pro MK3 with several
		// ports froze the UI. The job is owned so teardown() can cancel it on an early unplug.
		val connectionAtOpen = usbDeviceConnection
		pendingInitJob?.cancel()
		pendingInitJob = ioScope.launch {
			try {
				try {
					for (portInfo in targetInfo.ports) {
						if (portInfo.type == MidiDeviceInfo.PortInfo.TYPE_INPUT) {
							val port = device.openInputPort(portInfo.portNumber)
							if (port != null) {
								midiInputPorts[portInfo.portNumber] = port
								Log.midiDetail("MIDI API: Opened input port ${portInfo.portNumber} (${portInfo.name})")
							}
						}
					}

					// Send SysEx to ALL input ports (port names are empty, we don't know which is DAW)
					val initData = driver.getInitSysEx()
					if (initData != null && midiInputPorts.isNotEmpty()) {
						val (messages, _) = initData
						var sent = false
						for (portNum in midiInputPorts.keys) {
							Log.midiDetail("MIDI API: Sending init SysEx (${messages.size} messages) to port $portNum")
							if (sendViaMidiApi(messages, portNum)) sent = true
						}
						if (sent) initSysExSent = true
					}
				} catch (e: RuntimeException) {
					// Crashlytics 9b99a871: SecurityException from openInputPort on Android 16.
					Log.err("MIDI API: init over MIDI API failed, falling back to USB", e)
				}
				// Let the SysEx flush before the ports are closed
				delay(500)
			} finally {
				withContext(NonCancellable + Dispatchers.Main) {
					closeMidiApi()
					// A device that was torn down while we slept must not have its stale
					// interface claimed for whatever is plugged in now.
					if (connectionAtOpen != null && usbDeviceConnection === connectionAtOpen) claimUsbAndStart()
				}
			}
		}
	}

	private fun closeMidiApi() {
		Log.midiDetail("MIDI API: Closing ports and device")
		for ((portNum, port) in midiInputPorts) {
			try {
				port.close()
				Log.midiDetail("MIDI API: Closed input port $portNum")
			} catch (e: Exception) {
				Log.err("MIDI API: Failed to close port $portNum", e)
			}
		}
		midiInputPorts.clear()
		try {
			midiDevice?.close()
			midiDevice = null
			Log.midiDetail("MIDI API: Device closed")
		} catch (e: Exception) {
			Log.err("MIDI API: Failed to close device", e)
		}
	}

	private fun claimUsbAndStart() {
		pendingUsbClaim?.invoke()
		pendingUsbClaim = null
		if (usbDeviceConnection == null) return
		startSendLoop()
		if (!initSysExSent) {
			// The MIDI API path did not deliver the init SysEx (no MidiManager, no input port, or
			// the open failed). driver.initialize() at assignment time was dropped because the
			// connection did not exist yet, so without this the launchpad stays in its default
			// layout and every pad lands on the wrong coordinate. It now goes over the bulk endpoint.
			Log.midiDetail("USB: sending init SysEx over bulk transfer")
			driver.initialize()
		}
	}

	/** Releases the USB interface and fd, stops the loops, and clears every per-device field. */
	private fun teardown(notify: Boolean) {
		pendingInitJob?.cancel()
		pendingInitJob = null
		pendingUsbClaim = null
		initSysExSent = false
		closeMidiApi()
		// Null the connection first: the receive loop and the send path compare against it.
		val conn = usbDeviceConnection
		usbDeviceConnection = null
		val iface = usbInterface
		usbInterface = null
		usbEndpointIn = null
		usbEndpointOut = null
		if (conn != null) {
			try {
				if (iface != null) conn.releaseInterface(iface)
			} catch (e: RuntimeException) {
				Log.err("USB releaseInterface failed", e)
			}
			try {
				conn.close()
			} catch (e: RuntimeException) {
				Log.err("USB close failed", e)
			}
		}
		val hadDevice = conn != null || connectedDevice != null
		isRun = false
		if (notify && hadDevice) {
			driver.onDisconnected()
			connectedDevice = null
			connectionObserver?.onDisconnected()
		}
	}

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

	private fun sendViaMidiApi(messages: List<ByteArray>, cableNumber: Int): Boolean {
		val port = midiInputPorts[cableNumber] ?: return false
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


	internal fun sendRawBuffer(messages: List<ByteArray>, cableNumber: Int = 0) {
		// Try Android MIDI API first (handles SysEx properly)
		if (sendViaMidiApi(messages, cableNumber)) {
			Log.midiDetail("SysEx sent via MIDI API (port=$cableNumber)")
			return
		}

		// Fallback: USB bulk transfer with manual SysEx encoding
		Log.midiDetail("MIDI API not available for port=$cableNumber, falling back to USB bulk transfer")
		try {
			for ((index, msg) in messages.withIndex()) {
				val encoded = encodeSysEx(msg, cableNumber)
				Log.midiDetail("TX SysEx (USB): ${msg.joinToString(" ") { "%02X".format(it) }} (cable=$cableNumber)")

				val conn = usbDeviceConnection
				val ep = usbEndpointOut
				if (conn == null || ep == null) {
					Log.midiDetail("TX SysEx (USB): no connection, message dropped")
					return
				}
				var offset = 0
				while (offset < encoded.size) {
					val chunk = minOf(64, encoded.size - offset)
					val written = conn.bulkTransfer(ep, encoded, offset, chunk, USB_BULK_TIMEOUT_MS)
					if (written < 0) {
						Log.midiDetail("TX SysEx (USB): bulkTransfer failed ($written) at offset $offset")
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

	private fun startReceiveLoop(conn: UsbDeviceConnection, endpointIn: UsbEndpoint) {
		val previous = receiveJob
		receiveJob = ioScope.launch {
			// cancel() alone cannot interrupt a blocking bulkTransfer; wait for the old loop so two
			// loops never poll the same endpoint. The old loop sees a foreign connection in its
			// finally and stays silent, so the new device's "connected" is not followed by a bogus
			// "disconnected".
			previous?.cancelAndJoin()
			if (usbDeviceConnection !== conn) return@launch

			isRun = true
			withContext(Dispatchers.Main) {
				driver.onConnected()
			}
			Log.midiDetail("USB 시작")

			val byteArray = ByteArray(endpointIn.maxPacketSize)
			// Flat int array: [cmd0,sig0,note0,vel0, cmd1,sig1,note1,vel1, ...]
			val eventBuf = IntArray(endpointIn.maxPacketSize)
			var fastFailures = 0

			try {
				while (isActive && usbDeviceConnection === conn) {
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
									driver.getSignal(snapshot[base], snapshot[base + 1], snapshot[base + 2], snapshot[base + 3])
								}
							}
						}
					} else if (length < 0) {
						// An idle device also returns -1, but only after the full 1000 ms timeout;
						// a detached device fails within a few milliseconds. Counting only the fast
						// failures tells the two apart without depending on the same-millisecond
						// coincidence the old heuristic needed (on ROMs where a dead fd takes
						// >= 1 ms to fail it never fired and the loop spun forever).
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
				Log.err("MIDI receive loop error", e)
			} finally {
				Log.midiDetail("USB 끝")
				withContext(NonCancellable + Dispatchers.Main) {
					// Only the loop that still owns the connection reports the disconnect;
					// a superseded loop's device has already been torn down by initDevice.
					if (usbDeviceConnection === conn) teardown(notify = true)
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

	// Driver

	fun setDriverListener() {
		driver.setOnCycleListener(onCycleListener)
		driver.setOnGetSignalListener(onReceiveSignalListener)
		driver.setOnSendSignalListener(onSendSignalListener)
	}

	// Controller

	fun removeController(target: MidiController) {
		if (controller != null && controller === target)
			controller = null
	}


	@Volatile
	internal var listener: Listener? = null
		set(value) {
			field = value

			if (field != null) {
				field?.onChangeDriver(driver)
			}
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
