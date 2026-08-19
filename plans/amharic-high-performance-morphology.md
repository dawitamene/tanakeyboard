# Implementation Plan: Ultra-Fast Amharic Word Suggestions & HornMorpho Optimization

## Executive Summary
Addiyon Keyboard uses HornMorpho-based dynamic morphological construction (for nominal rules and runtime verb finite-state transducers) to support millions of inflected Amharic words without shipping a massive 250k+ surface wordlist. However, on Android devices, keystroke suggestions currently suffer from noticeable latency (75ms–300ms+ spikes).

This plan identifies the exact root causes of latency and details an end-to-end architecture to make Amharic suggestions **extremely fast** (P50 < 3ms, P95 < 10ms) while preserving the HornMorpho lexical validity contract, compact app size, and zero-AI offline execution.

---

## 1. Deep Root Cause Analysis

Profiling and codebase analysis reveal 5 distinct compounding bottlenecks on every keystroke:

### A. Dynamic Zlib Decompression on the Keystroke Hot Path (`AmharicVerbLexicon`)
- In `AmharicVerbLexicon.kt`, `unify()` reads compressed constraint pages via `inflatePage()` using `java.util.zip.Inflater`.
- With a tiny 4-page LRU cache (`PAGE_CACHE_SIZE = 4`), active FST traversal across diverse verb paradigms constantly misses the cache and runs software DEFLATE decompression on the keystroke critical path.

### B. Severe Object Allocation and ART GC Churn
- On every keystroke, `AmharicVerbLexicon.complete()` runs up to 50,000 node expansions using `PriorityQueue<SearchNode>`.
- Each step clones `ShortArray` objects (`base.clone()`), creates `FeatureKey` objects, and instantiates `SearchNode` objects on the heap.
- In `AmharicNounMorphology.kt` and `NominalRuleGraph.kt`, `reverseStemCandidates()` executes an 8-level BFS creating up to 128 String fragments and extensive affix state objects.
- On Android's ART runtime, generating tens of thousands of objects per keystroke triggers Generative Concurrent Copying (CC) GC pauses, directly stuttering the UI and delaying suggestion strip updates.

### C. Multi-Reading SQLite Query Explosion
- `Transliterator.candidateReadings()` generates 4 to 8+ candidate readings (e.g. `readings` + `quirkReadings`).
- `CandidateRanker.rankAmharicDetailed()` iterates over **all** readings and executes:
  - `dictionary.frequenciesOf()` (SQLite `IN (...)`)
  - `dictionary.suggestionEntriesForPrefixes()` (SQLite `UNION ALL`)
  - `morphLexicon.nounEntries()` (SQLite JOIN on `morph_lexemes` and `words`) for **each reading prefix**
  - `morphLexicon.surfaceFrequencies()`
  - `dictionary.fuzzySuggestions()` (Multiple SQLite range queries)
- These serialized disk/SQLite queries and cursor/string allocations add 20–60ms of overhead per keystroke.

### D. Unbounded Search Horizons and Fuzzy Search Triggering
- `COMPLETION_EXPANSION_LIMIT` is set to 50,000 expansions; `FUZZY_EXPANSION_LIMIT` is set to 250,000 expansions.
- If direct matches are fewer than 15, `verbLexicon.fuzzy()` executes dynamic programming Levenshtein search across the entire FST, taking 100ms+ on complex prefixes.

### E. Redundant Restarting of Keystroke Traversal (No Frontier Memoization)
- When typing incrementally (`s` → `se` → `sew` → `sewn`), each keystroke restarts FST traversal and reverse stem BFS from state 0 rather than resuming from the previous character's active state frontier.

---

## 2. Proposed Architectural Optimizations

```
+-----------------------------------------------------------------------------------+
|                            TYPING CONTROLLER (KEYSTROKE)                          |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| TIER 0: Instant L1 Cache & Greedy Reading (< 1ms, Sync / Main Thread Safe)       |
| - Fast transliteration reading + in-memory Top-Prefix L1 cache                    |
| - Immediate UI strip update (zero flicker, zero perceived lag)                    |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| TIER 1: In-Memory Zero-Allocation Morphology (< 6ms, Background Dispatch)         |
|                                                                                   |
|  +-------------------------------------+   +------------------------------------+ |
|  | In-Memory Nominal Lexicon Table     |   | Zero-Allocation HornMorpho WFST    | |
|  | - 18.8k stem records in flat memory |   | - Direct uncompressed constraints  | |
|  | - Zero SQLite queries on hot path   |   | - Primitive LongArray state queue  | |
|  | - Bounded reverse stem lookup       |   | - Bounded 1,500 expansion cap      | |
|  +-------------------------------------+   +------------------------------------+ |
|                                    \           /                                  |
|                                     v         v                                   |
|                      +-------------------------------+                            |
|                      | CandidateRanker (Pruned Loop) |                            |
|                      | - Deep morph on Top-2 readings|                            |
|                      | - Gated fuzzy search          |                            |
|                      +-------------------------------+                            |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| Published Suggestion Strip Updates                                                |
+-----------------------------------------------------------------------------------+
```

---

## 3. Detailed Technical Plan

### Phase 1: Zero-Allocation & Uncompressed HornMorpho Verb FST Engine
1. **Uncompressed Constraint Indexing (`amharic_verbs.ahrf`)**:
   - Eliminate runtime zlib `Inflater` decompression entirely. Store constraint tables in memory-mapped direct-access blocks within the `.ahrf` asset or pre-inflate all constraint pages into a compact byte array on startup (~300 KB resident RAM).
2. **Primitive Parallel Heap / Ring Buffer (Zero Heap Allocation)**:
   - Replace `PriorityQueue<SearchNode>` and `ArrayDeque<SearchNode>` with primitive `LongArray` / `IntArray` ring buffers:
     - Pack node state: `stateId` (18 bits), `inputPos` (6 bits), `cost` (8 bits), `depth` (8 bits), `featureRef` (24 bits) into a 64-bit `Long`.
   - Avoid creating `SearchNode`, `ShortArray`, and `FeatureKey` objects during traversal.
3. **Horizon Pruning**:
   - Cap `COMPLETION_EXPANSION_LIMIT` to 1,500 expansions (empirically sufficient to discover all high-probability candidates in top-15).
   - Early exit when 15 valid terminal candidates of cost $\le 2$ are found.

### Phase 2: In-Memory Hot Lexicon & Eliminating SQLite from Keystroke Path
1. **In-Memory Compact Nominal Lexicon**:
   - Load `morph_lexemes` (18,867 rows) and `morph_surface_stats` (481 rows) from SQLite once into an in-memory prefix trie / sorted arrays (`StemIndex`) during `loadAsync()`.
   - RAM footprint is under ~400 KB, but completely eliminates SQLite query setup, locks, and cursor allocations on keystrokes.
2. **Precomputed Reverse Stem Lookup**:
   - Instead of 8-level BFS generating up to 128 strings via `NominalRuleGraph.reverseStemCandidates()`, replace with a precomputed suffix-stripping lookup table or fast rule-indexed matcher.

### Phase 3: Pruned Transliteration & Gated Fuzzy Search
1. **Prioritized Reading Budgeting in `CandidateRanker`**:
   - In `CandidateRanker.kt`, only run full morphological expansion (`completionsForPrefix`) on the top 1 or 2 most likely readings (`readings.take(2)`).
   - For lower-ranked readings (quirk/split transliterations), perform only exact/prefix dictionary lookups.
2. **Fuzzy Search Gating**:
   - Skip expensive fuzzy FST traversal (`verbLexicon.fuzzy()`) and SQLite fuzzy queries when:
     - The user is actively typing at high speed (cadence < 250ms).
     - Or when the top-tier exact/morph candidates already provide 3+ high-confidence suggestions.
     - Or when typed length $\le 2$.

### Phase 4: Incremental Keystroke Frontier Memoization
1. **Prefix Frontier Caching**:
   - Cache the FST frontier states (set of active states) for prefix $P$.
   - When the user types the next character ($P + c$), start FST search directly from the cached active states of $P$ instead of starting from state 0.
   - Reduces traversal time from $O(L)$ to $O(1)$ transitions per keystroke (< 0.5ms).

---

## 4. Affected Files & Components

| Component | File | Proposed Modification |
|---|---|---|
| `:language:amharic` | `AmharicVerbLexicon.kt` | Zero-allocation primitive heap, uncompressed constraint access, 1.5k expansion cap, frontier memoization |
| `:language:amharic` | `NominalRuleGraph.kt` & `AmharicNounMorphology.kt` | Fast in-memory stem index lookup, bounded reverse stem generation, zero-copy candidate synthesis |
| `:language:amharic` | `AmharicSuggestionEngine.kt` | In-memory lexicon caching, top-2 reading morphological budgeting, gated fuzzy search |
| `:suggestions:sqlite` | `SQLiteMorphLexicon.kt` | Startup in-memory cache loader for `morph_lexemes` and `morph_surface_stats` |
| `:suggestions:core` | `CandidateRanker.kt` | Reading pruning (deep morphology on top-2 readings only), optimized candidate deduplication |
| `:language:amharic` | `AmharicSuggestionEngineIntegrationTest.kt` | Add latency benchmark tests asserting P50 < 3ms and P95 < 10ms |

---

## 5. Verification & Benchmarking Plan

### Automated Benchmark Tests
- Run `AmharicSuggestionEngineIntegrationTest`:
  ```sh
  /Users/dev/code/addiyon-keyboard/gradlew :language:amharic:testDebugUnitTest --tests "com.addiyon.keyboard.language.amharic.AmharicSuggestionEngineIntegrationTest"
  ```
- Performance assertions:
  - `nominal_lookup_p95_ms` $\le 2.0\text{ ms}$ (improved from 15.0 ms)
  - `suggestion_pipeline_p95_ms` $\le 10.0\text{ ms}$ (improved from 75.0 ms)
  - Heap allocation per completion query $\le 10\text{ KB}$ (zero GC pressure)

### Product Verification
- Build and verify both products:
  ```sh
  /Users/dev/code/addiyon-keyboard/gradlew checkKeyboardProducts
  /Users/dev/code/addiyon-keyboard/gradlew :apps:addiyon:assembleDebug :apps:textrevamp:assembleDebug
  ```
- Install and test live typing on Android device via `sendDebugToPhone` or `installDebug`.
