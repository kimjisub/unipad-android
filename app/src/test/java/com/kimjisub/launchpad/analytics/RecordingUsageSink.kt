package com.kimjisub.launchpad.analytics

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A stand-in for Firebase that keeps every event in memory, so tests never reach a server. */
class RecordingUsageSink : UsageEventSink {
	data class Event(val name: String, val parameters: Map<String, String>) {
		override fun toString() = "$name$parameters"
	}

	private val recorded = CopyOnWriteArrayList<Event>()
	private val onEvent = CopyOnWriteArrayList<(Event) -> Unit>()

	val events: List<Event> get() = recorded.toList()
	val analytics = UsageAnalytics(this)

	override fun log(name: String, parameters: Map<String, String>) {
		val event = Event(name, parameters)
		recorded += event
		onEvent.forEach { it(event) }
	}

	/** Runs [check] on the thread that logs each event, before the event is stored. */
	fun observe(check: (Event) -> Unit) {
		onEvent += check
	}

	fun named(name: String) = events.filter { it.name == name }

	/** Blocks until an event called [name] arrives from another thread. */
	fun awaitEvent(name: String, timeoutMs: Long = 10_000): Event {
		val arrived = CountDownLatch(1)
		observe { if (it.name == name) arrived.countDown() }
		if (recorded.none { it.name == name }) {
			check(arrived.await(timeoutMs, TimeUnit.MILLISECONDS)) { "no $name event; got $events" }
		}
		return named(name).first()
	}

	fun pack(name: String, vararg parameters: Pair<String, String>) = Event(name, mapOf(*parameters))
}
