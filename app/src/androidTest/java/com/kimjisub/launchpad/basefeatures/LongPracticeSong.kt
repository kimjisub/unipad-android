package com.kimjisub.launchpad.basefeatures

import kotlin.random.Random

/**
 * An autoPlay shaped like the recording of a whole song on an 8x8 pack with [CHAINS] chains: it moves
 * up, down and back across the chains, mixes single notes, chords and `touch` notes, and releases each
 * group's pads after the next group is pressed, as a recorded performance does. The pads come from a
 * fixed seed, so every run plays the same song.
 *
 * [steps] is what step practice must show, worked out while the script is written rather than read
 * back through the app's parser. Every command takes one autoPlay element except `touch`, which takes two.
 */
class LongPracticeSong {
    /**
     * One group of pads pressed together. [pads] are pad indices (row * 8 + column), [progress] the
     * element the runner stops at while it shows them: the delay that follows the group. A group that
     * opens a new chain first shows only that chain, stopped at the group's first press ([chainHintProgress]).
     */
    data class Step(val chain: Int, val pads: Set<Int>, val progress: Int, val chainHintProgress: Int?)

    val script: String
    val elementCount: Int
    val steps: List<Step>

    init {
        val random = Random(SEED)
        val lines = mutableListOf<String>()
        val steps = mutableListOf<Step>()
        var elements = 0
        fun add(line: String) {
            lines += line
            elements += if (line.startsWith("t ")) 2 else 1
        }
        fun command(name: String, pad: Int) = "$name ${pad / SIZE + 1} ${pad % SIZE + 1}"

        var chain = 0
        var held = emptySet<Int>()
        add("d 480")
        for ((segment, segmentChain) in CHAIN_ORDER.withIndex()) {
            var chainHintProgress: Int? = null
            if (segmentChain != chain) {
                // The previous chain's last pads are let go before the move, as a recording does.
                held.forEach { add(command("f", it)) }
                held = emptySet()
                add("c ${segmentChain + 1}")
                chain = segmentChain
                chainHintProgress = elements
            }
            repeat(GROUPS_PER_CHAIN) { group ->
                val size = when {
                    group % 11 == 7 -> 3
                    group % 5 == 2 -> 2
                    else -> 1
                }
                val pads = mutableSetOf<Int>()
                while (pads.size < size) random.nextInt(SIZE * SIZE).takeIf { it !in held }?.let(pads::add)
                val touch = size == 1 && group % 4 == 3
                pads.forEach { add(command(if (touch) "t" else "o", it)) }
                steps += Step(chain, pads, elements, chainHintProgress.takeIf { group == 0 })
                add("d ${PRESS_GAPS_MS[random.nextInt(PRESS_GAPS_MS.size)]}")
                held.forEach { add(command("f", it)) }
                held = if (touch) emptySet() else pads
                add("d ${RELEASE_GAPS_MS[random.nextInt(RELEASE_GAPS_MS.size)]}")
            }
            if (segment == CHAIN_ORDER.lastIndex) held.forEach { add(command("f", it)) }
        }
        add("d 1200")
        script = lines.joinToString("\n", postfix = "\n")
        elementCount = elements
        this.steps = steps
    }

    companion object {
        const val CHAINS = 8
        private const val SIZE = 8
        private const val SEED = 20261123
        private const val GROUPS_PER_CHAIN = 14
        private val CHAIN_ORDER = listOf(0, 1, 3, 2, 5, 4, 7, 6, 1, 0)
        // Every gap after a press is past the 50 ms within which the runner shows presses as one group.
        private val PRESS_GAPS_MS = listOf(60, 95, 120, 180, 240)
        private val RELEASE_GAPS_MS = listOf(25, 40, 80, 130)
    }
}
