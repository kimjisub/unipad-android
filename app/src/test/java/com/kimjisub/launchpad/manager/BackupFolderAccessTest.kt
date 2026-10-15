package com.kimjisub.launchpad.manager

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The backup folder is usable only while UniPad holds its permission, only a folder named Unipad
 * is accepted, and forgetting the folder works when its permission is already gone.
 */
class BackupFolderAccessTest {

	private val resolver = mockk<ContentResolver>()
	private val prefs = mockk<PreferenceManager>()
	private var savedUri: String? = null
	private val held = mutableListOf<UriPermission>()
	private val uris = mutableMapOf<String, Uri>()
	private val access = BackupFolderAccess(resolver, prefs)

	@Before
	fun setUp() {
		mockkStatic(Uri::class)
		every { Uri.parse(any()) } answers { uriOf(firstArg()) }
		every { prefs.backupSafUri } answers { savedUri }
		every { prefs.backupSafUri = any() } answers { savedUri = firstArg() }
		every { resolver.persistedUriPermissions } answers { held.toList() }
	}

	@After
	fun tearDown() {
		unmockkStatic(Uri::class)
	}

	@Test
	fun noSavedFolderIsNotUsable() {
		assertNull(access.usableUri)
	}

	@Test
	fun savedFolderWithoutPermissionIsNotUsable() {
		// Deleting Documents/Unipad in a Files app takes the permission away and leaves the address.
		savedUri = UNIPAD

		assertNull(access.usableUri)
	}

	@Test
	fun savedFolderWithReadAndWritePermissionIsUsable() {
		savedUri = UNIPAD
		held += permission(UNIPAD)

		assertEquals(UNIPAD, access.usableUri)
	}

	@Test
	fun permissionOnAnotherFolderOrReadOnlyDoesNotCount() {
		savedUri = UNIPAD
		held += permission(DOCUMENTS)
		held += permission(UNIPAD, write = false)

		assertNull(access.usableUri)
	}

	@Test
	fun disconnectWithoutPermissionForgetsTheFolder() {
		savedUri = UNIPAD
		every { resolver.releasePersistableUriPermission(any(), any()) } throws SecurityException("No permission grant found")

		access.disconnect()

		assertNull(savedUri)
	}

	@Test
	fun disconnectReleasesHeldPermission() {
		savedUri = UNIPAD
		every { resolver.releasePersistableUriPermission(any(), any()) } just runs

		access.disconnect()

		verify { resolver.releasePersistableUriPermission(uriOf(UNIPAD), FLAGS) }
		assertNull(savedUri)
	}

	@Test
	fun otherFolderIsRefusedWithoutTakingPermission() {
		val result = access.connect(uriOf(DOCUMENTS), "Documents")

		assertEquals(BackupFolderAccess.ConnectResult.WRONG_FOLDER, result)
		assertNull(savedUri)
		verify(exactly = 0) { resolver.takePersistableUriPermission(any(), any()) }
	}

	@Test
	fun unipadFolderIsSavedWithItsPermission() {
		every { resolver.takePersistableUriPermission(any(), any()) } just runs

		val result = access.connect(uriOf(UNIPAD), "unipad")

		assertEquals(BackupFolderAccess.ConnectResult.CONNECTED, result)
		assertEquals(UNIPAD, savedUri)
		verify { resolver.takePersistableUriPermission(uriOf(UNIPAD), FLAGS) }
	}

	@Test
	fun refusedPermissionIsReportedAndNotSaved() {
		// Another file manager answered the picker and cannot hand out a lasting permission.
		every { resolver.takePersistableUriPermission(any(), any()) } throws SecurityException("No persistable permission grants found")

		val result = access.connect(uriOf(UNIPAD), "Unipad")

		assertEquals(BackupFolderAccess.ConnectResult.PERMISSION_REFUSED, result)
		assertNull(savedUri)
	}

	@Test
	fun screensReadTheBackupFolderOnlyThroughThisClass() {
		val main = File("src/main/java")
		val readers = main.walkTopDown()
			.filter { it.isFile && it.extension == "kt" && savedAddressRead.containsMatchIn(it.readText()) }
			.map { it.name }
			.toSet()

		assertEquals(
			"A screen that reads the saved address directly misses a lost permission and a wrong folder",
			setOf("BackupFolderAccess.kt"),
			readers,
		)
	}

	/** One mock per address, so a call can be verified against the address it should carry. */
	private fun uriOf(value: String): Uri = uris.getOrPut(value) {
		mockk { every { this@mockk.toString() } returns value }
	}

	private fun permission(value: String, write: Boolean = true): UriPermission {
		val target = uriOf(value)
		return mockk {
			every { uri } returns target
			every { isReadPermission } returns true
			every { isWritePermission } returns write
		}
	}

	private companion object {
		const val DOCUMENTS = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
		const val UNIPAD = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FUnipad"
		val savedAddressRead = Regex("""\.backupSafUri\b""")
		const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
	}
}
