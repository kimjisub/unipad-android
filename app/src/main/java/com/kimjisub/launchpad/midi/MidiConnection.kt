package com.kimjisub.launchpad.midi

import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.SystemClock
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMK3
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


object MidiConnection {
	private const val SEND_MODE_ASYNC = 0
	private const val SEND_MODE_SYNC = 1

	private data class DriverEntry(
		val name: String,
		val factory: () -> DriverRef,
		val interfaceNum: Int = 0,
	)

	private val driverRegistry: Map<Int, DriverEntry> = mapOf(
		8 to DriverEntry("MidiFighter", ::MidiFighter),
		105 to DriverEntry("Launchpad MK2", ::LaunchpadMK2),
		81 to DriverEntry("Launchpad Pro", ::LaunchpadPRO),
		54 to DriverEntry("Launchpad mk2 mini", ::LaunchpadS),
		259 to DriverEntry("Launchpad X", ::LaunchpadX),
		291 to DriverEntry("Launchpad MK3", ::LaunchpadMK3),
		8211 to DriverEntry("LX 61 piano", ::MasterKeyboard),
		32822 to DriverEntry("Arduino Leonardo midi", ::LaunchpadPRO, interfaceNum = 3),
	)

	private const val MATRIX_PRODUCT_ID_MASK = 0xFFC0
	private const val MATRIX_PRODUCT_ID_BASE = 0x1040

	private var ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private var usbManager: UsbManager? = null
	private var usbInterface: UsbInterface? = null
	private var usbEndpointIn: UsbEndpoint? = null
	private var usbEndpointOut: UsbEndpoint? = null
	private var usbDeviceConnection: UsbDeviceConnection? = null

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
	@Volatile
	var mode = 0
		set(value) {
			field = value

			listener?.onChangeMode(field)
		}

	// Listener


	fun initConnection(intent: Intent, usbManager: UsbManager) {
		this.usbManager = usbManager

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
					if (mode == SEND_MODE_ASYNC) {
						ioScope.launch {
							sendBuffer(cmd, sig, note, velocity)
						}

					} else if (mode == SEND_MODE_SYNC)
						sendBuffer(cmd, sig, note, velocity)
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

			val entry = driverRegistry[device.productId]
			if (entry != null) {
				listener?.onUiLog("prediction : ${entry.name}")
				interfaceNum = entry.interfaceNum
				driver = entry.factory()
			} else if (device.productId and MATRIX_PRODUCT_ID_MASK == MATRIX_PRODUCT_ID_BASE) {
				listener?.onUiLog("prediction : 203 Matrix")
				driver = Matrix()
			} else {
				listener?.onUiLog("prediction : unknown")
				driver = MasterKeyboard()
			}
		} catch (e: SecurityException) {
			Log.err("USB driver selection failed", e)
		}

		for (i in interfaceNum until device.interfaceCount) {
			val ui = device.getInterface(i)
			if (ui.endpointCount > 0) {
				usbInterface = ui
				listener?.onUiLog("Interface : (${i + 1}/${device.interfaceCount})")
				break
			}
		}
		val usbIf = usbInterface ?: run {
			Log.midiDetail("USB 에러 : usbInterface == null")
			return
		}
		for (i in 0 until usbIf.endpointCount) {
			val ep = usbIf.getEndpoint(i)
			when (ep.direction) {
				UsbConstants.USB_DIR_IN -> {
					listener?.onUiLog("Endpoint_In : (${i + 1}/${usbIf.endpointCount})")
					usbEndpointIn = ep
				}
				UsbConstants.USB_DIR_OUT -> {
					listener?.onUiLog("Endpoint_OUT : (${i + 1}/${usbIf.endpointCount})")
					usbEndpointOut = ep
				}
				else -> {
					listener?.onUiLog("Endpoint_Unknown : (${i + 1}/${usbIf.endpointCount})")
				}
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
		if (connection.claimInterface(usbIf, true)) {
			startReceiveLoop()
		} else {
			Log.midiDetail("USB 에러 : usbDeviceConnection.claimInterface(usbInterface, true)")
		}

		listener?.onConnectedListener()

		return
	}

	internal fun sendBuffer(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
		try {
			val buffer = byteArrayOf(cmd, sig, note, velocity)
			usbDeviceConnection?.bulkTransfer(usbEndpointOut, buffer, buffer.size, 1000)
		} catch (_: RuntimeException) {
			// Intentional: silently ignore send failures (device may be disconnected)
		}

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
							var i = 0
							while (i < length) {
								val cmd = byteArray[i].toInt()
								val sig = byteArray[i + 1].toInt()
								val note = byteArray[i + 2].toInt()
								val velocity = byteArray[i + 3].toInt()

								withContext(Dispatchers.Main) {
									driver.getSignal(cmd, sig, note, velocity)
								}
								Log.midi(
									String.format(
										Locale.US,
										"%-7d%-7d%-7d%-7d",
										cmd,
										sig,
										note,
										velocity
									)
								)
								i += 4
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
				field?.onChangeMode(mode)
			}
		}

	interface Listener {
		fun onConnectedListener()

		fun onChangeDriver(driverRef: DriverRef)

		fun onChangeMode(mode: Int)

		fun onUiLog(log: String)
	}
}
