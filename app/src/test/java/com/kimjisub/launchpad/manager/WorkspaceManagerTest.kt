package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File

class WorkspaceManagerTest {

	private lateinit var mockContext: Context
	private lateinit var mockSharedPrefs: SharedPreferences
	private lateinit var mockEditor: SharedPreferences.Editor
	private lateinit var mockRepo: UnipackRepository
	private lateinit var tempDir: File

	@Before
	fun setUp() {
		mockContext = mockk(relaxed = true)
		mockSharedPrefs = mockk(relaxed = true)
		mockEditor = mockk(relaxed = true)
		mockRepo = mockk(relaxed = true)

		every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPrefs
		every { mockSharedPrefs.edit() } returns mockEditor
		every { mockEditor.putStringSet(any(), any()) } returns mockEditor

		// Mock workspace name string resources
		every { mockContext.getString(R.string.workspace_documents_android10) } returns "Documents (Android 10)"
		every { mockContext.getString(R.string.workspace_app_storage) } returns "App Storage (Recommended)"
		every { mockContext.getString(R.string.workspace_internal_storage) } returns "Internal Storage"
		every { mockContext.getString(R.string.workspace_internal_sd_card) } returns "Internal SD Card"
		every { mockContext.getString(R.string.workspace_external_sd_card_format, *anyVararg()) } answers {
			val args = secondArg<Array<out Any?>>()
			"External SD Card ${args[0]}"
		}

		// Create temp directories for testing
		tempDir = File(System.getProperty("java.io.tmpdir"), "unipad_workspace_test_${System.nanoTime()}")
		tempDir.mkdirs()

		// Mock FileManager static methods
		mockkObject(FileManager)
		every { FileManager.makeDirWhenNotExist(any()) } just runs
		every { FileManager.makeNomedia(any()) } just runs

		// Mock Environment for the deprecated API branch
		mockkStatic(Environment::class)
		every { Environment.getExternalStoragePublicDirectory(any()) } returns File(tempDir, "documents")

		// Start Koin with mock repo
		startKoin {
			modules(module {
				single { mockRepo }
			})
		}
	}

	@After
	fun tearDown() {
		stopKoin()
		unmockkObject(FileManager)
		unmockkStatic(Environment::class)
		tempDir.deleteRecursively()
	}

	// === Workspace data class tests ===

	@Test
	fun workspace_toString_format() {
		val file = File("/some/path")
		val workspace = WorkspaceManager.Workspace("Test", file)
		val expected = "Workspace(name=Test, file=${file.path})"
		assertEquals(expected, workspace.toString())
	}

	@Test
	fun workspace_equality_sameValues() {
		val file = File("/some/path")
		val ws1 = WorkspaceManager.Workspace("Test", file)
		val ws2 = WorkspaceManager.Workspace("Test", file)
		assertEquals(ws1, ws2)
	}

	@Test
	fun workspace_equality_differentName() {
		val file = File("/some/path")
		val ws1 = WorkspaceManager.Workspace("Test1", file)
		val ws2 = WorkspaceManager.Workspace("Test2", file)
		assertNotEquals(ws1, ws2)
	}

	@Test
	fun workspace_equality_differentFile() {
		val ws1 = WorkspaceManager.Workspace("Test", File("/path1"))
		val ws2 = WorkspaceManager.Workspace("Test", File("/path2"))
		assertNotEquals(ws1, ws2)
	}

	// === downloadWorkspaceIn tests ===

	@Test
	fun downloadWorkspaceIn_readsTheChoiceAtCallTimeWithoutListingStorage() {
		val first = WorkspaceManager.Workspace("A", File(tempDir, "a"))
		val second = WorkspaceManager.Workspace("B", File(tempDir, "b"))
		val manager = WorkspaceManager(mockContext)

		// The settings screen lists storage off the main thread and picks the target afterwards,
		// so the target must come from the stored choice, not from a fresh listing.
		every { mockSharedPrefs.getString(any(), any()) } returns second.file.path

		assertEquals(second, manager.downloadWorkspaceIn(listOf(first, second)))
		verify(exactly = 0) { mockContext.getExternalFilesDir(any()) }
		verify(exactly = 0) { mockContext.getExternalFilesDirs(any()) }
	}

	@Test
	fun downloadWorkspaceIn_fallsBackToTheFirstWhenTheChoiceIsGone() {
		val first = WorkspaceManager.Workspace("A", File(tempDir, "a"))
		val second = WorkspaceManager.Workspace("B", File(tempDir, "b"))
		every { mockSharedPrefs.getString(any(), any()) } returns File(tempDir, "removed-card").path

		assertEquals(first, WorkspaceManager(mockContext).downloadWorkspaceIn(listOf(first, second)))
	}

	// === availableWorkspaces tests ===

	@Test
	fun availableWorkspaces_includesAppStorage() {
		val appDir = File(tempDir, "app_external")
		appDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns appDir
		every { mockContext.filesDir } returns File(tempDir, "internal")
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf()

		val manager = WorkspaceManager(mockContext)
		val workspaces = manager.availableWorkspaces

		assertTrue(
			"Should contain 'App Storage (Recommended)' workspace",
			workspaces.any { it.name == "App Storage (Recommended)" }
		)
	}

	@Test
	fun availableWorkspaces_fallsBackToInternalStorageWhenAppExternalDirIsUnavailable() {
		val internalDir = File(tempDir, "internal")
		internalDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns null
		every { mockContext.filesDir } returns internalDir
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf()

		val manager = WorkspaceManager(mockContext)
		val workspaces = manager.availableWorkspaces

		assertEquals("Internal Storage", workspaces.first().name)
		assertEquals(File(internalDir, "UniPack"), workspaces.first().file)
	}

	@Test
	fun availableWorkspaces_doesNotAddInternalStorageWhenAppExternalDirExists() {
		val appDir = File(tempDir, "app_external")
		appDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns appDir
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf()

		val manager = WorkspaceManager(mockContext)

		assertTrue(manager.availableWorkspaces.none { it.name == "Internal Storage" })
	}

	@Test
	fun availableWorkspaces_includesExternalSdCards() {
		val sdCardDir = File(tempDir, "sdcard")
		sdCardDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns null
		every { mockContext.filesDir } returns File(tempDir, "internal")
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf(sdCardDir)

		val manager = WorkspaceManager(mockContext)
		val workspaces = manager.availableWorkspaces

		assertTrue(
			"Should contain external SD card workspace",
			workspaces.any { it.name.contains("SD Card") }
		)
	}

	@Test
	fun availableWorkspaces_skipsUnmountedExternalDirs() {
		val sdCardDir = File(tempDir, "sdcard")
		sdCardDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns null
		every { mockContext.filesDir } returns File(tempDir, "internal")
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf(sdCardDir, null)

		val manager = WorkspaceManager(mockContext)
		val workspaces = manager.availableWorkspaces

		assertEquals(
			"Unmounted volume should be treated as absent",
			listOf("External SD Card 1"),
			workspaces.filter { it.name.contains("SD Card") }.map { it.name }
		)
	}

	@Test
	fun getLegacyUniPackDir_skipsUnmountedExternalDirs() {
		val internalDir = File(tempDir, "storage/emulated/0/UniPack")
		internalDir.mkdirs()

		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf(null, internalDir)

		val manager = WorkspaceManager(mockContext)

		assertEquals(internalDir, manager.getLegacyUniPackDir())
	}

	// === Home screen call paths (Play vitals, versionCode 109: NPE at WorkspaceManager.kt:54) ===

	private fun managerWithExternalDirs(vararg dirs: File?): WorkspaceManager {
		every { mockContext.getExternalFilesDir(null) } returns File(tempDir, "app_external").apply { mkdirs() }
		every { mockContext.filesDir } returns File(tempDir, "internal")
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf(*dirs)
		return WorkspaceManager(mockContext)
	}

	private fun createSdCardWithPack(): File {
		val sdCardDir = File(tempDir, "sdcard").apply { mkdirs() }
		val pack = File(sdCardDir, "pack").apply { mkdirs() }
		File(pack, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(pack, "keySound").writeText("1 1 1 a.wav\n")
		File(pack, "sounds").mkdirs()
		File(pack, "sounds/a.wav").writeText("")
		return sdCardDir
	}

	@Test
	fun getAvailableWorkspacesSize_emptyStorage() {
		val manager = managerWithExternalDirs()

		assertEquals(0L, runBlocking { manager.getAvailableWorkspacesSize() })
	}

	@Test
	fun getAvailableWorkspacesSize_unavailableVolume() {
		val manager = managerWithExternalDirs(null)

		assertEquals(0L, runBlocking { manager.getAvailableWorkspacesSize() })
	}

	@Test
	fun getAvailableWorkspacesSize_countsMountedFolderAndSkipsUnmountedVolume() {
		val sdCardDir = createSdCardWithPack()
		val expected = sdCardDir.walk().filter { it.isFile }.sumOf { it.length() }
		val manager = managerWithExternalDirs(sdCardDir, null)

		assertEquals(expected, runBlocking { manager.getAvailableWorkspacesSize() })
	}

	// The total panel calls this from the main thread; listing workspaces there created folders on it.
	@Test
	fun getAvailableWorkspacesSize_listsWorkspacesOffTheCallingThread() {
		val manager = managerWithExternalDirs()
		val caller = Thread.currentThread()
		val listedOn = mutableListOf<Thread>()
		every { FileManager.makeNomedia(any()) } answers { listedOn += Thread.currentThread() }

		runBlocking { manager.getAvailableWorkspacesSize() }

		assertTrue(listedOn.isNotEmpty())
		assertTrue(listedOn.none { it === caller })
	}

	@Test
	fun getUnipacks_emptyStorage() {
		val manager = managerWithExternalDirs()

		assertEquals(0, runBlocking { manager.getUnipacks() }.size)
	}

	@Test
	fun getUnipacks_unavailableVolume() {
		val manager = managerWithExternalDirs(null)

		assertEquals(0, runBlocking { manager.getUnipacks() }.size)
	}

	@Test
	fun getUnipacks_loadsPackFromMountedFolderAndSkipsUnmountedVolume() {
		val manager = managerWithExternalDirs(createSdCardWithPack(), null)

		assertEquals(listOf("Test"), runBlocking { manager.getUnipacks() }.map { it.unipack.title })
	}

	// === deleteUnipack: files first, then the saved row ===

	private fun writePack(workspace: File, name: String = "pack"): File {
		val pack = File(workspace, name).apply { mkdirs() }
		File(pack, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(pack, "keySound").writeText("1 1 1 a.wav\n")
		File(pack, "sounds").mkdirs()
		File(pack, "sounds/a.wav").writeText("")
		return pack
	}

	private fun appWorkspace() = File(tempDir, "app_external/UniPack")

	@Test
	fun deleteUnipack_removesFilesThenRow() {
		val manager = managerWithExternalDirs()
		val pack = writePack(appWorkspace())
		every { mockRepo.delete("pack") } returns true

		val result = manager.deleteUnipack(UniPackFolder(pack))

		assertEquals(WorkspaceManager.DeleteResult.DELETED, result)
		assertFalse(pack.exists())
		verify(exactly = 1) { mockRepo.delete("pack") }
	}

	@Test
	fun deleteUnipack_keepsRowWhenFilesRemain() {
		val manager = managerWithExternalDirs()
		val pack = writePack(appWorkspace())
		val sounds = File(pack, "sounds")
		sounds.setWritable(false)
		try {
			val result = manager.deleteUnipack(UniPackFolder(pack))

			assertEquals(WorkspaceManager.DeleteResult.FILE_DELETE_FAILED, result)
			assertTrue(pack.exists())
			verify(exactly = 0) { mockRepo.delete(any()) }
		} finally {
			sounds.setWritable(true)
		}
	}

	@Test
	fun deleteUnipack_reportsFailureWhenRowRemains() {
		val manager = managerWithExternalDirs()
		val pack = writePack(appWorkspace())
		every { mockRepo.delete("pack") } returns false

		assertEquals(WorkspaceManager.DeleteResult.RECORD_DELETE_FAILED, manager.deleteUnipack(UniPackFolder(pack)))
	}

	@Test
	fun deleteUnipack_reportsFailureWhenRowDeleteThrows() {
		val manager = managerWithExternalDirs()
		val pack = writePack(appWorkspace())
		every { mockRepo.delete("pack") } throws IllegalStateException("database closed")

		assertEquals(WorkspaceManager.DeleteResult.RECORD_DELETE_FAILED, manager.deleteUnipack(UniPackFolder(pack)))
	}

	@Test
	fun deleteUnipack_leavesOtherPacksAlone() {
		val manager = managerWithExternalDirs()
		val pack = writePack(appWorkspace(), "pack")
		val other = writePack(appWorkspace(), "other")
		every { mockRepo.delete("pack") } returns true

		manager.deleteUnipack(UniPackFolder(pack))

		assertTrue(other.exists())
		verify(exactly = 0) { mockRepo.delete("other") }
	}

	@Test
	fun deleteUnipack_keepsSharedRowUntilLastSameNamedPackIsDeleted() {
		val sdCardDir = File(tempDir, "sdcard").apply { mkdirs() }
		val manager = managerWithExternalDirs(sdCardDir)
		val appCopy = writePack(appWorkspace())
		val sdCopy = writePack(sdCardDir)
		every { mockRepo.delete("pack") } returns true

		assertEquals(WorkspaceManager.DeleteResult.DELETED, manager.deleteUnipack(UniPackFolder(appCopy)))
		assertTrue(sdCopy.exists())
		verify(exactly = 0) { mockRepo.delete(any()) }

		assertEquals(WorkspaceManager.DeleteResult.DELETED, manager.deleteUnipack(UniPackFolder(sdCopy)))
		verify(exactly = 1) { mockRepo.delete("pack") }
	}

	@Test
	fun availableWorkspaces_appStoragePathEndsWithUnipad() {
		val appDir = File(tempDir, "app_external")
		appDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns appDir
		every { mockContext.filesDir } returns File(tempDir, "internal")
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf()

		val manager = WorkspaceManager(mockContext)
		val workspaces = manager.availableWorkspaces

		val appWorkspace = workspaces.first { it.name == "App Storage (Recommended)" }
		assertTrue(
			"App storage path should end with 'UniPack'",
			appWorkspace.file.name == "UniPack"
		)
	}
}
