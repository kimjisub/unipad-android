package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.fixture.SymlinkArchive
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ZipThemeImporterSymlinkTest {
    @get:Rule val tmp = TemporaryFolder()
    @After fun cleanupMocks() = unmockkAll()

    private fun importTheme(valid: Boolean) {
        val external = tmp.newFolder("external")
        val victim = File(external, "themes/other-theme").apply { mkdirs() }
        val image = File(victim, "image.png").apply { writeText("keep") }
        val entries = linkedMapOf("sub" to victim.path)
        if (valid) entries["theme.json"] = "{}"
        val archive = SymlinkArchive.write(tmp.newFile("theme.zip"), entries, setOf("sub"))
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.getExternalFilesDir(null) } returns external
        every { context.cacheDir } returns tmp.newFolder("cache")
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(uri) } answers { archive.inputStream() }
        mockkStatic(DocumentFile::class)
        every { DocumentFile.fromSingleUri(context, uri) } returns mockk { every { name } returns "theme.zip" }

        if (valid) {
            val imported = File(ZipThemeImporter.getThemesDir(context), ZipThemeImporter.import(context, uri))
            assertTrue(File(imported, "theme.json").isFile)
            assertFalse(Files.isSymbolicLink(File(imported, "sub").toPath()))
        } else {
            assertThrows(ZipThemeImporter.InvalidThemeException::class.java) { ZipThemeImporter.import(context, uri) }
            assertEquals(setOf("other-theme"), victim.parentFile!!.list()!!.toSet())
        }
        assertEquals("keep", image.readText())
    }

    @Test fun importingAThemeDoesNotCreateLinks() = importTheme(valid = true)
    @Test fun cleaningUpAnInvalidThemeDoesNotEmptyAnotherTheme() = importTheme(valid = false)
}
