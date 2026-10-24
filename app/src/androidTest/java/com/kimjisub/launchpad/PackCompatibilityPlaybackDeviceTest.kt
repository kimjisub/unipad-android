package com.kimjisub.launchpad

import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import com.kimjisub.launchpad.basefeatures.RecordingAudio
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PackCompatibilityPlaybackDeviceTest {
	@Test fun normalizedSoundPathAndInvalidColor_keepTheIntendedPlayback() {
		val screen = FeatureScreen()
		val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"
		val root = File(screen.context.cacheDir, "pack-playback-${System.nanoTime()}").apply { mkdirs() }
		val audio = RecordingAudio()
		val doubles = module { single<SoundRunner.Engine> { audio } }
		loadKoinModules(doubles)
		try {
			File(root, "info").writeText("title=Pack compatibility\nproducerName=UniPad test\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
			File(root, "keySound").writeText("1 1 1 ./fx\\\ud55c.wav -2147483648\n")
			File(root, "sounds/fx").mkdirs()
			val size = 1600
			File(root, "sounds/fx/\u1112\u1161\u11ab.wav").writeBytes(ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
				put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray())
				putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
				put("data".toByteArray()); putInt(size)
			}.array())
			File(root, "keyLED").mkdir()
			File(root, "keyLED/1 1 1").writeText("o 1 1 00ff00\no 1 1 +ff000\nc -2147483648\nd 20000\nf 1 1\n")
			screen.launch(PlayActivity::class.java) { putExtra("path", root.path) }
			screen.await("Pack did not load") { screen.ready() }
			if (screen.onMain { screen.vm().unipack.errorDetail != null }) {
				screen.node(By.text(screen.text(R.string.accept)))
				screen.capture("pack-compatibility-$label-warning")
				screen.clickText(R.string.accept)
				assertTrue(screen.device.wait(Until.gone(By.text(screen.text(R.string.accept))), 5000))
			}
			screen.await("Pad grid did not lay out") { screen.padBounds().let { it.size == 64 && it.all { pad -> pad.width() > 0 && pad.height() > 0 } } }
			screen.tapPad(0)
			screen.await("Light did not arrive") { screen.onMain { screen.vm().channelManager.get(0, 0)?.color != null } }
			val color = screen.onMain {
				val pad = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>().first()
				(pad.findViewById<android.view.View>(com.kimjisub.design.R.id.led).background as ColorDrawable).color
			}
			screen.capture("pack-compatibility-$label-light")
			Log.i("PackCompatibility", "plays=${audio.plays.size} loops=${audio.loops} color=${Integer.toHexString(color)}")
			assertEquals("Malformed color must not replace the valid green light", 0xff00ff00.toInt(), color)
			assertEquals("Normalized sound reference must play once", 1, audio.plays.size)
			assertEquals(listOf(Int.MIN_VALUE), audio.loops)
		} finally {
			screen.close()
			unloadKoinModules(doubles)
			root.deleteRecursively()
		}
	}
}
