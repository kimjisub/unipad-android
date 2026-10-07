package com.kimjisub.launchpad.basefeatures

import android.util.Log
import android.view.MotionEvent
import android.view.Window
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The touch and mouse events the play window was handed, as the app received them: action, device,
 * source and each pointer's ID and tool type, without moves. It shows whether the system cancelled
 * a held gesture and that a mouse and a finger arrived as two kinds of pointer. Each event is also
 * logged under [TAG].
 */
class ReceivedInput(private val screen: FeatureScreen) {
    data class Pointer(val id: Int, val toolType: Int) {
        override fun toString() = "id $id " + when (toolType) {
            MotionEvent.TOOL_TYPE_FINGER -> "TOOL_TYPE_FINGER"
            MotionEvent.TOOL_TYPE_MOUSE -> "TOOL_TYPE_MOUSE"
            else -> "tool type $toolType"
        }
    }

    /** [actor] is the pointer the action is about, among all [pointers] down at the time. */
    data class Event(val action: Int, val actor: Pointer, val deviceId: Int, val source: Int, val pointers: List<Pointer>) {
        override fun toString() =
            "${MotionEvent.actionToString(action)} ($actor) device $deviceId source 0x${Integer.toHexString(source)} $pointers"
    }

    val events = CopyOnWriteArrayList<Event>()
    private val window: Window = screen.onMain { screen.resumed().window }
    private val original: Window.Callback = window.callback

    init {
        val recording = Proxy.newProxyInstance(Window.Callback::class.java.classLoader, arrayOf(Window.Callback::class.java)) { _, method, args ->
            if (method.name == "dispatchTouchEvent" || method.name == "dispatchGenericMotionEvent") record(args!![0] as MotionEvent)
            try {
                method.invoke(original, *args.orEmpty())
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as Window.Callback
        screen.onMain { window.callback = recording }
    }

    fun detach() = screen.onMain { window.callback = original }

    fun cancelled() = events.any { it.action == MotionEvent.ACTION_CANCEL }

    override fun toString() = events.joinToString("\n", prefix = "Received input:\n")

    private fun record(event: MotionEvent) {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) return
        val pointers = (0 until event.pointerCount).map { Pointer(event.getPointerId(it), event.getToolType(it)) }
        Event(action, pointers[event.actionIndex], event.deviceId, event.source, pointers).also {
            events += it
            Log.i(TAG, it.toString())
        }
    }

    companion object {
        const val TAG = "ReceivedInput"
    }
}
