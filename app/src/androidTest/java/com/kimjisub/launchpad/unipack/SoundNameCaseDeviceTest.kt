package com.kimjisub.launchpad.unipack

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A keySound name that differs from its file only in case, as in official store packs (`End.wav` for
 * `END.wav`), read from the two places the app keeps packs: app storage, which Android serves without
 * regard to case, and the internal folder used when app storage is unavailable, which is case-sensitive.
 * A JVM test on a case-insensitive disk cannot show the internal-folder failure.
 */
@RunWith(AndroidJUnit4::class)
class SoundNameCaseDeviceTest {

	private val context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun appStorageFindsTheStoredFile() = checkPackIn(File(checkNotNull(context.getExternalFilesDir(null)), "UniPack"))

	@Test
	fun internalStorageFindsTheStoredFile() = checkPackIn(File(context.filesDir, "UniPack"))

	private fun checkPackIn(workspace: File) {
		val root = File(workspace, "sound-name-case-${System.nanoTime()}")
		try {
			File(root, "sounds/fx").mkdirs()
			File(root, "info").writeText("title=Sound name case\nproducerName=UniPad test\nbuttonX=8\nbuttonY=8\nchain=1\n")
			File(root, "keySound").writeText("1 1 1 End.wav\n1 1 2 FX/Sweep.wav\n1 1 3 kick.wav\n")
			File(root, "sounds/END.wav").writeBytes(ByteArray(64))
			File(root, "sounds/fx/sweep.wav").writeBytes(ByteArray(64))
			File(root, "sounds/kick.wav").writeBytes(ByteArray(64))
			Log.i(TAG, "${workspace.path}: case-insensitive=${File(root, "sounds/End.wav").isFile}")

			val pack = UniPackFolder(root).load().loadDetail()
			val found = (0..2).map { y -> pack.soundGet(0, 0, y)?.file?.relativeTo(File(root, "sounds"))?.invariantSeparatorsPath }
			Log.i(TAG, "${workspace.path}: found=$found errors=${pack.errorDetail}")

			assertNull("${workspace.path}: ${pack.errorDetail}", pack.errorDetail)
			assertEquals(listOf("END.wav", "fx/sweep.wav", "kick.wav"), found)
		} finally {
			root.deleteRecursively()
		}
	}

	private companion object {
		const val TAG = "SoundNameCaseDeviceTest"
	}
}
