package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.tool.AutoPlayFileReplacer
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Can also verify the same files in a fresh app process with -e verify-persisted true. */
@RunWith(AndroidJUnit4::class)
class AutoPlayRestartDeviceTest {
    @Test
    fun persistedSaveAndBackupCanBeLoadedAndSwapped() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.filesDir, "autoplay-restart-test")
        val current = File(root, "autoPlay")
        val original = "on 1 1\nd 37\n"
        val mapped = "d 1000\nt 1 1\n"
        if (InstrumentationRegistry.getArguments().getString("verify-persisted") != "true") {
            root.deleteRecursively()
            assertTrue(root.mkdirs())
            File(root, "keySound").writeText("")
            File(root, "info").writeText("title=Restart test\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=1\n")
            current.writeText(original)
            AutoPlayFileReplacer().replace(current, mapped)
        }
        val files = AutoPlayFileReplacer()
        files.recover(current)
        assertEquals(mapped, current.readText())
        assertTrue(files.hasBackup(current))
        val pack = UniPackFolder(root).load().loadDetail()
        assertTrue("Restart fixture must be a valid pack", !pack.criticalError)
        assertEquals(1000, (pack.autoPlayTable!!.elements.first() as AutoPlay.Element.Delay).delay)
        files.restore(current)
        assertEquals(original, current.readText())
        files.restore(current)
        assertEquals(mapped, current.readText())
        assertEquals(original, File(root, "autoPlay_previous").readText())
    }
}
