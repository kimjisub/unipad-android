package com.kimjisub.launchpad.ledtiming

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent

/** A Launchpad LED: a pad, or a round/logo LED when [x] is -1 (its index is [y]), as LedRunner addresses them. */
data class LedTarget(val x: Int, val y: Int) {
	override fun toString() = if (x == -1) "round $y" else "pad $x,$y"
}

/** [code] for [target] at [ms] after autoplay starts. */
interface LedCommand {
	val ms: Double
	val target: LedTarget
	val code: Int
}

/** One LED command the pack asks for: [code] on [target], [t0] ms after autoplay starts. */
data class IntendedCommand(val t0: Long, override val target: LedTarget, override val code: Int) : LedCommand {
	override val ms get() = t0.toDouble()
}

class IntendedTimeline(
	val commands: List<IntendedCommand>,
	/** When autoplay presses each pad, in order. */
	val pressTimes: List<Long>,
	/** The last autoplay element's time. */
	val autoPlayLengthMs: Long,
	/**
	 * Endless animations with no delay in a pass. LedRunner plays one pass per tick, which no timeline can
	 * state, so after the first pass this one plays nothing more of them but keeps the LEDs they lit theirs
	 * until a press or release ends them.
	 */
	val delaylessEndlessAnimations: Int,
) {
	val lastT0: Long get() = commands.lastOrNull()?.t0 ?: 0

	/** The most commands intended within any 1000 ms. */
	fun peakPerSecond(): Int {
		var peak = 0
		var from = 0
		for (to in commands.indices) {
			while (commands[to].t0 - commands[from].t0 >= 1000) from++
			peak = maxOf(peak, to - from + 1)
		}
		return peak
	}

	fun averagePerSecond(): Double = if (lastT0 == 0L) 0.0 else commands.size * 1000.0 / lastT0
}

/** The sum of the autoplay's delays: when its last element plays. */
fun UniPack.autoPlayLengthMs(): Long =
	autoPlayTable?.elements.orEmpty().sumOf { (it as? AutoPlay.Element.Delay)?.delay?.toLong() ?: 0L }

/**
 * The LED commands a pack asks for when autoplay plays it with no lag: each autoplay element at the
 * sum of the delays before it, and each keyLED event at its press time plus the delays before it in
 * the animation. Animations follow LedRunner's rules: an LED belongs to the pad and chain whose
 * animation lit it last, and only that owner's off turns it off; a press ends the running animation
 * of the same pad and chain and turns off every LED it owns; a release ends only endless (loop 0)
 * animations. LedRunner plays these on 4 ms ticks; here nothing waits for a tick, and events due in
 * the same millisecond play animations in press order, then autoplay.
 *
 * A keyLED chain event (`c`) moves the chain later presses and the final release use, as the play
 * screen applies it.
 *
 * [pack] must be loaded for this alone: its multi-mapping queues rotate as the play screen's do.
 * [releaseAllAtMs] releases every pad on the last chain, as PlayActivityViewModel.padInit does.
 * Nothing after [giveUpAtMs] is played, as [LedPathRun] stops recording then: an endless animation no
 * release reaches would otherwise run forever.
 */
fun intendedTimeline(pack: UniPack, releaseAllAtMs: Long? = null, giveUpAtMs: Long = Long.MAX_VALUE): IntendedTimeline =
	TimelineSimulation(pack).run(releaseAllAtMs, giveUpAtMs)

private class TimelineSimulation(private val pack: UniPack) {
	private data class Owner(val x: Int, val y: Int, val chain: Int)

	private class Animation(val owner: Owner, val led: LedAnimation, var due: Long) {
		var index = 0
		var loopProgress = 0
		var playing = true
	}

	private val pads = Array(pack.buttonX) { arrayOfNulls<Owner>(pack.buttonY) }
	private val rounds = arrayOfNulls<Owner>(ROUND_LED_COUNT)
	private val running = ArrayList<Animation>()
	private val out = ArrayList<IntendedCommand>()
	private val presses = ArrayList<Long>()
	private var delayless = 0
	private var chain = 0

	fun run(releaseAllAtMs: Long?, giveUpAtMs: Long): IntendedTimeline {
		val elements = pack.autoPlayTable?.elements ?: arrayListOf()
		var next = 0
		var autoPlayTime = 0L
		var released = releaseAllAtMs == null
		while (true) {
			while (next < elements.size && elements[next] is AutoPlay.Element.Delay) {
				autoPlayTime += (elements[next] as AutoPlay.Element.Delay).delay
				next++
			}
			val autoPlayDue = when {
				next < elements.size -> autoPlayTime
				!released -> maxOf(releaseAllAtMs!!, autoPlayTime)
				else -> Long.MAX_VALUE
			}
			val animationDue = running.minOfOrNull { it.due } ?: Long.MAX_VALUE
			val t = minOf(autoPlayDue, animationDue)
			if (t == Long.MAX_VALUE || t > giveUpAtMs) break

			for (animation in running.toList()) if (animation.due <= t) play(animation, t)
			running.removeAll { !it.playing }

			if (autoPlayDue == t) {
				if (next < elements.size) {
					while (next < elements.size && elements[next] !is AutoPlay.Element.Delay) apply(elements[next++], t)
				} else {
					for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) release(x, y, chain, t)
					released = true
				}
			}
		}
		return IntendedTimeline(out, presses, autoPlayTime, delayless)
	}

	private fun apply(element: AutoPlay.Element, t: Long) {
		when (element) {
			is AutoPlay.Element.On -> {
				chain = element.currChain
				pack.ledPush(chain, element.x, element.y, element.num)
				presses += t
				press(element.x, element.y, t)
			}
			is AutoPlay.Element.Off -> {
				chain = element.currChain
				release(element.x, element.y, chain, t)
			}
			is AutoPlay.Element.Chain -> chain = element.c
			is AutoPlay.Element.Delay -> Unit
		}
	}

	private fun press(x: Int, y: Int, t: Long) {
		val owner = Owner(x, y, chain)
		end(t) { it.owner == owner }
		val led = pack.ledGet(chain, x, y)
		pack.ledPush(chain, x, y)
		if (led != null) running += Animation(owner, led, t)
	}

	private fun release(x: Int, y: Int, chain: Int, t: Long) {
		val owner = Owner(x, y, chain)
		end(t) { it.owner == owner && it.led.loop == 0 }
	}

	private fun end(t: Long, which: (Animation) -> Boolean) {
		val ended = running.filter(which)
		for (animation in ended) {
			for (x in pads.indices) for (y in pads[x].indices) {
				if (pads[x][y] == animation.owner) {
					out += IntendedCommand(t, LedTarget(x, y), 0)
					pads[x][y] = null
				}
			}
			for (y in rounds.indices) {
				if (rounds[y] == animation.owner) {
					out += IntendedCommand(t, LedTarget(-1, y), 0)
					rounds[y] = null
				}
			}
		}
		running.removeAll(ended.toSet())
	}

	/** One animation's events due by [t], as LedRunner.loop plays one state. */
	private fun play(animation: Animation, t: Long) {
		val events = animation.led.ledEvents
		if (events.isEmpty()) {
			animation.playing = false
			return
		}
		var played = 0
		while (true) {
			if (animation.index >= events.size) {
				animation.loopProgress++
				animation.index = 0
			}
			if (animation.led.loop != 0 && animation.led.loop <= animation.loopProgress) {
				animation.playing = false
				return
			}
			if (animation.due > t) return
			// Only an endless animation whose pass has no delay gets here, LedRunner's cap for it.
			if (++played > events.size * animation.led.loop.coerceAtLeast(1) + 1) {
				delayless++
				animation.due = Long.MAX_VALUE
				return
			}
			when (val event = events[animation.index]) {
				is LedEvent.On -> light(event, animation.owner, t)
				is LedEvent.Off -> dim(event, animation.owner, t)
				is LedEvent.Delay -> animation.due += event.delay
				is LedEvent.Chain -> chain = event.chain.coerceIn(0, pack.chain - 1)
			}
			animation.index++
		}
	}

	private fun light(event: LedEvent.On, owner: Owner, t: Long) {
		if (event.x == -1) {
			if (event.y !in rounds.indices) return
			rounds[event.y] = owner
		} else {
			if (event.x !in pads.indices || event.y !in pads[event.x].indices) return
			pads[event.x][event.y] = owner
		}
		out += IntendedCommand(t, LedTarget(event.x, event.y), event.velocity)
	}

	private fun dim(event: LedEvent.Off, owner: Owner, t: Long) {
		if (event.x == -1) {
			if (event.y !in rounds.indices || rounds[event.y] != owner) return
			rounds[event.y] = null
		} else {
			if (event.x !in pads.indices || event.y !in pads[event.x].indices || pads[event.x][event.y] != owner) return
			pads[event.x][event.y] = null
		}
		out += IntendedCommand(t, LedTarget(event.x, event.y), 0)
	}

	private companion object {
		/** LedRunner's round LED table: 32 round buttons, the logo and spares. */
		const val ROUND_LED_COUNT = 36
	}
}
