package com.addiyon.keyboard.suggestion

import android.content.res.AssetManager
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.PriorityQueue
import java.util.Properties
import java.util.zip.CRC32
import java.util.zip.Inflater

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
    val morphologyCost: Int,
) {
    val lemmaId: String = "verb:${rootId.toString(16)}"
    val analysisId: String = "$lemmaId:${featureId.toUInt().toString(16)}"
}

data class VerbTerminal(
    val surface: String,
    val analyses: List<VerbAnalysis>,
) {
    val bestAnalysis: VerbAnalysis = analyses.maxWith(
        compareBy<VerbAnalysis> { it.rootFrequency }
            .thenBy { -it.morphologyCost }
            .thenByDescending { it.sourceClass.ordinal }
            .thenByDescending { it.rootId }
    )
}

class AmharicVerbLexicon private constructor(
    source: ByteBuffer?,
    private val byteSize: Int,
    verifyChecksum: Boolean,
) {
    private data class SearchNode(
        val state: Int,
        val inputPosition: Int,
        val surface: String,
        val features: List<ShortArray>,
        val depth: Int,
        val morphologyCost: Int,
        val rootFrequency: Int,
    )

    private data class FuzzyNode(
        val state: Int,
        val surface: String,
        val row: IntArray,
        val features: List<ShortArray>,
        val depth: Int,
    )

    private data class FuzzyTerminal(
        val terminal: VerbTerminal,
        val distance: Int,
    )

    private data class CompletionKey(
        val prefix: String,
        val limit: Int,
    )

    private class FeatureKey(private val values: ShortArray) {
        private val hash = values.contentHashCode()

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean =
            other is FeatureKey && values.contentEquals(other.values)
    }

    private class PageCache(
        private val read: (Int) -> ByteArray,
    ) {
        private val pages = object : LinkedHashMap<Int, ByteArray>(PAGE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>): Boolean =
                size > PAGE_CACHE_SIZE
        }

        @Synchronized
        fun get(page: Int): ByteArray = pages[page] ?: read(page).also { pages[page] = it }

        @Synchronized
        fun clear() = pages.clear()
    }

    private val buffer = source?.asReadOnlyBuffer()?.order(ByteOrder.BIG_ENDIAN)
    val isEnabled: Boolean = source != null
    val stateCount: Int
    val transitionCount: Int
    val ruleWeightCount: Int
    val constraintPageCount: Int
    val generatedSurfaceCount: Int = 0
    private val featureCount: Int
    private val valueCount: Int
    private val stateOffset: Int
    private val transitionOffset: Int
    private val weightIndexOffset: Int
    private val pageIndexOffset: Int
    private val pageDataOffset: Int
    private val featureIndexOffset: Int
    private val valueIndexOffset: Int
    private val rootFrequencyOffset: Int
    private val stringOffset: Int
    private val initialState: Int
    private val rootFeature: Int
    private val lemmaFeature: Int
    private val featureIds: Map<String, Int>
    private val values: List<String>
    private val morphologyExactRules: IntArray
    private val morphologyNonZeroRules: IntArray
    private val pageCache: PageCache?
    private val completionCache = object : LinkedHashMap<CompletionKey, List<VerbTerminal>>(
        COMPLETION_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<CompletionKey, List<VerbTerminal>>,
        ): Boolean = size > COMPLETION_CACHE_SIZE
    }

    init {
        if (source == null) {
            stateCount = 0
            transitionCount = 0
            ruleWeightCount = 0
            constraintPageCount = 0
            featureCount = 0
            valueCount = 0
            stateOffset = 0
            transitionOffset = 0
            weightIndexOffset = 0
            pageIndexOffset = 0
            pageDataOffset = 0
            featureIndexOffset = 0
            valueIndexOffset = 0
            rootFrequencyOffset = 0
            stringOffset = 0
            initialState = 0
            rootFeature = -1
            lemmaFeature = -1
            featureIds = emptyMap()
            values = emptyList()
            morphologyExactRules = IntArray(0)
            morphologyNonZeroRules = IntArray(0)
            pageCache = null
        } else {
            val content = requireNotNull(buffer)
            require(byteSize >= HEADER_SIZE && content.getInt(0) == MAGIC) {
                "unsupported HornMorpho runtime"
            }
            require(
                content.getShort(4).toInt() == VERSION &&
                    content.getShort(6).toInt() == HEADER_SIZE
            ) { "unsupported HornMorpho runtime" }
            stateCount = content.getInt(8)
            transitionCount = content.getInt(12)
            ruleWeightCount = content.getInt(16)
            constraintPageCount = content.getInt(20)
            featureCount = content.getInt(24)
            valueCount = content.getInt(28)
            stateOffset = content.getInt(32)
            transitionOffset = content.getInt(36)
            weightIndexOffset = content.getInt(40)
            pageIndexOffset = content.getInt(44)
            pageDataOffset = content.getInt(48)
            featureIndexOffset = content.getInt(52)
            valueIndexOffset = content.getInt(56)
            rootFrequencyOffset = content.getInt(96)
            stringOffset = content.getInt(60)
            initialState = content.getInt(72)
            validateHeader()
            if (verifyChecksum) verifyChecksum(content.getInt(68).toLong() and 0xffffffffL)
            val featureNames = List(featureCount) { readIndexedString(featureIndexOffset, it) }
            featureIds = featureNames.withIndex().associate { it.value to it.index }
            values = List(valueCount) { readIndexedString(valueIndexOffset, it) }
            val valueIds = values.withIndex().associate { it.value to it.index }
            morphologyExactRules = listOf(
                Triple("O", "+", 40),
                Triple("acc", "+", -20),
                Triple("det", "+", -10),
                Triple("sp", "3", -3),
                Triple("sn", "2", -3),
            ).flatMap { (feature, value, cost) ->
                listOf(featureIds.getValue(feature), valueIds.getValue(value) + 1, cost)
            }.toIntArray()
            morphologyNonZeroRules = listOf(
                "oc" to 15,
                "cconj1" to 20,
                "cconj2" to 20,
                "ax" to 10,
            ).flatMap { (feature, cost) ->
                listOf(featureIds.getValue(feature), valueIds.getValue("0") + 1, cost)
            }.toIntArray()
            rootFeature = featureNames.indexOf("r")
            lemmaFeature = featureNames.indexOf("lemma")
            require(rootFeature >= 0) { "HornMorpho runtime has no root feature" }
            pageCache = PageCache(::inflatePage)
        }
    }

    fun exact(surface: String): VerbTerminal? {
        if (!isEnabled || surface.isEmpty()) return null
        val normalized = EthiopicNormalizer.normalize(surface)
        val pending = ArrayDeque<SearchNode>()
        pending.addLast(
            SearchNode(
                state = initialState,
                inputPosition = 0,
                surface = normalized,
                features = topFeatures(),
                depth = 0,
                morphologyCost = 0,
                rootFrequency = 0,
            )
        )
        val analyses = LinkedHashMap<String, VerbAnalysis>()
        var expansions = 0
        while (pending.isNotEmpty() && expansions < EXACT_EXPANSION_LIMIT) {
            if (Thread.currentThread().isInterrupted) return null
            val current = pending.removeLast()
            if (isFinal(current.state) && current.inputPosition == normalized.length) {
                analyses(current.features).forEach { analyses.putIfAbsent(it.analysisId, it) }
                if (analyses.size >= EXACT_ANALYSIS_LIMIT) break
            }
            if (current.depth >= MAX_PATH_DEPTH) continue
            val first = stateFirstTransition(current.state)
            val count = stateTransitionCount(current.state)
            repeat(count) { child ->
                val transition = first + child
                val label = transitionInput(transition)
                if (label != 0 && (
                        current.inputPosition >= normalized.length ||
                            normalized[current.inputPosition].code != label
                        )
                ) return@repeat
                val unified = unify(current.features, transitionWeight(transition)) ?: return@repeat
                pending.addLast(
                    SearchNode(
                        state = transitionTarget(transition),
                        inputPosition = current.inputPosition + if (label == 0) 0 else 1,
                        surface = normalized,
                        features = unified,
                        depth = current.depth + 1,
                        morphologyCost = unified.minOf(::morphologyCost),
                        rootFrequency = unified.maxOf(::rootFrequency),
                    )
                )
                expansions++
            }
        }
        return analyses.values.takeIf { it.isNotEmpty() }?.let { VerbTerminal(normalized, it.toList()) }
    }

    fun clearCache() {
        synchronized(completionCache) { completionCache.clear() }
        pageCache?.clear()
    }

    fun complete(prefix: String, limit: Int): List<VerbTerminal> {
        if (!isEnabled || prefix.isEmpty() || limit <= 0) return emptyList()
        val normalizedPrefix = EthiopicNormalizer.normalize(prefix)
        val cacheKey = CompletionKey(normalizedPrefix, limit)
        synchronized(completionCache) { completionCache[cacheKey] }?.let { return it }
        val pending = PriorityQueue(
            compareBy<SearchNode> { node -> node.morphologyCost }
                .thenByDescending { node -> node.rootFrequency }
                .thenByDescending { node -> node.inputPosition }
                .thenByDescending { node -> node.depth }
        )
        pending.add(
            SearchNode(
                state = initialState,
                inputPosition = 0,
                surface = "",
                features = topFeatures(),
                depth = 0,
                morphologyCost = 0,
                rootFrequency = 0,
            )
        )
        val poolLimit = maxOf(limit, limit * COMPLETION_POOL_MULTIPLIER)
        val terminals = LinkedHashMap<String, VerbTerminal>()
        val paradigmCounts = HashMap<Int, Int>()
        val rootCounts = HashMap<Int, Int>()
        var expansions = 0
        while (
            pending.isNotEmpty() &&
            terminals.size < poolLimit &&
            expansions < COMPLETION_EXPANSION_LIMIT
        ) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val current = pending.remove()
            if (isFinal(current.state) && current.inputPosition == normalizedPrefix.length) {
                terminal(current.surface, current.features)?.let { terminal ->
                    val paradigm = paradigmHash(current.features)
                    val paradigmCount = paradigmCounts[paradigm] ?: 0
                    val root = preferredRoot(current.features)
                    val rootCount = rootCounts[root] ?: 0
                    if (
                        paradigmCount < COMPLETIONS_PER_PARADIGM &&
                        rootCount < COMPLETIONS_PER_ROOT
                    ) {
                        val key = EthiopicNormalizer.normalize(terminal.surface)
                        if (terminals.putIfAbsent(key, terminal) == null) {
                            paradigmCounts[paradigm] = paradigmCount + 1
                            rootCounts[root] = rootCount + 1
                        }
                    }
                }
            }
            if (
                current.depth >= MAX_PATH_DEPTH ||
                current.surface.length >= maxOf(MAX_SURFACE_LENGTH, normalizedPrefix.length)
            ) continue
            val first = stateFirstTransition(current.state)
            val count = stateTransitionCount(current.state)
            repeat(count) { child ->
                val transition = first + child
                val label = transitionInput(transition)
                if (
                    label != 0 &&
                    current.inputPosition < normalizedPrefix.length &&
                    normalizedPrefix[current.inputPosition].code != label
                ) return@repeat
                val unified = unify(current.features, transitionWeight(transition)) ?: return@repeat
                val consumesPrefix = label != 0 && current.inputPosition < normalizedPrefix.length
                if (pending.size >= MAX_FRONTIER_SIZE) return@repeat
                pending.add(
                    SearchNode(
                        state = transitionTarget(transition),
                        inputPosition = current.inputPosition + if (consumesPrefix) 1 else 0,
                        surface = if (label == 0) current.surface else current.surface + label.toChar(),
                        features = unified,
                        depth = current.depth + 1,
                        morphologyCost = unified.minOf(::morphologyCost),
                        rootFrequency = unified.maxOf(::rootFrequency),
                    )
                )
                expansions++
            }
        }
        val result = terminals.values.sortedWith(
            compareBy<VerbTerminal> { it.bestAnalysis.morphologyCost }
                .thenByDescending { it.bestAnalysis.rootFrequency }
                .thenBy { it.surface.length - normalizedPrefix.length }
                .thenBy { it.surface }
        ).take(limit)
        synchronized(completionCache) { completionCache[cacheKey] = result }
        return result
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
        val pending = ArrayDeque<FuzzyNode>()
        pending.addLast(FuzzyNode(initialState, "", initial, topFeatures(), 0))
        val matches = ArrayList<FuzzyTerminal>(limit * FUZZY_POOL_MULTIPLIER)
        val maximumLength = normalized.length + maxEdits / insertCost.coerceAtLeast(1)
        var expansions = 0
        while (
            pending.isNotEmpty() &&
            matches.size < limit * FUZZY_POOL_MULTIPLIER &&
            expansions < FUZZY_EXPANSION_LIMIT
        ) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val current = pending.removeLast()
            val distance = current.row[normalized.length]
            if (isFinal(current.state) && distance in 1..maxEdits) {
                terminal(current.surface, current.features)?.let { matches += FuzzyTerminal(it, distance) }
            }
            if (current.depth >= MAX_PATH_DEPTH || current.surface.length > maximumLength) continue
            val first = stateFirstTransition(current.state)
            val count = stateTransitionCount(current.state)
            repeat(count) { child ->
                val transition = first + child
                val label = transitionInput(transition)
                val row = if (label == 0) {
                    current.row
                } else {
                    fuzzyRow(
                        current.row,
                        normalized,
                        label.toChar(),
                        substitutionCost,
                        insertCost,
                        deleteCost,
                    )
                }
                if (row.minOrNull()!! > maxEdits) return@repeat
                val unified = unify(current.features, transitionWeight(transition)) ?: return@repeat
                if (pending.size >= MAX_FRONTIER_SIZE) return@repeat
                pending.addLast(
                    FuzzyNode(
                        state = transitionTarget(transition),
                        surface = if (label == 0) current.surface else current.surface + label.toChar(),
                        row = row,
                        features = unified,
                        depth = current.depth + 1,
                    )
                )
                expansions++
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

    private fun fuzzyRow(
        previous: IntArray,
        input: String,
        target: Char,
        substitutionCost: SubstitutionCost,
        insertCost: Int,
        deleteCost: Int,
    ): IntArray {
        val row = IntArray(input.length + 1)
        row[0] = previous[0] + insertCost
        for (column in 1..input.length) {
            val replace = previous[column - 1] + substitutionCost.cost(input[column - 1], target)
            val insert = previous[column] + insertCost
            val delete = row[column - 1] + deleteCost
            row[column] = minOf(replace, insert, delete)
        }
        return row
    }

    private fun terminal(surface: String, features: List<ShortArray>): VerbTerminal? =
        analyses(features).takeIf { it.isNotEmpty() }?.let { VerbTerminal(surface, it) }

    private fun analyses(features: List<ShortArray>): List<VerbAnalysis> =
        features.mapNotNull { values ->
            val rootValue = featureValue(values, rootFeature) ?: return@mapNotNull null
            val root = readValue(rootValue)
            val lemma = featureValue(values, lemmaFeature)?.let(::readValue) ?: root
            VerbAnalysis(
                rootId = stableHash64(root),
                featureId = values.contentHashCode(),
                sourceClass = AmharicVerbSourceClass.REGULAR,
                lexeme = lemma,
                root = root,
                rootFrequency = maxOf(
                    DEFAULT_ROOT_FREQUENCY,
                    requireNotNull(buffer).getInt(rootFrequencyOffset + rootValue * Int.SIZE_BYTES),
                ),
                morphologyCost = morphologyCost(values),
            )
        }.distinctBy { it.analysisId }

    private fun featureValue(features: ShortArray, feature: Int): Int? {
        if (feature < 0) return null
        val encoded = features[feature].toInt() and 0xffff
        return if (encoded == 0) null else encoded - 1
    }

    private fun morphologyCost(features: ShortArray): Int {
        var cost = 0
        var index = 0
        while (index < morphologyExactRules.size) {
            val feature = morphologyExactRules[index]
            val value = morphologyExactRules[index + 1]
            if ((features[feature].toInt() and 0xffff) == value) {
                cost += morphologyExactRules[index + 2]
            }
            index += 3
        }
        index = 0
        while (index < morphologyNonZeroRules.size) {
            val encoded = features[morphologyNonZeroRules[index]].toInt() and 0xffff
            if (encoded != 0 && encoded != morphologyNonZeroRules[index + 1]) {
                cost += morphologyNonZeroRules[index + 2]
            }
            index += 3
        }
        return cost
    }

    private fun rootFrequency(features: ShortArray): Int {
        val root = featureValue(features, rootFeature) ?: return 0
        return requireNotNull(buffer).getInt(rootFrequencyOffset + root * Int.SIZE_BYTES)
    }

    private fun preferredRoot(alternatives: List<ShortArray>): Int =
        alternatives.maxBy(::rootFrequency).let { featureValue(it, rootFeature) ?: -1 }

    private fun paradigmHash(alternatives: List<ShortArray>): Int {
        val features = alternatives.minBy(::morphologyCost)
        var hash = 1
        PARADIGM_FEATURES.forEach { name ->
            val encoded = featureIds[name]?.let { features[it].toInt() and 0xffff } ?: 0
            hash = 31 * hash + encoded
        }
        return hash
    }

    private fun topFeatures(): List<ShortArray> = listOf(ShortArray(featureCount))

    private fun unify(current: List<ShortArray>, weight: Int): List<ShortArray>? {
        if (weight == 0) return current
        val content = requireNotNull(buffer)
        val index = weightIndexOffset + weight * WEIGHT_INDEX_SIZE
        val pageId = content.getShort(index).toInt() and 0xffff
        var cursor = content.getShort(index + 2).toInt() and 0xffff
        val length = content.getShort(index + 4).toInt() and 0xffff
        val end = cursor + length
        val page = requireNotNull(pageCache).get(pageId)
        val alternativeCount = page[cursor++].toInt() and 0xff
        val merged = LinkedHashMap<FeatureKey, ShortArray>()
        repeat(alternativeCount) {
            val assignmentCount = page[cursor++].toInt() and 0xff
            val assignmentStart = cursor
            current.forEach { base ->
                val values = base.clone()
                var assignment = assignmentStart
                var valid = true
                repeat(assignmentCount) {
                    val feature = page[assignment].toInt() and 0xff
                    val value = (
                        ((page[assignment + 1].toInt() and 0xff) shl 8) or
                            (page[assignment + 2].toInt() and 0xff)
                        ) + 1
                    val previous = values[feature].toInt() and 0xffff
                    if (previous != 0 && previous != value) valid = false
                    values[feature] = value.toShort()
                    assignment += 3
                }
                if (valid) merged.putIfAbsent(FeatureKey(values), values)
            }
            cursor = assignmentStart + assignmentCount * 3
        }
        require(cursor == end) { "invalid HornMorpho constraint record" }
        return merged.values.takeIf { it.isNotEmpty() }?.toList()
    }

    private fun inflatePage(page: Int): ByteArray {
        val content = requireNotNull(buffer)
        val index = pageIndexOffset + page * PAGE_INDEX_SIZE
        val compressedOffset = content.getInt(index)
        val compressedLength = content.getInt(index + 4)
        val rawLength = content.getShort(index + 8).toInt() and 0xffff
        val compressed = ByteArray(compressedLength)
        content.duplicate().apply {
            position(pageDataOffset + compressedOffset)
            get(compressed)
        }
        val raw = ByteArray(rawLength)
        val inflater = Inflater(true)
        try {
            inflater.setInput(compressed)
            require(inflater.inflate(raw) == rawLength && inflater.finished()) {
                "invalid HornMorpho constraint page"
            }
        } finally {
            inflater.end()
        }
        return raw
    }

    private fun readValue(index: Int): String = values[index]

    private fun readIndexedString(indexOffset: Int, index: Int): String {
        val content = requireNotNull(buffer)
        val record = indexOffset + index * STRING_INDEX_SIZE
        val offset = content.getInt(record)
        val length = content.getShort(record + 4).toInt() and 0xffff
        val bytes = ByteArray(length)
        content.duplicate().apply {
            position(stringOffset + offset)
            get(bytes)
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun stateFirstTransition(state: Int): Int =
        requireNotNull(buffer).getInt(stateOffset + state * STATE_SIZE)

    private fun stateTransitionCount(state: Int): Int =
        requireNotNull(buffer).getShort(stateOffset + state * STATE_SIZE + 4).toInt() and 0xffff

    private fun isFinal(state: Int): Boolean =
        requireNotNull(buffer).getShort(stateOffset + state * STATE_SIZE + 6).toInt() != 0

    private fun transitionTarget(index: Int): Int =
        requireNotNull(buffer).getInt(transitionOffset + index * TRANSITION_SIZE)

    private fun transitionInput(index: Int): Int =
        requireNotNull(buffer).getShort(transitionOffset + index * TRANSITION_SIZE + 4).toInt() and 0xffff

    private fun transitionWeight(index: Int): Int =
        requireNotNull(buffer).getShort(transitionOffset + index * TRANSITION_SIZE + 8).toInt() and 0xffff

    private fun validateHeader() {
        val content = requireNotNull(buffer)
        val valid = runCatching {
            require(
                stateCount > 0 &&
                    transitionCount > 0 &&
                    ruleWeightCount > 0 &&
                    constraintPageCount > 0 &&
                    featureCount in 1..255 &&
                    valueCount in 1..0xfffe &&
                    stateOffset == HEADER_SIZE &&
                    transitionOffset.toLong() == stateOffset + stateCount.toLong() * STATE_SIZE &&
                    weightIndexOffset.toLong() == transitionOffset + transitionCount.toLong() * TRANSITION_SIZE &&
                    pageIndexOffset.toLong() == weightIndexOffset + ruleWeightCount.toLong() * WEIGHT_INDEX_SIZE &&
                    pageDataOffset.toLong() == pageIndexOffset + constraintPageCount.toLong() * PAGE_INDEX_SIZE &&
                    featureIndexOffset in pageDataOffset..byteSize &&
                    valueIndexOffset.toLong() == featureIndexOffset + featureCount.toLong() * STRING_INDEX_SIZE &&
                    rootFrequencyOffset.toLong() == valueIndexOffset + valueCount.toLong() * STRING_INDEX_SIZE &&
                    stringOffset.toLong() == rootFrequencyOffset + valueCount.toLong() * Int.SIZE_BYTES &&
                    content.getInt(64) == byteSize &&
                    initialState in 0 until stateCount
            )
            repeat(constraintPageCount) { page ->
                val index = pageIndexOffset + page * PAGE_INDEX_SIZE
                val offset = content.getInt(index)
                val compressedLength = content.getInt(index + 4)
                val rawLength = content.getShort(index + 8).toInt() and 0xffff
                require(
                    offset >= 0 &&
                        compressedLength > 0 &&
                        rawLength > 0 &&
                        pageDataOffset.toLong() + offset + compressedLength <= featureIndexOffset
                )
            }
            repeat(ruleWeightCount) { weight ->
                val index = weightIndexOffset + weight * WEIGHT_INDEX_SIZE
                val page = content.getShort(index).toInt() and 0xffff
                val offset = content.getShort(index + 2).toInt() and 0xffff
                val length = content.getShort(index + 4).toInt() and 0xffff
                require(page < constraintPageCount && length > 0)
                val pageLength = content.getShort(pageIndexOffset + page * PAGE_INDEX_SIZE + 8)
                    .toInt() and 0xffff
                require(offset + length <= pageLength)
            }
        }.isSuccess
        require(valid) { "invalid HornMorpho runtime records" }
    }

    private fun verifyChecksum(expected: Long) {
        val checksum = CRC32()
        val content = requireNotNull(buffer).duplicate()
        content.position(HEADER_SIZE)
        val chunk = ByteArray(64 * 1024)
        while (content.hasRemaining()) {
            val count = minOf(content.remaining(), chunk.size)
            content.get(chunk, 0, count)
            checksum.update(chunk, 0, count)
        }
        require(checksum.value == expected) { "HornMorpho runtime checksum mismatch" }
    }

    companion object {
        const val ASSET_NAME = "amharic_verbs.ahrf"
        const val MANIFEST_ASSET_NAME = "amharic_verbs_manifest.properties"
        private const val MAGIC = 0x41485246
        private const val VERSION = 1
        private const val HEADER_SIZE = 112
        private const val STATE_SIZE = 8
        private const val TRANSITION_SIZE = 10
        private const val WEIGHT_INDEX_SIZE = 8
        private const val PAGE_INDEX_SIZE = 12
        private const val STRING_INDEX_SIZE = 6
        private const val PAGE_CACHE_SIZE = 4
        private const val COMPLETION_CACHE_SIZE = 128
        private const val COMPLETION_POOL_MULTIPLIER = 2
        private const val COMPLETIONS_PER_PARADIGM = 4
        private const val COMPLETIONS_PER_ROOT = 8
        private const val FUZZY_POOL_MULTIPLIER = 4
        private const val MAX_PATH_DEPTH = 160
        private const val MAX_FRONTIER_SIZE = 8_192
        private const val MAX_SURFACE_LENGTH = 32
        private const val EXACT_ANALYSIS_LIMIT = 16
        private const val EXACT_EXPANSION_LIMIT = 1_000_000
        private const val COMPLETION_EXPANSION_LIMIT = 50_000
        private const val FUZZY_EXPANSION_LIMIT = 250_000
        private const val DEFAULT_ROOT_FREQUENCY = 1
        private val PARADIGM_FEATURES = listOf(
            "r",
            "a",
            "v",
            "t",
            "sp",
            "sn",
            "sg",
            "O",
            "o",
            "oc",
            "det",
            "acc",
            "neg",
            "rel",
            "sub",
            "ax",
        )

        val EMPTY = AmharicVerbLexicon(null, 0, false)

        fun fromBytes(bytes: ByteArray): AmharicVerbLexicon =
            AmharicVerbLexicon(ByteBuffer.wrap(bytes), bytes.size, true)

        fun load(
            assets: AssetManager,
            onWarning: (String) -> Unit,
        ): AmharicVerbLexicon = try {
            val metadata = assets.open(MANIFEST_ASSET_NAME).use { input ->
                Properties().also { properties -> properties.load(input) }
            }
            require(metadata.getProperty("schemaVersion") == VERSION.toString())
            require(metadata.getProperty("asset") == ASSET_NAME)
            require(metadata.getProperty("representation") == "weighted-runtime-fst")
            require(metadata.getProperty("generatedSurfaces") == "0")
            val declaredLength = requireNotNull(metadata.getProperty("length")).toLong()
            val declaredDigest = requireNotNull(metadata.getProperty("sha256"))
            require(declaredDigest.length == MessageDigest.getInstance("SHA-256").digestLength * 2)
            val mapped = assets.openFd(ASSET_NAME).use { descriptor ->
                require(descriptor.declaredLength == declaredLength)
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
            AmharicVerbLexicon(mapped, declaredLength.toInt(), false)
        } catch (failure: Throwable) {
            onWarning("Amharic verb morphology disabled: ${failure.message ?: failure.javaClass.simpleName}")
            EMPTY
        }

        private fun stableHash64(value: String): Long {
            var hash = -0x340d631b7bdddcdbL
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                hash = hash xor (byte.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash
        }
    }
}
