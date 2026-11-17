package com.kimjisub.launchpad

import com.kimjisub.launchpad.tool.AudioTrackDurationReader
import com.kimjisub.launchpad.tool.SoundDurationReader
import com.kimjisub.launchpad.tool.UniPackAutoMapper
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Controls the first real duration read on a live screen, so lifecycle and progress screenshots
 * can be checked without relying on how long the platform's metadata API takes. */
internal class HeldFirstDurationRead(private val vm: PlayActivityViewModel) : AutoCloseable {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val reads = AtomicInteger()
    private val factory = vm.javaClass.getDeclaredField("autoMapper").apply { isAccessible = true }
    private val original = factory.get(vm)

    init {
        val gated: (UniPackFolder) -> UniPackAutoMapper = { pack ->
            UniPackAutoMapper(pack, {
                object : SoundDurationReader {
                    private val reader = AudioTrackDurationReader()
                    override fun durationMs(file: File): Double? {
                        if (reads.incrementAndGet() == 1) {
                            started.countDown()
                            check(release.await(30, TimeUnit.SECONDS)) { "First duration read was not released" }
                        }
                        return reader.durationMs(file)
                    }
                    override fun close() = reader.close()
                }
            })
        }
        factory.set(vm, gated)
    }

    override fun close() {
        release.countDown()
        factory.set(vm, original)
    }
}
