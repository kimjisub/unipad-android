package com.kimjisub.launchpad.analytics

/**
 * One visit to the play screen: `pack_load` once it is read and its sounds are ready (or failed, or
 * the person left first), then `play_start` at the first accepted pad press (human or auto play) and `play_end` on leaving.
 * `play_first_input` records the first human press independently, including after auto play, and what it
 * was pressed on ([InputSource]). Every call
 * outside the expected order is ignored, so repeated callbacks, a screen that is rebuilt around the
 * same session, or auto play repeating cannot count twice, and leaving before the first sound sends
 * neither `play_start` nor `play_end`.
 *
 * Going to the background and coming back stays in the same session; only leaving the screen for
 * good ends it, so the `play_end` duration includes time spent in the background.
 *
 * `play_start` also carries the last known [ScreenLayout] from [screenLayoutChanged], when there is one.
 *
 * Durations come from [nanoTime], which must be monotonic.
 */
class PlaySessionTracker internal constructor(
	private val nanoTime: () -> Long,
	private val log: (String, Map<String, String>) -> Unit,
) {
	private sealed interface State {
		data object Idle : State
		data class Loading(val startedAt: Long) : State
		data object Loaded : State
		data class Playing(val startedAt: Long) : State
		data object Finished : State
	}

	private class Event(val name: String, val parameters: Map<String, String>)

	private val lock = Any()
	private var state: State = State.Idle
	private var humanInputRecorded = false
	private var screenLayout: ScreenLayout? = null

	/**
	 * The play screen's window was laid out anew: created, rotated, resized or moved in or out of
	 * multi-window. A null layout (a moment of undefined size) keeps the one known before.
	 */
	fun screenLayoutChanged(layout: ScreenLayout?) = synchronized(lock) { if (layout != null) screenLayout = layout }

	fun loadStarted() = transition {
		if (state is State.Idle) state = State.Loading(nanoTime())
		null
	}

	fun loadSucceeded() = transition {
		val loading = state as? State.Loading ?: return@transition null
		state = State.Loaded
		Event(
			UsageEvent.PACK_LOAD,
			mapOf(
				UsageParam.RESULT to UsageResult.SUCCESS.value,
				UsageParam.DURATION_BUCKET to DurationBucket.label(nanoTime() - loading.startedAt),
			),
		)
	}

	/** A partial sound failure records the failed load but can still leave a playable session. */
	fun loadFailed(errorType: UsageErrorType, canPlay: Boolean = false) = transition {
		if (state !is State.Loading) return@transition null
		state = if (canPlay) State.Loaded else State.Finished
		Event(
			UsageEvent.PACK_LOAD,
			mapOf(
				UsageParam.RESULT to UsageResult.FAILURE.value,
				UsageParam.ERROR_TYPE to errorType.value,
			),
		)
	}

	/** [source] is what a person's press ([PlayTrigger.PAD]) came from; auto play has none. */
	fun playTriggered(trigger: PlayTrigger, source: InputSource? = null) {
		val events = synchronized(lock) {
			if (state !is State.Loaded && state !is State.Playing) return
			if (state is State.Playing && (trigger != PlayTrigger.PAD || humanInputRecorded)) return
			buildList {
				if (state is State.Loaded) {
					state = State.Playing(nanoTime())
					add(Event(UsageEvent.PLAY_START, mapOf(UsageParam.TRIGGER to trigger.value) + screenLayout?.parameters.orEmpty()))
				}
				if (trigger == PlayTrigger.PAD && !humanInputRecorded) {
					humanInputRecorded = true
					val parameters = mapOf(UsageParam.TRIGGER to trigger.value) +
						listOfNotNull(source?.let { UsageParam.INPUT_SOURCE to it.value })
					add(Event(UsageEvent.PLAY_FIRST_INPUT, parameters))
				}
			}
		}
		// Both flags are settled under the same lock; reporting cannot block the MIDI state lock.
		events.forEach { log(it.name, it.parameters) }
	}

	/** The person left the play screen for good. */
	fun ended() = transition {
		val event = when (val current = state) {
			is State.Loading -> Event(UsageEvent.PACK_LOAD, mapOf(UsageParam.RESULT to UsageResult.CANCELLED.value))
			is State.Playing -> Event(
				UsageEvent.PLAY_END,
				mapOf(UsageParam.DURATION_BUCKET to DurationBucket.label(nanoTime() - current.startedAt)),
			)
			else -> null
		}
		state = State.Finished
		event
	}

	/** Callbacks arrive from the main, IO and MIDI threads; the event is sent after the lock is released. */
	private inline fun transition(change: () -> Event?) {
		val event = synchronized(lock) { change() }
		event?.let { log(it.name, it.parameters) }
	}
}
