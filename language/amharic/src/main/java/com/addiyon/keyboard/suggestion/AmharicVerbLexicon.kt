package com.addiyon.keyboard.suggestion

import android.content.res.AssetManager
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.CRC32

enum class AmharicVerbSourceClass {
    REGULAR,
    IRREGULAR,
    LIGHT,
}

data class VerbAnalysis(
    val rootId: Long,
    val featureId: Int,
    val sourceClass: AmharicVerbSourceClass,
    val lexeme: String,
    val root: String,
    val rootFrequency: Int,
) {
    val lemmaId: String = "verb:${rootId.toString(16)}"
    val analysisId: String = "$lemmaId:${featureId.toString(16)}"
}

data class VerbTerminal(
    val surface: String,
    val analyses: List<VerbAnalysis>,
) {
    val bestAnalysis: VerbAnalysis = analyses.maxWith(
        compareBy<VerbAnalysis> { it.rootFrequency }
            .thenByDescending { it.sourceClass.ordinal }
            .thenByDescending { it.rootId }
    )
}

class AmharicVerbLexicon private constructor(
    private val bytes: ByteArray?,
) {
    private data class PendingState(
        val state: Int,
        val row: IntArray,
        val depth: Int,
    )

    private data class FuzzyTerminal(
        val terminal: VerbTerminal,
        val distance: Int,
    )

    private val buffer = bytes?.let { ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN) }
    val isEnabled: Boolean = bytes != null
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
        if (bytes == null) {
            stateCount = 0
            transitionCount = 0
            terminalCount = 0
            analysisCount = 0
            stateOffset = 0
            transitionOffset = 0
            terminalOffset = 0
            analysisOffset = 0
            stringOffset = 0
        } else {
            val content = requireNotNull(buffer)
            require(bytes.size >= HEADER_SIZE && content.getInt(0) == MAGIC) {
                "unsupported verb automaton"
            }
            require(
                content.getShort(4).toInt() == VERSION &&
                    content.getShort(6).toInt() == HEADER_SIZE
            ) { "unsupported verb automaton" }
            stateCount = content.getInt(8)
            transitionCount = content.getInt(12)
            terminalCount = content.getInt(16)
            analysisCount = content.getInt(20)
            stateOffset = content.getInt(24)
            transitionOffset = content.getInt(28)
            terminalOffset = content.getInt(32)
            analysisOffset = content.getInt(36)
            stringOffset = content.getInt(40)
            val checksum = content.getInt(44).toLong() and 0xffffffffL
            val actualChecksum = CRC32().apply {
                update(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE)
            }.value
            require(actualChecksum == checksum) { "verb automaton checksum mismatch" }
            validateRecords(bytes.size)
        }
    }

    fun exact(surface: String): VerbTerminal? {
        if (!isEnabled || surface.isEmpty()) return null
        var state = 0
        EthiopicNormalizer.normalize(surface).forEach { character ->
            state = transition(state, character) ?: return null
        }
        return terminal(state)
    }

    fun complete(prefix: String, limit: Int): List<VerbTerminal> {
        if (!isEnabled || prefix.isEmpty() || limit <= 0) return emptyList()
        val normalizedPrefix = EthiopicNormalizer.normalize(prefix)
        var state = 0
        normalizedPrefix.forEach { character ->
            state = transition(state, character) ?: return emptyList()
        }
        val poolLimit = maxOf(limit, limit * COMPLETION_POOL_MULTIPLIER)
        val results = ArrayList<VerbTerminal>(poolLimit)
        val pending = ArrayDeque<Int>()
        pending.addLast(state)
        while (pending.isNotEmpty() && results.size < poolLimit) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val current = pending.removeLast()
            terminal(current)?.let(results::add)
            val first = stateFirstTransition(current)
            val count = stateTransitionCount(current)
            for (index in count - 1 downTo 0) {
                pending.addLast(transitionTarget(first + index))
            }
        }
        return results.distinctBy { EthiopicNormalizer.normalize(it.surface) }
            .sortedWith(
                compareByDescending<VerbTerminal> { it.bestAnalysis.rootFrequency }
                    .thenBy { it.surface.length - prefix.length }
                    .thenBy { it.surface }
            )
            .take(limit)
    }

    fun fuzzy(
        surface: String,
        maxEdits: Int,
        limit: Int,
        substitutionCost: SubstitutionCost,
        insertCost: Int,
        deleteCost: Int,
    ): List<FuzzyMatch> {
        if (!isEnabled || surface.isEmpty() || maxEdits <= 0 || limit <= 0) return emptyList()
        val normalized = EthiopicNormalizer.normalize(surface)
        val initial = IntArray(normalized.length + 1) { it * deleteCost }
        val pending = ArrayDeque<PendingState>()
        pending.addLast(PendingState(0, initial, 0))
        val matches = ArrayList<FuzzyTerminal>(limit * FUZZY_POOL_MULTIPLIER)
        val maximumDepth = normalized.length + maxEdits / insertCost.coerceAtLeast(1)
        while (pending.isNotEmpty() && matches.size < limit * FUZZY_POOL_MULTIPLIER) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val current = pending.removeLast()
            if (current.depth > maximumDepth) continue
            val first = stateFirstTransition(current.state)
            val count = stateTransitionCount(current.state)
            for (index in count - 1 downTo 0) {
                val transitionIndex = first + index
                val targetCharacter = transitionLabel(transitionIndex)
                val row = IntArray(normalized.length + 1)
                row[0] = current.row[0] + insertCost
                var minimum = row[0]
                for (column in 1..normalized.length) {
                    val replace = current.row[column - 1] +
                        substitutionCost.cost(normalized[column - 1], targetCharacter)
                    val insert = current.row[column] + insertCost
                    val delete = row[column - 1] + deleteCost
                    row[column] = minOf(replace, insert, delete)
                    minimum = minOf(minimum, row[column])
                }
                if (minimum > maxEdits) continue
                val target = transitionTarget(transitionIndex)
                val distance = row[normalized.length]
                if (distance in 1..maxEdits) {
                    terminal(target)?.let { matches += FuzzyTerminal(it, distance) }
                }
                pending.addLast(PendingState(target, row, current.depth + 1))
            }
        }
        return matches.distinctBy { EthiopicNormalizer.normalize(it.terminal.surface) }
            .sortedWith(
                compareBy<FuzzyTerminal> { it.distance }
                    .thenByDescending { it.terminal.bestAnalysis.rootFrequency }
                    .thenBy { it.terminal.surface }
            )
            .take(limit)
            .map {
                FuzzyMatch(
                    word = it.terminal.surface,
                    editDistance = it.distance,
                    frequency = it.terminal.bestAnalysis.rootFrequency,
                )
            }
    }

    private fun transition(state: Int, character: Char): Int? {
        val first = stateFirstTransition(state)
        val count = stateTransitionCount(state)
        var low = 0
        var high = count
        while (low < high) {
            val middle = (low + high) / 2
            val index = first + middle
            when {
                transitionLabel(index).code < character.code -> low = middle + 1
                transitionLabel(index).code > character.code -> high = middle
                else -> return transitionTarget(index)
            }
        }
        return null
    }

    private fun terminal(state: Int): VerbTerminal? {
        val content = requireNotNull(buffer)
        val terminalIndex = content.getInt(stateOffset + state * STATE_SIZE + 6)
        if (terminalIndex < 0) return null
        val offset = terminalOffset + terminalIndex * TERMINAL_SIZE
        val firstAnalysis = content.getInt(offset)
        val count = content.getShort(offset + 4).toInt() and 0xffff
        val surface = readString(
            content.getInt(offset + 6),
            content.getShort(offset + 10).toInt() and 0xffff,
        )
        return VerbTerminal(
            surface = surface,
            analyses = List(count) { index -> analysis(firstAnalysis + index) },
        )
    }

    private fun analysis(index: Int): VerbAnalysis {
        val content = requireNotNull(buffer)
        val offset = analysisOffset + index * ANALYSIS_SIZE
        return VerbAnalysis(
            rootId = content.getInt(offset).toLong() and 0xffffffffL,
            featureId = content.getShort(offset + 4).toInt() and 0xffff,
            sourceClass = AmharicVerbSourceClass.entries[content.get(offset + 6).toInt() and 0xff],
            lexeme = readString(
                content.getInt(offset + 8),
                content.getShort(offset + 12).toInt() and 0xffff,
            ),
            root = readString(
                content.getInt(offset + 14),
                content.getShort(offset + 18).toInt() and 0xffff,
            ),
            rootFrequency = content.getInt(offset + 20),
        )
    }

    private fun stateFirstTransition(state: Int): Int =
        requireNotNull(buffer).getInt(stateOffset + state * STATE_SIZE)

    private fun stateTransitionCount(state: Int): Int =
        requireNotNull(buffer).getShort(stateOffset + state * STATE_SIZE + 4).toInt() and 0xffff

    private fun transitionLabel(index: Int): Char =
        (requireNotNull(buffer).getShort(transitionOffset + index * TRANSITION_SIZE).toInt() and 0xffff).toChar()

    private fun transitionTarget(index: Int): Int =
        requireNotNull(buffer).getInt(transitionOffset + index * TRANSITION_SIZE + 2)

    private fun readString(offset: Int, length: Int): String =
        String(requireNotNull(bytes), stringOffset + offset, length, Charsets.UTF_8)

    private fun validateRecords(byteSize: Int) {
        val content = requireNotNull(buffer)
        val valid = runCatching {
            require(
                stateCount > 0 && transitionCount >= 0 && terminalCount >= 0 && analysisCount >= 0 &&
                    stateOffset == HEADER_SIZE &&
                    transitionOffset.toLong() == stateOffset + stateCount.toLong() * STATE_SIZE &&
                    terminalOffset.toLong() == transitionOffset + transitionCount.toLong() * TRANSITION_SIZE &&
                    analysisOffset.toLong() == terminalOffset + terminalCount.toLong() * TERMINAL_SIZE &&
                    stringOffset.toLong() == analysisOffset + analysisCount.toLong() * ANALYSIS_SIZE &&
                    stringOffset <= byteSize
            )
            repeat(stateCount) { state ->
                val first = stateFirstTransition(state)
                val count = stateTransitionCount(state)
                val terminal = content.getInt(stateOffset + state * STATE_SIZE + 6)
                require(first >= 0 && first.toLong() + count <= transitionCount)
                require(terminal in -1 until terminalCount)
                var previous = -1
                repeat(count) { child ->
                    val index = first + child
                    val label = transitionLabel(index).code
                    require(label > previous)
                    require(transitionTarget(index) in 0 until stateCount)
                    previous = label
                }
            }
            repeat(terminalCount) { index ->
                val offset = terminalOffset + index * TERMINAL_SIZE
                val first = content.getInt(offset)
                val count = content.getShort(offset + 4).toInt() and 0xffff
                require(first >= 0 && first.toLong() + count <= analysisCount)
                requireString(
                    content.getInt(offset + 6),
                    content.getShort(offset + 10).toInt() and 0xffff,
                    byteSize,
                )
            }
            repeat(analysisCount) { index ->
                val offset = analysisOffset + index * ANALYSIS_SIZE
                require((content.get(offset + 6).toInt() and 0xff) < AmharicVerbSourceClass.entries.size)
                requireString(
                    content.getInt(offset + 8),
                    content.getShort(offset + 12).toInt() and 0xffff,
                    byteSize,
                )
                requireString(
                    content.getInt(offset + 14),
                    content.getShort(offset + 18).toInt() and 0xffff,
                    byteSize,
                )
                require(content.getInt(offset + 20) >= 0)
            }
        }.isSuccess
        require(valid) { "invalid verb automaton records" }
    }

    private fun requireString(offset: Int, length: Int, byteSize: Int) {
        require(offset >= 0 && stringOffset.toLong() + offset + length <= byteSize)
    }

    companion object {
        const val ASSET_NAME = "amharic_verbs.ahva"
        const val MANIFEST_ASSET_NAME = "amharic_verbs_manifest.properties"
        private const val MAGIC = 0x41485641
        private const val VERSION = 2
        private const val HEADER_SIZE = 48
        private const val STATE_SIZE = 12
        private const val TRANSITION_SIZE = 8
        private const val TERMINAL_SIZE = 12
        private const val ANALYSIS_SIZE = 24
        private const val COMPLETION_POOL_MULTIPLIER = 8
        private const val FUZZY_POOL_MULTIPLIER = 4

        val EMPTY = AmharicVerbLexicon(null)

        fun fromBytes(bytes: ByteArray): AmharicVerbLexicon = AmharicVerbLexicon(bytes)

        fun load(
            assets: AssetManager,
            onWarning: (String) -> Unit,
        ): AmharicVerbLexicon = try {
            val metadata = assets.open(MANIFEST_ASSET_NAME).use { input ->
                Properties().also { properties -> properties.load(input) }
            }
            require(metadata.getProperty("schemaVersion") == VERSION.toString())
            require(metadata.getProperty("asset") == ASSET_NAME)
            val bytes = assets.open(ASSET_NAME).use { it.readBytes() }
            require(bytes.size.toString() == metadata.getProperty("length"))
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { value -> "%02x".format(value) }
            require(digest == metadata.getProperty("sha256"))
            fromBytes(bytes)
        } catch (failure: Throwable) {
            onWarning("Amharic verb morphology disabled: ${failure.message ?: failure.javaClass.simpleName}")
            EMPTY
        }
    }
}
