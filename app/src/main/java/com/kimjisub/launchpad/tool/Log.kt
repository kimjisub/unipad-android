package com.kimjisub.launchpad.tool

import android.util.Log
import com.kimjisub.launchpad.BuildConfig

object Log {
	private val DEBUG = BuildConfig.DEBUG
	private const val TAG_PAD_WIDTH = 30
	private const val MSG_PAD_WIDTH = 50
	private const val TRACE_PAD_WIDTH = 40
	fun log(msg: String) { if (DEBUG) printLog("com.kimjisub._log", msg) }
	fun fbmsg(msg: String) { if (DEBUG) printLog("com.kimjisub._fbmsg", msg) }
	fun test(msg: String) { if (DEBUG) printLog("com.kimjisub._test", msg) }
	fun play(msg: String) { if (DEBUG) printLog("com.kimjisub._play", msg) }
	fun download(msg: String) { if (DEBUG) printLog("com.kimjisub._download", msg) }
	fun network(msg: String) { if (DEBUG) printLog("com.kimjisub._network", msg) }
	fun activity(msg: String) { if (DEBUG) printLog("com.kimjisub._activity", msg) }
	fun midi(msg: String) { if (DEBUG) printLog("com.kimjisub._midi", msg) }
	fun thread(msg: String) { if (DEBUG) printLog("com.kimjisub._thread", msg) }
	fun midiDetail(msg: String) { if (DEBUG) printLog("com.kimjisub._mididetail", msg) }
	fun driverCycle(msg: String) { if (DEBUG) printLog("com.kimjisub._cycle", msg) }
	fun err(msg: String) = printLog("com.kimjisub._err", msg, isError = true)
	fun err(msg: String, throwable: Throwable) = printLog("com.kimjisub._err", msg, throwable)

	private fun printLog(tag: String, msg: String?, throwable: Throwable? = null, isError: Boolean = false) {
		val message: String = msg ?: "(null)"
		val logMsg = if (DEBUG) {
			val space = " ".repeat((TAG_PAD_WIDTH - tag.length).coerceAtLeast(0))
			val stackTrace = Thread.currentThread().stackTrace
			val trace1 = trace(stackTrace, 5)
			val trace2 = trace(stackTrace, 4)
			String.format("%s%-${MSG_PAD_WIDTH}s  %-${TRACE_PAD_WIDTH}s  %-${TRACE_PAD_WIDTH}s", space, message, trace1, trace2)
		} else message
		if (throwable != null) Log.e(tag, logMsg, throwable)
		else if (isError) Log.e(tag, logMsg)
		else Log.d(tag, logMsg)
	}

	fun trace(e: Array<StackTraceElement>?, level: Int): String? {
		if (e != null && e.size >= level)
			return "${e[level].methodName}(${e[level].fileName}:${e[level].lineNumber})"
		return null
	}
}