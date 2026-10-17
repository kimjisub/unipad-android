package com.kimjisub.launchpad.chainrelease

import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Case
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Input
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Pad
import com.kimjisub.launchpad.chainrelease.ChainReleaseCorpus.Step

/**
 * Plays the cases of [ChainReleaseCorpus] through one layer of the app ([Driver]) and checks every
 * step. Playback is judged from the audio engine's requests: each play is a start, and a stop
 * request counts only against a voice that is still playing, since a press also asks to stop the
 * pad's previous voice, which has usually ended long before. With [lights] the pack's LED animations
 * are judged too: the cells lit must be those of the active playbacks.
 */
class ChainReleaseReplay(private val driver: Driver, private val lights: ChainReleaseLights? = null) {
	interface Driver {
		/**
		 * Whether the layer runs on a virtual clock. Without one, the natural end of finite samples, a
		 * checkpoint between two timers and the chain just before a pack's timer moves it cannot be
		 * pinned to the corpus's milliseconds and are not checked.
		 */
		val virtualTime: Boolean
		fun advanceTo(atMs: Long)
		fun press(inputId: String, pad: Pad)
		/**
		 * Ends the press of [inputId]. [stale] when that press already ended; returns false when this
		 * layer cannot deliver such an event at all.
		 */
		fun release(inputId: String, cancelled: Boolean, stale: Boolean): Boolean
		fun tapChain(chain: Int)
		/** Waits until [problem] returns null and fails with its last message otherwise. */
		fun await(problem: () -> String?)
		/** Lets queued input and runner work finish, before checking nothing else happened. */
		fun settle()
		fun chain(): Int
		fun pressedPads(): Set<Pad>
		/** Every play request so far, as (file name, loop count with -1 for infinite). A voice's stop key is its 1-based position here. */
		fun plays(): List<Pair<String, Int>>
		/** Every stop key requested so far. */
		fun stops(): List<Int>
		/** Pad cells an LED animation lights now; asked only when the replay has [ChainReleaseLights]. */
		fun litCells(): Set<Pad> = emptySet()
		/** Called once a step passed, with a name like `CR-001-4` for its record. */
		fun stepChecked(name: String) = Unit
	}

	private class Voice(val label: String, val pad: Pad, val infinite: Boolean, val endsAtMs: Long)

	/** Runs [case] from a freshly opened pack and returns one line per step for the run's record. */
	fun run(case: Case): List<String> {
		val record = ArrayList<String>()
		val voices = HashMap<Int, Voice>()
		val playing = LinkedHashSet<Int>()
		val pads = HashMap<String, Pad>()
		val held = HashSet<String>()
		val cells = HashMap<String, Pad>()
		var seenPlays = driver.plays().size
		var seenStops = driver.stops().size

		case.steps.forEachIndexed { index, step ->
			val name = "${case.id} step ${index + 1} at ${step.atMs} ms ${step.input}"
			if (!driver.virtualTime && step.input == Input.Checkpoint && case.steps.getOrNull(index + 1)?.input == Input.Checkpoint) {
				record += "$name: not checked without a virtual clock"
				return@forEachIndexed
			}
			lights?.let { for (start in step.expected.starts) cells[start.playbackId] = it.cellOf(step.expected.chain, start.pad, start.file) }
			driver.advanceTo(step.atMs)
			val delivered = when (val input = step.input) {
				is Input.Press -> {
					pads[input.inputId] = input.pad
					held += input.inputId
					driver.press(input.inputId, input.pad)
					true
				}
				is Input.Release -> driver.release(input.inputId, input.cancelled, stale = !held.remove(input.inputId))
				is Input.ChainButton -> {
					driver.tapChain(input.chain)
					true
				}
				Input.Checkpoint -> true
			}
			// A pack's timer changes the chain before the next input; only a virtual clock can hold it still to look.
			val chainMovesOnTimer = case.steps.drop(index + 1).takeWhile { it.input == Input.Checkpoint }.any { it.expected.chain != step.expected.chain }
			val ended = if (driver.virtualTime) playing.filter { voices.getValue(it).let { v -> !v.infinite && v.endsAtMs <= step.atMs } }.toSet() else emptySet()

			fun problem(): String? {
				val expected = step.expected
				val newPlays = driver.plays().drop(seenPlays)
				if (newPlays.size != expected.starts.size) return "$name: ${newPlays.size} plays, expected ${expected.starts}"
				expected.starts.zip(newPlays).forEach { (start, play) ->
					val loop = start.plays?.minus(1) ?: -1
					if (play != start.file to loop) return "$name: played $play, expected ${start.file} with loop $loop for ${start.playbackId}"
				}
				val stopped = (driver.stops().drop(seenStops).toSet() intersect playing) - ended
				val cutOff = stopped.map(voices::getValue).filter { !it.infinite && (step.input as? Input.Press)?.pad != it.pad }
				if (cutOff.isNotEmpty()) return "$name: stopped finite ${cutOff.map { it.label }}"
				// A finite voice stopped by a new press of its own pad is restarted, not released.
				val releaseStops = stopped.map(voices::getValue).filter { it.infinite }.map { it.label }.toSet()
				if (releaseStops != expected.releaseStops) return "$name: stopped $releaseStops, expected ${expected.releaseStops}"
				if (driver.virtualTime) {
					val naturalEnds = ended.map { voices.getValue(it).label }.toSet()
					if (naturalEnds != expected.naturalEnds) return "$name: ended $naturalEnds, expected ${expected.naturalEnds}"
				}
				val started = expected.starts.associate { it.playbackId to (it.plays == null) }
				val labels = (playing - ended - stopped).associate { voices.getValue(it).label to voices.getValue(it).infinite } + started
				val active = labels.filter { driver.virtualTime || it.value }.keys
				val expectedActive = expected.active.filter { driver.virtualTime || labels[it] != false }.toSet()
				if (active != expectedActive) return "$name: playing $active, expected $expectedActive"
				lights?.let {
					// Without a virtual clock a finite animation may end a little early or late, so only endless ones are judged.
					val judged = if (driver.virtualTime) it.all else it.endless
					val lit = driver.litCells() intersect judged
					val expectedLit = expected.active.map(cells::getValue).filter(judged::contains).toSet()
					if (lit != expectedLit) {
						fun named(pads: Set<Pad>) = pads.map { pad -> cells.filterValues { it == pad }.keys.sorted().joinToString("/").ifEmpty { "$pad" } }.toSet()
						return "$name: lights of ${named(lit)} lit, expected ${named(expectedLit)}"
					}
				}
				val chain = driver.chain()
				if (chain != expected.chain && (driver.virtualTime || !chainMovesOnTimer)) return "$name: chain ${chain + 1}, expected ${expected.chain + 1}"
				val pressed = driver.pressedPads()
				val expectedPressed = expected.pressedInputs.map(pads::getValue).toSet()
				if (pressed != expectedPressed) return "$name: pressed lights $pressed, expected $expectedPressed"
				return null
			}

			driver.await(::problem)
			driver.settle()
			problem()?.let { throw AssertionError("After settling, $it") }

			val newKeys = seenPlays + 1..driver.plays().size
			val stopped = (driver.stops().drop(seenStops).toSet() intersect playing) - ended
			playing -= ended + stopped
			step.expected.starts.zip(newKeys).forEach { (start, key) ->
				voices[key] = Voice(start.playbackId, start.pad, start.plays == null, step.atMs + SAMPLE_MS * (start.plays ?: 0))
				playing += key
			}
			seenPlays = driver.plays().size
			seenStops = driver.stops().size
			driver.stepChecked("${case.id}-${index + 1}")
			record += if (delivered) "$name: as expected" else "$name: not deliverable on this layer; state unchanged as expected"
		}
		return record
	}

	private companion object {
		/** Every sample in the corpus is 100 ms long. */
		const val SAMPLE_MS = 100L
	}
}
