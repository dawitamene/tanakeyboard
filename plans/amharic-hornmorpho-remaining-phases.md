# Amharic HornMorpho remaining phases — implementation handoff

Status captured: 2026-08-12  
Repository: `/Users/dev/code/addiyon-keyboard`  
Branch at handoff: `textrevamp`  
HEAD at handoff: `fc0b927` (`feat: finalize modular keyboard products and Amharic lexicon`)  
HornMorpho snapshot: version 5.3.6, commit `7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`

## 1. Purpose

This document is the execution plan for completing Addiyon's Amharic
morphology and suggestion system after the lexicon replacement and first noun
morphology implementation.

The product direction is fixed:

- HornMorpho/Amsalu lexemes are the lexical authority.
- Productive forms such as `የሰው`, `ሰውን`, and `ቤቶች` must be constructed or
  validated morphologically, not retained as independent dictionary entries.
- A corpus may influence ranking, but it must not grant lexical validity.
- The old approximately 254,000-row surface wordlist must never return as a
  runtime dictionary or build input.
- The Android IME must stay offline, compact, fast, and independent of Python
  at runtime.
- HornMorpho's Python/transducer implementation is a build-time oracle and
  source of truth. Do not attempt a casual handwritten port of the complete
  system, especially not the verb system.

The target architecture is:

```text
Latin keystrokes
    -> Transliterator.candidateReadings()
    -> exact lexeme/dictionary lookup
    -> compact morphology lookup/generation
    -> sparse corpus and personal ranking evidence
    -> CandidateRanker
    -> suggestion chips

Lexical validity: HornMorpho lexeme + morphology
Ranking evidence: HornMorpho frequency + clean corpus + local personal history
```

## 2. Read this before changing anything

### 2.1 Repository rules

Read `/Users/dev/code/addiyon-keyboard/AGENTS.md` before doing work.

Important constraints from it:

- `:apps:addiyon` is the Amharic-capable product. It must not acquire an AI
  dependency or AI UI.
- Language implementation stays in `:language:amharic`; SQLite implementation
  stays in `:suggestions:sqlite`; shared ranking stays in `:suggestions:core`.
- Product app modules must remain composition roots.
- Do not introduce absolute document offsets into the composing layer.
- Every code change requires focused tests, Addiyon installation when a device
  exists, and both APK assemblies.
- All user-facing commands must use the full repository path.

No UI work is expected in the morphology phases. If a phase unexpectedly
requires UI changes, first read `docs/DESIGN_SYSTEM.md` and follow its contract.

### 2.2 The worktree is dirty and contains unrelated work

At the time of this handoff, morphology changes are uncommitted and the
worktree also contains unrelated product, AI, app-shell, runtime, and UI edits.
They belong to the user or another active task.

Do not run any of the following:

- `git reset --hard`
- `git checkout -- .`
- `git clean`
- blanket `git add .`
- formatting or mechanical rewrites across unrelated modules

Before editing, run:

```sh
cd /Users/dev/code/addiyon-keyboard
git status --short
git diff --check
git diff -- language/amharic suggestions build-logic tools
```

The current morphology change set is expected to involve only these paths:

- `apps/textrevamp/src/test/java/com/addiyon/keyboard/suggestion/DictionaryDbContractTest.kt`
- `build-logic/src/main/kotlin/com/addiyon/buildlogic/dictionary/DictionaryDatabaseFormat.kt`
- `build-logic/src/main/kotlin/com/addiyon/buildlogic/dictionary/GenerateDictionaryDatabase.kt`
- `language/amharic/hornmorpho/UPSTREAM.md`
- `language/amharic/src/dictionary/amharic_lexemes.dat`
- `language/amharic/src/dictionary/amharic_words.dat`
- `language/amharic/src/main/java/com/addiyon/keyboard/language/amharic/AmharicSuggestionEngine.kt`
- `language/amharic/src/main/java/com/addiyon/keyboard/suggestion/AmharicNounMorphology.kt`
- `language/amharic/src/main/java/com/addiyon/keyboard/transliteration/AmharicTable.kt`
- `language/amharic/src/test/java/com/addiyon/keyboard/suggestion/AmharicNounMorphologyTest.kt`
- `language/amharic/src/test/java/com/addiyon/keyboard/suggestion/CandidateRankerTest.kt`
- `suggestions/core/src/main/kotlin/com/addiyon/keyboard/suggestion/CandidateRanker.kt`
- `suggestions/sqlite/src/main/java/com/addiyon/keyboard/suggestion/SQLiteMorphLexicon.kt`
- `tools/README.md`
- `tools/build_amharic_dict.py`

Re-check the diff before staging because this list describes the captured
state, not a promise that nobody else will edit those files later.

### 2.3 Known unrelated full-suite failures

`checkKeyboardProducts` currently reports two architecture failures unrelated
to the morphology implementation:

1. Addiyon manual-screen classes are under the app module instead of a shared
   feature module:
   - `apps/addiyon/src/main/java/com/addiyon/keyboard/ui/manual/GuideModel.kt`
   - `apps/addiyon/src/main/java/com/addiyon/keyboard/ui/manual/RailMagnification.kt`
   - `apps/addiyon/src/main/java/com/addiyon/keyboard/ui/manual/ManualScreen.kt`
2. `keyboard/ui/.../AddiyonDesignTokens.kt` contains shared symbols named
   `aiToneIcons`, which violate the Addiyon/no-AI boundary contract.

Do not solve those issues as part of this morphology plan unless the user
separately asks for it. Continue to run `checkKeyboardProducts` and report the
distinction between morphology regressions and these known failures.

## 3. Completed foundation

### Phase 1 — complete: HornMorpho lexeme baseline and compact SQLite

The discarded surface dictionary is no longer a build input. The checked-in
HornMorpho snapshot is transformed deterministically into:

- `amharic_lexemes.dat`: 18,867 lexical records
- `amharic_words.dat`: 18,251 displayable normalized base lemmas
- `amharic_ngrams.dat`: originally empty at this milestone; Phase 6 now supplies
  the clean morphology-gated baseline

Lexeme kinds currently mean:

| Kind | Source | Captured count | Current runtime use |
|---:|---|---:|---|
| 0 | `n_stem.lex` | 11,748 | Nouns/adjectives and other nominal records |
| 1 | `n_stem_an.lex` | 395 | Alternate `-an` nominal class |
| 2 | `n_name.lex` | 2,584 | Person names |
| 3 | `n_place.lex` | 2,308 | Place names |
| 4 | `vroot.lex` | 1,832 | Preserved for future verb work; not indexed by surface |

The generated SQLite schema is version 8. `morph_lexemes` now has a normalized
nullable `key`, and `idx_morph_lexemes_key` indexes the 17,035 displayable
kind 0–3 records. Verb roots intentionally have `key IS NULL` because their
internal root notation is not a surface word.

The generated Amharic database measured 2,019,328 bytes at handoff and remains
under the current 2,000 KiB test threshold.

### Phase 2 — complete as an MVP: runtime noun morphology

The new pure-Kotlin `AmharicNounMorphology` layer and
`SQLiteMorphLexicon` currently provide:

- indexed exact-stem recovery from an inflected typed form;
- indexed base-lexeme prefix completion;
- prefixes including `የ`, `በ`, `ለ`, `ከ`, `እንደ`, `እስከ`, `በስተ`, `ስለ`,
  `ወደ`, `ያለ`, and compound distributive prefixes;
- accusative `-ን`;
- ordinary `-ዎች/-ኦች`-style plural realization;
- alternate `-አን` noun-class realization;
- masculine/feminine definite forms;
- common possessive forms;
- common prefix + number + determiner/possessive + accusative combinations;
- filtering through HornMorpho features including `-pl`, `-def`, `-gen`,
  `-dis`, `p=0`, and `adp=0`;
- conservative handling of person/place names;
- discounted ranking for generated forms;
- generated exact forms can appear as suggestions even when absent from the
  `words` table;
- contaminated structural transliteration readings remain hidden unless they
  are dictionary-backed or form a valid generated construct.

Examples already covered by unit tests include:

- `ሰው` -> `የሰው`, `ሰውን`, `የሰውን`, `ለሰው`
- `ቤት` -> `ቤቶች`, `ቤቱ`, `ቤቶቹን`
- `መምህር` kind 1 -> `መምህራን`
- `ቦታ` -> `ቦታዎች`
- `ሌ` and split bare-vowel readings are hidden unless lexically validated

This is deliberately not full HornMorpho parity. It is a compact productive
noun subset suitable for shipping only after Phase 3 hardening.

## 4. Current data and runtime contracts

### 4.1 SQLite contract

Current relevant schema:

```sql
CREATE TABLE words (... key TEXT PRIMARY KEY, display TEXT, freq INTEGER, ...);

CREATE TABLE morph_lexemes (
    kind     INTEGER NOT NULL,
    form     TEXT NOT NULL,
    features TEXT NOT NULL,
    key      TEXT,
    PRIMARY KEY(kind, form, features)
) WITHOUT ROWID;

CREATE INDEX idx_morph_lexemes_key
ON morph_lexemes(key)
WHERE key IS NOT NULL;
```

Do not add expanded generated words to `words`. Schema changes require:

- incrementing `DictionaryDatabaseFormat.SCHEMA_VERSION`;
- updating generator SQL;
- updating store schema validation;
- updating `DictionaryDbContractTest`;
- checking the database size budget;
- rebuilding manifests and both APKs.

### 4.2 Ranking contract

The current ranking tiers are:

1. exact dictionary reading;
2. literal greedy transliteration;
3. dictionary and generated completions;
4. fuzzy candidates.

Generated morphology currently enters as `CandidateRanker.DictionaryWord`
with stem frequency divided by eight. This is a temporary compatibility
mechanism. A stem's frequency is not the frequency of every inflected form.
Phase 5 replaces this implicit encoding with explicit candidate provenance.

### 4.3 Normalization contract

Every database key and comparison must use the same Ethiopic normalization as
the runtime:

- build-time: `build-logic/.../EthiopicNormalizer.kt`
- runtime: `language/amharic/.../EthiopicNormalizer.kt`

Do not normalize the displayed word destructively. Store or generate a display
form, use the folded key for lookup/deduplication, and preserve HornMorpho's
raw slash/gemination notation in the source record for reproducibility.

### 4.4 Runtime constraints

- No Python process, network request, JNI FST compiler, or full in-memory word
  trie in the IME.
- SQLite queries must remain indexed range/equality queries.
- Morphology runs off the IME's suggestion worker, never Compose rendering.
- A result must be valid because of a lexeme analysis, not just because it can
  be split into plausible-looking affixes.
- Morphological candidates must not change the composing buffer until the user
  taps a suggestion or existing commit policy commits it.

## 5. Remaining roadmap

| Phase | Objective | Primary output | Safe stopping point |
|---:|---|---|---|
| 3 — complete | Harden and prove the current noun MVP | Oracle corpus, typed features, integration tests | Ship conservative noun generation |
| 4 — complete | Reach practical HornMorpho noun/adjective parity | Compact symmetric nominal analyzer/generator | Robust nominal suggestions |
| 5 — complete | Make ranking provenance-aware | Explicit source tiers and sparse surface statistics | Correct ranking without wordlist bloat |
| 6 — complete | Restore clean Amharic next-word prediction | Morphology-gated corpus and n-grams | Useful predictions with no garbage leakage |
| 7 — complete | Decide the verb artifact architecture | Benchmarked ADR/prototype | No verb implementation until decision passes |
| 8 — complete | Add high-value verb generation and validation | Compact verb artifact and runtime lookup | Common verbs without paradigm explosion |
| 9 — implementation complete; release review pending | Add morphology-aware learning/correction and production gates | Personal weighting, fuzz validation, QA/perf suite | Technical gates complete; external linguistic/GPL sign-off pending |

Phases should be delivered sequentially. Phase 6 may be developed alongside
Phase 5 after the validity API is stable. Do not begin Phase 8 before Phase 7
has a measured architecture decision.

## 6. Phase 3 — complete: harden and prove the noun MVP

### Goal

Convert the current useful heuristic MVP into a well-specified, regression-
tested subset whose accepted forms can be explained by pinned HornMorpho
behavior.

### 3.1 First preserve the current implementation

1. Inspect every expected morphology file listed in section 2.2.
2. Confirm no unrelated edits overlap those hunks.
3. Run the focused tests before further refactoring.
4. If the user asks for a commit, stage only the explicit morphology paths.
5. Record the resulting commit hash in this document's implementation log.

Baseline commands:

```sh
/Users/dev/code/addiyon-keyboard/gradlew \
  :language:amharic:testDebugUnitTest \
  :suggestions:core:test \
  :suggestions:sqlite:testDebugUnitTest

/Users/dev/code/addiyon-keyboard/gradlew \
  :apps:textrevamp:testDebugUnitTest \
  --tests "com.addiyon.keyboard.suggestion.DictionaryDbContractTest" \
  --tests "com.addiyon.keyboard.suggestion.DictionaryDbSizeBudgetTest"
```

### 3.2 Vendor the relevant HornMorpho control sources

The current repository vendors lexicons but not the nominal morphotactics and
boundary rules used to validate the Kotlin port. Add the following files from
the exact pinned upstream commit under an appropriate documented directory,
for example `language/amharic/hornmorpho/fst/`:

- `src/hm/languages/a/fst/n.mtx`
- `src/hm/languages/a/fst/n_aff_bound.fst`
- `src/hm/languages/a/fst/nM.mtx`
- `src/hm/languages/a/fst/n_aff_boundM.fst`
- any directly referenced nominal phonology rules needed by the oracle
- the language definition/unification files needed to reproduce generation,
  if the oracle cannot run without them

Do not download `main`. Always fetch commit
`7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`. Update `UPSTREAM.md` with the
exact file list and transformation process.

### 3.3 Build a HornMorpho oracle, not a runtime dependency

Create a reproducible developer/build-time oracle under `tools/`, preferably:

```text
tools/hornmorpho/
    README.md
    requirements.lock or equivalent pinned environment
    generate_nominal_oracle.py
    compare_nominal_runtime.py
```

The oracle must:

- use the pinned HornMorpho version;
- analyze a supplied surface and return all analyses;
- generate surfaces for a supplied lemma plus features;
- produce deterministic UTF-8 JSONL or TSV;
- normalize only for comparison while retaining original surface and analysis;
- never run as part of a normal Android keystroke or app startup;
- fail clearly if the pinned toolchain cannot be reproduced.

If current HornMorpho cannot be installed on the host Python version, use a
pinned container or isolated virtual environment. Do not silently substitute a
new HornMorpho release.

### 3.4 Replace raw feature substring checks with a typed parser

`AmharicNounMorphology` currently parses some features with regular expressions
and exact token matching. Introduce a pure-Kotlin feature representation:

```kotlin
data class NominalFeatures(
    val partsOfSpeech: Set<PartOfSpeech>,
    val gender: Gender?,
    val stemClass: StemClass,
    val human: FeatureState,
    val plural: FeatureState,
    val definite: FeatureState,
    val person: PersonFeature,
    val allowsAdposition: Boolean,
    val allowsGenitive: Boolean,
    val allowsDistributive: Boolean,
    val allowsCollective: Boolean,
)
```

Requirements:

- distinguish absent, positive, negative, and enumerated features;
- parse alternatives such as `pos=N|ADJ`;
- preserve unknown features for diagnostics;
- reject malformed records at dictionary generation time where possible;
- never confuse `p=0` with another token containing the same substring;
- write table-driven tests using real lines from every nominal lexicon source.

Initially the typed representation may be built from the existing `features`
text at query time. Before Phase 4 is complete, move frequently used fields to
compact build-time columns or a bitmask so regular expressions are not run for
every candidate on every keystroke.

Likely schema-9 additions:

```sql
ALTER TABLE morph_lexemes ADD COLUMN morph_bits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE morph_lexemes ADD COLUMN stem_class INTEGER NOT NULL DEFAULT 0;
```

Retain raw `features` for provenance and debugging.

### 3.5 Create a checked-in nominal golden corpus

Add a small, human-reviewable fixture such as:

```text
language/amharic/src/test/resources/hornmorpho_nominal_golden.jsonl
```

Each record should include:

- source lexeme kind and raw HornMorpho form;
- cleaned display lemma;
- source features;
- requested analysis/generation features;
- generated surface;
- normalized comparison key;
- whether Addiyon should suggest it;
- a reason for deliberately excluded forms.

Coverage must include at least:

- consonant-final and vowel-final nouns;
- ordinary `ps=oc` and alternate `ps=an` stems;
- masculine and feminine nouns;
- human and non-human stems;
- names and places;
- `-pl`, `-def`, `p=0`, `adp=0`, `-gen`, `-dis`, and `-col` restrictions;
- prefix + suffix combinations;
- possessive persons/numbers/formality;
- plural + definite/possessive + accusative;
- HornMorpho slash notation and gemination marks;
- homoglyph-normalized lookup with original display retained;
- ambiguous surfaces with multiple analyses;
- invalid near-misses that must not appear.

Seed cases should include the existing examples, but the oracle decides the
expected analysis. Do not encode an intuition as a golden expectation without
checking it against HornMorpho and, for user-facing high-frequency examples,
an Amharic speaker.

### 3.6 Add end-to-end suggestion-engine tests

The pure morphology tests do not prove the Android engine queries SQLite and
ranks the result correctly. Add a test seam or test implementation allowing a
real generated test database to exercise:

```text
Latin raw input
  -> transliteration readings
  -> SQLiteMorphLexicon
  -> morphology
  -> CandidateRanker
  -> final chip strings
```

Required end-to-end cases:

- an exact base lexeme beats a generated completion;
- a generated `ሰውን` appears even though `words` contains no `ሰውን`;
- `የሰውን` is recovered from base lexeme `ሰው`;
- `le` does not show `ሌ` unless it is a real exact lexeme;
- split-vowel suggestions are absent for nonsense and present for a real word
  such as `ርዕስ`;
- a generated form from a non-greedy reading is shown only when its underlying
  lexeme and features validate it;
- duplicate analyses do not create duplicate chips;
- an unavailable/loading database returns the safe literal behavior.

Prefer JVM/Robolectric or a narrow Android-library test over constructing the
entire keyboard service.

### 3.7 Remove or quarantine obsolete blind prefix synthesis

`AmharicPrefixCompletion.kt` remains in the source tree but is no longer used
by `AmharicSuggestionEngine`. Its logic can produce prefixed strings without
checking lexeme features.

After the new integration tests pass:

- delete `AmharicPrefixCompletion.kt` and its tests, or
- explicitly move it to test/reference-only code if it remains useful for
  comparison.

It must not be reintroduced into the production fallback path.

### Phase 3 acceptance criteria

- The nominal feature parser has exhaustive unit tests.
- At least 200 oracle-derived positive forms and 100 negative near-misses are
  checked in across at least 40 lexemes.
- Kotlin MVP results match the declared supported subset of the oracle corpus.
- Final engine integration tests cover SQLite and ranking.
- No generated surface rows are added to `words`.
- Dictionary regeneration is deterministic across two runs.
- Amharic DB remains below 2,500 KiB.
- Focused tests and both debug APK assemblies pass.
- A connected-device smoke test, when possible, confirms visible suggestions
  for `ሰው`, `ሰውን`, `የሰውን`, `ቤቶች`, `ቤቱ`, and the contaminated-alternate
  negatives.

## 7. Phase 4 — complete: practical HornMorpho nominal parity

### Goal

Implement the remainder of HornMorpho's useful noun/adjective morphotactics
without expanding all surfaces into a word table. Generation and analysis must
share the same rules so a candidate cannot be generated but then rejected by a
different reverse heuristic.

### 4.1 Inventory the exact upstream nominal pipeline

Use pinned `n.mtx` and `n_aff_bound.fst` to document, in order:

1. pre-stem adposition/genitive selection;
2. distributive and collective marking;
3. stem class and lexical restrictions;
4. ordinary and `-an` plural realization;
5. human suffix behavior;
6. definite article and possessive selection;
7. accusative;
8. copula attachment where appropriate;
9. conjunctive suffixes;
10. postpositions such as `ጋ`;
11. boundary-conditioned fidel changes;
12. alternate orthographic realizations accepted by HornMorpho.

Record the supported subset in code-neutral documentation before modifying
the generator. Every excluded upstream branch needs a reason: low value,
ambiguous, unsupported by the lexicon snapshot, or deferred.

### 4.2 Replace ad hoc strings with structured analyses

Introduce types similar to:

```kotlin
data class MorphAnalysis(
    val lexemeId: Long,
    val lemma: String,
    val surface: String,
    val partOfSpeech: PartOfSpeech,
    val features: NominalFeatures,
    val affixes: List<Affix>,
    val orthographicCost: Int,
)

data class MorphCandidate(
    val word: String,
    val normalizedKey: String,
    val analysis: MorphAnalysis,
    val source: MorphSource,
    val lexicalFrequency: Int,
    val surfaceFrequency: Int?,
)
```

The production engine does not have to expose all fields outside the language
module, but tests and diagnostics need enough information to explain why a
form is valid.

### 4.3 Make generation and analysis symmetric

The current reverse lookup removes likely suffixes to guess a base stem up to
a fixed depth of three. That is useful but incomplete.

Replace it with a compact rule graph or shared transitions:

- forward generation walks stem -> number -> human -> poss/def -> accusative
  -> later suffix states;
- reverse analysis walks the same transitions backward;
- every transition records its feature constraints and surface rewrite;
- partial-prefix completion can stop at intermediate surface states;
- deduplication happens by normalized surface plus analysis identity;
- ambiguity is retained internally and collapsed only for display ranking.

Do not recursively enumerate a lexeme's entire paradigm on each keystroke.
Search only paths compatible with the typed prefix and stop when the chip limit
plus a small ranking buffer is satisfied.

### 4.4 Complete nominal morphophonemics

Implement oracle-verified transformations currently missing or simplified:

- initial-vowel changes at prefix boundaries;
- all plural boundary variants in `n_aff_bound.fst`;
- human suffix variants and their possessive interaction;
- full first/second/third person possessive allomorphy;
- formal second-person variants;
- feminine article variants including lexically allowed `-ኢቱ` behavior;
- collective/distributive forms;
- conjunctive and postpositional suffix ordering;
- relevant copular nominal forms;
- accepted alternate spellings with a higher orthographic cost than the
  canonical spelling.

Keep canonical spelling first. An alternative accepted by the analyzer should
not automatically outrank the canonical generated form.

### 4.5 Correct proper-name and place behavior

The MVP conservatively limits kinds 2 and 3 to base + accusative. HornMorpho
allows more nuanced proper-noun behavior, including plural/collective contexts.

Use the oracle to define:

- which prefixes are productive on person and place names;
- when plural or collective marking is valid;
- possessive restrictions;
- capitalization-equivalent concerns do not apply to Ethiopic, but display
  variants and normalized homographs still do;
- how multiple identical name/place keys should be ranked and deduplicated.

### 4.6 Optimize SQLite lookup for the rule graph

The current exact-key OR prefix-range query is a sound baseline. Improve only
after measuring:

- materialize a stable numeric `lexeme_id` if analyses need compact identity;
- materialize feature bits used by every query;
- consider an index on `(key, kind, morph_bits)` only if the query planner and
  benchmark show benefit;
- keep verb roots out of nominal prefix scans;
- inspect `EXPLAIN QUERY PLAN` in a contract test;
- never use `%prefix%`, full scans, or per-keystroke table creation.

The one-character-prefix behavior currently samples at most 48 high-frequency
lexemes. Test whether that hides valid low-frequency completions before the
user types enough of the stem. If it does, use incremental paged/bounded search
or prefix-top metadata rather than dramatically increasing the scan limit.

### 4.7 Affected files

Expected modifications/additions:

- `language/amharic/src/main/java/com/addiyon/keyboard/suggestion/AmharicNounMorphology.kt`
- new nominal feature/rule classes in the same pure-Kotlin package
- `language/amharic/src/main/java/com/addiyon/keyboard/language/amharic/AmharicSuggestionEngine.kt`
- `suggestions/sqlite/.../SQLiteMorphLexicon.kt`
- dictionary generator/schema files under `build-logic`
- HornMorpho oracle and dictionary scripts under `tools`
- nominal golden resources and tests under `language/amharic/src/test`
- SQLite contract tests under `apps/textrevamp/src/test`
- `UPSTREAM.md` and `tools/README.md`

Do not put Amharic rules into `keyboard/runtime` or either app module.

### Phase 4 acceptance criteria

- At least 95% of the declared nominal oracle corpus matches exactly.
- All mismatches are checked in as explicit exclusions with reasons.
- The negative corpus has zero false-positive suggestions.
- Forward generation and reverse analysis round-trip for every supported test
  analysis.
- No per-keystroke full paradigm expansion or SQLite full scan occurs.
- Warm nominal lookup p95 is at most 15 ms on the `TanaLowRam` AVD.
- End-to-end warm suggestion publication p95 is at most 75 ms for the nominal
  stress corpus and never exceeds the existing 200 ms user-visible ceiling.
- Amharic DB remains below 3 MiB unless a measured, reviewed tradeoff changes
  the budget.

## 8. Phase 5 — complete: explicit candidate provenance and ranking

### Goal

Replace frequency discount hacks with a ranking model that distinguishes
lexical truth, morphological validity, corpus evidence, personal evidence, and
fuzzy similarity.

### 5.1 Define source types

Refactor the completion candidate passed to `CandidateRanker` to carry an
explicit source, for example:

```kotlin
enum class CandidateSource {
    EXACT_LEXEME,
    ATTESTED_SURFACE,
    GENERATED_MORPHOLOGY,
    PERSONAL,
    FUZZY,
}
```

Do not encode the source by dividing frequency. Preserve separate values:

- lexical/root frequency;
- surface frequency, if available;
- context/ngram weight;
- personal count and recency;
- morphology/orthography cost;
- transliteration structural index;
- fuzzy edit distance.

### 5.2 Lock ranking invariants

Add tests proving these invariants:

- a real exact dictionary reading beats every completion tier;
- the greedy literal remains tap-committable;
- an attested valid inflected surface may beat an unattested generated surface
  of the same stem;
- an unattested generated form can appear but does not inherit the full stem
  frequency;
- personal evidence may reorder valid forms but may not validate an otherwise
  invalid structural alternate globally;
- fuzzy candidates never cross the valid completion tier;
- context reorders within allowed bounds and cannot promote an invalid form;
- canonical spelling wins ties over an accepted orthographic variant;
- source and structural ties are deterministic.

### 5.3 Add a sparse surface-statistics table

A corpus may contain useful counts for `የሰው` and `ሰውን`. Store those counts
only as ranking metadata after morphology validates the surface.

Recommended schema:

```sql
CREATE TABLE morph_surface_stats (
    key       TEXT PRIMARY KEY,
    display   TEXT NOT NULL,
    frequency INTEGER NOT NULL,
    lexeme_id INTEGER,
    analysis  INTEGER
) WITHOUT ROWID;
```

Rules:

- this table is not a dictionary and cannot establish validity;
- every row must have passed the pinned analyzer during the build;
- aggressively prune low-frequency rows;
- cap it by measured value/size, not by taking an old wordlist wholesale;
- do not duplicate base entries already represented adequately in `words`;
- prefer compact analysis/lexeme identifiers over raw feature strings;
- schema tests must prove that known garbage and legacy-only forms are absent.

An alternative is a keyed frequency table without display/analysis if the
runtime can recover both cheaply. Benchmark both before choosing.

### 5.4 Update caches and diagnostics

- Include schema/model version in suggestion cache invalidation.
- Cache parsed lexeme features or materialize them at build time.
- Extend `SuggestionTrace` around morphology query, generation, and ranking.
- Add debug-only explanations for candidate source and analysis; do not expose
  developer strings in production UI.

### Phase 5 acceptance criteria

- Candidate provenance is explicit in code and tests.
- No ranking path treats stem frequency as surface frequency.
- Sparse surface statistics contain only analyzer-valid surfaces.
- Ranking is deterministic across repeated runs.
- Existing English ranking behavior remains unchanged.
- The additional database data has an explicit size cap and keeps total
  Amharic DB under the agreed budget.

## 9. Phase 6 — complete: clean morphology-gated Amharic prediction

### Goal

Replace the Phase 1 empty Amharic n-gram asset with a useful model that
cannot leak the discarded dictionary's garbage back into suggestions.

### 6.1 Select and document a corpus

Corpus selection is a user/product decision. Requirements:

- clear redistribution/build provenance;
- contemporary standard Amharic coverage;
- no private user text;
- known encoding and cleanup procedure;
- enough conversational text to be useful for a keyboard;
- raw corpus stays outside the repository unless its license explicitly
  permits inclusion.

Record corpus name, version/date, license, checksum, and acquisition command in
`tools/README.md` or a dedicated provenance document.

### 6.2 Morphology-aware token gate

Upgrade `tools/build_ngrams.py` so an Amharic token is accepted when either:

1. it is an exact normalized base lexeme; or
2. the pinned morphology oracle returns at least one permitted analysis.

Unknown tokens must break adjacency. They must not create n-grams across the
gap. Preserve current punctuation and mixed-token boundary behavior.

For every accepted token, retain:

- canonical display surface;
- normalized lookup key;
- preferred analysis/lexeme when unambiguous;
- surface frequency for Phase 5;
- ambiguity count for diagnostics.

Do not reduce every inflected token to its lemma for display prediction. A
language model should be able to predict a valid surface form, while lemma and
analysis provide backoff/ranking metadata.

### 6.3 Build sparse n-grams

Start with conservative pruning:

- retain the existing bounded top-successor structure;
- generate surface bigrams first;
- add trigrams only after bigrams pass quality review;
- prune candidates not supported by the runtime morphology subset;
- ensure every successor can be displayed by the runtime;
- keep model generation byte-deterministic (`mtime=0`, stable sorting);
- measure value and size on a held-out corpus before loosening thresholds.

### 6.4 Prediction quality fixture

Create a checked-in mini corpus and assertions for:

- common noun/adjective sequences;
- inflected predicted surfaces;
- punctuation boundaries;
- unknown-token boundaries;
- homoglyph folding without display destruction;
- no prediction of `የነው`-style invalid constructions;
- no legacy garbage resurrection;
- stable bigram/trigram backoff.

Have a fluent Amharic reviewer inspect the top 500 contexts and the top 10
successors for a representative sample before enabling the model by default.

### Phase 6 acceptance criteria

- Every n-gram vocabulary item is exact-lexeme or analyzer-valid.
- Legacy garbage sentinel tests remain absent.
- Model and surface-stat generation are deterministic.
- Held-out top-3 prediction quality improves over the empty baseline and is
  documented; choose a metric before tuning.
- Combined database stays within the agreed size budget.
- Prediction queries stay indexed and within existing latency limits.

## 10. Phase 7 — complete: verb architecture spike and decision

### Goal

Choose a compact, reproducible way to use the 1,832 HornMorpho verb roots.
Amharic verb morphology is root-and-pattern morphology with many agreement,
tense/aspect/mood, polarity, derivation, and object-suffix combinations. It
must not be implemented by extending the noun suffix code.

### 7.1 Vendor and study the exact verb sources

At minimum evaluate these pinned upstream files and all of their direct
dependencies:

- `fst/v.mtx`
- `fst/v0.mtx`
- `fst/vM.mtx`
- `fst/vMG.mtx`
- `fst/v.root`
- `fst/v_irr.root`
- `fst/v_light.root`
- `fst/v_light_irr.root`
- `fst/v_light_aff_bound.fst`
- `fst/aff_bound.fst`
- `fst/misc_v.fst`
- `lex/vroot.lex`
- language alternation/unification definitions used to compile them

Update HornMorpho provenance and licensing for every vendored source.

### 7.2 Prototype three approaches

#### Option A — preferred candidate: compact compiled runtime automaton

Use HornMorpho at build time to compile a deterministic/minimized automaton or
equivalent compact transition artifact supporting:

- surface-prefix traversal for completions;
- exact surface analysis;
- terminal analysis/lexeme identifiers;
- memory-mapped or SQLite-backed access without loading all states eagerly.

Do not assume HornMorpho's serialized Python object is appropriate for
Android. Define a simple versioned binary or relational format that Kotlin can
read safely.

#### Option B — pruned generated surface index

Use HornMorpho offline to generate only a product-approved, corpus-ranked
subset of paradigms, then minimize shared prefixes in a DAFSA/FST.

This is acceptable only if:

- lexical validity still derives from HornMorpho;
- the artifact is compact because prefixes/transitions are shared, not a
  return to a flat 200k word table;
- coverage and excluded feature combinations are explicit;
- common irregular verbs are covered.

#### Option C — rule interpreter in Kotlin

Port the compiled HornMorpho transition graph, not informal grammar rules. A
manual Kotlin verb generator is rejected unless the spike proves that the
ported representation is mechanically derived and oracle-equivalent.

### 7.3 Benchmark the prototypes

For the same root and feature subset, measure:

- artifact size compressed and installed;
- number of states/transitions/terminal analyses;
- cold-open time;
- exact-analysis p50/p95;
- prefix-completion p50/p95;
- peak Java/native heap;
- generated coverage against HornMorpho;
- update reproducibility;
- complexity of schema/version migration;
- license/source-distribution implications.

Target budgets for the spike, subject to measured revision:

- incremental compressed artifact under 3 MiB;
- no eager heap structure over 5 MiB;
- warm exact lookup p95 under 10 ms;
- warm prefix completion p95 under 25 ms;
- deterministic byte-identical rebuild.

### 7.4 Write an ADR

Add `docs/adr/amharic-verb-morphology-artifact.md` containing:

- the three evaluated options;
- benchmark table;
- selected representation;
- binary/schema versioning strategy;
- failure and fallback behavior;
- license/provenance obligations;
- rejected alternatives and why.

Do not proceed to Phase 8 without this reviewed decision.

### Phase 7 acceptance criteria

- The pinned HornMorpho verb oracle runs reproducibly.
- At least 100 roots spanning regular, irregular, and light-verb classes are
  in the spike corpus.
- Prototype results and raw benchmark commands are checked in.
- The selected artifact meets the agreed size/latency/heap limits.
- The ADR is reviewed before runtime integration.

## 11. Phase 8 — high-value verb morphology

### Goal

Integrate the selected compact artifact and provide high-value valid verb
suggestions without attempting every theoretical paradigm on every keystroke.

### 8.1 Define the first supported feature slice

Use corpus frequency and product usage, then have an Amharic expert approve the
slice. A likely order is:

1. common perfective and imperfective forms;
2. jussive/imperative forms;
3. negation;
4. subject agreement persons/numbers/genders;
5. common derivational voice/stem classes;
6. object suffixes;
7. relative/subordinate and conjunction combinations.

This ordering is a proposal, not a linguistic assertion. Let oracle coverage
and user value set the final boundaries.

### 8.2 Preserve root records and add compact identity

Kind-4 records currently preserve raw root notation/features and have no
surface key. Build-time compilation should assign stable root/analysis IDs
without converting root notation into a fake display word.

Possible schema/artifact metadata:

```text
root_id
raw_root
lemma/display citation form
root class
transitivity/derivation constraints
HornMorpho feature bundle
root frequency
```

If a display citation form cannot be generated unambiguously, do not invent
one. Keep it internal until the oracle provides the canonical form.

### 8.3 Runtime integration

Add a language-owned verb lexicon interface parallel to the nominal one:

- exact surface validation;
- prefix completion;
- compact analysis retrieval;
- source and morphology cost for Phase 5 ranking;
- bounded results and cancellation/coalescing through the existing suggestion
  worker;
- safe empty fallback on artifact/schema failure.

Merge noun and verb candidates before `CandidateRanker`, deduplicating by
normalized display surface while retaining the best analysis and source
evidence.

### 8.4 Verb tests

Create:

- oracle golden generation tests;
- exact surface-analysis tests;
- partial-prefix completion tests;
- negative near-miss tests;
- irregular/light-verb tests;
- ambiguity tests where a surface has nominal and verbal analyses;
- transliteration alternate tests so split vowels appear only for a valid
  verbal surface;
- artifact corruption/version mismatch fallback tests;
- performance tests on short one-character prefixes.

Do not make up a list of expected conjugations. Generate the corpus from the
pinned oracle and review high-frequency user-facing examples.

### Phase 8 acceptance criteria

- Supported verb features match the oracle at the Phase 7 target rate.
- Negative corpus has zero false-positive valid suggestions.
- Common irregular/light verbs are explicitly covered.
- Noun suggestion latency does not regress materially when the verb artifact
  is enabled.
- Total Amharic morphology assets stay inside the ADR budget.
- Failure to open the verb artifact leaves noun/base suggestions operational.

Implementation status (2026-08-13): the version-2 production artifact and
pure-Kotlin fail-closed reader are implemented. The checked-in corpus contains
100 regular, 12 irregular, and 64 light roots across 7,666 terminal surfaces
and 7,931 analyses. Exact, prefix, negative, irregular/light, normalized
ambiguity, split-vowel validation, corruption, and latency gates are covered.
Fluent-speaker review remains a Phase 9 release gate, not an automated claim.

## 12. Phase 9 — adaptive learning, morphology-aware correction, and release

### Goal

Use the analyzer to improve personalization and correction while locking down
performance, licensing, reproducibility, and real-device quality.

### 9.1 Morphology-aware personal learning

When a user commits an analyzer-valid inflected surface:

- retain the exact surface for personal completion;
- optionally retain its lexeme/analysis ID for lemma-family backoff;
- keep counts and recency on-device only;
- do not write generated forms into the global SQLite asset;
- do not learn from password/private/no-suggestion fields;
- keep existing personal dictionary encoding migration-safe.

A learned invalid literal may remain available as an explicitly personal item,
but it must not globally unlock the same transliteration segmentation for all
users or all stems.

### 9.2 Morphology-aware fuzzy correction

Current fuzzy lookup searches stored dictionary surfaces. Extend correction by
validating proposed variants through the analyzer:

- fuzzy search proposes a bounded set of likely stems/surfaces;
- morphology confirms or generates the target;
- edit distance never establishes lexical validity;
- low-RAM policy remains respected;
- valid completion tiers remain above fuzzy tiers;
- correction must not autocorrect silently without an existing product policy.

### 9.3 Real-device performance suite

Use the existing `TanaLowRam` plan and add a morphology typing corpus.

Measure at minimum:

- cold language-pack open;
- first Amharic keystroke;
- warm per-keystroke p50/p95/p99;
- one-character high-fanout prefixes;
- deep inflected forms requiring reverse analysis;
- rapid typing and deletion;
- language toggles;
- 10-minute sustained typing heap/PSS;
- database page-cache behavior;
- no suggestion-strip flicker or stale-result publication.

Suggested production targets:

- warm suggestions p95 <= 75 ms on `TanaLowRam`;
- worst normal warm query <= 200 ms;
- no unbounded query or allocation growth;
- Amharic database/artifacts within the budgets agreed in Phases 4 and 7;
- no OOM or ANR in the sustained test.

### 9.4 Linguistic quality review

Before release, produce review sheets grouped by source:

- exact base lexemes;
- generated nouns/adjectives;
- generated verbs;
- surface-stat-ranked forms;
- n-gram predictions;
- fuzzy corrections;
- accepted orthographic alternatives.

Have at least one fluent Amharic reviewer mark valid/invalid/unnatural and
canonical/noncanonical. Feed corrections back into lexeme exceptions or rule
constraints, never into an untracked hard-coded blacklist without a source and
test.

### 9.5 Licensing and reproducibility gate

HornMorpho is GPLv3 in the vendored snapshot. Before distributing expanded
derived artifacts:

- keep `language/amharic/hornmorpho/LICENSE.txt`;
- keep the packaged `hornmorpho_LICENSE.txt` asset;
- document every upstream file, version, commit, and transformation;
- publish or retain the scripts needed to reproduce shipped derived data;
- verify source-offer/distribution obligations with the project's release
  owner; this plan is not legal advice;
- run a clean offline rebuild from only documented inputs.

### 9.6 Final release gates

Run:

```sh
/Users/dev/code/addiyon-keyboard/gradlew \
  :language:amharic:testDebugUnitTest \
  :suggestions:core:test \
  :suggestions:sqlite:testDebugUnitTest

/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest

/Users/dev/code/addiyon-keyboard/gradlew checkKeyboardProducts

/Users/dev/code/addiyon-keyboard/gradlew \
  :apps:addiyon:assembleDebug \
  :apps:textrevamp:assembleDebug

/Users/dev/code/addiyon-keyboard/gradlew :apps:addiyon:installDebug
```

If no emulator/device is connected, report installation as not executed; do
not claim it passed.

Also verify:

- clean checkout regeneration produces byte-identical dictionary/morphology
  assets;
- manifest length and SHA-256 match packaged assets;
- database `application_id`, `user_version`, tables, indexes, and row counts;
- `EXPLAIN QUERY PLAN` uses the intended indexes;
- APK contains the HornMorpho license;
- old surface dictionary files are absent from APK and build inputs;
- release build/R8 does not remove artifact readers or serializers;
- failure/corruption paths fall back safely.

Implementation status (2026-08-13): morphology-aware local learning and
version-3 migration, analyzer-backed weighted fuzzy correction, low-RAM and
private/no-learning policy, connected publication/stability benchmarks,
opt-in 10-minute heap/PSS soak, grouped review sheet, packaging boundaries,
and deterministic regeneration checks are implemented. Final release remains
gated on a completed fluent-Amharic review sheet and release-owner GPL
distribution/source-offer approval. The Android 15 `TanaLowRam` warm
publication test passed at 69.258334 ms p95 (82.454167 ms max; 20.349375 ms
for a one-character prefix). The isolated 600,000 ms soak passed 1,606 typing
iterations; post-GC heap changed from 7,457,856 to 7,461,984 bytes and PSS
changed from 205,794,304 to 204,076,032 bytes, with bounded sampled peaks of
21,937,280 and 219,596,800 bytes respectively.

## 13. Cross-phase test matrix

The following is a minimum matrix. Oracle-derived cases should expand it.

| Area | Positive examples | Negative/ordering requirement |
|---|---|---|
| Base lexeme | `ሰው`, `ቤት`, `ቦታ` | Exact base outranks generation |
| Genitive | `የሰው`, `የሰውን` | Respect `-gen` and `adp=0` |
| Prepositions | `ለሰው`, `ከቤት`, `በቤት` | No blind prefix + arbitrary word |
| Ordinary plural | `ሰዎች`, `ቤቶች`, `ቦታዎች` | Respect `-pl` |
| Alternate plural | `መምህራን` | Only correct stem class |
| Definite | `ቤቱ`, feminine/article oracle cases | Respect `-def`; canonical first |
| Possessive | `ቤቴ`, `ቤትህ`, `ቤታችን` | Respect `p=0` |
| Combined | `የቤቶቹን` | Enforce HornMorpho suffix order |
| Names/places | Oracle-approved prefix/accusative | No indiscriminate plural/possessive |
| Human/collective | Oracle-derived Phase 4 cases | Respect `+/-h`, `+/-col`, `+/-dis` |
| Orthography | canonical + accepted variants | Variant never wins an unsupported tie |
| Transliteration | `le -> ለ`; `rEs -> ርዕስ` when valid | Hide `ሌ` and split vowels unless valid |
| Loading/failure | safe literal/base behavior | No crash, stale chip, or garbage fallback |
| Personal | learned valid surface rises locally | Personal item does not alter global validity |
| Prediction | analyzer-valid surface successors | Unknown/invalid token breaks adjacency |
| Verb | oracle corpus from Phase 8 | No noun-rule reuse or invented paradigm |

## 14. Database and artifact budgets

Budgets should be changed only with before/after measurements and an updated
size test.

| Milestone | Current/proposed budget |
|---|---:|
| Phase 2 current Amharic DB | 2,019,328 bytes observed |
| Phase 3 hardened nouns | < 2,500 KiB |
| Phase 4 full practical nouns | < 3 MiB |
| Phase 5 sparse surface stats | 2,477-byte asset; 16,384-byte DB delta observed; 2,048-row cap |
| Phase 6 n-grams | 454-byte asset; schema 11 DB is 2,506,752 bytes, 32,768 bytes smaller than Phase 5 |
| Phase 7 verb spike | 12,006-byte selected prototype; target < 3 MiB compressed delta retained for Phase 8 |
| Runtime eager morphology heap | Target < 5 MiB incremental |

Do not optimize only for APK size. Record database/artifact size, compressed
APK delta, page-cache/native heap, Java heap, and latency together.

## 15. Risks and mitigations

### Risk: overgeneration looks plausible

Amharic affixes are productive enough that a bad generator can create strings
that look convincing. Require a base lexeme, typed feature constraints, a legal
rule path, and negative oracle tests.

### Risk: a full surface expansion recreates the original problem

Do not insert generated paradigms into `words`. Prefer shared rule/automaton
states. Sparse attested surface statistics are ranking metadata only and must
be pruned and analyzer-validated.

### Risk: HornMorpho runtime integration is too heavy

Keep Python and compiler tooling offline/build-time. Ship a compact,
versioned, measurable artifact with a minimal Kotlin reader.

### Risk: verb scope explodes

Require Phase 7's ADR and a declared feature slice. Optimize for common typing
value, not theoretical coverage in the first release.

### Risk: feature text parsing is slow or subtly wrong

Parse and validate at build time, encode common constraints in compact fields,
retain raw text only for provenance, and test unknown/malformed features.

### Risk: normalization merges distinct displays

Use normalized keys for lookup/deduplication, preserve original/canonical
display, retain multiple analyses internally, and make tie-breaking explicit.

### Risk: corpus garbage becomes lexical truth

The analyzer gates corpus tokens. Corpus counts can rank a valid form but never
authorize one.

### Risk: short prefixes miss low-frequency lexemes

Measure the current 48-lexeme limit. Use bounded incremental paging or compact
prefix metadata, not an unbounded result set.

### Risk: mixed worktree causes lost user changes

Stage explicit paths, inspect every hunk, and never reset unrelated work.

### Risk: licensing/provenance drifts

Pin commit hashes, vendor required source/control files with the license, keep
reproduction scripts, and update provenance in the same change as an artifact.

## 16. Open decisions requiring user or expert input

The next AI should not silently decide these:

1. Which Amharic corpus may be used for surface statistics and predictions?
2. What should the first verb feature slice prioritize for real users?
3. Who will perform fluent-Amharic linguistic review of top suggestions?
4. How much additional APK/database size is acceptable for verbs and n-grams?
5. Should analyzer-valid personal forms be allowed to outrank globally
   attested forms, and by how much?
6. Should accepted noncanonical spellings be displayed, used only for lookup,
   or hidden unless typed exactly?
7. Are nominal copula and conjunctive suffixes important enough for Phase 4,
   or should they be a separate post-noun phase?

Default recommendations until answered:

- favor precision over recall;
- show canonical spellings before variants;
- keep generated candidates below exact/attested candidates;
- limit the first verb release to a measured high-frequency subset;
- keep all learning local and disabled in private fields;
- do not increase asset budgets without a quality and performance result.

## 17. Handoff completion checklist for every phase

- [ ] Read `AGENTS.md` and this plan.
- [ ] Inspect dirty worktree and preserve unrelated edits.
- [ ] Record exact HornMorpho inputs and commit.
- [ ] Update deterministic build scripts before generated assets.
- [ ] Add positive, negative, ambiguity, and failure tests.
- [ ] Run focused module tests.
- [ ] Regenerate database/artifacts twice and compare hashes.
- [ ] Run DB schema, row-count, index, and size contracts.
- [ ] Measure latency, heap, and APK delta where applicable.
- [ ] Assemble both products.
- [ ] Install Addiyon when a device is connected.
- [ ] Run `checkKeyboardProducts` and distinguish known unrelated failures.
- [ ] Update `UPSTREAM.md`, tool docs, and licenses/provenance.
- [ ] Review the actual staged diff before committing.
- [ ] Report unsupported HornMorpho branches and remaining limitations.

## 18. Implementation log

Append entries here instead of rewriting the historical snapshot.

| Date | Phase | Commit | Result | Notes |
|---|---:|---|---|---|
| 2026-08-12 | 1 | `fc0b927` plus captured uncommitted follow-up | Lexeme baseline | 18,867 lexemes; 18,251 base display lemmas |
| 2026-08-12 | 2 | Uncommitted at handoff | Noun MVP implemented and tested | Both APKs assembled; install unavailable because no device was connected |
| 2026-08-12 | 3 | Uncommitted | Noun MVP hardened and oracle-verified | Typed feature parser; pinned HornMorpho oracle; deterministic 442-case golden corpus across 42 lexemes; real-SQLite engine tests; obsolete blind prefix synthesis removed; 1,972 KiB DB; Addiyon installed and connected smoke passed on two Android 16 devices; both APKs assembled. `checkKeyboardProducts` reports the two known unrelated architecture-contract failures in section 2.3. |
| 2026-08-12 | 4 | Uncommitted | Practical nominal parity implemented | Shared bounded forward/reverse rule graph with structured analyses; schema 9 stable lexeme IDs, feature bits, stem classes, and indexed nominal filtering; deterministic 1,060-case Phase 4 oracle corpus (948 supported, 17 explicit exclusions, 95 negatives) across 42 lexemes; combined Phase 3/4 coverage is 1,270 supported and 215 negative cases. SQLite DB is 2,523,136 bytes and byte-deterministic (`461ae3a2f1627b0f3ab117b0fd592e278d9e1f564e15baa343339e930b76f604`). JVM p95: 0.44 ms nominal lookup and 7.24 ms complete pipeline. Connected Android 16 Medium Phone AVD p95: 67.13 ms from final key to publication, max 92.64 ms; the requested `TanaLowRam` AVD was not available on this machine. |
| 2026-08-13 | 5 | Uncommitted | Provenance-aware ranking and sparse statistics implemented | Exact lexeme, greedy literal, attested surface, generated morphology, personal evidence, and fuzzy provenance are explicit with invariant-tested source bands and deterministic canonical ties. Schema 10 adds 481 analyzer-gated surface counts as ranking metadata only; the 2,477-byte gzip is capped at 2,048 rows and byte-deterministic (`25491607220c2a6ffd622e6903f835c741f3a46f9c448e685990cd9c919ae331`). The Amharic DB is 2,539,520 bytes, a 16,384-byte delta from Phase 4, and byte-deterministic (`b9d30ca9c1cb8767b7125cc9dd698074c19db8de2bb91799890947dac4bb6f6a`). JVM p95: 0.60 ms nominal lookup and 7.22 ms complete pipeline. Connected Android 15 `TanaLowRam` p95: 71.02 ms from final key to publication, max 73.27 ms. All Phase 5 affected suites, including the existing English ranking tests, remain green. |
| 2026-08-13 | 6 | Uncommitted | Conservative morphology-gated prediction baseline implemented | A project-authored 31-line corpus produces a deterministic 454-byte bigram-only model with 40 validated surface entries, 33 contexts, and 50 successor rows. Schema 11 separates `ngram_vocab` from base `words`, allowing analyzer-valid inflections without dictionary leakage. The preselected held-out metric is 11/14 top-3 hits (`0.785714`) versus zero for the empty baseline. The 2,506,752-byte DB is deterministic (`6df9462b48a876953d9fbd7b18421db4f848cc9f0431989b90b6d4b2c9367d03`); warm JVM prediction p95 is 0.106 ms. Focused/full affected suites, both APK assemblies, Addiyon install, and a connected Android 15 `TanaLowRam` inflected-prediction smoke test pass. The product gate still reports the two unrelated pre-existing architecture-contract failures for Addiyon manual-screen placement and shared `aiTone*` design tokens. Broader corpus import and trigrams remain gated on documented corpus provenance and fluent-speaker context review. |
| 2026-08-13 | 7 | Uncommitted | Compact verb automaton selected and architecture gate passed | Vendored the pinned direct verb cascade and fixed two invalid developer-oracle dependency pins. A deterministic 407-row oracle corpus round-trips 100 regular roots, 12 irregular roots, and 20 light-verb lexemes across three high-value feature probes. The accepted test-only `AHVA` version-1 artifact has 922 states, 921 transitions, 402 surfaces, and 407 analyses; it is 39,394 bytes installed and 12,006 bytes under deterministic gzip (`a72950884fdfa6c76cd7a9f6e37e05bf7ca2f51b4ddd67e6386f17433feee963`). Reference p95 is 0.0052 ms exact and 0.0141 ms prefix with 171,320 peak allocated bytes. The pruned DAFSA saves only 971 installed bytes while losing 2.7% of oracle rows, and the mechanical rule-graph prototype cannot execute HornMorpho unification. A clean second oracle/artifact build was byte-identical; the full JVM suite, both APK assemblies, and Addiyon install on Android 15 `TanaLowRam` pass. Both APKs retain the HornMorpho license and exclude the test-only verb artifacts. `checkKeyboardProducts` reaches only the two unrelated pre-existing architecture-contract failures from section 2.3. The accepted ADR is `docs/adr/amharic-verb-morphology-artifact.md`; runtime verb integration remains Phase 8. |
| 2026-08-13 | 8 | Uncommitted | Production high-value verb morphology implemented | A deterministic `AHVA` version-2 asset ships 100 regular, 12 irregular, and 64 light roots with 7,666 terminal surfaces and 7,931 analyses across perfective, imperfective, and jussive/imperative forms. The 770,172-byte artifact is 194,597 bytes under deterministic gzip and has SHA-256 `8a1f683873f9972d6444f4b969a1acb3af0fc86d4f1d5e945601bbcf94456927`. The pure-Kotlin reader validates manifest/SHA, schema, CRC, offsets, records, and string bounds; failures preserve noun/base suggestions. Runtime merges normalized noun/verb candidates before provenance-aware ranking, and analyzer-backed split-vowel forms are admitted only when a terminal exists. |
| 2026-08-13 | 9 | Uncommitted | Adaptive morphology and technical release gates implemented | Personal dictionary version 3 retains exact surfaces plus stable optional verb lemma/analysis IDs with v2/legacy migration and on-device-only storage. Password, no-suggestions, and no-personalized-learning fields do not learn. Weighted fuzzy traversal can return only exact analyzer terminals and stays disabled on low-RAM paths. Android 15 `TanaLowRam` warm publication passed at 69.258334 ms p95 (82.454167 ms max), and the isolated 600,000 ms soak passed 1,606 iterations with post-GC heap 7,457,856 → 7,461,984 bytes and PSS 205,794,304 → 204,076,032 bytes. A deterministic 210-row seven-source review sheet is ready; fluent-Amharic verdicts and release-owner GPL distribution/source-offer sign-off remain external release blockers. |

## 19. Primary references

- Pinned HornMorpho repository:
  <https://github.com/hltdi/HornMorpho/tree/7e3d93af760e27ea6dbc3b7a078d2d9c3335f618>
- Nominal morphotactics:
  <https://github.com/hltdi/HornMorpho/blob/7e3d93af760e27ea6dbc3b7a078d2d9c3335f618/src/hm/languages/a/fst/n.mtx>
- Nominal boundary rules:
  <https://github.com/hltdi/HornMorpho/blob/7e3d93af760e27ea6dbc3b7a078d2d9c3335f618/src/hm/languages/a/fst/n_aff_bound.fst>
- Main verb morphotactics:
  <https://github.com/hltdi/HornMorpho/blob/7e3d93af760e27ea6dbc3b7a078d2d9c3335f618/src/hm/languages/a/fst/v.mtx>
- Local provenance:
  `language/amharic/hornmorpho/UPSTREAM.md`
- Local dictionary tool documentation: `tools/README.md`
- SQLite architecture background: `plans/sqlite-dictionaries.md`
