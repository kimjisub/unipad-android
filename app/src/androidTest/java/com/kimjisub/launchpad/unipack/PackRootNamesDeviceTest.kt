package com.kimjisub.launchpad.unipack

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** Root names must be tested on the very filesystem used to read the pack. */
@RunWith(AndroidJUnit4::class)
class PackRootNamesDeviceTest {
    @get:Rule
    val dir = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.filesDir)

    private fun probe() {
        val upper = File(dir.root, "CaseProbe").apply { writeText("upper") }
        val lower = File(dir.root, "caseprobe").apply { writeText("lower") }
        assertEquals("case-sensitive upper file", "upper", upper.readText())
        assertEquals("case-sensitive lower file", "lower", lower.readText())
        assertNotEquals(upper.canonicalPath, lower.canonicalPath)
        Log.i("PackRootNames", "case-sensitive probe passed at ${dir.root}: CaseProbe=upper caseprobe=lower")
    }

    private fun pack(info: String = "INFO", sound: String = "KEYSOUND"): UniPack {
        probe()
        File(dir.root, info).writeText("title=Root names\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=1\n")
        File(dir.root, sound).writeText("1 1 1 tone.wav\n")
        File(dir.root, "SOUNDS").mkdir()
        File(dir.root, "SOUNDS/tone.wav").writeBytes(byteArrayOf(1, 2))
        File(dir.root, "AUTOPLAY").writeText("t 1 1\n")
        File(dir.root, "KEYLED").mkdir()
        File(dir.root, "KEYLED/1 1 1").writeText("o 1 1 ff0000\n")
        return UniPackFolder(dir.root).load().loadDetail()
    }

    @Test
    fun uppercaseRootNamesLoadOnCaseSensitiveStorage() {
        val pack = pack()
        assertFalse(pack.criticalError)
        assertNull(pack.errorDetail)
        assertEquals("Root names", pack.title)
        assertEquals("SOUNDS/tone.wav", pack.soundGet(0, 0, 0)?.file?.relativeTo(dir.root)?.path)
        assertTrue(pack.autoPlayExist)
        assertTrue(pack.keyLedExist)
        assertEquals(1, pack.ledTableCount)
        assertEquals(1, pack.ledAnimationTable!![0][0][0]!!.first().ledEvents.size)
        assertTrue(pack.autoPlayTable!!.elements.first() is AutoPlay.Element.On)
        assertEquals(2, pack.autoPlayTable!!.elements.size)
    }

    @Test
    fun infoWithAnExtensionIsNotInfo() {
        val pack = pack(info = "info.txt")
        assertTrue(pack.criticalError)
        assertTrue(pack.errorDetail.orEmpty().contains("info doesn't exist"))
    }

    @Test
    fun pluralKeySoundsIsNotKeySound() {
        val pack = pack(sound = "keySounds")
        assertTrue(pack.criticalError)
        assertTrue(pack.errorDetail.orEmpty().contains("keySound doesn't exist"))
    }
}
