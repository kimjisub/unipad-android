package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniPackDownloadFractionTest {
	@Test fun anUnknownSizeHasNoFraction() {
		// OkHttp reports -1 for a chunked response; 0 is a server that sent no length either.
		assertNull(UniPackDownloader.fraction(4_404_019, -1))
		assertNull(UniPackDownloader.fraction(4_404_019, 0))
	}

	@Test fun aKnownSizeGivesTheShareReceived() {
		assertEquals(0f, UniPackDownloader.fraction(0, 2_000)!!, 0f)
		assertEquals(0.5f, UniPackDownloader.fraction(1_000, 2_000)!!, 0f)
		assertEquals(1f, UniPackDownloader.fraction(2_000, 2_000)!!, 0f)
	}

	@Test fun moreThanAnnouncedStaysFull() {
		assertEquals(1f, UniPackDownloader.fraction(2_500, 2_000)!!, 0f)
	}
}
