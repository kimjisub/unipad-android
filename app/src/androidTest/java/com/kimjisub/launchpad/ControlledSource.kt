package com.kimjisub.launchpad

import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.manager.AppUpdateSource
import com.kimjisub.launchpad.manager.UpdateStatus

/** A store whose answer and pushed download progress the test decides; it never installs. */
internal class ControlledSource : AppUpdateSource {
    var answer: UpdateStatus = UpdateStatus.None
    var listener: ((UpdateStatus) -> Unit)? = null
    var error: Exception? = null
    override suspend fun status(): UpdateStatus {
        error?.let { throw it }
        return answer
    }
    override fun startDownload() = false
    override suspend fun install() = error("must not install automatically")
    override fun observe(onChange: (UpdateStatus) -> Unit) { listener = onChange }
    override fun close() { listener = null }

    /** Pushes [status] as progress, as the store does while the app is open. */
    fun push(status: UpdateStatus) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { listener!!(status) }
    }

    /** Pushes [status] and answers it to later lookups too. */
    fun emit(status: UpdateStatus) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            answer = status
            listener!!(status)
        }
    }
}
