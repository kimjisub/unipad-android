package com.kimjisub.launchpad.unipack

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Packs made on Korean Windows before UTF-8 became its default keep their text files and archive
 * entry names in CP949 ("ANSI" in old Notepad). Text is read as UTF-8 and, only when it is not valid
 * UTF-8, as CP949, as iOS does.
 */
object LegacyText {
	/** CP949 (a superset of EUC-KR) under the name this runtime knows it by; EUC-KR as a last resort. */
	val CP949: Charset = listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")
		.firstNotNullOf { name -> runCatching { Charset.forName(name) }.getOrNull() }

	fun decode(bytes: ByteArray): String = utf8OrNull(bytes) ?: String(bytes, CP949)

	private fun utf8OrNull(bytes: ByteArray): String? = try {
		Charsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(bytes))
			.toString()
	} catch (e: CharacterCodingException) {
		null
	}
}
