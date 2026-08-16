package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.EthiopicNormalizer

enum class MorphSource {
    GENERATED_MORPHOLOGY,
}

enum class NominalNumber {
    SINGULAR,
    PLURAL,
    COLLECTIVE,
}

enum class NominalPossessor {
    FIRST_SINGULAR,
    FIRST_PLURAL,
    SECOND_MASCULINE_SINGULAR,
    SECOND_FEMININE_SINGULAR,
    SECOND_PLURAL,
    SECOND_FORMAL,
    THIRD_MASCULINE_SINGULAR,
    THIRD_FEMININE_SINGULAR,
    THIRD_PLURAL,
}

sealed interface NominalAffix {
    data class Adposition(val surface: String) : NominalAffix
    data object Distributive : NominalAffix
    data object Collective : NominalAffix
    data class Number(val value: NominalNumber) : NominalAffix
    data class Human(val feminine: Boolean) : NominalAffix
    data class Possessive(val person: NominalPossessor) : NominalAffix
    data class Definite(val gender: Gender, val ituVariant: Boolean = false) : NominalAffix
    data class Accusative(val alternate: Boolean = false) : NominalAffix
    data class Conjunctive(val surface: String) : NominalAffix
    data object PostpositionGa : NominalAffix
}

data class MorphAnalysis(
    val lexemeId: Long,
    val lemma: String,
    val surface: String,
    val partOfSpeech: PartOfSpeech,
    val features: NominalFeatures,
    val affixes: List<NominalAffix>,
    val orthographicCost: Int,
)

data class MorphCandidate(
    val word: String,
    val normalizedKey: String,
    val analysis: MorphAnalysis,
    val source: MorphSource,
    val lexicalFrequency: Int,
    val surfaceFrequency: Int? = null,
)

internal enum class NominalPrefixMarker {
    NONE,
    DISTRIBUTIVE,
    COLLECTIVE,
}

internal data class NominalPrefixContext(
    val surface: String,
    val adposition: String? = null,
    val marker: NominalPrefixMarker = NominalPrefixMarker.NONE,
    val contractedInitial: Boolean = false,
)

internal object NominalRuleGraph {
    private data class State(
        val surface: String,
        val affixes: List<NominalAffix>,
        val cost: Int,
    )

    private val conjunctions = listOf("ም", "ስ", "ማ", "ሳ", "ና", "ኮ")
    private val transformedPossessives = listOf("ችን", "ችሁ", "ቸው")
    private val directPossessives = listOf("ዎን", "ዎት", "ህ", "ሽ", "ዎ")
    private const val MAX_REVERSE_STATES = 128
    private const val MAX_REVERSE_DEPTH = 8

    fun prefixContexts(typed: String): List<Pair<NominalPrefixContext, String>> {
        val candidates = buildList {
            NominalFeatureBits.adpositions.forEach { adposition ->
                add(NominalPrefixContext(adposition, adposition))
            }
            listOf("በ", "ለ", "ከ").forEach { adposition ->
                add(NominalPrefixContext(adposition + "የ", adposition, NominalPrefixMarker.DISTRIBUTIVE))
            }
            add(NominalPrefixContext("እየ", marker = NominalPrefixMarker.DISTRIBUTIVE))
            add(NominalPrefixContext("እነ", marker = NominalPrefixMarker.COLLECTIVE))
            NominalFeatureBits.adpositions.forEach { adposition ->
                add(NominalPrefixContext(adposition + "እነ", adposition, NominalPrefixMarker.COLLECTIVE))
            }
            listOf("የ", "ለ", "በ", "ከ").forEach { adposition ->
                val contracted = replaceLastWithOrder(adposition, 3) ?: return@forEach
                add(NominalPrefixContext(contracted, adposition, contractedInitial = true))
            }
        }.sortedByDescending { it.surface.length }
        val matching = candidates.filter {
            typed.length > it.surface.length && typed.startsWith(it.surface)
        }
        val plain = NominalPrefixContext("") to typed
        return buildList {
            add(plain)
            matching.forEach { context ->
                val fragment = typed.removePrefix(context.surface)
                add(context to if (context.contractedInitial) "አ$fragment" else fragment)
            }
        }
    }

    fun reverseStemCandidates(fragment: String): Set<String> {
        if (fragment.isEmpty()) return emptySet()
        val seen = linkedSetOf(fragment)
        var frontier = listOf(fragment)
        repeat(MAX_REVERSE_DEPTH) {
            val next = ArrayList<String>()
            for (candidate in frontier) {
                reverseOne(candidate).forEach { value ->
                    if (value.isNotEmpty() && seen.size < MAX_REVERSE_STATES && seen.add(value)) next += value
                }
            }
            frontier = next
            if (frontier.isEmpty() || seen.size >= MAX_REVERSE_STATES) return seen
        }
        return seen
    }

    fun generate(
        lexeme: AmharicNounMorphology.Lexeme,
        prefix: NominalPrefixContext,
        typed: String,
        limit: Int,
    ): List<MorphCandidate> {
        if (limit <= 0 || lexeme.effectiveBits and NominalFeatureBits.PRODUCTIVE == 0L) return emptyList()
        if (!allowsPrefix(lexeme, prefix)) return emptyList()
        val normalizedTyped = EthiopicNormalizer.normalize(typed)
        val partOfSpeech = when {
            lexeme.kind in 2..3 -> PartOfSpeech.PROPER_NOUN
            lexeme.effectiveBits and NominalFeatureBits.POS_NOUN != 0L -> PartOfSpeech.NOUN
            else -> PartOfSpeech.ADJECTIVE
        }
        val prefixAffixes = buildList {
            prefix.adposition?.let { add(NominalAffix.Adposition(it)) }
            if (prefix.marker == NominalPrefixMarker.DISTRIBUTIVE) add(NominalAffix.Distributive)
            if (prefix.marker == NominalPrefixMarker.COLLECTIVE) add(NominalAffix.Collective)
        }
        val coreStates = coreStates(lexeme, prefix, prefixAffixes, normalizedTyped)
        val candidates = ArrayList<MorphCandidate>(minOf(limit, 64))
        val identities = HashSet<Pair<String, List<NominalAffix>>>()
        for (tier in 0..4) {
            for (core in coreStates) {
                for (terminal in terminalStates(core, normalizedTyped, tier)) {
                val normalized = EthiopicNormalizer.normalize(terminal.surface)
                if (!normalized.startsWith(normalizedTyped)) continue
                if (!identities.add(normalized to terminal.affixes)) continue
                val analysis = MorphAnalysis(
                    lexemeId = lexeme.lexemeId,
                    lemma = lexeme.surface,
                    surface = terminal.surface,
                    partOfSpeech = partOfSpeech,
                    features = lexeme.nominalFeatures,
                    affixes = terminal.affixes,
                    orthographicCost = terminal.cost,
                )
                candidates += MorphCandidate(
                    word = terminal.surface,
                    normalizedKey = normalized,
                    analysis = analysis,
                    source = MorphSource.GENERATED_MORPHOLOGY,
                    lexicalFrequency = lexeme.frequency,
                )
                if (candidates.size >= limit) return candidates
                }
            }
        }
        return candidates
    }

    private fun coreStates(
        lexeme: AmharicNounMorphology.Lexeme,
        prefix: NominalPrefixContext,
        prefixAffixes: List<NominalAffix>,
        normalizedTyped: String,
    ): List<State> {
        val stems = ArrayList<State>()
        val singularAffixes = prefixAffixes + NominalAffix.Number(
            if (prefix.marker == NominalPrefixMarker.COLLECTIVE) NominalNumber.COLLECTIVE else NominalNumber.SINGULAR
        )
        applyPrefix(lexeme.surface, prefix)?.let { stems += State(it, singularAffixes, 0) }
        if (prefix.marker != NominalPrefixMarker.COLLECTIVE &&
            lexeme.effectiveBits and NominalFeatureBits.ORDINARY_PLURAL != 0L
        ) {
            pluralForms(lexeme.surface, lexeme.stemClass).forEach { (surface, cost) ->
                applyPrefix(surface, prefix)?.let {
                    stems += State(it, prefixAffixes + NominalAffix.Number(NominalNumber.PLURAL), cost + 1)
                }
            }
        }

        val result = ArrayList<State>()
        stems.forEach { numberState ->
            if (surfaceCompatible(numberState.surface, normalizedTyped)) result += numberState
            val plural = numberState.affixes.any {
                it is NominalAffix.Number && it.value != NominalNumber.SINGULAR
            }
            if (lexeme.effectiveBits and NominalFeatureBits.DEFINITE != 0L) {
                determinerStates(numberState, lexeme, plural).forEach {
                    if (surfaceCompatible(it.surface, normalizedTyped)) result += it
                }
            }
            if (lexeme.effectiveBits and NominalFeatureBits.POSSESSIVE != 0L) {
                possessiveStates(numberState, plural).forEach {
                    if (surfaceCompatible(it.surface, normalizedTyped)) result += it
                }
            }
        }

        if (prefix.marker != NominalPrefixMarker.COLLECTIVE &&
            lexeme.effectiveBits and NominalFeatureBits.HUMAN_SUFFIX != 0L
        ) {
            listOf(false to "የ", false to "ዬ", true to "ዮ").forEachIndexed { index, (feminine, suffix) ->
                val surface = applyPrefix(lexeme.surface + suffix, prefix) ?: return@forEachIndexed
                val state = State(
                    surface,
                    prefixAffixes + NominalAffix.Number(NominalNumber.SINGULAR) + NominalAffix.Human(feminine),
                    index,
                )
                if (surfaceCompatible(state.surface, normalizedTyped)) {
                    result += state
                    val possessed = State(
                        surface + "ው",
                        state.affixes + NominalAffix.Possessive(NominalPossessor.THIRD_MASCULINE_SINGULAR),
                        state.cost + 1,
                    )
                    if (surfaceCompatible(possessed.surface, normalizedTyped)) result += possessed
                }
            }
        }
        return result
    }

    private fun determinerStates(state: State, lexeme: AmharicNounMorphology.Lexeme, plural: Boolean): List<State> {
        val result = ArrayList<State>()
        masculineDefinite(state.surface)?.let {
            result += State(
                it,
                state.affixes + NominalAffix.Definite(Gender.MASCULINE),
                state.cost + 1,
            )
        }
        if (!plural) {
            feminineDefiniteForms(state.surface).forEach { (surface, cost) ->
                result += State(
                    surface,
                    state.affixes + NominalAffix.Definite(Gender.FEMININE),
                    state.cost + 1 + cost,
                )
            }
            feminineItu(state.surface)?.let {
                result += State(
                    it,
                    state.affixes + NominalAffix.Definite(Gender.FEMININE, ituVariant = true),
                    state.cost + if (lexeme.effectiveBits and NominalFeatureBits.GENDER_FEMININE != 0L) 1 else 2,
                )
            }
        }
        return result
    }

    private fun possessiveStates(state: State, plural: Boolean): List<State> = buildList {
        firstPersonSingular(state.surface)?.let {
            add(state.possessive(it, NominalPossessor.FIRST_SINGULAR, 1))
        }
        transformedSuffixForms(state.surface, "ችን").forEach { (surface, cost) ->
            add(state.possessive(surface, NominalPossessor.FIRST_PLURAL, 2 + cost))
        }
        add(state.possessive(state.surface + "ህ", NominalPossessor.SECOND_MASCULINE_SINGULAR, 1))
        add(state.possessive(state.surface + "ሽ", NominalPossessor.SECOND_FEMININE_SINGULAR, 1))
        transformedSuffixForms(state.surface, "ችሁ").forEach { (surface, cost) ->
            add(state.possessive(surface, NominalPossessor.SECOND_PLURAL, 2 + cost))
        }
        listOf("ዎ", "ዎን", "ዎት").forEachIndexed { index, suffix ->
            add(state.possessive(state.surface + suffix, NominalPossessor.SECOND_FORMAL, 2 + index))
        }
        masculineDefinite(state.surface)?.let {
            add(state.possessive(it, NominalPossessor.THIRD_MASCULINE_SINGULAR, 1))
        }
        if (!plural) {
            feminineDefiniteForms(state.surface).forEach { (surface, cost) ->
                add(state.possessive(surface, NominalPossessor.THIRD_FEMININE_SINGULAR, 1 + cost))
            }
        }
        transformedSuffixForms(state.surface, "ቸው").forEach { (surface, cost) ->
            add(state.possessive(surface, NominalPossessor.THIRD_PLURAL, 2 + cost))
        }
    }.distinctBy { it.surface to it.affixes.last() }

    private fun State.possessive(surface: String, person: NominalPossessor, extraCost: Int) = State(
        surface,
        affixes + NominalAffix.Possessive(person),
        cost + extraCost,
    )

    private fun terminalStates(core: State, normalizedTyped: String, tier: Int): Sequence<State> = sequence {
        if (tier == 0) {
            yield(core)
            return@sequence
        }
        val accusatives = listOf(
            State(core.surface + "ን", core.affixes + NominalAffix.Accusative(), core.cost + 1),
            State(core.surface + "ኑ", core.affixes + NominalAffix.Accusative(alternate = true), core.cost + 3),
        )
        if (tier == 1) {
            if (surfaceCompatible(accusatives[0].surface, normalizedTyped)) yield(accusatives[0])
            return@sequence
        }
        if (tier == 2) {
            if (surfaceCompatible(accusatives[1].surface, normalizedTyped)) yield(accusatives[1])
            return@sequence
        }
        if (tier == 3) {
            conjunctions.forEachIndexed { index, suffix ->
                val state = State(
                    core.surface + suffix,
                    core.affixes + NominalAffix.Conjunctive(suffix),
                    core.cost + 2 + index,
                )
                if (surfaceCompatible(state.surface, normalizedTyped)) yield(state)
            }
            val ga = State(core.surface + "ጋ", core.affixes + NominalAffix.PostpositionGa, core.cost + 2)
            if (surfaceCompatible(ga.surface, normalizedTyped)) yield(ga)
            return@sequence
        }
        accusatives.forEach { accusative ->
            conjunctions.forEachIndexed { index, suffix ->
                val state = State(
                    accusative.surface + suffix,
                    accusative.affixes + NominalAffix.Conjunctive(suffix),
                    accusative.cost + 2 + index,
                )
                if (surfaceCompatible(state.surface, normalizedTyped)) yield(state)
            }
            val state = State(
                accusative.surface + "ጋ",
                accusative.affixes + NominalAffix.PostpositionGa,
                accusative.cost + 2,
            )
            if (surfaceCompatible(state.surface, normalizedTyped)) yield(state)
        }
    }

    private fun allowsPrefix(lexeme: AmharicNounMorphology.Lexeme, prefix: NominalPrefixContext): Boolean {
        val bits = lexeme.effectiveBits
        if (prefix.contractedInitial && (!lexeme.surface.startsWith("አ") || bits and NominalFeatureBits.INITIAL_DELETION == 0L)) {
            return false
        }
        prefix.adposition?.let { adposition ->
            if (bits and NominalFeatureBits.ADPOSITION == 0L) return false
            if (bits and NominalFeatureBits.adpositionBit(adposition) == 0L) return false
            if (adposition == "የ" && bits and NominalFeatureBits.GENITIVE == 0L) return false
        }
        if (prefix.marker == NominalPrefixMarker.DISTRIBUTIVE && bits and NominalFeatureBits.DISTRIBUTIVE == 0L) {
            return false
        }
        if (prefix.marker == NominalPrefixMarker.COLLECTIVE) {
            if (bits and NominalFeatureBits.COLLECTIVE == 0L) return false
            if (lexeme.kind != 2 && lexeme.nominalFeatures.human == FeatureState.NEGATIVE) return false
        }
        return true
    }

    private fun applyPrefix(stem: String, prefix: NominalPrefixContext): String? {
        if (!prefix.contractedInitial) return prefix.surface + stem
        if (!stem.startsWith("አ")) return null
        return prefix.surface + stem.drop(1)
    }

    private fun pluralForms(surface: String, stemClass: StemClass): List<Pair<String, Int>> {
        if (surface.isEmpty()) return emptyList()
        if (stemClass == StemClass.ALTERNATE_AN) {
            if (surface.endsWith("ዊ")) return listOf(surface + "ያን" to 0)
            return replaceLastWithOrder(surface, 3)?.plus("ን")?.let { listOf(it to 0) }.orEmpty()
        }
        val order = AmharicTable.orderIndexOfFidel(surface.last()) ?: return emptyList()
        val result = LinkedHashMap<String, Int>()
        if (order == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(surface, 6)?.plus("ች")?.let { result[it] = 0 }
            replaceLastWithOrder(surface, 0)?.plus("ዎች")?.let { result[it] = 2 }
        } else {
            result[surface + "ዎች"] = 0
            if (order == 2 || order == 3 || order == 4) {
                replaceLastWithOrder(surface, 6)?.plus("ች")?.let { result[it] = 1 }
            }
        }
        return result.entries.map { it.key to it.value }
    }

    private fun masculineDefinite(surface: String): String? {
        if (surface.isEmpty() || surface.endsWith("ው")) return null
        return if (AmharicTable.orderIndexOfFidel(surface.last()) == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(surface, 1)
        } else {
            surface + "ው"
        }
    }

    private fun feminineDefiniteForms(surface: String): List<Pair<String, Int>> {
        if (surface.isEmpty()) return emptyList()
        val result = LinkedHashMap<String, Int>()
        val order = AmharicTable.orderIndexOfFidel(surface.last())
        val labialized = AmharicTable.labializedFormOfFidel(surface.last())
        if (order == AmharicTable.BARE_FORM_INDEX && labialized != null) {
            result[surface.dropLast(1) + labialized] = 0
            result[surface + "ዋ"] = 2
        } else {
            result[surface + "ዋ"] = 0
            if (labialized != null) result[surface.dropLast(1) + labialized] = 1
            if (order == 1 || order == 6) result[surface + "አ"] = 2
        }
        return result.entries.map { it.key to it.value }
    }

    private fun feminineItu(surface: String): String? {
        if (surface.isEmpty()) return null
        return if (AmharicTable.orderIndexOfFidel(surface.last()) == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(surface, 2)?.plus("ቱ")
        } else {
            surface + "ኢቱ"
        }
    }

    private fun firstPersonSingular(surface: String): String? {
        if (surface.isEmpty()) return null
        return if (AmharicTable.orderIndexOfFidel(surface.last()) == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(surface, 4)
        } else {
            surface + "ዬ"
        }
    }

    private fun transformedSuffixForms(surface: String, suffix: String): List<Pair<String, Int>> {
        val finalOrder = AmharicTable.orderIndexOfFidel(surface.last())
        if (finalOrder == AmharicTable.BARE_FORM_INDEX) {
            return listOf(requireNotNull(replaceLastWithOrder(surface, 3)) + suffix to 0)
        }
        return when (finalOrder) {
            2, 4 -> listOf(surface + "ያ" + suffix to 0, surface + suffix to 1)
            1, 6 -> listOf(surface + "አ" + suffix to 0, surface + suffix to 1)
            3 -> listOf(surface + suffix to 0, surface + "ያ" + suffix to 1)
            else -> listOf(surface + suffix to 0)
        }
    }

    private fun reverseOne(surface: String): Set<String> = buildSet {
        fun strip(suffix: String) {
            if (surface.endsWith(suffix) && surface.length > suffix.length) add(surface.dropLast(suffix.length))
        }
        strip("ጋ")
        conjunctions.sortedByDescending(String::length).forEach(::strip)
        strip("ኑ")
        if (surface.endsWith("ን") && surface.length > 1) {
            val remainder = surface.dropLast(1)
            add(remainder)
            if (AmharicTable.orderIndexOfFidel(remainder.last()) == 3) {
                replaceLastWithOrder(remainder, AmharicTable.BARE_FORM_INDEX)?.let(::add)
            }
        }
        directPossessives.forEach(::strip)
        transformedPossessives.forEach { suffix ->
            if (surface.endsWith(suffix) && surface.length > suffix.length) {
                val remainder = surface.dropLast(suffix.length)
                add(remainder)
                reverseLastOrders(remainder).forEach(::add)
                if (remainder.endsWith("ያ") || remainder.endsWith("አ")) add(remainder.dropLast(1))
            }
        }
        if (surface.endsWith("ያን") && surface.length > 2) add(surface.dropLast(2))
        if (surface.endsWith("ዎች") && surface.length > 2) {
            val remainder = surface.dropLast(2)
            add(remainder)
            reverseLastOrders(remainder).forEach(::add)
        }
        if (surface.endsWith("ች") && surface.length > 1) {
            reverseLastOrders(surface.dropLast(1)).forEach(::add)
        }
        if (surface.endsWith("ቱ") && surface.length > 1) {
            val remainder = surface.dropLast(1)
            reverseLastOrders(remainder).forEach(::add)
        }
        listOf("ው", "ዋ", "ዬ", "የ", "ዮ").forEach { suffix ->
            if (surface.endsWith(suffix) && surface.length > 1) add(surface.dropLast(1))
        }
        val finalOrder = AmharicTable.orderIndexOfFidel(surface.last())
        if (finalOrder == 1 || finalOrder == 4 || AmharicTable.labializedFormOfFidel(surface.last()) == surface.last()) {
            AmharicTable.bareFormOfFidel(surface.last())?.let { add(surface.dropLast(1) + it) }
        }
    }

    private fun reverseLastOrders(surface: String): Set<String> {
        if (surface.isEmpty()) return emptySet()
        return buildSet {
            listOf(AmharicTable.BARE_FORM_INDEX, 2, 3, 4).forEach { order ->
                replaceLastWithOrder(surface, order)?.let(::add)
            }
            AmharicTable.bareFormOfFidel(surface.last())?.let { add(surface.dropLast(1) + it) }
        }
    }

    private fun surfaceCompatible(surface: String, normalizedTyped: String): Boolean {
        val normalized = EthiopicNormalizer.normalize(surface)
        return normalized.startsWith(normalizedTyped) || normalizedTyped.startsWith(normalized)
    }

    private fun replaceLastWithOrder(surface: String, orderIndex: Int): String? {
        if (surface.isEmpty()) return null
        val replacement = AmharicTable.formInSameFamily(surface.last(), orderIndex) ?: return null
        return surface.dropLast(1) + replacement
    }
}
