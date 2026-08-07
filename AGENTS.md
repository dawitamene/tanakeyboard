# AGENTS.md

Addiyon Keyboard is an Android IME (`InputMethodService`) with Amharic (Ge'ez script) Latin-to-Fidel transliteration, an English layout, and a SQLite-backed suggestion engine. UI is Jetpack Compose. Single module `:app`.

## Commands

All commands assume workspace root `/Users/dev/code/addiyon-keyboard`. When the user asks for a command, always include the full path so it can be pasted from any terminal — e.g. `/Users/dev/code/addiyon-keyboard/gradlew assembleDebug` (or `bash /Users/dev/code/addiyon-keyboard/gradlew ...`), not just `./gradlew ...`.

- Build: `/Users/dev/code/addiyon-keyboard/gradlew assembleDebug` (also via `./gradlew assembleDebug` from workspace root)
- JVM unit tests (no emulator): `/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest`
- Single unit test: `/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest --tests "com.addiyon.keyboard.composing.TypingControllerTest"`
- Compile-only check (fast): `/Users/dev/code/addiyon-keyboard/gradlew compileDebugKotlin`
- Instrumented tests (needs emulator): `/Users/dev/code/addiyon-keyboard/gradlew connectedAndroidTest`
- `app/build.gradle.kts` has an `assembleProvider` hook that copies the APK to `/Users/dev/Sync` with a timestamped filename — local convenience, do not remove.

## Architecture


### Entry points
- `AddiyonKeyboardService` — the real product, an `InputMethodService`
- `MainActivity` — settings/test harness only

### Transliteration pipeline (pure Kotlin, JVM-testable)
`transliteration/AmharicTable.kt` → `transliteration/Transliterator.kt` → suggestions consumed via `candidateRanker` + `AmharicPrefixCompletion`.

- **`AmharicTable`**: sole source of transliteration data. `Family` per consonant (7 syllabic forms + optional labialized "ua"). `bareVowels` is a *separate* index table from `vowels` — do not conflate them. "a" alone → አ (glottal order 1), "a" after consonant → order 4.
- **`Transliterator`**: stateless, whole-buffer retransliteration on every keystroke (not incremental). Matching: longest consonant (case-sensitive first) → longest vowel → longest bare vowel → passthrough. Case-sensitive-first matters only for h/H, t/T, ch/C (the three families with distinct uppercase consonants); other letters fall through to case-insensitive.

### Composing layer (pure Kotlin, JVM-testable — THE load-bearing class for cursor/rich-text bugs)
`composing/Composition.kt` + `composing/TypingController.kt` + `composing/WordAdoption.kt` + `composing/ResumableWord.kt`

- **`Composition`** owns the raw buffer that IS the field's composing region. Never computes, stores, or passes an **absolute document offset** — every editor call (`setComposingText`, `commitText`, `finishComposingText`, `deleteBeforeCursor`) is cursor-relative, so the field's offset reporting (which Compose TextFields, WebViews and cross-platform toolkits do not reliably expose) cannot desync it. The invariant: a composition exists only while the caret is at its end; any other caret move finalizes the region in place (`finalizeInPlace`) and never adds, removes, or replaces text. Replacing the legacy `WordComposer` (which tracked `composingStart`/`composingEnd` as absolute offsets seeded from selection reads) is what made the "spaces disappear / random words appear" cursor bugs go away.
- **`TypingController`** owns every edit the keyboard makes: key handling (`onCharacter` / `onSpace` / `onEnter` / `onDelete` / `onCommitText`), caret-aware word resume via `WordAdoption`, chip-tap replacement (`onSuggestionTap` with `SuggestionKind.COMPLETION` / `PREDICTION`), session lifecycle (`onStartInput` / `onFinishInput`), and selection-change handling (`onSelectionChanged`). The only platform-specific dependency is `EditorGateway`, which is mockable — see `TypingControllerTest` for the full behavioural contract.
- **`WordAdoption`** re-opens a committed word the caret sits at the end of, offset-free: `deleteSurroundingText(n,0)` + `setComposingText(word, 1)`, both cursor-relative. Refuses interior carets (typing mid-word inserts plain text — what every mainstream IME does, and removes the entire absolute-offset-resume bug class).
- **`ResumableWord`** extracts the word ENDING at the caret (Latin / Amharic / email). Pure, JVM-unit-testable.

### Service / UI layer
- **`AddiyonKeyboardService`** owns `isAmharic`, `isEmailField`, `shiftState` (OFF → SHIFT → CAPS_LOCK → OFF), `numbersMode`, and the single `TypingController`. All key handling goes through service methods (`onCharacter`, `onDelete`, `onSpace`, `onEnter`, `toggleShift`, `toggleLanguage`), never UI touching `InputConnection` directly. Case resolution from shift state happens inside `onCharacter` via `latin.uppercase()`/`.lowercase()`. Language/field-specific behaviour comes from `typingProfile()`, read fresh on every controller call — a switch is just flipping `isAmharic`.
- **UI stack**: `KeyboardScreen` → `KeyRow` → `KeyComposables` renders whichever `KeyboardLayout` is active (`AmharicLayout.kt` / `EnglishLayout.kt`, flat `KeyData` row lists).
- Every `KeyData.Character` carries exactly one base Latin letter — digraphs ("sh", "ch", "gn") arise from sequential keypresses matching an `AmharicTable` family.
- Composables never read `service.currentInputConnection` at composition time (goes stale across input sessions) — they call service methods that re-fetch it on each tap.
- Corner preview glyph: looked up live via `AmharicTable.bareFormOf` off the shift-resolved letter, not baked into layout data.

### Suggestion pipeline (service-owned)
- `updateSuggestions()` derives the strip's UI state from the controller. The strip's row shape never changes mid-keystroke: completion and prediction lookups carry the previous chip-bearing state forward while the new one computes, instead of dropping to the toolbar (which swaps a row of icons for a row of chips and is the flash the user sees). The completion path also short-circuits a re-schedule when the same `(raw, amharic)` is already in flight or done (`activeCompletionKey`), so the selection-change echo that follows every keystroke doesn't double the lookup latency.

## Planning

When the user asks to create a plan or plan something, write a detailed plan as a Markdown file in `plans/` (e.g. `plans/feature-name.md`). Do NOT execute or write any code — only the plan file. The plan should cover the approach, affected files, step-by-step breakdown, and any risks or open questions.

## After every code change
- Run the relevant test(s) for the changed behavior. Prefer the focused test class/target when possible; broaden the test run when shared behavior is affected.
- When adding a new feature, add or update tests that cover that feature.
- Build and install on emulator: `/Users/dev/code/addiyon-keyboard/gradlew installDebug`
- Generate timestamped APK in `/Users/dev/Sync`: `/Users/dev/code/addiyon-keyboard/gradlew assembleDebug`

## Conventions
- kotlin.code.style=official
- No code comments in generated code (repo convention from prior work)
- No multi-character key labels in layout data
- No absolute document offsets in the composing layer; everything cursor-relative. New code that needs to identify a region must go through `EditorGateway` and use cursor-relative calls (`setComposingText`, `commitText`, `finishComposingText`, `deleteBeforeCursor`, `recomposeBeforeCursor`).
