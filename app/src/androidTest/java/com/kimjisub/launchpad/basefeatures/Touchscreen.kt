package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.hardware.input.InputManager
import android.os.ParcelFileDescriptor
import android.view.Surface
import java.io.OutputStream
import java.util.UUID

/**
 * A second touchscreen, created as a kernel input device with the platform `uinput` shell tool, for
 * a finger that must come from another device than the [Mouse]. Events injected through
 * UiAutomation all arrive as one device, so the system ends a held injected mouse press as soon as
 * an injected finger lands; a real mouse and touchscreen are two devices. Positions are in screen
 * coordinates, as with [Fingers]; the device reports them in the display's natural orientation.
 */
class Touchscreen(
    private val screen: FeatureScreen,
    private val findDevice: (InputManager, String) -> Int? = { inputs, name ->
        inputs.inputDeviceIds.firstOrNull { inputs.getInputDevice(it)?.name == name }
    },
) : AutoCloseable {
    private val natural: Point = screen.onMain {
        val mode = screen.resumed().display!!.mode
        Point(mode.physicalWidth, mode.physicalHeight)
    }
    // Android groups input nodes sharing a physical port, even if their names differ.
    private val name = "UniPad test touchscreen ${UUID.randomUUID()}"
    private val streams: Array<ParcelFileDescriptor> = screen.instrumentation.uiAutomation.executeShellCommandRwe("uinput -")
    private val commands: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(streams[1])
    private var closed = false
    private val slots = mutableListOf<Int>()
    private var nextTrackingId = 1

    /** The input device ID the app sees on this touchscreen's events. */
    val deviceId: Int

    init {
        try {
            val abs = listOf(ABS_X to natural.x, ABS_Y to natural.y, ABS_MT_SLOT to SLOTS, ABS_MT_POSITION_X to natural.x,
                ABS_MT_POSITION_Y to natural.y, ABS_MT_TRACKING_ID to 65536)
            send("""{"id":1,"command":"register","name":"$name","port":"$name","vid":6353,"pid":45057,"bus":"usb","configuration":[""" +
                """{"type":$UI_SET_EVBIT,"data":[$EV_KEY,$EV_ABS]},{"type":$UI_SET_KEYBIT,"data":[$BTN_TOUCH]},""" +
                """{"type":$UI_SET_ABSBIT,"data":[${abs.joinToString(",") { it.first.toString() }}]},""" +
                """{"type":$UI_SET_PROPBIT,"data":[$INPUT_PROP_DIRECT]}],"abs_info":[""" +
                abs.joinToString(",") { (code, size) ->
                    """{"code":$code,"info":{"value":0,"minimum":0,"maximum":${size - 1},"fuzz":0,"flat":0,"resolution":0}}"""
                } + "]}")
            val inputs = screen.context.getSystemService(InputManager::class.java)
            var found: Int? = null
            screen.await("Virtual touchscreen did not appear") {
                found = findDevice(inputs, name)
                found != null
            }
            deviceId = found!!
        } catch (failure: Throwable) {
            try { closeStreams() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /** Puts a finger down and returns its slot, which the app sees as its pointer ID. */
    fun down(at: Point): Int {
        val slot = (0 until SLOTS).first { it !in slots }
        val (x, y) = raw(at)
        val touch = if (slots.isEmpty()) listOf(EV_KEY, BTN_TOUCH, 1, EV_ABS, ABS_X, x, EV_ABS, ABS_Y, y) else emptyList()
        slots += slot
        inject(listOf(EV_ABS, ABS_MT_SLOT, slot, EV_ABS, ABS_MT_TRACKING_ID, nextTrackingId++,
            EV_ABS, ABS_MT_POSITION_X, x, EV_ABS, ABS_MT_POSITION_Y, y) + touch)
        return slot
    }

    fun up(slot: Int) {
        slots -= slot
        val release = if (slots.isEmpty()) listOf(EV_KEY, BTN_TOUCH, 0) else emptyList()
        inject(listOf(EV_ABS, ABS_MT_SLOT, slot, EV_ABS, ABS_MT_TRACKING_ID, -1) + release)
    }

    /** Lifts any finger left down and removes the device. */
    override fun close() {
        if (closed) return
        try {
            slots.toList().forEach(::up)
        } finally {
            closeStreams()
        }
    }

    /** Closing stdin ends uinput and removes its device, including failed registration. */
    private fun closeStreams() {
        closed = true
        try {
            commands.close()
        } finally {
            try { streams[0].close() } finally { streams[2].close() }
        }
    }

    private fun raw(at: Point): Pair<Int, Int> = when (val rotation = screen.onMain { screen.resumed().display!!.rotation }) {
        Surface.ROTATION_0 -> at.x to at.y
        Surface.ROTATION_90 -> natural.x - 1 - at.y to at.x
        Surface.ROTATION_180 -> natural.x - 1 - at.x to natural.y - 1 - at.y
        Surface.ROTATION_270 -> at.y to natural.y - 1 - at.x
        else -> throw AssertionError("Unknown rotation $rotation")
    }

    private fun inject(events: List<Int>) = send("""{"id":1,"command":"inject","events":[${(events + listOf(EV_SYN, SYN_REPORT, 0)).joinToString(",")}]}""")

    private fun send(command: String) {
        commands.write("$command\n".toByteArray())
        commands.flush()
    }

    private companion object {
        const val SLOTS = 10
        // linux/uinput.h, linux/input.h and linux/input-event-codes.h
        const val UI_SET_EVBIT = 100
        const val UI_SET_KEYBIT = 101
        const val UI_SET_ABSBIT = 103
        const val UI_SET_PROPBIT = 110
        const val INPUT_PROP_DIRECT = 1
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val SYN_REPORT = 0
        const val BTN_TOUCH = 330
        const val ABS_X = 0
        const val ABS_Y = 1
        const val ABS_MT_SLOT = 47
        const val ABS_MT_POSITION_X = 53
        const val ABS_MT_POSITION_Y = 54
        const val ABS_MT_TRACKING_ID = 57
    }
}
