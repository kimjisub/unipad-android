package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.db.repository.UnipackRepository
import io.mockk.*
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
	fun availableWorkspaces_includesInternalStorage() {
		val internalDir = File(tempDir, "internal")
		internalDir.mkdirs()

		every { mockContext.getExternalFilesDir(null) } returns null
		every { mockContext.filesDir } returns internalDir
		every { mockContext.getExternalFilesDirs("UniPack") } returns arrayOf()

		val manager = WorkspaceManager(mockContext)
		val workspaces = manager.availableWorkspaces

		assertTrue(
			"Should contain 'Internal Storage' workspace",
			workspaces.any { it.name == "Internal Storage" }
		)
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
