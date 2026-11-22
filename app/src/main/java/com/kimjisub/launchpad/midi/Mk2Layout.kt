package com.kimjisub.launchpad.midi

/** MIDI 1.0 addresses, independent of USB packet framing and controller state. */
object Mk2Layout {
    const val SIZE = 80
    data class Address(val status: Int, val number: Int)

    fun padNote(row: Int, column: Int): Int? =
        if (row in 0..7 && column in 0..7) 10 * (8 - row) + column + 1 else null

    fun address(index: Int): Address? = when (index) {
        in 0..63 -> Address(0x90, padNote(index / 8, index % 8)!!)
        in 64..71 -> Address(0xb0, 104 + index - 64)
        in 72..79 -> Address(0x90, 89 - 10 * (index - 72))
        else -> null
    }

    fun index(status: Int, number: Int): Int? {
        if (status == 0xb0) return if (number in 104..111) 64 + number - 104 else null
        if (status != 0x90 && status != 0x80) return null
        val row = 8 - number / 10
        val column = number % 10 - 1
        return when {
            row !in 0..7 -> null
            column in 0..7 -> row * 8 + column
            column == 8 -> 72 + row
            else -> null
        }
    }

    fun message(index: Int, pressed: Boolean): ByteArray? {
        val address = address(index) ?: return null
        val status = if (!pressed && address.status == 0x90) 0x80 else address.status
        return byteArrayOf(status.toByte(), address.number.toByte(), if (pressed) 127 else 0)
    }
}
