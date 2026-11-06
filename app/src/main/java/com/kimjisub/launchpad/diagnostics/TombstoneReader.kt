package com.kimjisub.launchpad.diagnostics

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Where a native crash happened, as crash report key values; each value fits one key. */
internal data class NativeCrashTrace(
	val status: String,
	val signal: String = "",
	val frames: String = "",
	val buildIds: String = "",
) {
	companion object {
		/** The crashing thread's frames were read. */
		const val FRAMES = "frames"
		/** The record was read but named no crashing thread frames. */
		const val NO_FRAMES = "no_frames"
		/** The system kept no record (Android 11, or none for this crash). */
		const val NONE = "none"
		const val UNREADABLE = "unreadable"
		const val TOO_LARGE = "too_large"
	}
}

/**
 * Reads the tombstone Android 12 and later attach to a native crash exit, a protobuf message laid out in
 * AOSP `system/core/debuggerd/proto/tombstone.proto`. Only the crashing thread id, the signal number and code
 * and that thread's frames (library name, function name and offset, build ID) are decoded. Everything else
 * may hold user data (registers, memory, memory maps, the abort message, logs, other threads, open files) and
 * is skipped without being decoded.
 */
internal object TombstoneReader {
	const val MAX_FRAMES = 12
	/** Crashlytics keeps the first 1024 characters of a key value; whole lines or entries are dropped instead. */
	const val MAX_VALUE = 1024
	/** Debuggerd writes fields in number order, so the crashing thread comes before memory maps and logs. */
	private const val MAX_READ_BYTES = 4L shl 20
	private const val MAX_STRING_BYTES = 4096
	private const val MAX_FUNCTION_NAME = 120

	// tombstone.proto field numbers.
	private const val TOMBSTONE_TID = 6
	private const val TOMBSTONE_SIGNAL = 10
	private const val TOMBSTONE_THREADS = 16
	private const val SIGNAL_NUMBER = 1
	private const val SIGNAL_NAME = 2
	private const val SIGNAL_CODE = 3
	private const val SIGNAL_CODE_NAME = 4
	private const val MAP_KEY = 1
	private const val MAP_VALUE = 2
	private const val THREAD_BACKTRACE = 4
	private const val FRAME_REL_PC = 1
	private const val FRAME_FUNCTION_NAME = 4
	private const val FRAME_FUNCTION_OFFSET = 5
	private const val FRAME_FILE_NAME = 6
	private const val FRAME_BUILD_ID = 8

	private val signalName = Regex("[A-Z0-9_]{1,32}")
	private val buildId = Regex("[0-9a-fA-F]{8,64}")
	private val codeFile = Regex("""[A-Za-z0-9_.+@!-]{1,96}\.(so|oat|odex|vdex|apk|jar|dex|art)""")
	private val systemBinary = Regex("linker(64)?|app_process(32|64)?")
	private val namedMapping = Regex("""\[[A-Za-z0-9_:. -]{1,64}\]""")

	fun summarize(input: InputStream?): NativeCrashTrace {
		input ?: return NativeCrashTrace(NativeCrashTrace.NONE)
		val tombstone = try {
			parse(ProtoReader(BufferedInputStream(input), MAX_READ_BYTES))
		} catch (e: TraceTooLargeException) {
			return NativeCrashTrace(NativeCrashTrace.TOO_LARGE)
		} catch (e: IOException) {
			return NativeCrashTrace(NativeCrashTrace.UNREADABLE)
		}
		val buildIds = tombstone.frames.mapNotNull { frame -> frame.buildId?.let { "${frame.file}:$it" } }.distinct()
		return NativeCrashTrace(
			if (tombstone.frames.isEmpty()) NativeCrashTrace.NO_FRAMES else NativeCrashTrace.FRAMES,
			tombstone.signal.orEmpty(),
			joinWithin(tombstone.frames.mapIndexed { index, frame -> frame.line(index) }, " | "),
			joinWithin(buildIds, " "),
		)
	}

	private fun joinWithin(parts: List<String>, separator: String): String {
		val joined = StringBuilder()
		for (part in parts) {
			val needed = part.length + if (joined.isEmpty()) 0 else separator.length
			if (joined.length + needed > MAX_VALUE) break
			if (joined.isNotEmpty()) joined.append(separator)
			joined.append(part)
		}
		return joined.toString()
	}

	private class Tombstone(val signal: String?, val frames: List<Frame>)

	private class Frame(val file: String, val relPc: Long, val function: String?, val offset: Long, val buildId: String?) {
		fun line(index: Int) = buildString {
			append('#').append(index.toString().padStart(2, '0')).append(' ').append(file)
			append(" 0x").append(java.lang.Long.toHexString(relPc))
			function?.let { append(" (").append(it).append("+").append(offset).append(')') }
		}
	}

	private fun parse(reader: ProtoReader): Tombstone {
		var tid: Long? = null
		var signal: String? = null
		var frames: List<Frame>? = null
		while (frames == null || signal == null) {
			val tag = reader.tagOrEnd() ?: break
			when (tag.field) {
				TOMBSTONE_TID -> if (tag.wire == VARINT) tid = reader.varint() else reader.skip(tag)
				TOMBSTONE_SIGNAL -> if (tag.wire == LENGTH) signal = readSignal(reader, reader.end()) else reader.skip(tag)
				TOMBSTONE_THREADS ->
					if (tag.wire == LENGTH && tid != null) frames = readThreadEntry(reader, reader.end(), tid) ?: frames
					else reader.skip(tag)
				else -> reader.skip(tag)
			}
		}
		return Tombstone(signal, frames.orEmpty())
	}

	private fun readSignal(reader: ProtoReader, end: Long): String {
		var number = 0L
		var name: String? = null
		var code = 0L
		var codeName: String? = null
		reader.fields(end) { tag ->
			when {
				tag.field == SIGNAL_NUMBER && tag.wire == VARINT -> number = reader.varint()
				tag.field == SIGNAL_CODE && tag.wire == VARINT -> code = reader.varint()
				tag.field == SIGNAL_NAME && tag.wire == LENGTH -> name = reader.string()
				tag.field == SIGNAL_CODE_NAME && tag.wire == LENGTH -> codeName = reader.string()
				else -> reader.skip(tag)
			}
		}
		fun shown(label: String?, value: Long) =
			"${label?.takeIf { signalName.matches(it) } ?: "?"}(${value.toInt()})"
		return "${shown(name, number)} ${shown(codeName, code)}"
	}

	/** A `threads` map entry; the other threads are skipped without decoding their message. */
	private fun readThreadEntry(reader: ProtoReader, end: Long, tid: Long): List<Frame>? {
		var key: Long? = null
		var frames: List<Frame>? = null
		reader.fields(end) { tag ->
			when {
				tag.field == MAP_KEY && tag.wire == VARINT -> key = reader.varint()
				tag.field == MAP_VALUE && tag.wire == LENGTH && key == tid -> frames = readFrames(reader, reader.end())
				else -> reader.skip(tag)
			}
		}
		return frames?.takeIf { key == tid }
	}

	private fun readFrames(reader: ProtoReader, end: Long): List<Frame> {
		val frames = mutableListOf<Frame>()
		reader.fields(end) { tag ->
			if (tag.field == THREAD_BACKTRACE && tag.wire == LENGTH && frames.size < MAX_FRAMES) {
				frames += readFrame(reader, reader.end())
			} else {
				reader.skip(tag)
			}
		}
		return frames
	}

	private fun readFrame(reader: ProtoReader, end: Long): Frame {
		var relPc = 0L
		var function: String? = null
		var offset = 0L
		var file: String? = null
		var id: String? = null
		reader.fields(end) { tag ->
			when {
				tag.field == FRAME_REL_PC && tag.wire == VARINT -> relPc = reader.varint()
				tag.field == FRAME_FUNCTION_OFFSET && tag.wire == VARINT -> offset = reader.varint()
				tag.field == FRAME_FUNCTION_NAME && tag.wire == LENGTH -> function = reader.string()
				tag.field == FRAME_FILE_NAME && tag.wire == LENGTH -> file = reader.string()
				tag.field == FRAME_BUILD_ID && tag.wire == LENGTH -> id = reader.string()
				else -> reader.skip(tag)
			}
		}
		return Frame(fileName(file), relPc, functionName(function), offset, id?.takeIf { buildId.matches(it) })
	}

	/** Only the last part of a code file's path; any other mapped file could be one of the user's own. */
	private fun fileName(path: String?): String {
		val name = path.orEmpty().substringAfterLast('/')
		return name.takeIf {
			codeFile.matches(it) || systemBinary.matches(it) || (path == name && namedMapping.matches(it))
		} ?: "?"
	}

	private fun functionName(name: String?): String? {
		if (name.isNullOrEmpty()) return null
		val printable = name.map { if (it in ' '..'~') it else '?' }.joinToString("")
		return if (printable.length > MAX_FUNCTION_NAME) printable.take(MAX_FUNCTION_NAME - 3) + "..." else printable
	}

	private const val VARINT = 0
	private const val FIXED64 = 1
	private const val LENGTH = 2
	private const val FIXED32 = 5

	private class Tag(val field: Int, val wire: Int)

	private class TraceTooLargeException : IOException()

	/** Protobuf wire format over a stream, never reading past [limit] bytes or a message's end. */
	private class ProtoReader(private val input: InputStream, private val limit: Long) {
		private var position = 0L

		fun tagOrEnd(): Tag? {
			if (position >= limit) throw TraceTooLargeException()
			input.mark(1)
			if (input.read() < 0) return null
			input.reset()
			return tag()
		}

		/** Reads each field of the message ending at [end], which must end exactly there. */
		fun fields(end: Long, read: (Tag) -> Unit) {
			while (position < end) read(tag())
			if (position != end) throw IOException("Field runs past its message")
		}

		private fun tag(): Tag {
			val value = varint()
			val field = value ushr 3
			if (field < 1 || field > Int.MAX_VALUE) throw IOException("Bad field number")
			return Tag(field.toInt(), (value and 7L).toInt())
		}

		fun varint(): Long {
			var result = 0L
			var shift = 0
			while (shift < 64) {
				val byte = byte()
				result = result or ((byte and 0x7f).toLong() shl shift)
				if ((byte and 0x80) == 0) return result
				shift += 7
			}
			throw IOException("Varint too long")
		}

		/** Where the length-delimited field whose tag was just read ends. */
		fun end(): Long {
			val length = length()
			return position + length
		}

		/** The length-delimited field whose tag was just read, or null when it is too long to keep. */
		fun string(): String? {
			val length = length()
			if (length > MAX_STRING_BYTES) {
				skipBytes(length)
				return null
			}
			val bytes = ByteArray(length.toInt())
			for (i in bytes.indices) bytes[i] = byte().toByte()
			return String(bytes, Charsets.UTF_8)
		}

		fun skip(tag: Tag) {
			when (tag.wire) {
				VARINT -> varint()
				FIXED64 -> skipBytes(8)
				LENGTH -> skipBytes(length())
				FIXED32 -> skipBytes(4)
				else -> throw IOException("Unsupported wire type ${tag.wire}")
			}
		}

		private fun length(): Long {
			val length = varint()
			if (length < 0) throw IOException("Bad length")
			if (length > limit - position) throw TraceTooLargeException()
			return length
		}

		private fun skipBytes(count: Long) {
			if (count > limit - position) throw TraceTooLargeException()
			var left = count
			while (left > 0) {
				val skipped = input.skip(left)
				if (skipped > 0) {
					left -= skipped
				} else {
					if (input.read() < 0) throw EOFException()
					left--
				}
			}
			position += count
		}

		private fun byte(): Int {
			if (position >= limit) throw TraceTooLargeException()
			val byte = input.read()
			if (byte < 0) throw EOFException()
			position++
			return byte
		}
	}
}
