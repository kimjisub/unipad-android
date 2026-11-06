package com.kimjisub.launchpad.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TombstoneReaderTest {
	@Test fun crashingThreadFramesAndSignalAreRead() {
		val trace = summarize(tombstone())
		assertEquals(NativeCrashTrace.FRAMES, trace.status)
		assertEquals("SIGSEGV(11) SEGV_MAPERR(1)", trace.signal)
		assertEquals(
			"#00 libhwui.so 0x2b4f10 (android::uirenderer::RenderNode::prepareTreeImpl+120)" +
				" | #01 base.apk!libunipad.so 0x1c40 (Java_com_kimjisub_launchpad_Native_play+16)" +
				" | #02 [anon:dalvik-jit-code-cache] 0x88 (com.kimjisub.launchpad.Pad.press+4)" +
				" | #03 ? 0x10" +
				" | #04 linker64 0x5000",
			trace.frames,
		)
		assertEquals("libhwui.so:9f3c2a1b7d5e4c60aa11 base.apk!libunipad.so:00ff00ff00ff00ff", trace.buildIds)
	}

	@Test fun userDataFieldsAndOtherThreadsAreNotInTheResult() {
		val values = summarize(tombstone()).let { listOf(it.status, it.signal, it.frames, it.buildIds) }
		for (secret in listOf("secret", "other_thread_function", "my song", "0xdeadbeef", "Unipad")) {
			assertFalse(secret, values.any { it.contains(secret) })
		}
	}

	@Test fun crashingThreadIsFoundAfterOtherThreadsAndBeforeTheMaps() {
		val bytes = protoMessage {
			varint(6, 30)
			bytes(16, protoMessage { varint(1, 29); bytes(2, thread(listOf(frame("/system/lib64/libc.so", "other_thread_function")))) })
			bytes(16, protoMessage { varint(1, 30); bytes(2, thread(listOf(frame("/system/lib64/libc.so", "abort")))) })
			bytes(10, protoMessage { varint(1, 6); string(2, "SIGABRT"); varint(3, -6); string(4, "SI_TKILL") })
			bytes(17, ByteArray(10)) // A broken memory map after the needed fields is never read.
		}
		val trace = summarize(bytes)
		assertEquals("#00 libc.so 0x10 (abort+0)", trace.frames)
		assertEquals("SIGABRT(6) SI_TKILL(-6)", trace.signal)
	}

	@Test fun recordWithoutTheCrashingThreadKeepsOnlyTheSignal() {
		val trace = summarize(protoMessage {
			varint(6, 30)
			bytes(10, protoMessage { varint(1, 11) })
			bytes(16, protoMessage { varint(1, 29); bytes(2, thread(listOf(frame("/system/lib64/libc.so", "other_thread_function")))) })
		})
		assertEquals(NativeCrashTrace.NO_FRAMES, trace.status)
		assertEquals("?(11) ?(0)", trace.signal)
		assertEquals("", trace.frames)
	}

	@Test fun emptyRecordHasNoFrames() {
		assertEquals(NativeCrashTrace(NativeCrashTrace.NO_FRAMES), summarize(ByteArray(0)))
	}

	@Test fun missingRecordIsNone() {
		assertEquals(NativeCrashTrace(NativeCrashTrace.NONE), TombstoneReader.summarize(null))
	}

	@Test fun truncatedRecordGivesNoNames() {
		val bytes = tombstone()
		val text = String(bytes, Charsets.ISO_8859_1)
		for (cut in listOf(1, 3, bytes.size / 2, text.indexOf("libhwui") + 3, text.indexOf("linker64"))) {
			assertEquals("cut at $cut", NativeCrashTrace(NativeCrashTrace.UNREADABLE), summarize(bytes.copyOf(cut)))
		}
	}

	@Test fun corruptRecordGivesNoNames() {
		assertEquals(NativeCrashTrace(NativeCrashTrace.UNREADABLE), summarize(byteArrayOf(0x0b, 0x01)))
		assertEquals(NativeCrashTrace(NativeCrashTrace.UNREADABLE), summarize(ByteArray(12) { 0xff.toByte() }))
		// A field that runs past the end of the thread it belongs to.
		val overrun = protoMessage { varint(6, 1); bytes(16, byteArrayOf(0x08, 0x01, 0x12, 0x05, 0x22, 0x10)) }
		assertEquals(NativeCrashTrace(NativeCrashTrace.UNREADABLE), summarize(overrun + ByteArray(20)))
	}

	@Test fun oversizedRecordGivesNoNames() {
		val longAbort = protoMessage { string(14, "a".repeat(5 shl 20)) }
		assertEquals(NativeCrashTrace(NativeCrashTrace.TOO_LARGE), summarize(longAbort + tombstone()))
		val claimsHuge = protoMessage { varint(6, 1) } + byteArrayOf(0x82.toByte(), 0x01) + protoVarint(1L shl 40)
		assertEquals(NativeCrashTrace(NativeCrashTrace.TOO_LARGE), summarize(claimsHuge))
	}

	@Test fun framesStopAtTwelveAndValuesStayWithinTheKeyLimit() {
		val longName = "std::__1::function<void (android::uirenderer::RenderNode*)>::operator()" + "x".repeat(400)
		val frames = (0 until 20).map { frame("/system/lib64/lib$it.so", longName, buildId = "%040x".format(it)) }
		val trace = summarize(protoMessage { varint(6, 1); bytes(16, protoMessage { varint(1, 1); bytes(2, thread(frames)) }) })
		val lines = trace.frames.split(" | ")
		assertTrue(trace.frames.length <= TombstoneReader.MAX_VALUE)
		assertTrue(trace.buildIds.length <= TombstoneReader.MAX_VALUE)
		assertTrue(lines.size in 2..TombstoneReader.MAX_FRAMES)
		assertTrue(lines.first(), lines.first().startsWith("#00 lib0.so 0x10 (std::__1::function"))
		assertTrue(lines.first(), lines.first().endsWith("xxx...+0)"))

		val short = (0 until 20).map { frame("/system/lib64/libc.so", "f$it") }
		val shortTrace = summarize(protoMessage { varint(6, 1); bytes(16, protoMessage { varint(1, 1); bytes(2, thread(short)) }) })
		assertEquals(TombstoneReader.MAX_FRAMES, shortTrace.frames.split(" | ").size)
		assertTrue(shortTrace.frames.endsWith("#11 libc.so 0x10 (f11+0)"))
	}

	@Test fun controlCharactersInNamesAreReplaced() {
		val trace = summarize(protoMessage {
			varint(6, 1)
			bytes(16, protoMessage { varint(1, 1); bytes(2, thread(listOf(frame("/system/lib64/libc.so", "a\nb\u0000c")))) })
		})
		assertEquals("#00 libc.so 0x10 (a?b?c+0)", trace.frames)
	}

	private fun summarize(bytes: ByteArray) = TombstoneReader.summarize(ByteArrayInputStream(bytes))

	/** A tombstone with every user-data field AOSP writes, in the order debuggerd writes them. */
	private fun tombstone() = protoMessage {
		varint(1, 1)
		string(2, "google/sdk_gphone64_arm64/emu64a:15/secret-fingerprint")
		varint(5, 4321)
		varint(6, 4400)
		string(8, "u:r:untrusted_app:s0:secret")
		string(9, "com.kimjisub.launchpad")
		bytes(10, protoMessage {
			varint(1, 11); string(2, "SIGSEGV"); varint(3, 1); string(4, "SEGV_MAPERR")
			varint(8, 1); varint(9, 0xdeadbeef)
			bytes(10, protoMessage { string(2, "/storage/emulated/0/Unipad/secret pack.zip"); bytes(4, "secret memory".toByteArray()) })
		})
		string(14, "Abort message: secret abort")
		bytes(15, protoMessage { string(1, "secret cause") })
		bytes(16, protoMessage {
			varint(1, 4401)
			bytes(2, thread(listOf(frame("/storage/emulated/0/Unipad/my song.mp3", "other_thread_function"))))
		})
		bytes(16, protoMessage {
			varint(1, 4400)
			bytes(2, thread(listOf(
				frame("/system/lib64/libhwui.so", "android::uirenderer::RenderNode::prepareTreeImpl", 0x2b4f10, 120,
					"9f3c2a1b7d5e4c60aa11"),
				frame("/data/app/~~abc/com.kimjisub.launchpad-xyz/base.apk!libunipad.so",
					"Java_com_kimjisub_launchpad_Native_play", 0x1c40, 16, "00ff00ff00ff00ff"),
				frame("[anon:dalvik-jit-code-cache]", "com.kimjisub.launchpad.Pad.press", 0x88, 4, "not hex secret"),
				frame("/storage/emulated/0/Unipad/my song.mp3", null, 0x10),
				frame("/system/bin/linker64", "", 0x5000),
			), name = "RenderThread secret"))
		})
		bytes(17, protoMessage { string(7, "/storage/emulated/0/Unipad/secret pack.zip"); string(8, "abcdef0123") })
		bytes(18, protoMessage { string(1, "main"); bytes(2, protoMessage { string(5, "Unipad"); string(6, "secret log") }) })
		bytes(19, protoMessage { varint(1, 42); string(2, "/storage/emulated/0/Unipad/secret pack.zip") })
	}

	private fun thread(frames: List<ByteArray>, name: String = "main") = protoMessage {
		varint(1, 1)
		string(2, name)
		bytes(3, protoMessage { string(1, "x0"); varint(2, 0x5ec2e7) })
		for (frame in frames) bytes(4, frame)
		bytes(5, protoMessage { string(1, "x0"); bytes(4, "secret memory".toByteArray()) })
		string(7, "secret note")
	}

	private fun frame(file: String, function: String?, relPc: Long = 0x10, offset: Long = 0, buildId: String? = null) =
		protoMessage {
			varint(1, relPc)
			varint(2, relPc + 0x7000000000)
			varint(3, 0x7fff0000)
			function?.let { string(4, it) }
			varint(5, offset)
			string(6, file)
			varint(7, 0)
			buildId?.let { string(8, it) }
		}
}
