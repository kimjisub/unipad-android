package com.kimjisub.launchpad.manager

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class FileManagerTest {

	private lateinit var tempDir: File

	@Before
	fun setUp() {
		tempDir = File(System.getProperty("java.io.tmpdir"), "filemanager_test_${System.nanoTime()}")
		tempDir.mkdirs()
	}

	@After
	fun tearDown() {
		tempDir.deleteRecursively()
	}

	// filterFilename tests

	@Test
	fun filterFilename_removesInvalidCharacters() {
		assertEquals("hello world", FileManager.filterFilename("hello world"))
		assertEquals("test", FileManager.filterFilename("te|st"))
		assertEquals("test", FileManager.filterFilename("te\\st"))
		assertEquals("test", FileManager.filterFilename("te?st"))
		assertEquals("test", FileManager.filterFilename("te*st"))
		assertEquals("test", FileManager.filterFilename("te<st"))
		assertEquals("test", FileManager.filterFilename("te\"st"))
		assertEquals("test", FileManager.filterFilename("te:st"))
		assertEquals("test", FileManager.filterFilename("te>st"))
		assertEquals("test", FileManager.filterFilename("te/st"))
	}

	@Test
	fun filterFilename_preservesValidCharacters() {
		assertEquals("my-file_name (1)", FileManager.filterFilename("my-file_name (1)"))
		assertEquals("simple.txt", FileManager.filterFilename("simple.txt"))
	}

	@Test
	fun filterFilename_removesMultipleInvalidChars() {
		assertEquals("abc", FileManager.filterFilename("a|b:c"))
		assertEquals("", FileManager.filterFilename("|\\?*<\":>/"))
	}

	// byteToMB tests

	@Test
	fun byteToMB_convertsCorrectly() {
		// 1 MB = 1024 * 1024 = 1048576 bytes
		assertEquals("1.00", FileManager.byteToMB(1048576))
	}

	@Test
	fun byteToMB_zeroBytes() {
		assertEquals("0.00", FileManager.byteToMB(0))
	}

	@Test
	fun byteToMB_customFormat() {
		assertEquals("1.0", FileManager.byteToMB(1048576, "%.1f"))
	}

	@Test
	fun byteToMB_smallValue() {
		// 512 KB = 524288 bytes = 0.50 MB
		assertEquals("0.50", FileManager.byteToMB(524288))
	}

	// makeNextPath tests

	@Test
	fun makeNextPath_returnsBaseNameWhenNoConflict() {
		val result = FileManager.makeNextPath(tempDir, "myfile", ".txt")
		assertEquals("myfile.txt", result.name)
	}

	@Test
	fun makeNextPath_appendsNumberOnConflict() {
		File(tempDir, "myfile.txt").createNewFile()
		val result = FileManager.makeNextPath(tempDir, "myfile", ".txt")
		assertEquals("myfile (2).txt", result.name)
	}

	@Test
	fun makeNextPath_incrementsUntilAvailable() {
		File(tempDir, "myfile.txt").createNewFile()
		File(tempDir, "myfile (2).txt").createNewFile()
		File(tempDir, "myfile (3).txt").createNewFile()
		val result = FileManager.makeNextPath(tempDir, "myfile", ".txt")
		assertEquals("myfile (4).txt", result.name)
	}

	@Test
	fun makeNextPath_filtersInvalidCharsFromName() {
		val result = FileManager.makeNextPath(tempDir, "my:file", ".txt")
		assertEquals("myfile.txt", result.name)
	}

	// makeDirWhenNotExist tests

	@Test
	fun makeDirWhenNotExist_createsDirectory() {
		val dir = File(tempDir, "newdir")
		assertFalse(dir.exists())
		FileManager.makeDirWhenNotExist(dir)
		assertTrue(dir.isDirectory)
	}

	@Test
	fun makeDirWhenNotExist_doesNothingWhenExists() {
		val dir = File(tempDir, "existing")
		dir.mkdirs()
		assertTrue(dir.isDirectory)
		FileManager.makeDirWhenNotExist(dir)
		assertTrue(dir.isDirectory)
	}

	@Test
	fun makeDirWhenNotExist_replacesFileWithDir() {
		val dir = File(tempDir, "wasfile")
		dir.createNewFile()
		assertTrue(dir.isFile)
		FileManager.makeDirWhenNotExist(dir)
		assertTrue(dir.isDirectory)
	}

	// deleteDirectory tests

	@Test
	fun deleteDirectory_deletesFile() {
		val file = File(tempDir, "test.txt")
		file.createNewFile()
		assertTrue(file.exists())
		FileManager.deleteDirectory(file)
		assertFalse(file.exists())
	}

	@Test
	fun deleteDirectory_deletesDirectoryRecursively() {
		val subdir = File(tempDir, "subdir")
		subdir.mkdirs()
		File(subdir, "a.txt").createNewFile()
		File(subdir, "b.txt").createNewFile()
		val nested = File(subdir, "nested")
		nested.mkdirs()
		File(nested, "c.txt").createNewFile()

		FileManager.deleteDirectory(subdir)
		assertFalse(subdir.exists())
	}

	// sortByName tests

	@Test
	fun sortByName_sortsCaseInsensitive() {
		val a = File(tempDir, "banana")
		val b = File(tempDir, "Apple")
		val c = File(tempDir, "cherry")
		a.createNewFile()
		b.createNewFile()
		c.createNewFile()

		val sorted = FileManager.sortByName(arrayOf(a, b, c))
		assertEquals("Apple", sorted[0].name)
		assertEquals("banana", sorted[1].name)
		assertEquals("cherry", sorted[2].name)
	}

	// sortByTime tests

	@Test
	fun sortByTime_sortsByInnerFileModifiedTimeDescending() {
		// Create directories with inner files at different times
		val dir1 = File(tempDir, "dir1").apply { mkdirs() }
		val dir2 = File(tempDir, "dir2").apply { mkdirs() }

		val file1 = File(dir1, "inner.txt").apply { createNewFile() }
		val file2 = File(dir2, "inner.txt").apply { createNewFile() }

		// Set different modification times
		file1.setLastModified(1000L)
		file2.setLastModified(2000L)

		val sorted = FileManager.sortByTime(arrayOf(dir1, dir2))
		// dir2 has newer inner file, should be first (descending)
		assertEquals("dir2", sorted[0].name)
		assertEquals("dir1", sorted[1].name)
	}

	// getInnerFileLastModified tests

	@Test
	fun getInnerFileLastModified_returnsFirstFileModTime() {
		val dir = File(tempDir, "testdir").apply { mkdirs() }
		val file = File(dir, "file.txt").apply { createNewFile() }
		file.setLastModified(5000L)

		val result = FileManager.getInnerFileLastModified(dir)
		assertEquals(5000L, result)
	}

	@Test
	fun getInnerFileLastModified_returnsZeroForEmptyDir() {
		val dir = File(tempDir, "empty").apply { mkdirs() }
		assertEquals(0L, FileManager.getInnerFileLastModified(dir))
	}

	@Test
	fun getInnerFileLastModified_returnsZeroForFile() {
		val file = File(tempDir, "notadir.txt").apply { createNewFile() }
		assertEquals(0L, FileManager.getInnerFileLastModified(file))
	}

	// removeDoubleFolder tests

	@Test
	fun removeDoubleFolder_movesContentsUp() {
		val outer = File(tempDir, "myfolder").apply { mkdirs() }
		val inner = File(outer, "myfolder").apply { mkdirs() }
		File(inner, "file.txt").apply {
			createNewFile()
			writeText("content")
		}

		FileManager.removeDoubleFolder(outer.absolutePath)

		assertTrue(File(outer, "file.txt").exists())
		assertEquals("content", File(outer, "file.txt").readText())
		assertFalse(inner.exists())
	}

	// makeNomedia tests

	@Test
	fun makeNomedia_createsNomediaFile() {
		FileManager.makeNomedia(tempDir)
		assertTrue(File(tempDir, ".nomedia").isFile)
	}

	@Test
	fun makeNomedia_doesNothingIfExists() {
		val nomedia = File(tempDir, ".nomedia")
		nomedia.createNewFile()
		nomedia.writeText("existing")

		FileManager.makeNomedia(tempDir)
		// Should not overwrite
		assertTrue(nomedia.isFile)
	}
}
