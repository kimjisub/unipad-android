package com.kimjisub.launchpad.manager

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * A pack being installed or moved is built in a folder of its own under the workspace's staging
 * folder and renamed under its final name only once complete. The app can be killed at any moment;
 * a half-built pack then stays in the staging folder, which is never listed as a pack, instead of
 * showing up with missing sounds. The staging folder sits on the same storage as the packs, so the
 * final rename moves nothing.
 *
 * Only this object creates folders under the staging folder, so clearing it on the next start
 * ([setAsideLeftovers]) never touches a folder the person put in the workspace.
 */
object PackStaging {
	private const val DIR_NAME = ".unipad-staging"
	private const val LEFTOVER_PREFIX = "$DIR_NAME-leftover-"
	private const val CREATE_ATTEMPTS = 3

	/** The staging folder, or leftovers set aside from it: never a pack. */
	fun isStagingFolder(file: File): Boolean = file.name.startsWith(DIR_NAME)

	/** A new, empty folder on [workspace]'s storage for one install or move. */
	fun create(workspace: File): File {
		val root = File(workspace, DIR_NAME)
		repeat(CREATE_ATTEMPTS) {
			// Another job's discard() can remove the then-empty root between these two calls.
			root.mkdirs()
			val folder = File(root, UUID.randomUUID().toString())
			if (folder.mkdir()) return folder
		}
		throw IOException("Could not create a staging folder in ${workspace.path}")
	}

	/** Deletes [staged] if it was not renamed into place, and the staging folder once no job uses it. */
	fun discard(staged: File) {
		FileManager.deleteDirectory(staged)
		staged.parentFile?.delete()
	}

	/**
	 * Renames what a killed run left in [workspace]'s staging folder out of the way and returns it
	 * for deletion. The rename is quick, so it runs on start before anything can stage again; the
	 * deletion, which can take long for a large pack, is left to the caller.
	 */
	fun setAsideLeftovers(workspace: File): List<File> {
		val root = File(workspace, DIR_NAME)
		if (root.isDirectory) root.renameTo(File(workspace, LEFTOVER_PREFIX + UUID.randomUUID()))
		return workspace.listFiles().orEmpty().filter { it.name.startsWith(LEFTOVER_PREFIX) }
	}
}
