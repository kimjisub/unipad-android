package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.diagnostics.NativeCrashTrace
import com.kimjisub.launchpad.diagnostics.TombstoneReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
        val tombstone = number(6, 7) +
            field(10, number(1, 11) + field(2, "SIGSEGV".toByteArray()) + number(3, 1) +
                field(4, "SEGV_MAPERR".toByteArray())) +
            field(16, number(1, 7) + field(2, frames.fold(ByteArray(0)) { all, frame -> all + field(4, frame) }))
        val trace = TombstoneReader.summarize(ByteArrayInputStream(tombstone))
        assertEquals(NativeCrashTrace.FRAMES, trace.status)
        assertEquals("SIGSEGV(11) SEGV_MAPERR(1)", trace.signal)
        assertEquals("#00 libhwui.so 0x10 (android::uirenderer::RenderNode::prepareTree+0) | " +
            "#01 [anon_shmem:dalvik-jit-code-cache] 0x10 (android.os.Looper.loopOnce+0) | " +
            "#02 linker64 0x10 (__dl__start+0) | #03 ? 0x10 (f+0)", trace.frames)
        assertEquals("libhwui.so:9f3c2a1b7d5e4c60", trace.buildIds)
    }

    private fun frame(file: String, function: String, buildId: String?) =
        number(1, 0x10) + field(4, function.toByteArray()) + field(6, file.toByteArray()) +
            (buildId?.let { field(8, it.toByteArray()) } ?: ByteArray(0))

    private fun number(field: Int, value: Long) = encode((field shl 3).toLong()) + encode(value)

    private fun field(number: Int, value: ByteArray) =
        encode((number shl 3 or 2).toLong()) + encode(value.size.toLong()) + value

    private fun encode(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var rest = value
        while ((rest and 0x7fL.inv()) != 0L) {
            out.write(((rest and 0x7fL) or 0x80L).toInt())
            rest = rest ushr 7
        }
        out.write(rest.toInt())
        return out.toByteArray()
    }
}
