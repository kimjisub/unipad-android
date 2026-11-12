package com.kimjisub.launchpad.manager

import org.junit.Assert.*
import org.junit.Test

class PadOffColorTest {
    @Test fun solidPadKeepsItsExactColor() {
        assertEquals(0xFF2A3A52.toInt(), averageOpaqueColor(sequenceOf(0xFF2A3A52.toInt(), 0xFF2A3A52.toInt())))
    }
    @Test fun alphaWeightsRgbAndTheResultIsOpaque() {
        // Two parts red to one part blue; invisible green contributes nothing.
        assertEquals(0xFFAA0055.toInt(), averageOpaqueColor(sequenceOf(0x80FF0000.toInt(), 0x400000FF, 0x0000FF00)))
    }
    @Test fun transparentAndMissingPixelsHaveNoRepresentativeColor() {
        assertNull(averageOpaqueColor(sequenceOf(0, 0x00FFFFFF)))
        assertNull(averageOpaqueColor(emptySequence()))
    }
    @Test fun accumulationDoesNotOverflowForLargeImages() {
        assertEquals(0xFFFFFFFF.toInt(), averageOpaqueColor(generateSequence { -1 }.take(100_000)))
    }
}
