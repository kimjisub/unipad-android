package com.kimjisub.launchpad.tool

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.json.JSONObject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors

/** Debug-only, opt-in observation across cold process starts; never included in release builds. */
class ProcessRecoveryProbe : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        val writer = Executors.newSingleThreadExecutor { task ->
            Thread(task, "process-recovery-observer").apply { isDaemon = true }
        }
        val output = File(app.filesDir, "process-recovery-events.jsonl")
        fun record(event: String, values: Map<String, Any> = emptyMap()) {
            writer.execute {
                val row = JSONObject(values).put("event", event).put("pid", Process.myPid())
                output.appendText("$row\n")
            }
        }
        val engine = object : SoundRunner.Engine by SoundRunner.OboeEngine {
            override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
                val voice = SoundRunner.OboeEngine.play(soundId, volumeL, volumeR, loop)
                record("play", mapOf("soundId" to soundId, "voice" to voice))
                return voice
            }
        }
        val handler = Handler(Looper.getMainLooper())
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private val savedStates = java.util.WeakHashMap<Activity, Boolean>()
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is PlayActivity) {
                    // Application.onCreate has now installed the normal services. Only audio observation
                    // is added: all engine calls still reach the real decoder and native engine.
                    loadKoinModules(module { single<SoundRunner.Engine> { engine } })
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                savedStates[activity] = savedInstanceState != null
            }
            override fun onActivityResumed(activity: Activity) {
                if (activity !is PlayActivity) return
                fun inspect() {
                    if (activity.isDestroyed || activity.isFinishing) return
                    val vm = ViewModelProvider(activity)[PlayActivityViewModel::class.java]
                    val pad = pads(activity.window.decorView).firstOrNull { it.width > 0 && it.height > 0 }
                    if (!activity.hasWindowFocus() || !vm.uiLoaded || vm.soundLoadingActive || vm.unipackLoading || pad == null) {
                        handler.postDelayed({ inspect() }, 50)
                        return
                    }
                    val position = IntArray(2)
                    pad.getLocationOnScreen(position)
                    record("ready", mapOf(
                        "title" to vm.unipack.title,
                        "taskId" to activity.taskId,
                        "path" to (activity.intent.getStringExtra("path") ?: ""),
                        "savedState" to (savedStates[activity] == true),
                        "x" to position[0] + pad.width / 2,
                        "y" to position[1] + pad.height / 2,
                    ))
                }
                handler.postDelayed({ inspect() }, 100)
            }
            override fun onActivityStopped(activity: Activity) {
                if (activity is PlayActivity) record("stopped")
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) { savedStates.remove(activity) }
        })
        return true
    }

    private fun pads(view: View): Sequence<PadView> = sequence {
        if (view is PadView) yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(pads(view.getChildAt(i)))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
