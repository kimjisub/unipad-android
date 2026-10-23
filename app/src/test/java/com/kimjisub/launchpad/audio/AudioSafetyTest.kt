package com.kimjisub.launchpad.audio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.ErrorCollector
import kotlinx.serialization.json.*
import java.io.File
import java.io.RandomAccessFile

class AudioSafetyTest {
    @get:Rule val temporary = TemporaryFolder(File(System.getenv("PAPERCLIP_RUN_SCRATCH_DIR")
        ?: System.getenv("PAPERCLIP_SCRATCH_DIR") ?: System.getProperty("java.io.tmpdir")))
    @get:Rule val errors = ErrorCollector()
    private fun corpus() = Json.parseToJsonElement(javaClass.getResource("/audio-safety/cases.json")!!.readText()).jsonObject

    @Test fun integerBoundariesMatchTheSharedCorpus() {
        for (case in corpus().getValue("costs").jsonArray.map { it.jsonObject }) errors.checkSucceeds {
            val facts = AudioFileFacts(case.getValue("rate").jsonPrimitive.int,
                case.getValue("channels").jsonPrimitive.int, case.getValue("frames").jsonPrimitive.long)
            val result = AudioSafety.cost(facts)
            assertEquals(case.getValue("id").jsonPrimitive.content, case["exclusion"]?.jsonPrimitive?.content,
                result.exclusion?.name)
            assertEquals(case.toString(), case["cost"]?.jsonPrimitive?.long ?: 0, result.value)
        }
    }

    @Test fun fileFactsMatchTheSharedCorpusRegardlessOfExtension() {
        for (case in corpus().getValue("files").jsonArray.map { it.jsonObject }) errors.checkSucceeds {
            val name = case.getValue("file").jsonPrimitive.content
            val file = temporary.newFile(name)
            javaClass.getResourceAsStream("/audio-safety/$name")!!.use { input -> file.outputStream().use(input::copyTo) }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(name, case.getValue("sha256").jsonPrimitive.content, digest)
            val result = AudioFileInspector.inspect(file)
            if (case["exclusion"] != null) {
                assertEquals(name, AudioInspection.Rejected(AudioExclusion.valueOf(case.getValue("exclusion").jsonPrimitive.content)), result)
            } else {
                assertTrue("$name: $result", result is AudioInspection.Readable)
                val facts = (result as AudioInspection.Readable).facts
                assertEquals(name, case.getValue("rate").jsonPrimitive.int, facts.sampleRate)
                assertEquals(name, case.getValue("channels").jsonPrimitive.int, facts.channels)
                assertEquals(name, case.getValue("frames").jsonPrimitive.long, facts.frames)
            }
        }
    }

    @Test fun realWavDataDeterminesTheDurationAndTruncationIsRejected() {
        // Sparse PCM data is real zero-valued audio; no giant allocation or checked-in blob.
        for ((seconds, displayed) in listOf(5 to 700, 700 to 60)) {
            val file = wav("$seconds.wav", 48_000, 2, seconds * 48_000L, displayed)
            val result = AudioFileInspector.inspect(file)
            assertTrue("$result", result is AudioInspection.Readable)
            val facts = (result as AudioInspection.Readable).facts
            assertEquals(seconds * 48_000L, facts.frames)
            assertEquals(if (seconds == 700) AudioExclusion.DURATION else null, AudioSafety.cost(facts).exclusion)
            RandomAccessFile(file, "rw").use { it.setLength(it.length() - 1) }
            assertEquals(AudioInspection.Rejected(AudioExclusion.INVALID_FORMAT), AudioFileInspector.inspect(file))
        }
    }

    @Test fun selectionRespectsOrderDuplicatesBoundsAndContinuesAfterRejections() {
        val ten = AudioFileFacts(48_000, 2, 28_800_000)
        val facts = mapOf("a" to ten, "b" to ten, "c" to ten,
            "d" to AudioFileFacts(44_100, 2, 220_500), "bad" to null)
        val refs = listOf(AudioReference(0, 0, 0, 3, "c"), AudioReference(0, 0, 0, 1, "a"),
            AudioReference(0, 0, 0, 2, "b"), AudioReference(0, 0, 1, 1, "bad"),
            AudioReference(0, 0, 2, 1, "d"), AudioReference(0, 0, 3, 1, "a"),
            AudioReference(0, 0, 4, 1, "c"), AudioReference(-1, 0, 0, 0, "outside"),
            AudioReference(0, 8, 0, 0, "outside"))
        val reads = mutableListOf<String>()
        val plan = AudioSelector.select(refs, AudioPackBounds(1, 8, 8)) { id ->
            reads.add(id); facts[id]?.let { AudioInspection.Readable(it) }
                ?: AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
        }
        assertEquals(listOf("a", "b", "c", "bad", "d"), reads)
        assertEquals(listOf(null, null, AudioExclusion.PACK_LIMIT, AudioExclusion.READ_FAILURE, null), plan.files.map { it.exclusion })
        assertEquals(115_680_000L, plan.reservedCost)
        assertEquals(listOf("a", "b", "d"), plan.files.filter { it.exclusion == null }.map { it.identity })
        // A consumer can fail to decode a; it cannot release its reservation or admit c later.
        val failedToDecode = plan.files.first()
        assertEquals("a", failedToDecode.identity)
        assertEquals(115_680_000L, plan.reservedCost)
        assertEquals(AudioExclusion.PACK_LIMIT, plan.files[2].exclusion)
    }

    @Test fun remainingBudgetUsesExactRoundingAtBothRates() {
        for ((rate, edge) in listOf(48_000 to 9_508_864L, 44_100 to 8_736_268L)) {
            for (extra in 0L..1L) {
                val refs = listOf("a", "b", "edge", "tiny").mapIndexed { i, s -> AudioReference(0, 0, 0, i, s) }
                val plan = AudioSelector.select(refs, AudioPackBounds(1, 8, 8)) { id -> AudioInspection.Readable(
                    when(id) { "edge" -> AudioFileFacts(rate, 2, edge + extra)
                        "tiny" -> AudioFileFacts(48_000, 2, 1)
                        else -> AudioFileFacts(48_000, 2, 28_800_000) }) }
                assertEquals(if(extra == 0L) null else AudioExclusion.PACK_LIMIT, plan.files[2].exclusion)
                assertEquals(if(extra == 0L) AudioExclusion.PACK_LIMIT else null, plan.files[3].exclusion)
                assertEquals(if(extra == 0L) AudioSafety.PACK_LIMIT else 115_200_002L, plan.reservedCost)
            }
        }
    }

    @Test fun wavRecipesMatchActualDataAndSafetyBoundaries() {
        for (case in corpus().getValue("wavRecipes").jsonArray.map { it.jsonObject }) errors.checkSucceeds {
            val expected = AudioFileFacts(case.getValue("rate").jsonPrimitive.int,
                case.getValue("channels").jsonPrimitive.int, case.getValue("frames").jsonPrimitive.long, "wav")
            val file = wav(case.getValue("id").jsonPrimitive.content, expected.sampleRate,
                expected.channels, expected.frames, case.getValue("claimedSeconds").jsonPrimitive.int)
            assertEquals(case.toString(), AudioInspection.Readable(expected), AudioFileInspector.inspect(file))
            val cost = AudioSafety.cost(expected)
            assertEquals(case.toString(), case["exclusion"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content, cost.exclusion?.name)
            assertEquals(case.toString(), case.getValue("expectedCost").jsonPrimitive.long, cost.value)
        }
    }

    @Test fun chainXYAndLineAreNumericAndRejectedDuplicatesAreNotRetried() {
        val reads = mutableListOf<String>()
        val refs = listOf(AudioReference(1, 0, 0, 0, "last"), AudioReference(0, 1, 0, 0, "x"),
            AudioReference(0, 0, 1, 0, "y"), AudioReference(0, 0, 0, 10, "line10"),
            AudioReference(0, 0, 0, 2, "line2"), AudioReference(0, 0, 0, 1, "rejected"),
            AudioReference(0, 0, 0, 11, "rejected"))
        val plan = AudioSelector.select(refs, AudioPackBounds(2, 8, 8)) { id ->
            reads.add(id)
            if (id == "rejected") AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
            else AudioInspection.Readable(AudioFileFacts(48_000, 1, 1))
        }
        assertEquals(listOf("rejected", "line2", "line10", "y", "x", "last"), reads)
        assertEquals(10L, plan.reservedCost)
        assertEquals(6, plan.files.size)
    }

    @Test fun emptyAndEntirelyRejectedPlansReserveNothing() {
        assertEquals(AudioSelectionPlan(emptyList(), 0), AudioSelector.select(emptyList(), AudioPackBounds(1, 8, 8)) { error("unexpected read") })
        val refs = listOf(AudioReference(0, 0, 0, 0, "large"), AudioReference(0, 0, 0, 1, "tiny"))
        val plan = AudioSelector.select(refs, AudioPackBounds(1, 8, 8)) { id -> AudioInspection.Readable(
            AudioFileFacts(384_000, 8, if (id == "large") 33_554_432 else 1)) }
        assertEquals(AudioExclusion.SINGLE_LIMIT, plan.files[0].exclusion)
        assertEquals(8L, plan.reservedCost)
    }

    @Test fun selectionCasesMatchTheSharedCorpus() {
        for (case in corpus().getValue("selections").jsonArray.map { it.jsonObject }) {
            val bounds = case.getValue("bounds").jsonArray.map { it.jsonPrimitive.int }
            val refs = case.getValue("references").jsonArray.map { row -> row.jsonArray.let {
                AudioReference(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int, it[2].jsonPrimitive.int,
                    it[3].jsonPrimitive.int, it[4].jsonPrimitive.content) } }
            val reads = mutableListOf<String>()
            val plan = AudioSelector.select(refs, AudioPackBounds(bounds[0], bounds[1], bounds[2])) { id ->
                reads.add(id)
                val facts = case.getValue("facts").jsonObject.getValue(id)
                if (facts is JsonNull) AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
                else facts.jsonArray.let { AudioInspection.Readable(AudioFileFacts(it[0].jsonPrimitive.int,
                    it[1].jsonPrimitive.int, it[2].jsonPrimitive.long)) }
            }
            assertEquals(case.getValue("order").jsonArray.map { it.jsonPrimitive.content }, reads)
            assertEquals(case.getValue("selected").jsonArray.map { it.jsonPrimitive.content }, plan.files.filter { it.exclusion == null }.map { it.identity })
            assertEquals(case.getValue("exclusions").jsonArray.map { if (it is JsonNull) null else it.jsonPrimitive.content }, plan.files.map { it.exclusion?.name })
            assertEquals(case.getValue("reservedCost").jsonPrimitive.long, plan.reservedCost)
        }
    }

    @Test fun missingFilesAreReadFailuresRatherThanRuleExclusions() {
        assertEquals(AudioInspection.Rejected(AudioExclusion.READ_FAILURE), AudioFileInspector.inspect(File(temporary.root, "missing")))
    }

    private fun wav(name: String, rate: Int, channels: Int, frames: Long, displayedSeconds: Int): File {
        val file = temporary.newFile(name)
        RandomAccessFile(file, "rw").use { out ->
            fun le(value: Long, bytes: Int) { repeat(bytes) { out.write((value ushr (it * 8)).toInt()) } }
            val dataSize = frames * channels * 2
            out.writeBytes("RIFF"); le(dataSize + 48, 4); out.writeBytes("WAVEfmt "); le(16, 4)
            le(1, 2); le(channels.toLong(), 2); le(rate.toLong(), 4); le(rate.toLong() * channels * 2, 4)
            le((channels * 2).toLong(), 2); le(16, 2)
            out.writeBytes("fact"); le(4, 4); le(displayedSeconds.toLong() * rate, 4)
            out.writeBytes("data"); le(dataSize, 4); out.setLength(out.filePointer + dataSize)
        }
        return file
    }
}
