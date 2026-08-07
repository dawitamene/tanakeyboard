# Fix next-word suggestion flicker — loading indicator + faster predictions

## Goal

Remove the toolbar flash that appears at every word boundary (space) before next-word predictions arrive. Fix in two complementary ways simultaneously:

1. **Visual hold:** when a prediction lookup is in flight, keep the suggestion bar in shape and show `...` as an explicit loading placeholder, so slow devices see a stable row instead of icons → chips flash.
2. **Speed:** make the lookup fast enough that `...` rarely renders at all (fast path completes before the placeholder delay).

No code is written here — plan only, per `AGENTS.md`.

## Current behavior (evidence from code)

### Where the flicker comes from

- `AddiyonKeyboardService.updateSuggestions()` is the only producer of `SuggestionUiState` (`app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt:609`).
- `typed.isEmpty()` branch (word boundary) handles next-word predictions:
  - It extracts `NgramContext` via `captureNgramContext()` which reads the editor (`getTextBeforeCursor` / `getSurroundingText`) synchronously on the main thread.
  - It creates `PredictionRequestKey(prev2, prev1, limit)` and checks `activePredictionRequest` for dedup, then publishes `carried ?: LoadingPredictions` (`line 708-709`) and calls `schedulePredictionComputation()`.
  - `carried` is **only** `WordCompletions`. If the previous strip was `NextWordPredictions` (second word in a row) or `Toolbar`, there is nothing to carry, so it falls to `LoadingPredictions`.
  - `LoadingPredictions` and `LoadingCompletions` both render `PredictionLoadingStrip()` (`app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt:227`), which today is 3 blank weighted slots with a single central dot shown only after `PREDICTION_LOADING_INDICATOR_DELAY_MILLIS = 120L` (`line 78-79`). On slow devices the blank reads as a flash; on very slow predictions the dot is subtle and still feels like “toolbar → blank → chips”.
- `publishSuggestions(..., arePredictions=true)` (`line 1367`) turns an empty result into `LoadingPredictions` (holds the blank) not `Toolbar`. Empty mid-sentence therefore does not flash icons, but it also never recovers to chips — it lingers blank.
- `schedulePredictionComputation()` (`line 1072`) always goes async via `predictionExecutor` (single thread, `ArrayBlockingQueue(1)`). Inside it:
  - `predictionsFor()` → `predictionCache.get()` check happens **on the background thread**, then `SQLiteNgramModel.predict()` runs 2-3 SQLite queries (`wordId` for `prev2`/`prev1` + `trigrams` join + `bigrams` join) (`app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteNgramModel.kt:63-155`), then optional `topFrequentWords()` fallback query, then `personalDictionary` merge. All off-main, but pay binder + scheduling latency even on cache hit.
  - The fallback `topFrequentWords` result is cached in `SQLiteNgramModel.topWordsCache` (`line 20-22`), and `PredictionCache` caches final predictions per `(language, prev2, prev1, limit)` (`app/src/main/java/com/addiyon/keyboard/suggestion/PredictionCache.kt:11`), so the second lookup for same context should be near-instant — but still pays the executor hop.

### Why completions don’t flicker

`typed.isNotEmpty()` branch (`line 733-735`) carries **either** `WordCompletions` or `NextWordPredictions`, so mid-word typing never drops to toolbar (`AGENTS.md` “strip’s row shape never changes mid-keystroke”). Predictions lack the same carry.

### Existing anti-flicker pieces to keep

- `activeCompletionKey` dedup (`line 722-723`) prevents selection-change echo from cancelling the in-flight completion lookup (doubles latency).
- `suggestionGeneration` / `predictionGeneration` guards discard stale posts.
- `pendingPredictionBoundary` (`line 782`) is an already-captured `CapturedNgramContext` built at acceptance time to avoid re-reading the editor.

## Fix 1 — `...` loading placeholder (correctness for slow devices)

### Desired visual

- While a next-word prediction is loading, show `...` in the suggestion bar — same 40dp height, same row shape as chips, so the layout never swaps to `Toolbar` (icons). The bar stays as three equal-weight slots with dividers (English shape); Amharic uses its fixed-3 layout when `NEXT_WORD_LIMIT` is 3-6 under low-RAM vs normal — pick the 3-slot shape for loading to match both.
- Behavior: after `PREDICTION_LOADING_INDICATOR_DELAY_MILLIS` (keep 120ms), render `...` text (e.g. `Text("...", fontSize 16sp, color onSurface 0.6f)`) centered in each slot, or a single centered `...` spanning the row. Preference: **three `...` slots** — it matches `EnglishSuggestionStrip` (3 slots) and `AmharicFixedSuggestionStrip` (3 slots) and keeps divider positions identical to real chips. Single centered `...` would center-shift chips on arrival.
- Applies to both `LoadingPredictions` and `LoadingCompletions` — they already share `PredictionLoadingStrip`. Update that composable only; other states (`Toolbar`, `Private`, `LoadingLanguage`) unchanged.

### Implementation plan — visual

1. **Edit `app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt`:**
   - Modify `PredictionLoadingStrip()` (`line 227-265`) to render `...` instead of the single dot. Keep the `LaunchedEffect` + `delay(120)` gating via `showIndicator`. When `showIndicator` becomes true, each `Box` shows `Text("...")` (or `BasicText`) centered. Before delay, keep boxes empty — fast loads (under 120ms) never show `...`.
   - Tag slots with existing `predictionLoadingSlotTag(index)` and indicator with `PREDICTION_LOADING_INDICATOR_TAG` so `SuggestionAreaUiTest` continues to find them (or update test to expect `...`).
   - No change to `SuggestionUiState` sealed interface itself — reuse `LoadingPredictions` / `LoadingCompletions` as the signal (`app/src/main/java/com/addiyon/keyboard/ui/SuggestionUiState.kt:13-22`).

2. **Alternative considered and rejected:** introducing a new `SuggestionUiState.LoadingPredictionsEllipsis` — unnecessary; the existing loading states already encode the need, only rendering changes.

### Fix 1b — carry predictions at boundary (remove blank even before `...`)

- In `updateSuggestions()` typed-empty branch, change:
  ```kotlin
  val carried = nonVoiceSuggestionUiState as? SuggestionUiState.WordCompletions
  ```
  to
  ```kotlin
  val carried = nonVoiceSuggestionUiState as? SuggestionUiState.WordCompletions
      ?: nonVoiceSuggestionUiState as? SuggestionUiState.NextWordPredictions
  ```
  mirroring the completion branch (`line 733-734`). This keeps the prior predictions visible while the next word’s predictions compute (second word in a row). Stale chips stay scoped to their `actionGeneration`, rejected by `isCompletionChipTapValid` / `contentIdentityMatches` in `onSuggestionTapped`, same as current completion carry — no safety regression.

## Fix 2 — make predictions faster so `...` rarely appears

### Bottlenecks to address (measured intuition — confirm with tracing before coding)

1. **Executor hop on cache hit** — biggest win. `PredictionCache` hit should not need `predictionExecutor`.
2. **Main-thread editor reads** — `captureNgramContext()` does `surroundingText()` / `textBeforeCursor()` binder round-trips on every `updateSuggestions()` call. At boundary, `pendingPredictionBoundary` already has the context and should be preferred (it already is via `currentBoundaryContext()`, but ensure `onSpace` / accepted-replacement paths populate it before calling `updateSuggestions`).
3. **SQLite work per miss** — `wordId` lookups + trigram/bigram joins are indexed but still disk I/O if page cache cold. No schema change here; leverage existing caches.

### Implementation plan — speed

1. **Synchronous cache-fast path in `updateSuggestions()` (main thread, before scheduling):**
   - After building `request = PredictionRequestKey(...)` and verifying `ngramModelFor(amharic)?.isReady`, check `predictionCache.get(language, prev2, prev1, limit)` **on the main thread** (it is `@Synchronized`).
   - On hit: build the final word list synchronously (take cached `List<Prediction>` → `map { it.word }` + `personalDictionary.ranked(limit)` merge + `distinct().take(limit)`), then `publishSuggestions(cachedWords, arePredictions=true)` directly and `return` without `schedulePredictionComputation()`. This avoids `Handler`, `ThreadPoolExecutor`, and `SuggestionTrace` overhead — typically 1-3ms vs 30-80ms.
   - On miss: fall through to existing `schedulePredictionComputation()` path (which will populate cache on completion).
   - Cache includes `limit` in key, so low-RAM (limit 3) vs normal (limit 6) properly separate.

2. **Reuse `topFrequentWords` cache aggressively:**
   - Already cached in `SQLiteNgramModel.topWordsCache` (first fetch cached, `take(limit)`). Ensure `schedulePredictionComputation()` still uses it via fallback path; no extra work.

3. **Keep `predictionBoundaryAfterAcceptedReplacement` optimization:**
   - Verify `onSpace()` and `onSuggestionTapped()` (non-prediction path) set `pendingPredictionBoundary` via `predictionBoundaryAfterAcceptedReplacement()` / `predictionContextAfterAcceptedWord()` *before* `updateSuggestions()` so the next boundary read is `pendingPredictionBoundary` not a fresh `surroundingText()` read. Already partially done for suggestion taps; extend to `onSpace` commit path if missing (audit `onSpace` at `line 2612` — it currently does `updateSuggestions()` after `typingController` commit but does not visibly create a `PendingPredictionBoundary` for the space case; `captureNgramContext` then reads editor. Add `pendingPredictionBoundary = predictionContextAfterAcceptedWord(priorContext, committedWord)` pattern analogous to tap path).

4. **Optional micro-wins (do only if tracing shows value, keep diff small):**
   - In `predictionsFor()` (`line 1334`), keep `SafeRun` + `SuggestionTrace.section` but avoid re-`normalize` overhead (already done by `PredictionCache.key` normalize). No logic change.
   - Ensure `SQLiteLanguageStore.databaseOrNull()` check is fast (volatile read).
   - No change to `predictionExecutor` size (keep 1+1 threads; suggestionExecutor already correctly at `THREAD_PRIORITY_DEFAULT` per comment `line 984-991`).

### What not to do

- Do not introduce in-memory vocab trie/NGram re-hydration — defeats SQLite memory win (AGENTS: 45MB → 5-7MB) and duplicates `low-end-performance-and-startup-recovery.md` findings.
- Do not lower thread priority or add new dispatchers.
- Do not change DB schema/indexes in this patch — measure first; cache-fast path dwarfs query time.

## Affected files

| File | Change |
|---|---|
| `app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt` | `PredictionLoadingStrip`: render `...` in 3 slots after 120ms delay; keep tags; keep `PredictionLoadingStrip` for both loading states. |
| `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt` | `updateSuggestions()` typed-empty branch: carry `NextWordPredictions` + synchronous `predictionCache.get` fast path before `schedulePredictionComputation`; optional `pendingPredictionBoundary` wiring in `onSpace`. `schedulePredictionComputation` unchanged except population of cache still occurs. |
| `app/src/main/java/com/addiyon/keyboard/ui/SuggestionUiState.kt` | No change (reuse `LoadingPredictions` / `LoadingCompletions`). |
| `app/src/main/java/com/addiyon/keyboard/suggestion/PredictionCache.kt` | No change (already thread-safe). |
| `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteNgramModel.kt` | No change (already caches fallback). |
| `app/src/test` / `app/src/androidTest` | Update `SuggestionAreaUiTest` / create unit test for cache fast path and carry logic; add `PredictionCacheTest` case if needed. |

## Step-by-step breakdown

1. **Branch & baseline measurement**
   - Add `SuggestionTrace` markers or `SystemClock.elapsedRealtime()` logs around `captureNgramContext` and `predictionsFor` to confirm cache-hit latency vs miss latency on a low-end AVD. Record before numbers.

2. **Visual placeholder (Fix 1)**
   - Edit `PredictionLoadingStrip` to show `...` per slot after delay. Verify row height 40dp, dividers present, matches `EnglishSuggestionStrip` / `AmharicFixedSuggestionStrip` metrics. Screenshot compare.

3. **Prediction carry (Fix 1b)**
   - One-line edit to carry `NextWordPredictions` at word boundary.

4. **Synchronous cache fast path (Fix 2)**
   - In `updateSuggestions()`, add main-thread `predictionCache.get` before scheduling. Return early on hit. Ensure `personalDictionary` merge stays consistent with background path (extract helper `mergePersonal(predictions, limit)` shared).
   - Optional: wire `pendingPredictionBoundary` creation in `onSpace` to avoid extra editor read.

5. **Tests**
   - JVM unit: `AddiyonKeyboardService` behavior via `EditorGateway` fake + `FakePredictionCache` — verify word-boundary publishes cached predictions synchronously without executor, and blank never becomes `Toolbar`.
   - UI: `SuggestionAreaUiTest` — `LoadingPredictions` shows `...` after delay, does not show `ToolbarActions`.

6. **Perf re-measure**
   - Re-run step 1 logs; fast-path should be <16ms (one frame). Slow path still shows `...` at 120ms, never icons.

7. **Manual verification**
   - `./gradlew testDebugUnitTest` (JVM) + `./gradlew connectedAndroidTest` on low-end AVD (API 24/28, 1GB RAM). Keyboard open → type `hello␣` → bar holds `...` or instantly shows predictions — no icon flash. Repeat for Amharic `ሰላም␣`.
   - Build/install: `./gradlew installDebug` + `./gradlew assembleDebug` (copies APK to `/Users/dev/Shared` per `app/build.gradle.kts` hook — do not remove).

## Risks & open questions

- **Cache staleness:** `PredictionCache` key includes `prev2/prev1/limit/language` but not `personalDictionary` contents. After adding a personal word, stale cached predictions would miss the personal merge. Current background path re-merges personal per call and then `distinct().take(limit)`. Fast path must apply same merge on cached DB predictions — not serve stale merged list. Options: cache only DB predictions (current) and merge synchronously on hit (chosen), or invalidate cache on personal dict mutation (`predictionCache.clear()` is already called in generation bumps — verify). Risk low.
- **Binder latency on first word:** even with cache hit, `captureNgramContext` still reads editor. `pendingPredictionBoundary` eliminates one read for tap-committed words; for space-committed words confirm it is populated. If not, first boundary after space still pays one binder call (~2-8ms).
- **Low-RAM limit mismatch:** `NEXT_WORD_LIMIT` vs `PREDICTION_FALLBACK_ENGLISH_LIMIT` vs low-RAM=3 — cache key already includes `limit`, so hits/misses partition correctly. Ensure English fallback limit (3) vs next-word limit (6) do not cross-pollute cache (they have different limits, so separate entries).
- **Compose recomposition cost:** publishing `...` is one extra recomposition at +120ms. Kept intentionally low-frequency and with same layout shape, so no `windowInsets` or IME resize jank.
- **Thread safety:** `predictionCache` is `@Synchronized`, main-thread read is safe; background writes still synchronize. No data race.

## Success criteria

- No `Toolbar` (icon row) renders between committing a word (space / chip tap) and next-word predictions appearing, on both English and Amharic.
- On cache hit (second occurrence of same `prev1/prev2`), suggestions appear within one frame (<16ms after `updateSuggestions`), so `...` never becomes visible.
- On cache miss / cold DB page, `...` appears at 120ms in the same 3-slot shape and is replaced by chips — not by toolbar icons.
- Existing tests green: `testDebugUnitTest` + relevant `connectedAndroidTest` (`SuggestionAreaUiTest`).

## Next step after approval

Implement in the order above (visual → carry → cache fast path) as a single PR, or two stacked PRs if reviewer prefers (1: visual, 2: speed) — both target `AddiyonKeyboardService.kt` and `SuggestionBar.kt`.
