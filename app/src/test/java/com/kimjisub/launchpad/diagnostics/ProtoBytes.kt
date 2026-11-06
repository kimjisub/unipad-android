package com.kimjisub.launchpad.diagnostics

import java.io.ByteArrayOutputStream

/** Builds protobuf wire-format bytes for tests. */
internal class ProtoWriter {
	val out = ByteArrayOutputStream()

	fun varint(field: Int, value: Long) {
		out.write(protoVarint((field shl 3).toLong()))
		out.write(protoVarint(value))
	}

	fun varint(field: Int, value: Int) = varint(field, value.toLong())

	fun bytes(field: Int, value: ByteArray) {
		out.write(protoVarint((field shl 3 or 2).toLong()))
		out.write(protoVarint(value.size.toLong()))
		out.write(value)
	}

	fun string(field: Int, value: String) = bytes(field, value.toByteArray())
}

internal fun protoMessage(build: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(build).out.toByteArray()

internal fun protoVarint(value: Long): ByteArray {
	val out = ByteArrayOutputStream()
	var rest = value
	while ((rest and 0x7fL.inv()) != 0L) {
		out.write(((rest and 0x7fL) or 0x80L).toInt())
		rest = rest ushr 7
	}
	out.write(rest.toInt())
	return out.toByteArray()
}
