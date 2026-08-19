# Full HornMorpho Amharic Features Implementation Plan

This document outlines the architecture, data pipeline, performance strategies, and step-by-step implementation for adding all 7 missing HornMorpho capabilities to Addiyon's Amharic morphology module while maintaining ultra-fast (<10ms) on-device execution.

---

## 1. Executive Summary & Goals

The goal is to bring Addiyon's Amharic morphology engine to full practical parity with [HornMorpho 5.3.6](https://github.com/hltdi/HornMorpho) across 7 critical dimensions:
1. **Light Verbs & Preverb Idioms** (`ዝም አለ`, `ብድግ አለ`, `ቁጭ አለ`)
2. **Multiword Verb Expressions & Auxiliary Compounding** (`እየ... ነው`, `... ነበር`, `ሊ... ነው`)
3. **Verbal Nouns (Infinitives) in the Nominal Cascade** (`መሄድ` $\rightarrow$ `መሄዴ`, `መሄዳችን`, `መምጣቱ`)
4. **Pronouns, Demonstratives & Interrogatives Morphology** (`እኔ` $\rightarrow$ `እንደእኔ`/`እንደኔ`, `ለእሱ`, `የእኛ`, `በዚህ`, `ስለዚህ`)
5. **Overt Copula Inflections & Nominal Clitics** (`ነው`, `ነበር`, `አይደለም`, `ናቸው`, and attached clitics like `ሰውነኝ`)
6. **Bounded OOV Unknown-Word Stem Guesser** (Robust fallback for slang, foreign roots, and neologisms)
7. **Full Morphological Analysis, Segmentation & UniMorph / UD Feature API** (`የ-ቤት-ኦች-አችን-ን`, UD/UniMorph tagging)

---

## 2. High-Performance Architecture Principles

To ensure zero keystroke lag on low-end Android devices (budget: <10ms latency, zero GC pressure, memory footprint < 3MB):
1. **Bitmask & Finite-State Encodings**: Rules, features, and stem properties are encoded as 64-bit integer bitmasks (`NominalFeatureBits`).
2. **Memory-Mapped & Compact Lookup**: Lexicons remain memory-mapped or indexed in SQLite (`WITHOUT ROWID`, indexed integer keys).
3. **Bounded Reverse & Forward Search**: Search frontiers are capped (e.g. max depth 5, max states 64), terminating immediately once top candidates are found.
4. **Zero-Allocation Primitives**: Reusable string builders and primitive collections for inner graph traversals.
5. **Multi-tier Ranking Integrity**: Exact Lexeme > Attested Surface > Generated Morphology > Guesser Fallback > Fuzzy.

---

## 3. Feature Breakdown & Implementation Strategy

### Feature 1: Light Verbs & Preverbs (`v_light.lex`, `cas/v_light_stem.cas`)
- **Data Source**: HornMorpho `lex/v_light.lex` (778 preverbs) + 3 core light auxiliary paradigms (`አለ`, `አደረገ`, `አሰኘ`).
- **Implementation**:
  - Store normalized preverb stems in SQLite `morph_lexemes` (`kind=5` or bitmask `STEM_PREVERB`).
  - Add light-verb compounding generator in `AmharicVerbLexicon` / `NominalRuleGraph`.
  - Handle both spaced input (`zim al` $\rightarrow$ `ዝም አለ`, `ዝም ይላል`, `ዝም ብሎ`) and concatenated input (`zimal` $\rightarrow$ `ዝም አለ`).

### Feature 2: Multiword Verb Expressions & Auxiliary Cascades (`cas/vM.cas`, `cas/vMG.cas`)
- **Data Source**: HornMorpho auxiliary verb cascades and aspect combinations.
- **Implementation**:
  - Grammatical context engine in `AmharicSuggestionPipeline` & `SQLiteNgramModel`:
    - Converb marker detection (`-ኦ`, `-አ`): e.g. `ተቀምጦ` $\rightarrow$ boosts `ነበር`, `ነው`, `አለ`.
    - Progressive marker detection (`እየ-...`): e.g. `እየሄደ` $\rightarrow$ boosts `ነው`, `ነበር`, `አይደለም`.
    - Intentional marker detection (`ሊ-...`): e.g. `ሊመጣ` $\rightarrow$ boosts `ነው`, `ነበር`, `ይችላል`.
  - Phrase completion for common continuous compound verb forms.

### Feature 3: Verbal Nouns (Infinitives) in the Nominal Cascade
- **Data Source**: Verbal noun stems derived from verb roots (`መ- + stem` like `መሄድ`, `ማድረግ`, `መናገር`, `መጻፍ`).
- **Implementation**:
  - In `morph_lexemes` and `NominalRuleGraph`, admit verbal noun stems (`POS_VERBAL_NOUN`).
  - Enable verbal nouns to undergo the full nominal graph:
    - Possessives: `መሄድ` + `1s` $\rightarrow$ `መሄዴ`, `3ms` $\rightarrow$ `መሄዱ`, `1p` $\rightarrow$ `መሄዳችን`.
    - Definite & Accusative: `መሄዱን`, `ማድረጉን`, `መመሪያዎችን`.
    - Adpositions: `በመሄድ`, `ለመሄድ`, `ከመሄድ`, `ስለመሄድ`.

### Feature 4: Pronouns, Demonstratives, and Interrogatives Morphology
- **Data Source**: HornMorpho pronominal paradigms (`a.lg`, `lex/n_stem.lex`).
- **Implementation**:
  - Personal pronouns (`እኔ`, `አንተ`, `አንቺ`, `እሱ`, `እሷ`, `እኛ`, `እናንተ`, `እነሱ`, `እርስዎ`).
  - Demonstratives (`ይህ`, `ይሄ`, `ይህች`, `እነዚህ`, `ያ`, `ያች`, `እነዚያ`, `እዚህ`, `እዚያ`).
  - Interrogatives (`ማን`, `ምን`, `የት`, `መቼ`, `እንዴት`, `ስንት`, `ለምን`).
  - Model contraction rules:
    - `የ + እኔ` $\rightarrow$ `የእኔ` / `የኔ`
    - `ለ + እኔ` $\rightarrow$ `ለእኔ` / `ለኔ`
    - `በ + ይህ` $\rightarrow$ `በዚህ`, `ከ + ይህ` $\rightarrow$ `ከዚህ`, `ስለ + ይህ` $\rightarrow$ `ስለዚህ`
    - Accusative: `እኔን`, `አንተን`, `እሱን`, `ማንን`, `ምንን`.

### Feature 5: Overt Copula Inflections & Nominal Clitics
- **Data Source**: Upstream HornMorpho copula conjugation tables (`ነው`, `ነበር`, `አይደለም`).
- **Implementation**:
  - Affirmative present: `ነኝ`, `ነህ`, `ነሽ`, `ነው`, `ናት`/`ነች`, `ነን`, `ናችሁ`, `ናቸው`.
  - Negative present: `አይደለሁም`, `አይደለህም`, `አይደለሽም`, `አይደለም`, `አይደለችም`, `አይደለንም`, `አይደላችሁም`, `አይደሉም`.
  - Affirmative past: `ነበርኩ`, `ነበርክ`, `ነበርሽ`, `ነበር`/`ነበረ`, `ነበረች`, `ነበርን`, `ነበራችሁ`, `ነበሩ`.
  - Nominal clitic attachment in `NominalRuleGraph`: `stem + [affixes] + copula` (e.g. `ሰውነኝ`, `ደህናነሽ`, `የእኔነው`).

### Feature 6: Bounded OOV Unknown-Word Stem Guesser
- **Data Source**: HornMorpho `guesser` morphotactics & Amharic syllable phonotactics.
- **Implementation**:
  - When exact stem lookup in SQLite returns 0 results:
    1. Apply prefix stripping (`ለ-`, `በ-`, `ከ-`, `የ-`, `እንደ-`, `እስከ-`, `ስለ-`, `እየ-`, `እነ-`).
    2. Apply suffix stripping (`-ኦች`, `-አችን`, `-አቸው`, `-ን`, `-ም`, `-ና`, `-ጋ`).
    3. Validate remaining core with Fidel syllable phonotactics (length $\ge 2$, valid Ge'ez syllabic structure).
    4. Synthesize valid inflectional candidates flagged as `CandidateSource.GUESSER_MORPHOLOGY`.
    5. Ensure strict execution budget ($\le 1\text{ms}$).

### Feature 7: Full Morphological Analysis / Segmentation / UniMorph API
- **Implementation**:
  - Expose high-level pure-Kotlin API `AmharicMorphologyAnalyzer`:
    ```kotlin
    data class DetailedMorphAnalysis(
        val surface: String,
        val lemma: String,
        val root: String?,
        val partOfSpeech: PartOfSpeech,
        val segmentation: String, // e.g. "የ-ቤት-ኦች-አችን-ን"
        val features: Map<String, String>,
        val uniMorphTag: String, // e.g. "N;PL;PSS1P;ACC"
    )
    ```
  - Unifies `NominalRuleGraph` analysis and `AmharicVerbLexicon` analysis to decompose any input surface into segments and UniMorph/UD standard tags.

---

## 4. Affected Files & Deliverables

1. **Rule Engine & Logic**:
   - `language/amharic/.../suggestion/NominalRuleGraph.kt` (verbal nouns, pronouns, copulas, clitics, guesser)
   - `language/amharic/.../suggestion/NominalFeatures.kt` (updated feature bitmasks & enums)
   - `language/amharic/.../suggestion/NominalFeatureBits.kt` (new bit allocations)
   - `language/amharic/.../suggestion/AmharicVerbLexicon.kt` (light verbs and auxiliary constructs)
   - `language/amharic/.../suggestion/AmharicMorphologyAnalyzer.kt` [NEW] (segmentation & UniMorph API)
   - `language/amharic/.../suggestion/AmharicGuesser.kt` [NEW] (bounded OOV guesser)
2. **Pipeline & Ranking Integration**:
   - `language/amharic/.../language/amharic/AmharicSuggestionPipeline.kt`
   - `language/amharic/.../language/amharic/AmharicSuggestionEngine.kt`
   - `suggestions/core/.../suggestion/CandidateRanker.kt` (new source tiers for guesser/light verbs)
3. **Data Build Scripts**:
   - `tools/build_amharic_dict.py` (include preverbs, pronouns, verbal nouns)
   - `build-logic/.../dictionary/DictionaryDatabaseFormat.kt` (schema bump if necessary)
4. **Verification & Tests**:
   - `language/amharic/src/test/.../AmharicMorphologyAnalyzerTest.kt` [NEW]
   - `language/amharic/src/test/.../AmharicLightVerbTest.kt` [NEW]
   - `language/amharic/src/test/.../AmharicPronounMorphologyTest.kt` [NEW]
   - `language/amharic/src/test/.../AmharicGuesserTest.kt` [NEW]
   - `language/amharic/src/test/.../NominalRuleGraphTest.kt`
   - `language/amharic/src/test/.../AmharicSuggestionEngineIntegrationTest.kt`

---

## 5. Verification Plan

1. **Unit & Morphology Parity Tests**:
   - Run focused JVM unit tests against all 7 areas:
     ```sh
     /Users/dev/code/addiyon-keyboard/gradlew :language:amharic:testDebugUnitTest :suggestions:core:test
     ```
2. **Performance Benchmarks**:
   - Verify warm suggestion lookup latency $\le 10\text{ms}$ on low-RAM profiles.
   - Verify zero regression on English and base Amharic dictionary lookups.
3. **Compilation & Packaging**:
   - Compile and verify both products:
     ```sh
     /Users/dev/code/addiyon-keyboard/gradlew checkKeyboardProducts
     /Users/dev/code/addiyon-keyboard/gradlew :apps:addiyon:assembleDebug :apps:textrevamp:assembleDebug
     ```
