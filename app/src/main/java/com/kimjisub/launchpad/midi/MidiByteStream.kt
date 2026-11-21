package com.kimjisub.launchpad.midi

/** A bounded MIDI 1.0 stream decoder. SysEx payload is never retained. */
internal class MidiByteStream(private val receive: (Int, Int, Int) -> Unit) {
    private var status = 0
    private var first = -1
    private var inSysex = false
    var sysexBytes = 0
        private set

    fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset) {
        if (offset < 0 || count < 0 || offset > bytes.size || count > bytes.size - offset) return
        for (i in offset until offset + count) {
            val value = bytes[i].toInt() and 255
            if (value >= 0xf8) continue
            if (value >= 0x80) {
                inSysex = value == 0xf0
                sysexBytes = if (inSysex) 1 else 0
                status = if (value < 0xf0) value else 0
                first = -1
                continue
            }
            if (inSysex) {
                sysexBytes = (sysexBytes + 1).coerceAtMost(4096)
                continue
            }
            if (status == 0) continue
            val command = status and 0xf0
            if (command == 0xc0 || command == 0xd0) continue // One-byte messages, including running status.
            if (first == -1) first = value else {
                if (command == 0x80 || command == 0x90 || command == 0xb0) receive(status, first, value)
                first = -1
            }
        }
    }
}
