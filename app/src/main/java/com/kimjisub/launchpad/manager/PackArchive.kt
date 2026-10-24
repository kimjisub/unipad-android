package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.unipack.LegacyText
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
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
	 * name, so names are read as ISO-8859-1, which keeps each byte as one char, and every file is
	 * extracted under its decoded name.
	 */
	fun extract(archive: File, destination: File) {
		val root = destination.canonicalFile
		ZipFile(archive).use { zip ->
			zip.charset = Charsets.ISO_8859_1
			for (header in zip.fileHeaders) {
				val rawName = header.fileName.toByteArray(Charsets.ISO_8859_1)
				val name = unicodePath(header, rawName) ?: LegacyText.decode(rawName)
				if (header.isDirectory) {
					val dir = File(root, name).canonicalFile
					if (!dir.startsWith(root)) throw IOException("Entry outside the destination: $name")
					dir.mkdirs()
				} else {
					zip.extractFile(header, root.path, name)
				}
			}
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
