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

	/**
	 * Only the case-sensitive internal folder can hold files whose names differ only in case, so only there
	 * can the exact name be seen to win over a case-insensitive match.
	 */
	@Test
	fun internalStorageChoosesTheExactNameFirst() {
		val root = File(File(context.filesDir, "UniPack"), "sound-name-exact-${System.nanoTime()}")
		try {
			val sounds = File(root, "sounds")
			File(sounds, "fx").mkdirs()
			File(sounds, "FX").mkdirs()
			File(sounds, "dir.wav").mkdirs()
			for (name in listOf("End.wav", "END.wav", "fx/a.wav", "FX/a.wav")) File(sounds, name).writeBytes(ByteArray(64))
			assertEquals(
				"internal storage keeps names that differ only in case apart",
				listOf("END.wav", "End.wav", "FX/a.wav", "fx/a.wav"),
				sounds.walkTopDown().filter { it.isFile }.map { it.relativeTo(sounds).invariantSeparatorsPath }.sorted().toList(),
			)
			val names = listOf("End.wav", "END.wav", "end.wav", "fx/a.wav", "FX/a.wav", "dir.wav", "DIR.wav")
			File(root, "info").writeText("title=Sound name exact\nproducerName=UniPad test\nbuttonX=8\nbuttonY=8\nchain=1\n")
			File(root, "keySound").writeText(names.mapIndexed { y, name -> "1 1 ${y + 1} $name\n" }.joinToString(""))

			val pack = UniPackFolder(root).load().loadDetail()
			val found = names.indices.map { y -> pack.soundGet(0, 0, y)?.file?.relativeTo(sounds)?.invariantSeparatorsPath }
			Log.i(TAG, "${root.path}: found=$found errors=${pack.errorDetail}")

			// end.wav takes the first of END.wav and End.wav in path order, so the choice never varies.
			assertEquals(listOf("End.wav", "END.wav", "END.wav", "fx/a.wav", "FX/a.wav", null, null), found)
			assertEquals(
				"keySound : [1 1 6 dir.wav] sound was not found\nkeySound : [1 1 7 DIR.wav] sound was not found",
				pack.errorDetail,
			)
		} finally {
			root.deleteRecursively()
		}
	}

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
