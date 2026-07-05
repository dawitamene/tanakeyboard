# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Tana Keyboard is an Android IME (`InputMethodService`) that provides an Amharic (Ge'ez script)
keyboard with Latin-to-Fidel transliteration, plus a standard English layout. UI is built entirely
in Jetpack Compose. `MainActivity` is just a settings/test harness (buttons to open keyboard
settings and a text field to try typing) — the real product is `TanaKeyboardService`.

## Commands

- Build debug APK: `./gradlew assembleDebug`
- Run JVM unit tests (no emulator needed): `./gradlew testDebugUnitTest`
- Run a single unit test class: `./gradlew testDebugUnitTest --tests "com.addiyon.tanakeyboard.transliteration.SomeTest"`
- Compile-only check (fast, no test run): `./gradlew compileDebugKotlin`
- Instrumented tests (needs emulator/device): `./gradlew connectedAndroidTest`

Note: `app/build.gradle.kts` has an `assembleProvider` hook that copies the built APK to
`/Users/dev/Shared` with a timestamped filename after every assemble — this is a local
convenience for sideloading onto a test device, not something to remove or "fix".

To actually try the keyboard: install the debug APK, enable "Tana Keyboard" in
Settings > System > Languages & input > On-screen keyboard, then switch to it in any text field.

## Architecture

### The transliteration pipeline (the core logic, all pure Kotlin / JVM-testable)

`transliteration/AmharicTable.kt` → `transliteration/Transliterator.kt` → `transliteration/AmharicComposer.kt`

- **`AmharicTable`** is the sole source of transliteration data: a `Family` per consonant (7 ordered
  syllabic forms — e/u/i/a/ie/bare/o — plus an optional labialized "ua" form), keyed by SERA-style
  Latin spelling. Forms are literal Ethiopic-block strings copied from the source table, not derived
  by codepoint arithmetic, because the "ua" forms break the arithmetic pattern inconsistently.
  `bareVowels` is a *separate* index table (not reused from `vowels`) for standalone/word-initial
  vowels — e.g. `"a"` alone must resolve to አ (order 1 of the glottal family), which is a different
  index than what `"a"` means after a consonant (order 4, per SERA convention where consonant-order-1
  is spelled "e", e.g. "le" → ለ). Don't conflate these two tables when touching vowel logic.
- **`Transliterator`** is stateless and whole-buffer: every keystroke re-transliterates the *entire*
  Latin buffer from scratch rather than incrementally patching previous output. This is deliberate —
  incremental patching creates desync bugs across backspace, cursor jumps, and future autocomplete.
  Matching at each position: longest consonant (case-sensitive first, then case-insensitive fallback)
  → longest vowel after it, else longest bare vowel, else pass the character through unchanged.
  The case-sensitive-first rule matters: h/H, t/T, ch/C are the three families where shift
  genuinely selects a different consonant (ሀ/ሐ, ተ/ጠ, ቸ/ጨ); every other letter has no distinct
  uppercase family, so shift should be a no-op for it and falls through to the case-insensitive pass.
- **`AmharicComposer`** owns the live "being-typed" word: appends to a Latin `StringBuilder`, re-runs
  `Transliterator` on the whole buffer, and pushes the result into the `InputConnection`'s composing
  region (`setComposingText`) so it's underlined until committed. Backspace deletes the whole Latin
  span behind the last rendered fidel unit (via `Transliterator.lastUnitStart`), not one Latin
  char — so "she" → ሸ, one backspace removes the whole thing. It's fed an `InputConnection` *lambda*,
  not a captured reference, because the system swaps `InputConnection` instances between input
  sessions.

### Service / UI layer

- **`TanaKeyboardService`** is the actual `InputMethodService`. It owns `isAmharic`, `shiftState`
  (`ShiftState`: OFF → SHIFT (one-shot) → CAPS_LOCK → OFF), and the `AmharicComposer`. All key
  handling goes through methods on the service (`onCharacter`, `onDelete`, `onSpace`, `onEnter`,
  `toggleShift`, `toggleLanguage`) rather than the UI touching `InputConnection` directly — in
  Amharic mode a keypress isn't a single `commitText`, it's a mutation of the composer's buffer, and
  only the service can keep that state consistent. Case resolution from shift state happens inside
  `onCharacter` (`latin.uppercase()`/`.lowercase()`), not in the UI layer.
- **`KeyboardScreen` → `KeyRow` → `KeyComposables`** render whichever `KeyboardLayout` is active
  (`layout/AmharicLayout.kt` or `layout/EnglishLayout.kt`, both flat `KeyData` row lists). Every
  `KeyData.Character` key carries exactly one base Latin letter — there are no multi-character key
  labels; digraphs like "sh"/"ch"/"gn" only arise from two sequential single-letter keypresses whose
  concatenated buffer happens to match a family in `AmharicTable`.
  Composables never read `service.currentInputConnection` at composition time (it goes stale across
  input sessions) — they call service methods that re-fetch it fresh on each tap, and a key's corner
  preview glyph is looked up live via `AmharicTable.bareFormOf` off the shift-resolved letter, not
  baked into the layout data.
