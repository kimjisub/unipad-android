package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.unipack.LegacyText
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.UnzipParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.util.zip.CRC32
import java.io.File
import java.io.IOException

/** Unpacks a pack archive (a .zip or .uni) into a folder. */
object PackArchive {
	/**
	 * Entry names are read as UTF-8, and as CP949 when they are not valid UTF-8 ([LegacyText]).
	 * Archives zipped on Korean Windows store names in CP949 without the UTF-8 flag, which zip4j read
	 * as CP437, so a sound named in Korean was not found under the name keySound gives it.
	 *
	 * zip4j decodes every name with one charset and checks each entry against its central directory
	 * name, so names are read as ISO-8859-1, which keeps each byte as one char, and decoded names are
	 * retained alongside portable aliases when shared storage would merge two entries.
	 */
	fun extract(archive: File, destination: File) {
		val root = destination.canonicalFile
		ZipFile(archive).use { zip ->
			zip.charset = Charsets.ISO_8859_1
			val parameters = UnzipParameters().apply { isExtractSymbolicLinks = false }
			// Allocate names before touching disk, even on case-sensitive storage: the installed
			// folder must also survive a later copy to FUSE or an SD card.
			val tree = Node("")
			for (header in zip.fileHeaders) {
				val rawName = header.fileName.toByteArray(Charsets.ISO_8859_1)
				val name = unicodePath(header, rawName) ?: LegacyText.decode(rawName)
				val path = name.replace('\\', '/')
				if (path.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(path))
					throw IOException("Entry outside the destination: $name")
				val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
				if (parts.isEmpty() && header.isDirectory) continue
				if (parts.isEmpty() || parts.any { !PackFileNames.validName(it) })
					throw IOException("Invalid entry path: $name")
				var node = tree
				for ((index, part) in parts.withIndex()) {
					if (node.header?.isDirectory == false) throw IOException("File used as a folder: $name")
					node = node.children.getOrPut(part) { Node(part) }
					if (index == parts.lastIndex) {
						if (node.children.isNotEmpty() && !header.isDirectory)
							throw IOException("Folder used as a file: $name")
						node.header = header
					}
				}
			}
			extractChildren(zip, tree, root, root, parameters)
		}
	}

	private class Node(val name: String) {
		var header: FileHeader? = null
		val children = linkedMapOf<String, Node>()
	}

	private fun extractChildren(zip: ZipFile, node: Node, dir: File, root: File, parameters: UnzipParameters) {
		val pending = ArrayDeque<Pair<Node, File>>()
		pending.addLast(node to dir)
		while (pending.isNotEmpty()) {
			val (current, folder) = pending.removeLast()
			if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create $folder")
			val children = current.children.values
			val collisionKeys = children.groupingBy { PackFileNames.folded(it.name) }.eachCount()
				.filterValues { it > 1 }.keys
			val collisions = collisionKeys.isNotEmpty()
			val reserved = children.map { PackFileNames.folded(it.name) }.toMutableSet()
			val manifestName = PackFileNames.folded(PackFileNames.MANIFEST)
			if (collisions) reserved += manifestName
			val allocated = mutableSetOf<String>()
			val names = linkedMapOf<String, String>()
			var nextAlias = 0
			for (child in children) {
				val folded = PackFileNames.folded(child.name)
				val stored = if (!allocated.add(folded) || collisions && folded == manifestName) {
					val extension = child.name.substringAfterLast('.', "").takeIf {
						Regex("[A-Za-z0-9]{1,16}").matches(it)
					}?.let { ".$it" }.orEmpty()
					var alias: String
					do { alias = "${PackFileNames.ALIAS_PREFIX}${++nextAlias}$extension" } while (!reserved.add(PackFileNames.folded(alias)))
					alias
				} else child.name
				if (folded in collisionKeys || stored != child.name) names[stored] = child.name
				val target = File(folder, stored).canonicalFile
				if (!target.startsWith(root)) throw IOException("Entry outside the destination: ${child.name}")
				val header = child.header
				if (header == null || header.isDirectory) pending.addLast(child to target)
				else zip.extractFile(header, root.path, File(folder, stored).relativeTo(root).invariantSeparatorsPath, parameters)
			}
			// Ordinary packs have exactly the same names and folder layout as before.
			if (collisions) PackFileNames.write(folder, names)
		}
	}

	/** A Unicode path is authoritative only when its version and original-name checksum match. */
	private fun unicodePath(header: FileHeader, rawName: ByteArray): String? {
		if (header.isFileNameUTF8Encoded) return null
		val checksum = CRC32().apply { update(rawName) }.value
		for (extra in header.extraDataRecords.orEmpty()) {
			if (extra.header != 0x7075L) continue
			val data = extra.data ?: continue
			if (data.size < 5 || data[0] != 1.toByte()) continue
			val storedChecksum = ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
			if (storedChecksum != checksum) continue
			try {
				return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data, 5, data.size - 5)).toString()
			} catch (_: CharacterCodingException) {
				// Invalid extra data falls back to the ordinary entry name.
			}
		}
		return null
	}
}
