package com.kimjisub.manager

import android.util.Log

object Log {
	private const val DEBUG = true
	fun log(msg: String) = printLog("com.kimjisub._log", msg)
	fun fbmsg(msg: String) = printLog("com.kimjisub._fbmsg", msg)
	fun test(msg: String) = printLog("com.kimjisub._test", msg)
	fun download(msg: String) = printLog("com.kimjisub._download", msg)
	fun network(msg: String) = printLog("com.kimjisub._network", msg)
	fun activity(msg: String) = printLog("com.kimjisub._activity", msg)
	fun firebase(msg: String) = printLog("com.kimjisub._firebase", msg)
	fun admob(msg: String) = printLog("com.kimjisub._admob", msg)
	fun midi(msg: String) = printLog("com.kimjisub._midi", msg)
	fun thread(msg: String) = printLog("com.kimjisub._thread", msg)
	fun midiDetail(msg: String) = printLog("com.kimjisub._mididetail", msg)
	fun driver(msg: String) = printLog("com.kimjisub._driver", msg)
	fun driverCycle(msg: String) = printLog("com.kimjisub._cycle", msg)
	fun err(msg: String) = printLog("com.kimjisub._err", msg)

	private fun printLog(tag: String, msg: String?) {
		val msg: String = msg ?: "(null)"
		var space = ""
		for (i in 0 until 30 - tag.length) space += " "
		val trace1 = trace(Thread.currentThread().stackTrace, 5)
		val trace2 = trace(Thread.currentThread().stackTrace, 4)
		val detailMsg = String.format("%s%-50s  %-40s  %-40s", space, msg, trace1, trace2)
		Log.e(tag, if (DEBUG) detailMsg else msg)
	}

	fun trace(e: Array<StackTraceElement>?, level: Int): String? {
		if (e != null && e.size >= level)
			return "${e[level].methodName}(${e[level].fileName}:${e[level].lineNumber})"
		return null
	}
}