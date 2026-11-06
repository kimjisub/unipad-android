package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.diagnostics.NativeCrashTrace
import com.kimjisub.launchpad.diagnostics.TombstoneReader
import com.kimjisub.launchpad.diagnostics.protoMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * The tombstone reader keeps names by regular expressions, which Android compiles with its own engine, so
 * a crash record is read on the device as well as in the JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class TombstoneReaderDeviceTest {
    @Test fun crashingThreadFramesAreReadOnTheDevice() {
        val frames = listOf(
            frame("/system/lib64/libhwui.so", "android::uirenderer::RenderNode::prepareTree", "9f3c2a1b7d5e4c60"),
            frame("[anon_shmem:dalvik-jit-code-cache]", "android.os.Looper.loopOnce", null),
            frame("/system/bin/linker64", "__dl__start", null),
            frame("/storage/emulated/0/Unipad/my pack.zip", "f", null),
        )
        val tombstone = protoMessage {
            varint(6, 7)
            bytes(10, protoMessage { varint(1, 11); string(2, "SIGSEGV"); varint(3, 1); string(4, "SEGV_MAPERR") })
            bytes(16, protoMessage {
                varint(1, 7)
                bytes(2, protoMessage { frames.forEach { bytes(4, it) } })
            })
        }
        val trace = TombstoneReader.summarize(ByteArrayInputStream(tombstone))
        assertEquals(NativeCrashTrace.FRAMES, trace.status)
        assertEquals("SIGSEGV(11) SEGV_MAPERR(1)", trace.signal)
        assertEquals("#00 libhwui.so 0x10 (android::uirenderer::RenderNode::prepareTree+0) | " +
            "#01 [anon_shmem:dalvik-jit-code-cache] 0x10 (android.os.Looper.loopOnce+0) | " +
            "#02 linker64 0x10 (__dl__start+0) | #03 ? 0x10 (f+0)", trace.frames)
        assertEquals("libhwui.so:9f3c2a1b7d5e4c60", trace.buildIds)
    }

    private fun frame(file: String, function: String, buildId: String?) = protoMessage {
        varint(1, 0x10)
        string(4, function)
        string(6, file)
        buildId?.let { string(8, it) }
    }
}
