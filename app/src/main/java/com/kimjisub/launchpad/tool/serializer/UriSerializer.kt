package com.kimjisub.launchpad.tool.serializer

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(forClass = Uri::class)
object UriSerializer : KSerializer<Uri> {
	override fun deserialize(decoder: Decoder): Uri {
		return Uri.parse(decoder.decodeString())
	}

	override fun serialize(encoder: Encoder, value: Uri) {
		encoder.encodeString(value.toString())
	}
}