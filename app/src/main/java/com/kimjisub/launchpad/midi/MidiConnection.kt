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
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.MasterKeyboard
import com.kimjisub.launchpad.midi.driver.Matrix
import com.kimjisub.launchpad.midi.driver.MidiFighter
import com.kimjisub.launchpad.midi.driver.Noting
import com.kimjisub.launchpad.tool.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


object MidiConnection {

	private data class DriverEntry(
		val name: String,
		val factory: () -> DriverRef,
		val interfaceNum: Int = 0,
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

					usbDeviceConnection?.bulkTransfer(
						usbEndpointOut, batchBuffer, offset, USB_BULK_TIMEOUT_MS
					)
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
	private val midiInputPorts = mutableMapOf<Int, MidiInputPort>()

	// Deferred USB claim - stored for later use after MIDI API SysEx
	private var pendingUsbClaim: (() -> Unit)? = null

	private var onCycleListener: DriverRef.OnCycleListener? = null
	private var onReceiveSignalListener: DriverRef.OnReceiveSignalListener? = null
	private var onSendSignalListener: DriverRef.OnSendSignalListener? = null

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
			} catch (e: IllegalAccessException) {
				Log.err("Driver set failed", e)
			} catch (e: InstantiationException) {
				Log.err("Driver instantiation failed", e)
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
			val deviceIterator = requireNotNull(usbManager).deviceList.values.iterator()
			if (deviceIterator.hasNext())
				initDevice(deviceIterator.next())
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
			val exactEntry = driverRegistryExact[pid]
			val rangeEntry = driverRegistryRanges.firstOrNull { pid in it.pidStart..it.pidEnd }?.entry

			val entry = exactEntry ?: rangeEntry
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
			} else if (pid and MATRIX_PRODUCT_ID_MASK == MATRIX_PRODUCT_ID_BASE) {
				listener?.onUiLog("prediction : 203 Matrix")
				driver = Matrix()
			} else {
				listener?.onUiLog("prediction : unknown (PID=$pid)")
				driver = MasterKeyboard()
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
		val manager = usbManager ?: run {
			Log.midiDetail("USB 에러 : usbManager == null")
			return
		}
		val connection = manager.openDevice(device)
		if (connection == null) {
			Log.midiDetail("USB 에러 : usbDeviceConnection == null")
			return
		}
		usbDeviceConnection = connection

		// Defer USB interface claim - MIDI API needs the interface first for SysEx
		pendingUsbClaim = {
			Log.midiDetail("USB: Claiming interface for MIDI communication")
			if (connection.claimInterface(usbIf, true)) {
				startReceiveLoop()
			} else {
				Log.midiDetail("USB 에러 : claimInterface failed")
			}
		}

		listener?.onConnectedListener()

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

		@Suppress("DEPRECATION")
		val deviceInfos = manager.devices
		Log.midiDetail("MIDI API: ${deviceInfos.size} device(s) found")

		val targetInfo = deviceInfos.firstOrNull { info ->
			val props = info.properties
			Log.midiDetail("MIDI API device: name=${props.getString(MidiDeviceInfo.PROPERTY_NAME)}, " +
				"manufacturer=${props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)}, " +
				"product=${props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)}, " +
				"inputPorts=${info.inputPortCount}, outputPorts=${info.outputPortCount}")
			info.inputPortCount > 0
		}

		if (targetInfo == null) {
			Log.midiDetail("MIDI API: No matching MIDI device found, claiming USB interface directly")
			claimUsbAndStart()
			return
		}

		Log.midiDetail("MIDI API: Opening device (inputPorts=${targetInfo.inputPortCount}, outputPorts=${targetInfo.outputPortCount})")
		for (port in targetInfo.ports) {
			val dir = if (port.type == MidiDeviceInfo.PortInfo.TYPE_INPUT) "INPUT" else "OUTPUT"
			Log.midiDetail("  MIDI API Port[${port.portNumber}]: $dir name=${port.name}")
		}

		manager.openDevice(targetInfo, { device ->
			if (device != null) {
				midiDevice = device
				Log.midiDetail("MIDI API: Device opened successfully")

				// Open all input ports and send SysEx
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
					for ((portNum, _) in midiInputPorts) {
						Log.midiDetail("MIDI API: Sending init SysEx (${messages.size} messages) to port $portNum")
						sendViaMidiApi(messages, portNum)
					}
				}

				// Delay to ensure SysEx is flushed before closing ports
				Handler(Looper.getMainLooper()).postDelayed({
					closeMidiApi()
					claimUsbAndStart()
				}, 500)
			} else {
				Log.midiDetail("MIDI API: Failed to open device, claiming USB interface directly")
				claimUsbAndStart()
			}
		}, Handler(Looper.getMainLooper()))
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
		startSendLoop()
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

				var offset = 0
				while (offset < encoded.size) {
					val chunk = minOf(64, encoded.size - offset)
					usbDeviceConnection?.bulkTransfer(
						usbEndpointOut, encoded, offset, chunk, USB_BULK_TIMEOUT_MS
					)
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

	private fun startReceiveLoop() {
		receiveJob?.cancel()
		receiveJob = ioScope.launch {
			withContext(Dispatchers.Main) {
				driver.onConnected()
			}

			if (!isRun) {
				isRun = true
				Log.midiDetail("USB 시작")

				val endpointIn = usbEndpointIn ?: run {
					Log.midiDetail("USB 에러 : usbEndpointIn == null")
					isRun = false
					return@launch
				}

				var prevTime = SystemClock.elapsedRealtime()
				var count = 0
				val byteArray = ByteArray(endpointIn.maxPacketSize)
				// Flat int array: [cmd0,sig0,note0,vel0, cmd1,sig1,note1,vel1, ...]
				val eventBuf = IntArray(endpointIn.maxPacketSize)

				while (isRun) {
					try {
						val conn = usbDeviceConnection ?: break
						val length = conn.bulkTransfer(
							endpointIn,
							byteArray,
							byteArray.size,
							1000
						)
						if (length >= 4) {
							var eventCount = 0
							var i = 0
							while (i < length) {
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
						} else if (length == -1) {
							val currTime = SystemClock.elapsedRealtime()
							if (prevTime != currTime) {
								count = 0
								prevTime = currTime
							} else {
								count++
								if (count > 10)
									break
							}
						}
					} catch (e: RuntimeException) {
						Log.err("MIDI receive loop error", e)
						break
					}
				}

				Log.midiDetail("USB 끝")
			}
			isRun = false

			withContext(Dispatchers.Main) {
				driver.onDisconnected()
			}
		}
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
}
