package com.kimjisub.launchpad

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File

/** Fixture preparation uses the normal target classpath, before the independent driver starts. */
class ProcessRecoverySetup : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        try {
            val source = TestUniPack.install(targetContext, title = "Process Recovery Silent Pack")
            val target = File(source.parentFile, "process-recovery-pack")
            check(target.deleteRecursively())
            check(source.renameTo(target))
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "\nPROCESS_RECOVERY_PREPARED\n") })
        } catch (error: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "\n${android.util.Log.getStackTraceString(error)}") })
        }
    }
}
