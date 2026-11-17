package com.kimjisub.launchpad

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

/** Runs in the test APK's own process: killing the app cannot kill this driver. */
class ProcessRecoveryDriver : Instrumentation() {
    private lateinit var device: UiDevice
    private val pkg = "com.kimjisub.launchpad.dev"
    private val folder = "/storage/emulated/0/Android/data/$pkg/files/UniPack/process-recovery-pack"
    private val probe = "$pkg/com.kimjisub.launchpad.tool.ProcessRecoveryProbe"
    private val events = "files/process-recovery-events.jsonl"
    private val evidence = "/data/local/tmp/unipad_tests/process-recovery"

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    private fun quote(text: String) = "'" + text.replace("'", "'\"'\"'") + "'"
    private fun shell(command: String): String = uiAutomation.executeShellCommand("sh -c ${quote(command)}").use { fd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
    }

    private fun app(command: String) = shell("run-as $pkg sh -c ${quote(command)}")
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 30000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < end) { message }
            SystemClock.sleep(100)
        }
    }

    private fun records(): List<JSONObject> = app("cat $events").lineSequence().mapNotNull {
        runCatching { JSONObject(it) }.getOrNull()
    }.toList()

    private fun ready(pid: String) = records().lastOrNull {
        it.optString("event") == "ready" && it.optString("pid") == pid
    }

    private fun capture(name: String) {
        val staging = requireNotNull(context.getExternalFilesDir(null))
        listOfNotNull(staging.parentFile, staging).forEach { check(it.setExecutable(true, false)) }
        for (extension in listOf("png", "xml")) {
            val file = File(staging, "$name.$extension")
            try {
                if (extension == "png") check(device.takeScreenshot(file)) { "Screenshot failed: $name" }
                else device.dumpWindowHierarchy(file)
                check(file.setReadable(true, false))
                shell("cp ${file.path} $evidence/$name.$extension")
                check(shell("stat -c %s $evidence/$name.$extension").toLongOrNull() == file.length())
            } finally { file.delete() }
        }
    }

    private fun pressPad(pid: String) {
        val state = checkNotNull(ready(pid)) { "No loaded play screen for $pid" }
        check(state.getString("title") == "Process Recovery Silent Pack") { "Wrong pack restored: $state" }
        check(state.getString("path") == folder) { "Wrong pack path restored: $state" }
        val before = records().count { it.optString("event") == "play" && it.optString("pid") == pid }
        check(device.click(state.getInt("x"), state.getInt("y")))
        await("Pad did not request sound in process $pid") {
            records().count { it.optString("event") == "play" && it.optString("pid") == pid } == before + 1
        }
    }

    override fun onStart() {
        device = UiDevice.getInstance(this)
        var outcome = "PROCESS_RECOVERY_FAILED"
        var detail = ""
        try {
            check(android.os.Build.VERSION.SDK_INT >= 33) { "API 33+ required" }
            check(shell("settings get global airplane_mode_on") == "1") { "Offline device required" }
            shell("rm -rf $evidence; mkdir -p $evidence")
            shell("am force-stop $pkg")
            // Only the owned fixture is replaced. No app data or user packs are cleared.
            app("mkdir -p files; rm -f $events; mkdir -p $folder/sounds")
            check(shell("pm enable $probe").contains("enabled")) { "Could not enable the debug observation component" }
            val fixture = TestUniPack.install(context, title = "Process Recovery Silent Pack")
            fixture.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(fixture).invariantSeparatorsPath
                val target = "$folder/$relative"
                val bytes = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                app("mkdir -p ${quote(target.substringBeforeLast('/'))}; echo $bytes | base64 -d > ${quote(target)}")
            }
            shell("logcat -c")
            context.packageManager.getLaunchIntentForPackage(pkg)!!.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
            var row: androidx.test.uiautomator.UiObject2? = null
            await("Fixture not listed") { device.findObject(By.res("main_pack_process-recovery-pack")).also { row = it } != null }
            row!!.click()
            var play: androidx.test.uiautomator.UiObject2? = null
            await("Play control missing") { device.findObject(By.text("Play")).also { play = it } != null }
            play!!.click()
            val oldPid = shell("pidof $pkg")
            check(oldPid.matches(Regex("[0-9]+"))) { "No single app process: $oldPid" }
            check(oldPid != android.os.Process.myPid().toString()) { "Driver must run outside the app process" }
            await("Pack never finished loading") { ready(oldPid) != null }
            pressPad(oldPid)
            capture("before-home")
            check(device.pressHome())
            await("Play screen never stopped behind Home") {
                records().any { it.optString("event") == "stopped" && it.optString("pid") == oldPid }
            }
            val stoppedPlay = Regex("(?s)ActivityRecord\\{[^\\n]*${Regex.escape(pkg)}/[^\\n}]*\\.PlayActivity[^\\n]*\\n(?:(?!ActivityRecord\\{).)*?mState=STOPPED")
            await("System has not acknowledged the stopped play activity") {
                stoppedPlay.containsMatchIn(shell("dumpsys activity activities"))
            }
            shell("am kill $pkg")
            await("am kill did not end the background app process") { shell("pidof $pkg").isEmpty() }
            check(device.pressRecentApps())
            var card: androidx.test.uiautomator.UiObject2? = null
            await("UniPad recent task missing") {
                device.findObjects(By.res(Pattern.compile(".*:id/snapshot"))).firstOrNull().also { card = it } != null
            }
            capture("recent-apps")
            card!!.click()
            var newPid = ""
            await("Returning from Recents did not start a fresh app process") {
                newPid = shell("pidof $pkg")
                newPid.matches(Regex("[0-9]+")) && newPid != oldPid
            }
            await("Restored play screen never finished loading") { ready(newPid) != null }
            val restored = checkNotNull(ready(newPid))
            check(restored.getInt("taskId") == checkNotNull(ready(oldPid)).getInt("taskId")) { "Returned to another task" }
            check(restored.getBoolean("savedState")) { "Play screen was freshly launched, not restored: $restored" }
            pressPad(newPid)
            capture("after-return")
            // Observe another main-loop turn and system error reports after input.
            device.waitForIdle(1000)
            check(shell("pidof $pkg") == newPid) { "Restored process exited after pad input" }
            val errors = shell("dumpsys activity exit-info $pkg")
            shell("logcat -d -b crash -b events -v threadtime > $evidence/errors.txt")
            val logs = shell("cat $evidence/errors.txt")
            check(!Regex("am_crash.*${Regex.escape(pkg)}|am_anr.*${Regex.escape(pkg)}|FATAL EXCEPTION|Fatal signal").containsMatchIn(logs)) {
                "Crash or ANR after process recovery: $logs"
            }
            check(!Regex("reason=([456])\\b").containsMatchIn(errors)) { "System reported crash/native crash/ANR: $errors" }
            detail = "old_pid=$oldPid new_pid=$newPid saved_state=true same_pack=true sound_request=true"
            outcome = "PROCESS_RECOVERY_PASSED"
        } catch (error: Throwable) {
            detail = android.util.Log.getStackTraceString(error)
            runCatching { capture("failure") }
        } finally {
            runCatching {
                shell("logcat -d -v threadtime > $evidence/logcat.txt")
                shell("dumpsys activity activities > $evidence/activities.txt")
                shell("dumpsys activity exit-info $pkg > $evidence/exit-info.txt")
                shell("run-as $pkg cat $events > $evidence/events.jsonl")
            }
            app("rm -f $events; rm -rf $folder")
            shell("pm disable $probe")
            shell("am force-stop $pkg")
            TestUniPack.remove(context)
        }
        finish(
            if (outcome == "PROCESS_RECOVERY_PASSED") android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", "\n$outcome\n$detail\n") },
        )
    }
}
