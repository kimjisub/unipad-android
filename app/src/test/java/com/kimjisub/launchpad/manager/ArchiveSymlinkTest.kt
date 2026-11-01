package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.fixture.SymlinkArchive
import org.junit.Assert.*
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
}
