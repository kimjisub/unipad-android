package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.manager.LaunchpadColor
import org.junit.Assert.*
import org.junit.Test

class PeripheralPadRulesTest {
    @Test fun allEightyButtonsRoundTripWithoutUsbHeaders() {
        val addresses = (0 until 80).map { Mk2Layout.address(it)!! }
        assertEquals(80, addresses.toSet().size)
        addresses.forEachIndexed { index, address ->
            assertEquals(index, Mk2Layout.index(address.status, address.number))
            val down = Mk2Layout.message(index, true)!!
            val up = Mk2Layout.message(index, false)!!
            assertEquals(3, down.size)
            assertEquals(127, down[2].toInt())
            assertEquals(0, up[2].toInt())
            assertEquals(if (index in 64..71) 0xb0 else 0x80, up[0].toInt() and 255)
        }
        assertEquals(81, addresses[0].number)
        assertEquals(18, addresses[63].number)
        assertEquals(104, addresses[64].number)
        assertEquals(111, addresses[71].number)
        assertEquals(89, addresses[72].number)
        assertEquals(19, addresses[79].number)
        assertNull(Mk2Layout.address(-1))
        assertNull(Mk2Layout.address(80))
        assertNull(Mk2Layout.index(0x91, 81))
        assertNull(Mk2Layout.index(0x90, 91))
        assertNull(Mk2Layout.index(0xb0, 103))
    }

    @Test fun splitRunningStatusRealtimeAndSystemMessagesKeepBoundaries() {
        val received = mutableListOf<List<Int>>()
        val parser = MidiByteStream { status, number, value -> received.add(listOf(status, number, value)) }
        parser.feed(byteArrayOf(0x90.toByte(), 81))
        parser.feed(byteArrayOf(0xf8.toByte(), 5, 82, 6, 0xc0.toByte(), 7, 8, 0x90.toByte(), 83, 9))
        assertEquals(listOf(listOf(0x90,81,5), listOf(0x90,82,6), listOf(0x90,83,9)), received)
        parser.feed(byteArrayOf(0xf1.toByte(), 3, 84, 10))
        assertEquals(3, received.size)
        parser.feed(byteArrayOf(0x90.toByte(), 81, 0x91.toByte(), 82, 7, 0x90.toByte(), 83, 8))
        assertEquals(listOf(0x91,82,7), received[3])
        assertEquals(listOf(0x90,83,8), received[4])
        parser.feed(byteArrayOf(0x90.toByte(), 81, 5), -1, 3)
        assertEquals(5, received.size)
    }

    @Test fun oversizedSysexStoresNoPayloadAndRecoversAtNewStatusOrEnd() {
        val received = mutableListOf<Int>()
        val parser = MidiByteStream { _, number, _ -> received.add(number) }
        parser.feed(byteArrayOf(0xf0.toByte()))
        repeat(10000) { parser.feed(byteArrayOf(1, 0xf8.toByte(), 2)) }
        assertTrue(parser.sysexBytes <= 4096)
        parser.feed(byteArrayOf(0x90.toByte(), 81, 5, 0xf0.toByte(), 1, 0xf7.toByte(), 82, 6, 0x90.toByte(), 83, 7))
        assertEquals(listOf(81,83), received)
    }

    @Test fun lightsUseAllPaletteEntriesAndNeverOwnTouches() {
        val lights = PeripheralLights()
        for (color in 0..127) {
            lights.accept(0x90, 81, color)
            assertEquals(color, lights.snapshot()[0])
            assertEquals(LaunchpadColor.ARGB[color], lights.argb(0))
        }
        lights.accept(0x80,81,120)
        assertEquals(0, lights.snapshot()[0])
        lights.accept(0x90,81,3)
        lights.accept(0x90,81,0)
        lights.accept(0xb0,104,25)
        lights.accept(0x91,81,127)
        lights.accept(0x90,81,128)
        lights.accept(0xb0,103,127)
        assertEquals(0, lights.snapshot()[0])
        assertEquals(25, lights.snapshot()[64])
        repeat(10000) { lights.accept(0x90,81,it % 128) }
        assertTrue(lights.drainChanges().size <= 80)
        assertTrue(lights.drainChanges().isEmpty())
        lights.clear()
        assertTrue(lights.snapshot().all { it == 0 })
    }

    @Test fun twoFingersMoveOutsideAndCancelReleaseOnlyLastOwner() {
        val pointers = PadPointers()
        assertEquals(listOf(PadChange(0,true)), pointers.move(4,0))
        assertTrue(pointers.move(5,0).isEmpty())
        assertEquals(listOf(PadChange(1,true)), pointers.move(4,1))
        assertEquals(listOf(PadChange(0,false)), pointers.move(5,null))
        assertEquals(listOf(PadChange(1,false),PadChange(2,true)), pointers.move(4,2))
        assertEquals(listOf(PadChange(2,false)), pointers.cancel())
        assertTrue(pointers.cancel().isEmpty())
        assertTrue(pointers.move(1,80).isEmpty())
        repeat(1000) { pointers.move(it,0) }
        assertTrue(pointers.pointerCount <= 32)
        assertEquals(listOf(PadChange(0,false)), pointers.cancel())
    }
}
