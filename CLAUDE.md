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

`transliteration/AmharicTable.kt` → `transliteration/Transliterator.kt` → `composing/WordComposer.kt`

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
- **`WordComposer`** (`composing/`) owns the live "being-typed" word: appends to a raw
  `StringBuilder` and re-runs a `render` lambda on the whole buffer. There's one instance per
  language, differing in `render` and a `composesInline: Boolean` flag. English (`composesInline =
  true`, the default) pushes the rendered buffer into the `InputConnection`'s composing region
  (`setComposingText`) on every keystroke, so it's underlined in the field until committed —
  `render` is identity, so the buffer IS the composing text. Amharic (`composesInline = false`)
  never touches the field while typing: the raw Latin (`raw`) is surfaced only in a content-width,
  left-aligned preview strip above the suggestion row (`ui/BufferPreviewStrip.kt`, fed by
  `TanaKeyboardService.amharicBufferLatin`) — its fidel reading (`display`, via `render` =
  `Transliterator.transliterate`) isn't repeated there since it's already the first (bold/primary)
  suggestion chip. This is deliberate: composing the raw Latin inline used to be how Amharic
  worked, but `finish()` (called when the input view goes away) can only lock in whatever the
  composing region *currently shows* — so an uncommitted word left the raw Latin stranded in the
  field. With `composesInline = false`, `finish()`/`abandon()` are pure buffer discards for Amharic
  (nothing was ever written to the field to strand), while `commit()`/`commitSuggestion()` still
  work unchanged for both languages — they use `commitText` directly, which needs no composing
  region. Backspace removes one Latin character at a time in both languages, so the user clears the
  composed word letter by letter. It's fed an `InputConnection` *lambda*, not a captured reference,
  because the system swaps `InputConnection` instances between input sessions.

### The suggestion layer

`suggestion/WordTrie.kt` (pure Kotlin prefix trie, frequency-ranked) ← `suggestion/WordDictionary.kt`
(Android asset loader) ← `TanaKeyboardService.updateSuggestions()` → `ui/SuggestionBar.kt`.

- Dictionaries are gzip assets (`.dat`, not `.gz` — AGP silently decompresses `.gz` assets at build
  time), one `word<TAB>frequency` line each: `amharic_words.dat` (Hunspell am_ET, ~182k words,
  heuristic frequencies) and `english_words.dat` (FrequencyWords/OpenSubtitles full list, MIT, ~250k
  words, real corpus frequencies with common contractions reconstructed because the source tokenizer
  split "don't" into "don" + "'t"). The English asset is **regenerated by `tools/build_english_dict.py`**
  (not committed by hand) — it pulls the base frequency list, overlays proper-noun casing from a
  curated `tools/proper_nouns.txt` + downloaded name/city lists, and gzips the result.
- `WordTrie` matches **case-insensitively** (path edges keyed by `lowercaseChar()`) while storing and
  returning each word's **canonical casing** — that's how the English dictionary carries proper-noun
  capitalization ("england" typed → "England" suggested) even though lookups lowercase the prefix.
  Lookups are a bounded best-first search (each node caches its subtree's max frequency), so latency
  stays flat as the dictionary grows rather than scanning the whole subtree per keystroke.
- Amharic lookups key off the live *transliterated fidel* prefix (reverse-transliterating fidel back
  to Latin is ambiguous); Ge'ez has no case, so the case-insensitive keying is a no-op there. English
  lookups lowercase the typed prefix, then `suggestion/CasePattern.kt`'s `matchCase` reconciles the
  typed case with the word's canonical casing: an explicitly typed capital overrides ("Th" → "The";
  two+ uppercase → all caps), otherwise the dictionary's own casing is kept (proper nouns stay
  capitalized). Genuinely ambiguous homographs (march/March, us/US) get one canonical casing per
  lowercased key and bias toward lowercase unless force-listed in `proper_nouns.txt`.

### Service / UI layer

- **`TanaKeyboardService`** is the actual `InputMethodService`. It owns `isAmharic`, `shiftState`
  (`ShiftState`: OFF → SHIFT (one-shot) → CAPS_LOCK → OFF), and the two `WordComposer`s (only the
  one matching the current language is ever fed keys; every mode transition commits the active one
  first). All key handling goes through methods on the service (`onCharacter`, `onDelete`,
  `onSpace`, `onEnter`, `toggleShift`, `toggleLanguage`) rather than the UI touching
  `InputConnection` directly — on the letter layouts a keypress isn't a single `commitText`, it's a
  mutation of the active composer's buffer, and only the service can keep that state consistent.
  On the English layout, "." and "," (the only non-letter keys there) commit the word first, then
  commit directly. Case resolution from shift state happens inside `onCharacter`
  (`latin.uppercase()`/`.lowercase()`), not in the UI layer.
- **`KeyboardScreen` → `KeyRow` → `KeyComposables`** render whichever `KeyboardLayout` is active
  (`layout/AmharicLayout.kt` or `layout/EnglishLayout.kt`, both flat `KeyData` row lists). Every
  `KeyData.Character` key carries exactly one base Latin letter — there are no multi-character key
  labels; digraphs like "sh"/"ch"/"gn" only arise from two sequential single-letter keypresses whose
  concatenated buffer happens to match a family in `AmharicTable`.
  Composables never read `service.currentInputConnection` at composition time (it goes stale across
  input sessions) — they call service methods that re-fetch it fresh on each tap, and a key's corner
  preview glyph is computed live by running `Transliterator.transliterate` on the shift-resolved
  letter — the literal function the composer applies on keypress, so preview and behavior can't
  disagree by construction. Never bake preview glyphs into layout data (a former `KeyData.amharic`
  field did exactly that and drifted out of sync with actual output). The X and C keys work because
  `AmharicTable.families` contains SERA's single-letter aliases ("x" → ሸ family, "c" → ቸ family,
  sharing the digraphs' `Family` instances); "," and "." map to ፣/። via `AmharicTable.punctuation`,
  applied in the Transliterator's pass-through step and committed (not composed) by `onCharacter`.
