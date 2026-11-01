package com.kimjisub.launchpad

import android.content.ContextWrapper
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.fixture.SymlinkArchive
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PackArchive
import com.kimjisub.launchpad.tool.ZipThemeImporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ArchiveSymlinkDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun internalStoragePreservesOtherPacksAndThemes() = checkStorage(context.filesDir)
    @Test fun externalStoragePreservesOtherPacksAndThemes() = checkStorage(requireNotNull(context.getExternalFilesDir(null)))

    private fun checkStorage(storage: File) {
        val root = File(storage, "archive-safety-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val victim = File(root, "other-pack").apply { check(mkdir()) }
            val sound = File(victim, "sound.wav").apply { writeText("keep") }
            val probe = File(root, "probe")
            val supportsLinks = try {
                Os.symlink(victim.path, probe.path)
                true
            } catch (_: ErrnoException) { false }
            println("Archive storage=${storage.path} supportsSymlinks=$supportsLinks")
            if (supportsLinks) {
                Os.unlink(probe.path)
            }
            for ((index, entries) in listOf(
                mapOf("sub" to victim.path),
                mapOf("Pack/info" to "title=Pack", "Pack/sub" to victim.path),
                mapOf("info" to "title=Pack", "sub" to victim.path),
            ).withIndex()) {
                val link = entries.keys.single { it.endsWith("sub") }
                val archive = SymlinkArchive.write(File(root, "pack-$index.zip"), entries, setOf(link))
                val destination = File(root, "import-$index").apply { check(mkdir()) }
                PackArchive.extract(archive, destination)
                val linkCreated = File(destination, link).exists()
                println("Archive case=$index linkCreated=$linkCreated")
                FileManager.removeDoubleFolder(destination.path)
                FileManager.deleteDirectory(destination)
                assertEquals("keep", sound.readText())
                assertFalse("archive link must not be created", linkCreated)
            }
            if (supportsLinks) {
                val wrapper = File(root, "existing/Pack").apply { check(mkdirs()) }
                File(wrapper, "info").writeText("title=Pack")
                Os.symlink(victim.path, File(wrapper, "sub").path)
                FileManager.removeDoubleFolder(wrapper.parentFile!!.path)
                FileManager.deleteDirectory(wrapper.parentFile!!)
                assertEquals("keep", sound.readText())
            }
            val isolatedContext = object : ContextWrapper(context) {
                override fun getExternalFilesDir(type: String?): File = root
            }
            for (valid in listOf(false, true)) {
                val entries = linkedMapOf("sub" to victim.path)
                if (valid) entries["theme.json"] = "{}"
                val archive = SymlinkArchive.write(File(root, "theme-$valid.zip"), entries, setOf("sub"))
                if (valid) {
                    val name = ZipThemeImporter.import(isolatedContext, Uri.fromFile(archive))
                    val imported = File(root, "themes/$name")
                    assertTrue(File(imported, "theme.json").exists())
                    assertFalse(File(imported, "sub").exists())
                    FileManager.deleteDirectory(imported)
                } else {
                    assertThrows(ZipThemeImporter.InvalidThemeException::class.java) {
                        ZipThemeImporter.import(isolatedContext, Uri.fromFile(archive))
                    }
                }
                assertEquals("keep", sound.readText())
            }
        } finally {
            FileManager.deleteDirectory(root)
        }
    }
}
