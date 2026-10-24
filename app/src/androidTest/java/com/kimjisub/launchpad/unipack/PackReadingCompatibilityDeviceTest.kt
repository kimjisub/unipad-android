package com.kimjisub.launchpad.unipack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PackArchive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class PackReadingCompatibilityDeviceTest {
	private val context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test fun differentlyCasedSoundFolders_areBothReadAfterExtraction() {
		val root = File(context.cacheDir, "pack-reading-${System.nanoTime()}").apply { mkdirs() }
		try {
			val zip = File(root, "pack.zip")
			ZipOutputStream(zip.outputStream()).use { output ->
				for ((name, text) in linkedMapOf(
					"Pack/info" to "title=Folders\nproducerName=Tester\nbuttonX=1\nbuttonY=2\nchain=1\n",
					"Pack/keySound" to "1 1 1 a.wav\n1 1 2 b.wav\n",
					"Pack/Sounds/a.wav" to "first", "Pack/sounds/b.wav" to "second",
				)) {
					output.putNextEntry(ZipEntry(name))
					output.write(text.toByteArray())
					output.closeEntry()
				}
			}
			val packRoot = File(root, "extracted").apply { mkdir() }
			PackArchive.extract(zip, packRoot)
			FileManager.removeDoubleFolder(packRoot.path)
			assertEquals(2, packRoot.listFiles()!!.count { it.isDirectory && it.name.equals("sounds", true) })
			val pack = UniPackFolder(packRoot).load().loadDetail()
			assertNull(pack.errorDetail)
			assertEquals(listOf("first", "second"), (0..1).map { pack.soundGet(0, 0, it)?.file?.readText() })
		} finally { root.deleteRecursively() }
	}

	@Test fun canonicalSoundNames_preserveTheStoredSpelling() {
		val root = File(context.cacheDir, "pack-names-${System.nanoTime()}").apply { mkdirs() }
		try {
			File(root, "info").writeText("title=Names\nproducerName=Tester\nbuttonX=1\nbuttonY=2\nchain=1\n")
			File(root, "keySound").writeText("1 1 1 \ud55c.wav\n1 1 2 caf\u00e9.wav\n")
			File(root, "sounds").mkdir()
			val names = listOf("\u1112\u1161\u11ab.wav", "cafe\u0301.wav")
			for (name in names) File(root, "sounds/$name").writeText(name)
			val pack = UniPackFolder(root).load().loadDetail()
			assertNull(pack.errorDetail)
			assertEquals(names, (0..1).map { pack.soundGet(0, 0, it)?.file?.name })
		} finally { root.deleteRecursively() }
	}
}
