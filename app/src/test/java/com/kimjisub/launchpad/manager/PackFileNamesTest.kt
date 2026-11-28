package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.unipack.SoundFileIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PackFileNamesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun metadataIsNotIndexedAsASound() {
        val dir = tmp.newFolder()
        val alias = File(dir, ".unipad-entry-1").apply { writeText("sound") }
        PackFileNames.write(dir, linkedMapOf(alias.name to "cafe\u0301.wav"))
        val index = SoundFileIndex(dir)
        assertEquals(alias, index.find("cafe\u0301.wav"))
        assertEquals(alias, index.find("café.wav"))
        assertNull(index.find(PackFileNames.MANIFEST))
        assertNull(index.find(alias.name))
    }

    @Test fun invalidMetadataCannotRedirectANameOutsideItsDirectory() {
        val dir = tmp.newFolder()
        File(dir, "ordinary.wav").writeText("sound")
        File(dir, PackFileNames.MANIFEST).writeText(
            """{"format":"unipad-filenames-1","names":{"../escape":"ordinary.wav"}}""")
        val index = SoundFileIndex(dir)
        assertEquals("sound", index.find("ordinary.wav")!!.readText())
        assertNull(index.find("../escape"))
    }

    @Test fun brokenMetadataDoesNotPreventReadingOrdinaryInstalledFiles() {
        val dir = tmp.newFolder()
        File(dir, "ordinary.wav").writeText("sound")
        File(dir, PackFileNames.MANIFEST).writeText("not json")
        assertEquals("sound", SoundFileIndex(dir).find("ordinary.wav")!!.readText())
    }

    @Test fun storedAliasesWithIdenticalBytesAreStillTwoExactOriginals() {
        val dir = tmp.newFolder()
        val first = File(dir, "café.wav").apply { writeText("same") }
        val second = File(dir, ".unipad-entry-1").apply { writeText("same") }
        PackFileNames.write(dir, linkedMapOf(first.name to "café.wav", second.name to "cafe\u0301.wav"))
        val index = SoundFileIndex(dir)
        assertEquals(first, index.find("café.wav"))
        assertEquals(second, index.find("cafe\u0301.wav"))
        assertEquals(listOf("café.wav", "cafe\u0301.wav"), PackFileNames.children(dir).map { it.name })
    }
}
