package com.kimjisub.launchpad.fixture

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds Unix symlink entries without requiring the fixture filesystem to support symlinks. */
object SymlinkArchive {
    fun write(archive: File, entries: Map<String, String>, links: Set<String>): File {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val end = bytes.size - 22
        var offset = buffer.getInt(end + 16)
        repeat(entries.size) {
            check(buffer.getInt(offset) == 0x02014b50)
            val nameLength = buffer.getShort(offset + 28).toInt() and 0xffff
            val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)
            if (name in links) {
                bytes[offset + 5] = 3 // Unix creator
                buffer.putInt(offset + 38, 0xa1ff0000.toInt()) // S_IFLNK | 0777
            }
            offset += 46 + nameLength + (buffer.getShort(offset + 30).toInt() and 0xffff) +
                (buffer.getShort(offset + 32).toInt() and 0xffff)
        }
        return archive.apply { writeBytes(bytes) }
    }
}
