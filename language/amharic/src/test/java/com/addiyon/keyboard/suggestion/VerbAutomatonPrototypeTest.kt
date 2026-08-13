package com.addiyon.keyboard.suggestion

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VerbAutomatonPrototypeTest {
    @Test
    fun versionOneArtifactSupportsExactAnalysisAndPrefixTraversal() {
        val automaton = VerbAutomatonPrototype(loadArtifact())

        assertEquals(922, automaton.stateCount)
        assertEquals(921, automaton.transitionCount)
        assertEquals(402, automaton.terminalCount)
        assertEquals(407, automaton.analysisCount)

        val regular = requireNotNull(automaton.exact("ለመደ"))
        assertEquals("ለመደ", regular.surface)
        assertTrue(regular.analyses.any { it.lexeme == "regular:ልምድ:A" && it.root == "ልምድ" })

        val light = requireNotNull(automaton.exact("አፈፍ አለ"))
        assertEquals("አፈፍ አለ", light.surface)
        assertTrue(light.analyses.any { it.sourceClass == PrototypeVerbSourceClass.LIGHT })

        assertTrue(automaton.complete("ለመ", 8).contains("ለመደ"))
        assertTrue(automaton.complete("አፈፍ ", 8).contains("አፈፍ አለ"))
        assertEquals(null, automaton.exact("ያልተፈቀደ"))
    }

    @Test
    fun versionAndChecksumAreFailClosed() {
        val wrongVersion = loadArtifact().also { it[5] = 2 }
        expectFailure(wrongVersion, "unsupported verb automaton")

        val corrupt = loadArtifact().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        expectFailure(corrupt, "verb automaton checksum mismatch")

        val invalidBounds = loadArtifact().also { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(18416, Int.MAX_VALUE)
            val crc = CRC32().apply { update(bytes, 48, bytes.size - 48) }.value.toInt()
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(44, crc)
        }
        expectFailure(invalidBounds, "invalid verb automaton records")
    }

    private fun loadArtifact(): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/amharic_verb_automaton_v1.bin")
    ).use { it.readBytes() }

    private fun expectFailure(bytes: ByteArray, expectedMessage: String) {
        try {
            VerbAutomatonPrototype(bytes)
            fail("expected artifact validation to fail")
        } catch (error: IllegalArgumentException) {
            assertEquals(expectedMessage, error.message)
        }
    }
}

private enum class PrototypeVerbSourceClass {
    REGULAR,
    IRREGULAR,
    LIGHT,
}

private data class PrototypeVerbAnalysis(
    val rootId: Int,
    val featureId: Int,
    val sourceClass: PrototypeVerbSourceClass,
    val lexeme: String,
    val root: String,
)

private data class PrototypeVerbTerminal(
    val surface: String,
    val analyses: List<PrototypeVerbAnalysis>,
)

private class VerbAutomatonPrototype(bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    val stateCount: Int
    val transitionCount: Int
    val terminalCount: Int
    val analysisCount: Int
    private val stateOffset: Int
    private val transitionOffset: Int
    private val terminalOffset: Int
    private val analysisOffset: Int
    private val stringOffset: Int

    init {
        require(bytes.size >= HEADER_SIZE && buffer.getInt(0) == MAGIC) {
            "unsupported verb automaton"
        }
        require(buffer.getShort(4).toInt() == VERSION && buffer.getShort(6).toInt() == HEADER_SIZE) {
            "unsupported verb automaton"
        }
        stateCount = buffer.getInt(8)
        transitionCount = buffer.getInt(12)
        terminalCount = buffer.getInt(16)
        analysisCount = buffer.getInt(20)
        stateOffset = buffer.getInt(24)
        transitionOffset = buffer.getInt(28)
        terminalOffset = buffer.getInt(32)
        analysisOffset = buffer.getInt(36)
        stringOffset = buffer.getInt(40)
        val checksum = buffer.getInt(44).toLong() and 0xffffffffL
        val crc = CRC32().apply { update(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }.value
        require(crc == checksum) { "verb automaton checksum mismatch" }
        require(
            stateCount > 0 && transitionCount >= 0 && terminalCount >= 0 && analysisCount >= 0 &&
            stateOffset == HEADER_SIZE &&
                transitionOffset.toLong() == stateOffset + stateCount.toLong() * STATE_SIZE &&
                terminalOffset.toLong() == transitionOffset + transitionCount.toLong() * TRANSITION_SIZE &&
                analysisOffset.toLong() == terminalOffset + terminalCount.toLong() * TERMINAL_SIZE &&
                stringOffset.toLong() == analysisOffset + analysisCount.toLong() * ANALYSIS_SIZE &&
                stringOffset <= bytes.size
        ) { "invalid verb automaton offsets" }
        validateRecords(bytes.size)
    }

    fun exact(key: String): PrototypeVerbTerminal? {
        var state = 0
        key.forEach { character ->
            state = transition(state, character) ?: return null
        }
        return terminal(state)
    }

    fun complete(prefix: String, limit: Int): List<String> {
        var state = 0
        prefix.forEach { character ->
            state = transition(state, character) ?: return emptyList()
        }
        val results = mutableListOf<String>()
        val pending = ArrayDeque<Int>()
        pending.addLast(state)
        while (pending.isNotEmpty() && results.size < limit) {
            val current = pending.removeLast()
            terminal(current)?.let { results += it.surface }
            val first = stateFirstTransition(current)
            val count = stateTransitionCount(current)
            for (index in count - 1 downTo 0) {
                pending.addLast(buffer.getInt(transitionOffset + (first + index) * TRANSITION_SIZE + 2))
            }
        }
        return results
    }

    private fun transition(state: Int, character: Char): Int? {
        val first = stateFirstTransition(state)
        val count = stateTransitionCount(state)
        var low = 0
        var high = count
        while (low < high) {
            val middle = (low + high) / 2
            val offset = transitionOffset + (first + middle) * TRANSITION_SIZE
            val label = buffer.getShort(offset).toInt() and 0xffff
            when {
                label < character.code -> low = middle + 1
                label > character.code -> high = middle
                else -> return buffer.getInt(offset + 2)
            }
        }
        return null
    }

    private fun terminal(state: Int): PrototypeVerbTerminal? {
        val terminalIndex = buffer.getInt(stateOffset + state * STATE_SIZE + 6)
        if (terminalIndex < 0) return null
        val offset = terminalOffset + terminalIndex * TERMINAL_SIZE
        val firstAnalysis = buffer.getInt(offset)
        val count = buffer.getShort(offset + 4).toInt() and 0xffff
        val surface = readString(buffer.getInt(offset + 6), buffer.getShort(offset + 10).toInt() and 0xffff)
        val analyses = List(count) { index -> analysis(firstAnalysis + index) }
        return PrototypeVerbTerminal(surface, analyses)
    }

    private fun analysis(index: Int): PrototypeVerbAnalysis {
        val offset = analysisOffset + index * ANALYSIS_SIZE
        return PrototypeVerbAnalysis(
            rootId = buffer.getInt(offset),
            featureId = buffer.getShort(offset + 4).toInt() and 0xffff,
            sourceClass = PrototypeVerbSourceClass.entries[buffer.get(offset + 6).toInt() and 0xff],
            lexeme = readString(buffer.getInt(offset + 8), buffer.getShort(offset + 12).toInt() and 0xffff),
            root = readString(buffer.getInt(offset + 14), buffer.getShort(offset + 18).toInt() and 0xffff),
        )
    }

    private fun stateFirstTransition(state: Int): Int = buffer.getInt(stateOffset + state * STATE_SIZE)

    private fun stateTransitionCount(state: Int): Int =
        buffer.getShort(stateOffset + state * STATE_SIZE + 4).toInt() and 0xffff

    private fun readString(offset: Int, length: Int): String =
        String(buffer.array(), stringOffset + offset, length, Charsets.UTF_8)

    private fun validateRecords(byteSize: Int) {
        val valid = runCatching {
            repeat(stateCount) { state ->
                val first = stateFirstTransition(state)
                val count = stateTransitionCount(state)
                val terminal = buffer.getInt(stateOffset + state * STATE_SIZE + 6)
                require(first >= 0 && count >= 0 && first.toLong() + count <= transitionCount)
                require(terminal in -1 until terminalCount)
            }
            repeat(transitionCount) { index ->
                val offset = transitionOffset + index * TRANSITION_SIZE
                require(buffer.getInt(offset + 2) in 0 until stateCount)
            }
            repeat(terminalCount) { index ->
                val offset = terminalOffset + index * TERMINAL_SIZE
                val first = buffer.getInt(offset)
                val count = buffer.getShort(offset + 4).toInt() and 0xffff
                require(first >= 0 && first.toLong() + count <= analysisCount)
                requireString(buffer.getInt(offset + 6), buffer.getShort(offset + 10).toInt() and 0xffff, byteSize)
            }
            repeat(analysisCount) { index ->
                val offset = analysisOffset + index * ANALYSIS_SIZE
                require((buffer.get(offset + 6).toInt() and 0xff) < PrototypeVerbSourceClass.entries.size)
                requireString(buffer.getInt(offset + 8), buffer.getShort(offset + 12).toInt() and 0xffff, byteSize)
                requireString(buffer.getInt(offset + 14), buffer.getShort(offset + 18).toInt() and 0xffff, byteSize)
            }
        }.isSuccess
        require(valid) { "invalid verb automaton records" }
    }

    private fun requireString(offset: Int, length: Int, byteSize: Int) {
        require(offset >= 0 && length >= 0 && stringOffset.toLong() + offset + length <= byteSize)
    }

    private companion object {
        const val MAGIC = 0x41485641
        const val VERSION = 1
        const val HEADER_SIZE = 48
        const val STATE_SIZE = 12
        const val TRANSITION_SIZE = 8
        const val TERMINAL_SIZE = 12
        const val ANALYSIS_SIZE = 20
    }
}
