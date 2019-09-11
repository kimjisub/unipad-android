package com.kimjisub.launchpad.midi

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.usb.*
import android.os.AsyncTask
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.midi.driver.*
import com.kimjisub.manager.Log
import kotlinx.coroutines.withContext
import java.util.*

object MidiConnection {
    private var usbManager: UsbManager? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint_in: UsbEndpoint? = null
    private var usbEndpoint_out: UsbEndpoint? = null
    private var usbDeviceConnection: UsbDeviceConnection? = null

    private var onCycleListener: DriverRef.OnCycleListener? = null
    private var onGetSignalListener: DriverRef.OnGetSignalListener? = null
    private var onSendSignalListener: DriverRef.OnSendSignalListener? = null

    var driver: DriverRef = Noting()
    var controller: MidiController? = null


    private var isRun = false
    private var mode = 0

    // Listener /////////////////////////////////////////////////////////////////////////////////////////

    internal var listener: Listener? = null

    fun initConnection(intent: Intent, usbManager_: UsbManager) {
        usbManager = usbManager_
        val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action)
            initDevice(usbDevice)
        else {
            val deviceIterator = Objects.requireNonNull(usbManager_).deviceList.values.iterator()
            if (deviceIterator.hasNext())
                initDevice(deviceIterator.next())
        }

        onCycleListener = object : DriverRef.OnCycleListener {
            override fun onConnected() {
                controller!!.onAttach()
            }

            override fun onDisconnected() {
                controller!!.onDetach()
            }
        }

        onSendSignalListener = DriverRef.OnSendSignalListener { cmd, sig, note, velo ->
            if (usbDeviceConnection != null) {
                if (mode == 0) {
                    try {
                        object : AsyncTask<String, Int, String>() {
                            override fun doInBackground(vararg params: String): String? {
                                sendBuffer(cmd, sig, note, velo)
                                return null
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                    } catch (ignore: Exception) {
                        //Log.midiDetail("MIDI send thread execute fail");
                    }

                } else if (mode == 1)
                    sendBuffer(cmd, sig, note, velo)
            }
        }

        onGetSignalListener = object : DriverRef.OnGetSignalListener {
            override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int) {
                controller!!.onPadTouch(x, y, upDown, velo)
            }

            override fun onFunctionkeyTouch(f: Int, upDown: Boolean) {
                controller!!.onFunctionkeyTouch(f, upDown)
            }

            override fun onChainTouch(c: Int, upDown: Boolean) {
                controller!!.onChainTouch(c, upDown)
            }

            override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velo: Int) {
                controller!!.onUnknownEvent(cmd, sig, note, velo)
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
            Log.midiDetail("DeviceName : " + device.deviceName)
            Log.midiDetail("DeviceClass : " + device.deviceClass)
            Log.midiDetail("DeviceId : " + device.deviceId)
            Log.midiDetail("DeviceProtocol : " + device.deviceProtocol)
            Log.midiDetail("DeviceSubclass : " + device.deviceSubclass)
            Log.midiDetail("InterfaceCount : " + device.interfaceCount)
            Log.midiDetail("VendorId : " + device.vendorId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            Log.midiDetail("ProductId : " + device.productId)
            onUiLog("ProductId : " + device.productId + "\n")
            val driver: Class<*>
            when (device.productId) {
                8 -> {
                    driver = MidiFighter::class.java
                    onUiLog("prediction : MidiFighter\n")
                }
                105 -> {
                    driver = LaunchpadMK2::class.java
                    onUiLog("prediction : MK2\n")
                }
                81 -> {
                    driver = LaunchpadPRO::class.java
                    onUiLog("prediction : Pro\n")
                }
                54 -> {
                    driver = LaunchpadS::class.java
                    onUiLog("prediction : mk2 mini\n")
                }
                8211 -> {
                    driver = MasterKeyboard::class.java
                    onUiLog("prediction : LX 61 piano\n")
                }
                32822 -> {
                    driver = LaunchpadPRO::class.java
                    onUiLog("prediction : Arduino Leonardo midi\n")
                    interfaceNum = 3
                }
                else -> {
                    driver = MasterKeyboard::class.java
                    onUiLog("prediction : unknown\n")
                }
            }
            setDriver(driver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (i in interfaceNum until device.interfaceCount) {
            val ui = device.getInterface(i)
            if (ui.endpointCount > 0) {
                usbInterface = ui
                onUiLog("Interface : (" + (i + 1) + "/" + device.interfaceCount + ")\n")
                break
            }
        }
        for (i in 0 until usbInterface!!.endpointCount) {
            val ep = usbInterface!!.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN) {
                onUiLog("Endpoint_In : (" + (i + 1) + "/" + usbInterface!!.endpointCount + ")\n")
                usbEndpoint_in = ep
            } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                onUiLog("Endpoint_OUT : (" + (i + 1) + "/" + usbInterface!!.endpointCount + ")\n")
                usbEndpoint_out = ep
            } else {
                onUiLog("Endpoint_Unknown : (" + (i + 1) + "/" + usbInterface!!.endpointCount + ")\n")
            }
        }
        usbDeviceConnection = usbManager!!.openDevice(device)
        if (usbDeviceConnection == null) {
            Log.midiDetail("USB 에러 : usbDeviceConnection == null")
            return
        }
        if (usbDeviceConnection!!.claimInterface(usbInterface, true)) {
            ReceiveTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        } else {
            Log.midiDetail("USB 에러 : usbDeviceConnection.claimInterface(usbInterface, true)")
        }

        onConnectedListener()

        return
    }


    fun setMode(mode_: Int) {
        mode = mode_

        onChangeMode(mode)
    }

    internal fun sendBuffer(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
        try {
            val buffer = byteArrayOf(cmd, sig, note, velocity)
            usbDeviceConnection!!.bulkTransfer(usbEndpoint_out, buffer, buffer.size, 1000)
        } catch (ignored: Exception) {
        }

    }

    class ReceiveTask : AsyncTask<String, Int, String>() {

        override fun onPreExecute() {
            super.onPreExecute()
            driver!!.onConnected()
        }

        @SuppressLint("DefaultLocale")
        override fun doInBackground(vararg params: String): String? {
            if (!isRun) {
                isRun = true
                Log.midiDetail("USB 시작")

                var prevTime = System.currentTimeMillis()
                var count = 0
                val byteArray = ByteArray(usbEndpoint_in!!.maxPacketSize)
                while (isRun) {
                    try {
                        val length = usbDeviceConnection!!.bulkTransfer(
                            usbEndpoint_in,
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

                                publishProgress(cmd, sig, note, velocity)
                                Log.midi(
                                    String.format(
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
                            val currTime = System.currentTimeMillis()
                            if (prevTime != currTime) {
                                count = 0
                                prevTime = currTime
                            } else {
                                count++
                                if (count > 10)
                                    break
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }

                }

                Log.midiDetail("USB 끝")
            }
            isRun = false
            return null
        }

        override fun onProgressUpdate(vararg progress: Int?) {

            progress[0]
            driver!!.getSignal(progress[0]!!, progress[1]!!, progress[2]!!, progress[3]!!)
        }

        override fun onPostExecute(result: String) {
            driver!!.onDisconnected()
        }
    }

    // Driver /////////////////////////////////////////////////////////////////////////////////////////

    fun setDriver(cls: Class<*>) {
        if (driver != null) {
            driver!!.sendClearLED()
            driver!!.onDisconnected()
            driver = Noting()
        }

        try {
            driver = cls.newInstance() as DriverRef
            setDriverListener()
            if (isRun)
                driver!!.onConnected()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InstantiationException) {
            e.printStackTrace()
        }

        onChangeDriver(cls)
    }

    fun setDriverListener() {
        driver!!.setOnCycleListener(onCycleListener)
        driver!!.setOnGetSignalListener(onGetSignalListener)
        driver!!.setOnSendSignalListener(onSendSignalListener)
    }

    // Controller /////////////////////////////////////////////////////////////////////////////////////////

    fun removeController(controller_: MidiController) {
        if (controller != null && controller === controller_)
            controller = null
    }

    interface Listener {
        fun onConnectedListener()

        fun onChangeDriver(cls: Class<*>)

        fun onChangeMode(mode: Int)

        fun onUiLog(log: String)
    }

    fun setListener(listener_: Listener) {
        listener = listener_

        onChangeDriver(driver!!.javaClass)
        onChangeMode(mode)
    }

    fun removeListener() {
        listener = null
    }

    private fun onConnectedListener() {
        if (listener != null)
            listener!!.onConnectedListener()
    }

    private fun onChangeDriver(cls: Class<*>) {
        if (listener != null)
            listener!!.onChangeDriver(cls)
    }

    private fun onChangeMode(mode: Int) {
        if (listener != null)
            listener!!.onChangeMode(mode)
    }

    private fun onUiLog(log: String) {
        if (listener != null)
            listener!!.onUiLog(log)
    }
}
