# AGENTS.md

Tana Keyboard is an Android IME (`InputMethodService`) with Amharic (Ge'ez script) Latin-to-Fidel transliteration and an English layout. UI is Jetpack Compose. Single module `:app`.

## Commands

- Build: `./gradlew assembleDebug`
- JVM unit tests (no emulator): `./gradlew testDebugUnitTest`
- Single unit test: `./gradlew testDebugUnitTest --tests "com.addiyon.tanakeyboard.transliteration.SomeTest"`
- Compile-only check (fast): `./gradlew compileDebugKotlin`
- Instrumented tests (needs emulator): `./gradlew connectedAndroidTest`
- `app/build.gradle.kts` has an `assembleProvider` hook that copies the APK to `/Users/dev/Shared` with a timestamped filename — local convenience, do not remove.

## Architecture

### Entry points
- `TanaKeyboardService` (:17) — the real product, an `InputMethodService`
- `MainActivity` (:28) — settings/test harness only

### Transliteration pipeline (pure Kotlin, JVM-testable)
`transliteration/AmharicTable.kt` → `transliteration/Transliterator.kt` → `transliteration/AmharicComposer.kt`

- **`AmharicTable`**: sole source of transliteration data. `Family` per consonant (7 syllabic forms + optional labialized "ua"). `bareVowels` is a *separate* index table from `vowels` — do not conflate them. "a" alone → አ (glottal order 1), "a" after consonant → order 4.
- **`Transliterator`**: stateless, whole-buffer retransliteration on every keystroke (not incremental). Matching: longest consonant (case-sensitive first) → longest vowel → longest bare vowel → passthrough. Case-sensitive-first matters only for h/H, t/T, ch/C (the three families with distinct uppercase consonants); other letters fall through to case-insensitive.
- **`AmharicComposer`**: owns the live word. Appends to a Latin `StringBuilder`, re-runs `Transliterator` on entire buffer, pushes to `InputConnection.setComposingText`. Backspace removes the whole last fidel unit via `Transliterator.lastUnitStart` (not one Latin char). Uses `InputConnection` lambda (not captured reference) — system swaps instances across sessions.

### Service / UI layer
- **`TanaKeyboardService`** owns `isAmharic`, `shiftState` (OFF → SHIFT → CAPS_LOCK → OFF), and `AmharicComposer`. All key handling goes through service methods (`onCharacter`, `onDelete`, `onSpace`, `onEnter`, `toggleShift`, `toggleLanguage`), never UI touching `InputConnection` directly. Case resolution from shift state happens inside `onCharacter` via `latin.uppercase()`/`.lowercase()`.
- **UI stack**: `KeyboardScreen` → `KeyRow` → `KeyComposables` renders whichever `KeyboardLayout` is active (`AmharicLayout.kt` / `EnglishLayout.kt`, flat `KeyData` row lists).
- Every `KeyData.Character` carries exactly one base Latin letter — digraphs ("sh", "ch", "gn") arise from sequential keypresses matching an `AmharicTable` family.
- Composables never read `service.currentInputConnection` at composition time (goes stale across input sessions) — they call service methods that re-fetch it on each tap.
- Corner preview glyph: looked up live via `AmharicTable.bareFormOf` off the shift-resolved letter, not baked into layout data.

## Planning

When the user asks to create a plan or plan something, write a detailed plan as a Markdown file in `plans/` (e.g. `plans/feature-name.md`). Do NOT execute or write any code — only the plan file. The plan should cover the approach, affected files, step-by-step breakdown, and any risks or open questions.

## After every code change
- Build and install on emulator: `./gradlew installDebug`
- Generate timestamped APK in `/Users/dev/Shared`: `./gradlew assembleDebug`

## Conventions
- kotlin.code.style=official
- No code comments in generated code (repo convention from prior work)
- No multi-character key labels in layout data
