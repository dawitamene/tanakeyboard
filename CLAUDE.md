# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Addiyon Keyboard is an Android IME (`InputMethodService`) that provides an Amharic (Ge'ez script)
keyboard with Latin-to-Fidel transliteration, plus a standard English layout. UI is built entirely
in Jetpack Compose. `MainActivity` is just a settings/test harness (buttons to open keyboard
settings and a text field to try typing) — the real product is `AddiyonKeyboardService`.

## Planning

When the user asks to create a plan or plan something, write a detailed, structured plan as a Markdown file in `plans/` (e.g. `plans/feature-name.md`). Do NOT execute or write any code — only the plan file. The plan should cover the approach, affected files, step-by-step breakdown, risks, and open questions.

## After every code change

Always do the relevant verification after making changes:

1. Run the relevant test(s) for the changed behavior. Prefer a focused test class/target when possible; broaden the test run when shared behavior is affected.
2. When adding a new feature, add or update tests that cover that feature.
3. Install and run on emulator: `./gradlew installDebug`
4. Generate timestamped APK in `/Users/dev/Shared`: `./gradlew assembleDebug`

## Commands

- Build debug APK: `./gradlew assembleDebug`
- Install on emulator/device: `./gradlew installDebug`
- Run JVM unit tests (no emulator needed): `./gradlew testDebugUnitTest`
- Run a single unit test class: `./gradlew testDebugUnitTest --tests "com.addiyon.keyboard.transliteration.SomeTest"`
- Compile-only check (fast, no test run): `./gradlew compileDebugKotlin`
- Instrumented tests (needs emulator/device): `./gradlew connectedAndroidTest`

Note: `app/build.gradle.kts` has an `assembleProvider` hook that copies the built APK to
`/Users/dev/Shared` with a timestamped filename after every assemble — this is a local
convenience for sideloading onto a test device, not something to remove or "fix".

To actually try the keyboard: install the debug APK, enable "Addiyon Keyboard" in
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
  language; both compose inline (the rendered buffer is pushed into the `InputConnection`'s
  composing region on every keystroke, underlined in the field), differing in `render` and a
  `discardOnExit: Boolean` flag. English (`discardOnExit = false`, the default): `render` is
  identity, the buffer IS the composing text, and leaving the word (`finish()` on input-view hide,
  `abandon()` on cursor tap-away) finalizes whatever the region shows. Amharic (`discardOnExit =
  true`): `render` = `Transliterator.transliterate`, so the field live-shows the *greedy fidel
  reading* — which is why `amharicSuggestions` drops its first entry (the greedy reading) from the
  strip, offering only alternate readings and completions — and the in-field word is *tentative*:
  `finish()`/`abandon()` DELETE it from the field rather than committing, so nothing ever commits
  except space/enter/punctuation (`commit()`) or a tapped chip (`commitSuggestion()`). This
  discard-on-exit rule is the load-bearing part — finalize-on-exit semantics for Amharic were tried
  and reverted twice as "auto commit" (words the user never accepted got locked in by hiding the
  keyboard or moving the cursor). The one exception: a word adopted from already-committed field
  text by `resume()` (see the `resumed` flag) is *restored* on exit, never deleted — otherwise
  walking the caret back to an old word and tapping away would destroy committed text. The raw
  Latin (`raw`) is also mirrored to a preview strip above the suggestion row
  (`ui/BufferPreviewStrip.kt`, fed by `AddiyonKeyboardService.amharicBufferLatin`). Backspace
  removes one Latin character at a time in both languages, so the user clears the composed word
  letter by letter. It's fed an `InputConnection` *lambda*, not a captured reference, because the
  system swaps `InputConnection` instances between input sessions.

### The suggestion layer

`suggestion/WordTrie.kt` (pure Kotlin prefix trie, frequency-ranked) ← `suggestion/WordDictionary.kt`
(Android asset loader) ← `AddiyonKeyboardService.updateSuggestions()` → `ui/SuggestionBar.kt`.

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

- **`AddiyonKeyboardService`** is the actual `InputMethodService`. It owns `isAmharic`, `shiftState`
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
