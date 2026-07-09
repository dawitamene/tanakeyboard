# Amharic buffer strip (stop composing raw Latin in the field)

## Context

**Problem.** In Amharic mode today the raw Latin the user types ("selam") is written
into the target field's *composing region* (underlined) via `setComposingText`, and only
swapped for the fidel (ሰላም) when the word is committed (space / enter / tapped suggestion).
If the user leaves the field or hides the keyboard **before** committing, the composing
region is finalized in place as the raw **Latin** — so English letters are stranded in the
field. (`WordComposer.finish()` uses `finishComposingText`, which locks in whatever the
region currently *shows* — the Latin — see `WordComposer.kt:159-183`.)

**Fix / intended outcome.** In Amharic mode, stop writing anything to the field until the
word is committed. Surface the in-progress buffer in a new **strip above the suggestion/
toolbar row**: left half = the Latin buffer the user is typing, right half = its live fidel
transliteration. The strip appears while composing and disappears the moment the word is
committed (space or tapped suggestion). Because nothing is ever placed in the field until
commit, the "leftover English letters" bug is impossible by construction, and an
uncommitted word simply vanishes (field stays clean) on exit or tap-away.

**Decisions (confirmed with user).**
- Strip is **Amharic-only**. English keeps its current inline underlined composing
  (it works correctly and a "Latin | fidel" split is meaningless for English).
- On uncommitted exit / tap-away: **discard** the buffer, keep the field clean (do *not*
  auto-commit the greedy reading).

## Key insight (why this is small)

`WordComposer.commit()` and `commitSuggestion()` already use `commitText(...)`, which inserts
at the caret and does **not** require an active composing region. The *only* thing that writes
the raw Latin into the field is `pushComposing()` → `setComposingText`. So if the Amharic
composer never pushes a composing region:
- typing writes nothing to the field (buffer lives only in our state / the new strip);
- `commit()` / `commitSuggestion()` still insert the fidel correctly;
- `finish()` (exit) and `abandon()` (tap-away) become clean buffer-discards → field stays clean.

## Changes

### 1. `composing/WordComposer.kt` — add a non-inline mode; simplify away `composingText`

- Replace the `composingText` constructor param with a `composesInline: Boolean = true` flag.
  `pushComposing()` already only needs `render` (for English, `composingText` defaulted to
  `render` anyway), so `composingText` is now dead once Amharic stops composing inline — remove it.
- Gate every composing-region touch on `composesInline`:
  - `pushComposing()`: `if (!composesInline) return` before `setComposingText(render(buffer...), 1)`.
  - `onBackspace()`: only call `finishComposingText()` / `pushComposing()` when `composesInline`
    (buffer still truncates regardless).
  - `finish()`: `if (composesInline) inputConnection()?.finishComposingText()` then `buffer.clear()`.
    → non-inline path is a pure discard (the exit-clean behavior).
  - `abandon()`: `if (composesInline) inputConnection()?.commitText(display, 1)` then `buffer.clear()`.
    → non-inline path is a pure discard (the tap-away behavior; English keeps its involuntary commit).
- Leave `commit()` and `commitSuggestion()` untouched — `commitText` is correct with or without a region.
- Rewrite the class KDoc: the "WHY THE LATIN IS SHOWN INLINE" section is now inverted — document
  that Amharic composes **out-of-field** into a preview strip (`raw` = Latin half, `display` =
  fidel half) and only touches the field on commit, while English still composes inline.

### 2. `TanaKeyboardService.kt` — build the Amharic composer non-inline; expose buffer state

- Construct `amharicComposer` (around `TanaKeyboardService.kt:197-206`) with
  `composesInline = false` and drop the `composingText = { it }` arg. `englishComposer`
  keeps the default (`composesInline = true`). Update the nearby comment block (:187-206).
- Add two snapshot-state fields next to `suggestions` (`:181`), same `mutableStateOf` /
  `private set` pattern the UI already subscribes to:
  ```kotlin
  var amharicBufferLatin by mutableStateOf("")  // left half; "" hides the strip
  var amharicBufferFidel by mutableStateOf("")  // right half (live transliteration)
  ```
- Populate them from the existing single choke point `updateSuggestions()` (`:237`), which
  already runs after every composer mutation, toggle, backspace, and lifecycle event. Add:
  ```kotlin
  if (isAmharic && amharicComposer.isComposing) {
      amharicBufferLatin = amharicComposer.raw
      amharicBufferFidel = amharicComposer.display
  } else {
      amharicBufferLatin = ""; amharicBufferFidel = ""
  }
  ```
  (`raw` = Latin buffer, `display` = fidel — `WordComposer.kt:96-107`.)
- No handler logic changes needed: `onSpace`/`onEnter`/punctuation (`:503-517, 609-633`) already
  call `activeComposer.commit()` → inserts fidel; `onSuggestionTapped` (`:682`) → `commitSuggestion`;
  `onFinishInputView` (`:796`) → `finish()` (now a discard for Amharic); `onUpdateSelection`
  (`:768`) → `abandon()` (now a discard for Amharic). Verify the `onUpdateSelection` guard still
  behaves: with no composing region `candidatesStart == -1`, so any selection change while the
  Amharic buffer is non-empty is treated as tap-away → `abandon()` → discard. That is correct,
  since Amharic typing no longer generates selection events. Refresh the now-stale comments on
  `onCharacter`, `onSpace`, `onUpdateSelection`, and `onFinishInputView` that describe the
  "underlined Latin" behavior.

### 3. `ui/BufferPreviewStrip.kt` — new composable

A display-only strip mirroring `SuggestionArea`'s styling (`SuggestionBar.kt:75-84`):
- `Row`, `fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.background)`.
- Two `Modifier.weight(1f).fillMaxHeight()` halves with a `1.dp` full-height-0.5f divider
  (`onSurface.copy(alpha = 0.2f)`) between them — same divider token the suggestion strips use
  (`SuggestionBar.kt:167-169`).
- Left half: the Latin, `onSurface` (optionally `.copy(alpha = 0.6f)` to read as "raw input").
- Right half: the fidel, `MaterialTheme.colorScheme.primary`, `FontWeight.Bold` — matching the
  greedy-suggestion emphasis (`SuggestionBar.kt:152-157`).
- Both `maxLines = 1`, ellipsize/auto-shrink for long words.

### 4. `ui/KeyboardScreen.kt` — mount the strip above the toolbar row

Insert as the **first child** of the stacking `Column` (immediately before `SuggestionArea(...)`
at `KeyboardScreen.kt:83`), rendered only when the buffer is non-empty so it appears/disappears:
```kotlin
if (service.amharicBufferLatin.isNotEmpty()) {
    BufferPreviewStrip(
        latin = service.amharicBufferLatin,
        fidel = service.amharicBufferFidel
    )
}
```
This places it above both the suggestion strip and its empty-state toolbar (they are the same
`SuggestionArea` composable). The keyboard grows ~40.dp while composing and shrinks back on
commit — intended, since the strip is meant to be ephemeral.

### 5. Docs

Update `CLAUDE.md` — the `WordComposer` / "WHY THE LATIN IS SHOWN INLINE" and the
`TanaKeyboardService` composing paragraphs — to describe the Amharic out-of-field preview-strip
model and the Amharic-only `composesInline = false` distinction.

## Files

- `app/src/main/java/com/addiyon/tanakeyboard/composing/WordComposer.kt` (core change + KDoc)
- `app/src/main/java/com/addiyon/tanakeyboard/TanaKeyboardService.kt` (composer ctor, state, `updateSuggestions`, comments)
- `app/src/main/java/com/addiyon/tanakeyboard/ui/BufferPreviewStrip.kt` (new)
- `app/src/main/java/com/addiyon/tanakeyboard/ui/KeyboardScreen.kt` (mount point)
- `CLAUDE.md` (architecture docs)

## Verification

- **Unit tests:** `./gradlew testDebugUnitTest`. Check `app/src/test/**` for existing
  `WordComposer` tests — any that assert `setComposingText` for the Amharic composer must be
  updated to expect **no** composing-region write and a `commitText(fidel)` only on commit.
  Transliteration/suggestion tests are unaffected (`raw`/`display` semantics unchanged).
- **Compile check:** `./gradlew compileDebugKotlin`.
- **Manual (sideload the debug APK — `./gradlew assembleDebug` auto-copies to /Users/dev/Shared):**
  1. Amharic mode, type `selam`: field stays **empty**; strip shows `selam | ሰላም`; suggestion
     strip below shows fidel readings.
  2. Press **space**: `ሰላም ` inserted into field; buffer strip disappears.
  3. Type `selam` again, then **hide the keyboard / switch fields without committing**: field is
     **clean** (no `selam`, no ሰላም) — the core bug fix.
  4. Type a word, then **tap elsewhere** in existing text: buffer strip disappears, nothing inserted.
  5. Tap a **suggestion** chip mid-word: chosen fidel word + space committed, strip gone.
  6. **Backspace** clears the strip letter-by-letter; emptying it hides the strip.
  7. **English mode** unchanged: inline underlined composing still works; no buffer strip shown.
