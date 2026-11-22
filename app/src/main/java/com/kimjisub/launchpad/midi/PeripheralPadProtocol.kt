package com.kimjisub.launchpad.midi

/** Session hooks let the connection manage lifetime without knowing the message contract. */
internal interface MidiSessionRules {
    fun receive(bytes: ByteArray, offset: Int, count: Int)
    fun sent(bytes: ByteArray)
    fun releases(): List<ByteArray>
    fun clearReceived()
}

/** Combines independent message and layout rules, without opening or owning ports. */
internal class PeripheralPadProtocol(private val lights: PeripheralLights) : MidiSessionRules {
    private val active = BooleanArray(Mk2Layout.SIZE)
    private val parser = MidiStreamDecoder { status, number, value -> lights.accept(status,number,value) }
    override fun receive(bytes: ByteArray, offset: Int, count: Int) = parser.accept(bytes,offset,count)
    override fun sent(bytes: ByteArray) {
        if (bytes.size != 3) return
        val status = bytes[0].toInt() and 255
        val index = Mk2Layout.index(status, bytes[1].toInt() and 255) ?: return
        active[index] = status != 0x80 && bytes[2].toInt() != 0
    }
    override fun releases(): List<ByteArray> = active.indices.filter { active[it] }.map { Mk2Layout.message(it,false)!! }
    override fun clearReceived() { lights.clear() }
}
