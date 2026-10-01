package com.kimjisub.launchpad.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PackSearchTest {

	private data class Pack(val title: String, val producer: String)

	private val packs = listOf(
		Pack("Marshmello - Happier (Ft. Bastille)", "GabrielLeao"),
		Pack("Alan Walker - Faded", "Axri Izdihar"),
		Pack("Marshmello & Selena Gomez - Wolves", "Axri Izdihar"),
		Pack("아이유 - 좋은 날", "김지섭"),
		Pack("Noé - Decomposed accent", "Someone"),
	)

	private fun search(query: String) = PackSearch.filter(packs, query) { listOf(it.title, it.producer) }

	private fun titles(query: String) = search(query).map { it.title }

	@Test
	fun blankQueryKeepsEveryPackInOrder() {
		assertSame(packs, search(""))
		assertSame(packs, search("   "))
	}

	@Test
	fun matchesTitleSubstringIgnoringCase() {
		val expected = listOf(packs[0].title, packs[2].title)
		assertEquals(expected, titles("marsh"))
		assertEquals(expected, titles("MARSH"))
	}

	@Test
	fun ignoresLeadingAndTrailingWhitespace() {
		assertEquals(listOf(packs[0].title, packs[2].title), titles(" marsh "))
	}

	@Test
	fun matchesProducer() {
		assertEquals(listOf(packs[1].title, packs[2].title), titles("izdihar"))
	}

	@Test
	fun matchesKoreanPartially() {
		assertEquals(listOf(packs[3].title), titles("김지"))
		assertEquals(listOf(packs[3].title), titles("좋은"))
	}

	@Test
	fun composedQueryFindsDecomposedTitle() {
		assertEquals(listOf(packs[4].title), titles("Noé"))
		assertEquals(listOf(packs[4].title), titles("NOÉ"))
	}

	@Test
	fun noMatchIsEmpty() {
		assertEquals(emptyList<String>(), titles("zzzz"))
	}

	@Test
	fun keepsGivenSortOrder() {
		val reversed = packs.reversed()
		val result = PackSearch.filter(reversed, "marsh") { listOf(it.title, it.producer) }
		assertEquals(listOf(packs[2], packs[0]), result)
	}
}
