package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.fixture.SymlinkArchive
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ArchiveSymlinkTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun importAndDelete(entries: (File) -> Map<String, String>, link: String) {
        val victim = tmp.newFolder("other-pack")
        val sound = File(victim, "sound.wav").apply { writeText("keep this sound") }
        val destination = tmp.newFolder("import")
        val archive = SymlinkArchive.write(tmp.newFile("pack.zip"), entries(victim), setOf(link))

        PackArchive.extract(archive, destination)
        assertFalse("archive link must not be created", Files.isSymbolicLink(File(destination, link).toPath()))
        FileManager.removeDoubleFolder(destination.path)
        FileManager.deleteDirectory(destination)

        assertEquals("keep this sound", sound.readText())
    }

    @Test fun aTopLevelLinkCannotEmptyAnotherPack() = importAndDelete(
        { mapOf("sub" to it.path) }, "sub")

    @Test fun aWrappedPackLinkCannotEmptyAnotherPack() = importAndDelete(
        { mapOf("Pack/info" to "title=Pack", "Pack/sub" to it.path) }, "Pack/sub")

    @Test fun deletingAnImportedPackCannotEmptyAnotherPack() = importAndDelete(
        { mapOf("info" to "title=Pack", "sub" to it.path) }, "sub")

    @Test fun unwrappingAnExistingLinkLeavesItsTargetAlone() {
        val victim = tmp.newFolder("other-pack")
        val sound = File(victim, "sound.wav").apply { writeText("keep") }
        val destination = tmp.newFolder("import")
        Files.createSymbolicLink(File(destination, "sub").toPath(), victim.toPath())

        FileManager.removeDoubleFolder(destination.path)
        FileManager.deleteDirectory(destination)

        assertEquals("keep", sound.readText())
    }

    @Test fun movingAWrapperDoesNotCopyOrDeleteLinkedContents() {
        val victim = tmp.newFolder("other-pack")
        val sound = File(victim, "sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "info").writeText("title=Pack")
        Files.createSymbolicLink(File(source, "sub").toPath(), victim.toPath())
        val target = tmp.newFolder("target")

        FileManager.moveDirectory(source, target)

        assertEquals("keep", sound.readText())
        assertEquals("title=Pack", File(target, "info").readText())
        assertFalse("linked contents must not be copied", File(target, "sub").exists())
        assertFalse(source.exists())
    }

    @Test fun deletingExistingFileDirectoryAndDanglingLinksOnlyRemovesTheLinks() {
        val victim = tmp.newFolder("other-pack")
        val sound = File(victim, "sound.wav").apply { writeText("keep") }
        for ((name, target) in mapOf("directory" to victim, "file" to sound, "dangling" to File(victim, "missing"))) {
            val link = File(tmp.root, name).toPath()
            Files.createSymbolicLink(link, target.toPath())
            FileManager.deleteDirectory(link.toFile())
            assertFalse(Files.isSymbolicLink(link))
        }
        assertEquals("keep", sound.readText())
    }

    @Test fun movingIntoALinkedDestinationIsRefused() {
        val victim = tmp.newFolder("other-pack")
        val sound = File(victim, "sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "sound.wav").writeText("replacement")
        val target = File(tmp.root, "target")
        Files.createSymbolicLink(target.toPath(), victim.toPath())

        assertThrows(IOException::class.java) { FileManager.moveDirectory(source, target) }

        assertEquals("keep", sound.readText())
        assertEquals("replacement", File(source, "sound.wav").readText())
    }

    @Test fun movingALinkOnlyRemovesTheLink() {
        val victim = tmp.newFolder("other-pack")
        val sound = File(victim, "sound.wav").apply { writeText("keep") }
        val source = File(tmp.root, "source")
        Files.createSymbolicLink(source.toPath(), victim.toPath())
        val target = tmp.newFolder("target")

        FileManager.moveDirectory(source, target)

        assertEquals("keep", sound.readText())
        assertEquals(emptyList<String>(), target.list()!!.toList())
        assertFalse(Files.isSymbolicLink(source.toPath()))
    }

    @Test fun movingOntoALinkedFileIsRefused() {
        val victim = tmp.newFile("other-sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "sound.wav").writeText("replacement")
        val target = tmp.newFolder("target")
        Files.createSymbolicLink(File(target, "sound.wav").toPath(), victim.toPath())

        assertThrows(IOException::class.java) { FileManager.moveDirectory(source, target) }

        assertEquals("keep", victim.readText())
        assertEquals("replacement", File(source, "sound.wav").readText())
    }

    @Test fun movingOntoADanglingLinkCannotCreateAFileElsewhere() {
        val victim = File(tmp.newFolder("other-pack"), "new.wav")
        val source = tmp.newFolder("source")
        File(source, "sound.wav").writeText("replacement")
        val target = tmp.newFolder("target")
        Files.createSymbolicLink(File(target, "sound.wav").toPath(), victim.toPath())

        assertThrows(IOException::class.java) { FileManager.moveDirectory(source, target) }

        assertFalse(victim.exists())
        assertEquals("replacement", File(source, "sound.wav").readText())
    }

    private fun storageIgnoresFilenameCase(): Boolean {
        tmp.newFile("case-probe")
        return File(tmp.root, "CASE-PROBE").exists()
    }

    @Test fun movingOntoDifferentlyCasedLinkedFileIsRefused() {
        assumeTrue("requires case-insensitive storage", storageIgnoresFilenameCase())
        val victim = tmp.newFile("other-sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "SOUND.WAV").writeText("replacement")
        val target = tmp.newFolder("target")
        val link = File(target, "sound.wav").toPath()
        Files.createSymbolicLink(link, victim.toPath())

        val failure = runCatching { FileManager.moveDirectory(source, target) }.exceptionOrNull()

        assertEquals("keep", victim.readText())
        assertTrue("a linked destination must be refused", failure is IOException)
        assertEquals("replacement", File(source, "SOUND.WAV").readText())
        assertTrue(Files.isSymbolicLink(link))
    }

    @Test fun movingOntoDifferentlyCasedDanglingLinkIsRefused() {
        assumeTrue("requires case-insensitive storage", storageIgnoresFilenameCase())
        val victim = File(tmp.newFolder("other-pack"), "new.wav")
        val source = tmp.newFolder("source")
        File(source, "SOUND.WAV").writeText("replacement")
        val target = tmp.newFolder("target")
        Files.createSymbolicLink(File(target, "sound.wav").toPath(), victim.toPath())

        val failure = runCatching { FileManager.moveDirectory(source, target) }.exceptionOrNull()

        assertFalse("a dangling destination must not create its target", victim.exists())
        assertTrue("a dangling destination must be refused", failure is IOException)
        assertEquals("replacement", File(source, "SOUND.WAV").readText())
    }

    @Test fun movingOntoDifferentlyCasedRegularFileMovesNormally() {
        assumeTrue("requires case-insensitive storage", storageIgnoresFilenameCase())
        val victim = tmp.newFile("other-sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "SOUND.WAV").writeText("replacement")
        val target = tmp.newFolder("target")
        File(target, "sound.wav").writeText("old")

        FileManager.moveDirectory(source, target)

        assertEquals("keep", victim.readText())
        assertEquals("replacement", File(target, "sound.wav").readText())
        assertEquals(listOf("sound.wav"), target.list()!!.toList())
        assertFalse(source.exists())
    }

    @Test fun movingIntoDifferentlyCasedRegularDirectoryMovesNormally() {
        assumeTrue("requires case-insensitive storage", storageIgnoresFilenameCase())
        val source = tmp.newFolder("source")
        File(source, "sound.wav").writeText("replacement")
        val target = tmp.newFolder("target")
        File(target, "existing.wav").writeText("keep")

        FileManager.moveDirectory(source, File(tmp.root, "TARGET"))

        assertEquals("keep", File(target, "existing.wav").readText())
        assertEquals("replacement", File(target, "sound.wav").readText())
        assertFalse(source.exists())
    }

    @Test fun movingIntoDifferentlyCasedDanglingDirectoryIsRefused() {
        assumeTrue("requires case-insensitive storage", storageIgnoresFilenameCase())
        val source = tmp.newFolder("source")
        val victim = File(tmp.root, "missing-folder")
        Files.createSymbolicLink(File(tmp.root, "target").toPath(), victim.toPath())

        val failure = runCatching { FileManager.moveDirectory(source, File(tmp.root, "TARGET")) }.exceptionOrNull()

        assertTrue("the source folder must survive a refused move", source.isDirectory)
        assertFalse(victim.exists())
        assertTrue("a dangling directory destination must be refused", failure is IOException)
    }

    @Test fun anUnreadableDestinationCannotBypassLinkProtection() {
        val victim = tmp.newFile("other-sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "sound.wav").writeText("replacement")
        val target = tmp.newFolder("target")
        Files.createSymbolicLink(File(target, "sound.wav").toPath(), victim.toPath())
        try {
            assumeTrue("requires directory read permissions", target.setReadable(false, false) && target.listFiles() == null)

            val failure = runCatching { FileManager.moveDirectory(source, target) }.exceptionOrNull()

            assertEquals("keep", victim.readText())
            assertTrue("an unreadable destination must be refused", failure is IOException)
            assertEquals("replacement", File(source, "sound.wav").readText())
        } finally {
            check(target.setReadable(true, false))
        }
    }

    @Test fun differentlyCasedEntriesStayDistinctOnCaseSensitiveStorage() {
        assumeFalse("requires case-sensitive storage", storageIgnoresFilenameCase())
        val victim = tmp.newFile("other-sound.wav").apply { writeText("keep") }
        val source = tmp.newFolder("source")
        File(source, "SOUND.WAV").writeText("replacement")
        val target = tmp.newFolder("target")
        val link = File(target, "sound.wav").toPath()
        Files.createSymbolicLink(link, victim.toPath())

        FileManager.moveDirectory(source, target)

        assertEquals("keep", victim.readText())
        assertEquals("replacement", File(target, "SOUND.WAV").readText())
        assertTrue(Files.isSymbolicLink(link))
        assertFalse(source.exists())
    }

    private val unicodeAliases = listOf(
        "é.wav" to "e\u0301.wav",
        "straße.wav" to "STRASSE.wav",
        "İ.wav" to "i\u0307.wav",
        "ﬀ.wav" to "ff.wav",
    )

    @Test fun movingOntoUnicodeAliasedLinksIsRefused() {
        for ((index, names) in unicodeAliases.withIndex()) {
            val (storedName, requestedName) = names
            val source = tmp.newFolder("source-$index")
            File(source, requestedName).writeText("replacement")
            val target = tmp.newFolder("target-$index")
            // The ordinary file and the link have the same canonical destination.
            val victim = File(target, "other-sound.wav").apply { writeText("keep") }
            val link = File(target, storedName).toPath()
            Files.createSymbolicLink(link, victim.toPath())
            assumeTrue("requires filesystem aliases for $storedName and $requestedName", File(target, requestedName).exists())

            val failure = runCatching { FileManager.moveDirectory(source, target) }.exceptionOrNull()

            assertEquals("keep", victim.readText())
            assertTrue("an aliased link must be refused: $requestedName", failure is IOException)
            assertEquals("replacement", File(source, requestedName).readText())
            assertTrue(Files.isSymbolicLink(link))
        }
    }

    @Test fun movingOntoUnicodeAliasedRegularFilesMovesNormally() {
        for ((index, names) in unicodeAliases.withIndex()) {
            val (storedName, requestedName) = names
            val source = tmp.newFolder("source-$index")
            File(source, requestedName).writeText("replacement")
            val target = tmp.newFolder("target-$index")
            File(target, storedName).writeText("old")
            assumeTrue("requires filesystem aliases for $storedName and $requestedName", File(target, requestedName).exists())

            FileManager.moveDirectory(source, target)

            assertEquals("replacement", File(target, storedName).readText())
            assertFalse(source.exists())
        }
    }
}
