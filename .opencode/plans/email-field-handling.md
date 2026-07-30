# Email field: no-auto-cap verification + email-domain suggestion chips

## Goal

Two related changes for email-typed input fields (`TYPE_TEXT_VARIATION_EMAIL_ADDRESS` and `TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS`):

1. **No auto-capitalize in email fields.** The user reports the keyboard does not
   capitalize the first letter in a properly-declared email field today, and wants
   that confirmed and hardened against regressions.
2. **Email suggestion chips.** While the user types in an email field, the
   existing suggestion strip (between top toolbar icons and the key rows) shows
   three email-completion chips. The chips are domain-name suffixes, not words.

Decided behavior (from clarifying questions):

- Domain list, **stage 1** (no `@` typed yet): `@gmail.com`, `@yahoo.com`, `@outlook.com`.
- Domain list, **stage 2** (`@` already typed): `.com`, `.org`, `.net`.
- Chips appear **only after** the user types at least one character of the
  local part. Empty email field shows the toolbar icons (no chips).
- Applies in **both** layout modes (Amharic AND English). In Amharic mode, the
  email-typed flow falls back to Latin passthrough and shows email chips; the
  Amharic transliteration pipeline is bypassed while `isEmailField` is true.
- Stage-2 trigger: the moment the typed token contains `@`, the strip swaps to
  the three TLD suffixes.

Important: existing code already suppresses auto-cap for email fields (see
`NO_AUTOCAP_VARIATIONS` at `AddiyonKeyboardService.kt:125-133`; resolved in
`resolveAutoCap` at `:1260-1276` and gated in `maybeAutoCapitalize` at `:1297`).
This plan only hardens that gate against two specific gaps; it does NOT add
heuristic detection for editors that declare a plain text variation while
collecting an email (login-form heuristic detection) — out of scope per the
user's choice.

Note: AGENTS.md refers to `TanaKeyboardService` / package `com.addiyon.tanakeyboard`,
but the actual service file in the repo is
`app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
(package `com.addiyon.keyboard`). The plan references the actual file.

---

## Affected files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt` | Lifecycle hardening (point 1); typing-routing and suggestion pipeline branch for email fields (point 2) |
| `app/src/main/java/com/addiyon/keyboard/suggestion/EmailSuggestions.kt` | NEW pure-Kotlin module that computes the email chip list for a given typed token |
| `app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt` | Render email chips (display label vs commit text) — chip shows the suffix, tap commits the full text |
| `app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt` | Pass the email-chip list from the service into `SuggestionArea` (`:148-163`) |
| `app/src/main/java/com/addiyon/keyboard/composing/WordComposer.kt` | No source change. Reused as-is: `commitSuggestion(word)` already replaces the composing span with `"<word> "` (`:298-301`), which is exactly what email-chip taps need |
| `app/src/test/java/com/addiyon/keyboard/suggestion/EmailSuggestionsTest.kt` | NEW JVM unit test for the pure module |
| `app/src/test/java/com/addiyon/keyboard/...` (existing auto-cap test file) | Add a regression test asserting `fieldAllowsAutoCap == false` and `isEmailField == true` for an email-variation `EditorInfo` |

---

## Design detail

### Part 1 — harden "no auto-capitalize in email fields"

Existing code path that already works:
- `NO_AUTOCAP_VARIATIONS` includes both email variations.
- `resolveAutoCap(editorInfo)` sets `fieldAllowsAutoCap = false` for email
  variations, called from `onStartInputView` (`:1740`).
- `maybeAutoCapitalize()` early-returns when `!fieldAllowsAutoCap` (`:1297`).

Two hardening fixes (defensive, no UX change for the happy path):

a. **Also resolve on `onStartInput`, not only `onStartInputView`.** If the IME
   stays mounted across fields (an edge case where the system doesn't tear down
   the input view between two fields), `onStartInputView` may not fire for the
   new editor and `fieldAllowsAutoCap` would be stale from the prior field.
   `InputMethodService.onStartInput(editorInfo, restarting)` is the more
   universal hook (fires even when the input view is already up). Add an
   override that calls the same `resolveAutoCap(editorInfo)`. Keep
   `resolveAutoCap` in `onStartInputView` too (it's needed for the very first
   mount). It's idempotent — both write the same two booleans from the same
   `EditorInfo` bits.

b. **Reset armed shift on entering an email field.** Today `onStartInputView`
   resets the per-language composers (`:1732-1734`) but never touches
   `shiftState`. If the user armed `ShiftState.SHIFT` (or `CAPS_LOCK`) in the
   prior text field, the very next letter typed in the email field would come
   out uppercase via `onCharacter` (`:1355`). Fix: in `onStartInputView`, right
   after `resolveAutoCap(...)` at `:1740`, add `if (isEmailField)
   resetShift()` (`resetShift()` lives at `:1224-1226`). Does NOT touch the
   user's manual shift in non-email fields.

These two fixes are the entire scope of point 1.

### Part 2 — email-domain suggestion chips

#### 2.1  Email typing routing

In a properly-declared email field, the user types Latin, digits, `@` and `.`
to build an address. The current code reaches either `amharicComposer`
(when `isAmharic`) or `englishComposer`; for Amharic mode that runs the
SERA→fidel pipeline, which is wrong in an email field.

Change `onCharacter` (`:1341-1376`) so that when `isEmailField` is true:

- Letter / digit typing routes through `englishComposer.onCharacter(output)`
  **regardless of `isAmharic`** (i.e., email fields use the Latin composer
  exclusively). This keeps `output` Latin in the composing region. No fidel
  transliteration in email fields even when the user has Amharic mode selected.
- `@`, `.`, and digits are folded INTO the composing region instead of being
  treated as word-terminating punctuation. Concretely, expand the
  word-character predicate used inside `onCharacter` (today
  `isWordCharacter(output)` at `:1386`, delegated to
  `isComposingWordCharacter`) to additionally return `true` for `@`, `.`, and
  ASCII digits when `isEmailField`. This lets the composing region cover the
  whole `local@domain` token, so a chip tap can replace that whole span with
  the completed address.
  - Implementation choice: rather than mutate the shared predicate (which the
    comment block at `:1378-1385` documents carefully — the apostrophe /
    backtick carve-outs are load-bearing for Amharic), add an
    `isEmailWordCharacter(output)` predicate and select between them in
    `onCharacter` based on `isEmailField`. Keeps the Amharic contract intact.
- Backspace (`onDelete`, `:1431-1451`) already calls
  `activeComposer.onBackspace()` first, which shrinks one Latin char at a
  time. With `@` and `.` folded into the composing buffer, backspace within an
  email field just walks back through the buffer — natural. No change to
  `onDelete` is required (verify with an instrumented-path test).
- `onSpace` (`:1499+`) still calls `activeComposer.commit()` then a space.
  This finalizes the email address on the space after the user accepts it. No
  change needed; verify.
- `onEnter` similar.

#### 2.2  Suggestion strip vs strip behavior

State already used by the UI:
- `var suggestions: List<String> by mutableStateOf(emptyList())` (`:336`)
- `var suggestionsArePredictions: Boolean by mutableStateOf(false)` (`:346`)
- `publishSuggestions(value, arePredictions)` (`:594-597`)
- `SuggestionArea(suggestions, isAmharic, isPredictions, onTap = onSuggestionTapped(word), …)` wired from `KeyboardScreen.kt:148-163`.

For email chips the **display label** (what the chip shows) and the **commit
text** (what gets sent to the field on tap) differ:

- Stage 1 — chips show `"@gmail.com"`, `"@yahoo.com"`, `"@outlook.com"` but
  commit `"john@gmail.com"`, `"john@yahoo.com"`, `"john@outlook.com"`.
- Stage 2 — chips show `".com"`, `".org"`, `".net"` but commit
  `"john@mycomp.com"`, `"john@mycomp.org"`, `"john@mycomp.net"`.

`WordComposer.commitSuggestion(word)` already does `commitText("$word ", 1)`
which replaces the active composing region with `word` plus a trailing space —
exactly what we want for the commit text. So no `WordComposer` change is
needed; we just need the strip's `onTap` to invoke
`onSuggestionTapped(commit)` with the *commit* text, not the *display* text.

Introduce a small public model in the new `EmailSuggestions.kt`:

```
data class EmailChip(val display: String, val commit: String)
```

Add a parallel service-side state:

```
var emailSuggestions: List<EmailChip> by mutableStateOf(emptyList())
val isEmailSuggestionsActive: Boolean  // true when email field + composer active
```

When `isEmailField` is true, `updateSuggestions()` short-circuits BEFORE the
dictionary branch (`:469-513`) and instead:

1. Reads the in-progress email token from `englishComposer.raw` (the local
   part + `@` + domain prefix the user has typed so far, since the routing fix
   in 2.1 keeps the whole email in the composer).
2. Calls `EmailSuggestions.emailChipsFor(token)` (pure function, see 2.3).
3. Publishes the result into `emailSuggestions`, clears `suggestions` (and
   sets `suggestionsArePredictions = false`), so the strip shows email chips
   instead of word chips.
4. When `englishComposer.isComposing == false` → empty email chips → strip
   falls back to the toolbar icons (per "chips appear only after ≥1 char").

When `isEmailField` is false → `updateSuggestions` unchanged; reset
`emailSuggestions = emptyList()` on the next `onStartInputView` /
`onStartInput` after the email resolver runs.

`onSuggestionTapped(commit: String)` stays as-is (`:1610-1614`) — the strip now
invokes it with the chip's `commit` payload (see 2.4 below).

#### 2.3  Pure module: `suggestion/EmailSuggestions.kt`

```
package com.addiyon.keyboard.suggestion

data class EmailChip(val display: String, val commit: String)

object EmailSuggestions {
    private val DOMAIN_CHIPS = listOf("@gmail.com","@yahoo.com","@outlook.com")
    private val TLD_CHIPS     = listOf(".com",".org",".net")

    fun emailChipsFor(token: String): List<EmailChip> {
        if (token.isBlank()) return emptyList()
        if (token.any { it.isWhitespace() }) return emptyList()
        return if ('@' in token) TLD_CHIPS.map { EmailChip(it, "$token$it") }
               else              DOMAIN_CHIPS.map { EmailChip(it, "$token$it") }
    }
}
```

Pure JVM code, deterministic, unit-testable:

- empty / whitespace-only token → empty chips
- no `@` → 3 stage-1 chips, commit = `"<token>@gmail.com"` etc.
- contains `@` (anywhere) → 3 stage-2 chips, commit = `"<token>.com"` etc.
- multi-`@` token (e.g. `a@@b`) is still routed to stage 2 (any `@`
  suffices). The committed text would be malformed (`"a@@b.com"`), but that's
  fine — the user typed it; we don't validate.

#### 2.4  UI: render and tap email chips

`SuggestionArea` (`SuggestionBar.kt:75-91`) currently receives
`suggestions: List<String>` plus an `onTap: (String) -> Unit`. It picks between
`AmharicSuggestionStrip` and `EnglishSuggestionStrip` by `isAmharic`. For email
chips we need a third branch that renders chips from `List<EmailChip>` (display
label) and routes the tap to `onTap(commit)` (NOT display).

Minimal change:

- Add an optional `emailSuggestions: List<EmailChip> = emptyList()` parameter
  to `SuggestionArea`.
- Tighten the empty-toolbar-icons branch: `else if (suggestions.isEmpty())`
  at `:121` becomes `else if (suggestions.isEmpty() && emailSuggestions.isEmpty())`,
  so the toolbar icons only render when there are NEITHER word chips NOR
  email chips.
- In the `else` branch (lines `:131-140`) — when not voice mode and not empty
  — check `if (emailSuggestions.isNotEmpty())` FIRST
  → render `EmailSuggestionStrip(emailSuggestions, onTap)`.
  Otherwise fall through to the existing Amharic/English strips as today.
- Add a small `EmailSuggestionStrip` composable (mirroring
  `EnglishSuggestionStrip`'s three-slot equal-width layout, but drawing each
  chip's `display` and calling `onTap(chip.commit)`).
- `KeyboardScreen.kt:148-163` passes `service.emailSuggestions` through. The
  existing `onTap = service::onSuggestionTapped` works unchanged because the
  tap now passes the chip's *commit* text, which `commitText`-replaces the
  composing region per the existing `WordComposer.commitSuggestion` semantics.

---

## Tests

### JVM unit tests (no emulator)

1. `EmailSuggestionsTest` (new file):
   - empty token → `[]`
   - whitespace-only token → `[]`
   - `"john"` → 3 stage-1 chips; display `"@gmail.com"` etc; commit
     `"john@gmail.com"` etc.
   - `"Jo"` (mixed-case local part) → stage-1, commit `"Jo@gmail.com"` (case
     preserved, NOT lowercased).
   - `"john@"` → stage-2, display `".com"`, commit `"john@.com"`.
   - `"john@gm"` → stage-2, display `".com"`, commit `"john@gm.com"`.
   - `"john@mycomp"` → stage-2, display `".com"`, commit `"john@mycomp.com"`.
   - `"john@mycomp.co"` already includes `.co` — stage-2 triggered (still has
     `@`), chip display `.com`, commit `"john@mycomp.co.com"`. (Documenting the
     naive-append behavior; we do NOT merge/replace existing suffixes.)
   - `"a b"` → `[]` (whitespace terminates the email token).
2. Auto-cap regression test (add to an existing auto-cap-related test file):
   construct an `EditorInfo` with
   `inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_EMAIL_ADDRESS`, feed to
   the autocab resolver (will need to expose it as `internal` or test via a
   thin fixture), assert `fieldAllowsAutoCap == false` and `isEmailField ==
   true`. This locks in the existing gate so a future refactor can't silently
   re-enable auto-cap in email fields.

### Instrumented tests (emulator, if reachable)

3. Email field fixture:
   - launch an `EditText` configured with `InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS`.
   - type `"j"` → assert three chips display `"@gmail.com"`, `"@yahoo.com"`,
     `"@outlook.com"`.
   - tap the first chip → assert the field now contains `"j@gmail.com "`.
   - clear and type `"j@ex"` → assert three chips display `".com"`, `".org"`,
     `".net"`.
   - tap the first chip → assert the field contains `"j@ex.com "`.
   - type into the field `"a"`, never pressed shift — first letter MUST NOT
     come out uppercase (auto-cap-not-applied regression for point 1).

(Given the complexity of headless IME instrumented tests in this repo, the
pure-JVM tests for `EmailSuggestions` are the must-have; the instrumented
fixture is a stretch and may be deferred.)

---

## Step-by-step breakdown

1. Write `EmailSuggestions.kt` with `EmailChip` and `emailChipsFor(token)`.
   Write `EmailSuggestionsTest.kt` and run it: `./gradlew
   testDebugUnitTest --tests "com.addiyon.keyboard.suggestion.EmailSuggestionsTest"`.
2. Add `emailSuggestions: List<EmailChip>` mutableStateOf to
   `AddiyonKeyboardService` and a `isEmailSuggestionsActive` boolean.
3. Add `onStartInput(editorInfo, restarting)` override that calls
   `resolveAutoCap(editorInfo)` (mirrors the `onStartInputView` call at `:1740`).
   Keep `resolveAutoCap` in `onStartInputView`.
4. In `onStartInputView`, immediately after `resolveAutoCap(editorInfo)` at
   `:1740`, add: `if (isEmailField) resetShift()`. (Point 1 hardening #2.)
5. Expand `onCharacter` (`:1341-1376`):
   - when `isEmailField`, route to `englishComposer.onCharacter(output)`
     regardless of `isAmharic`;
   - extend the "is this a word character" check for the email-field path to
     additionally accept `@`, `.`, and ASCII digits, so they fold into the
     composing region.
6. Modify `updateSuggestions` (`:469-513`): short-circuit at the very top when
   `isEmailField` — compute `emailChipsFor(englishComposer.raw)` and set
   `emailSuggestions` accordingly (and clear `suggestions`). On exit
   (switching to a non-email field), reset `emailSuggestions = emptyList()`.
   Keep behavior unchanged for non-email fields.
7. Update `SuggestionBar.kt`:
   - add `emailSuggestions: List<EmailChip> = emptyList()` to `SuggestionArea`;
   - tighten the `else if (suggestions.isEmpty())` toolbar-icons branch at
     `:121` to also check `emailSuggestions.isEmpty()`;
   - in the `else` branch, when `emailSuggestions` is non-empty, render the
     new `EmailSuggestionStrip` (three equal slots, displays `chip.display`,
     tap invokes `onTap(chip.commit)`).
8. Update `KeyboardScreen.kt:148-163` to pass `service.emailSuggestions` to
   `SuggestionArea`.
9. Add the auto-cap regression JVM test (construct the email EditorInfo and
   assert `fieldAllowsAutoCap == false`). Either expose `resolveAutoCap` as
   `internal` or extract it to a small invariant test fixture.
10. Run: `./gradlew compileDebugKotlin` (fast gate), then
    `./gradlew testDebugUnitTest` (full JVM suite, since shared paths changed),
    then `./gradlew installDebug` and manual smoke test on emulator: open
    Gmail's compose "To:" field, type a local part, tap an `@gmail.com` chip,
    then start over, type `name@cust`, tap `.com`. Confirm no first-letter
    capital either when entering a fresh email field from a text field.

---

## Risks / open questions

- **Chip width.** `"@outlook.com"` is 11 chars and is the longest stage-1 chip.
  The existing `EnglishSuggestionStrip`'s three-equal-slots layout with
  `TextAutoSize.StepBased(min=15sp, max=16sp, ...)` is built for ~16-char
  word chips; `"@outlook.com"` should fit fine. If it doesn't, lower the
  autoSize `maxFontSize` for the email strip only.
- **Composing region spanning `@` and `.`:** The composing region with `@`
  and `.` folded in is unusual but legal. Some editors ship custom selection
  handling that may push the cursor away on a `setComposingText` call covering
  a domain dot; instrumented verification on the emulator is the only way to
  catch editor-specific regressions.
- **Stage-2 chip on an already-complete-looking token.** If the user typed
  `"john@gmail.com"`, `emailChipsFor` will append another suffix
  (`"john@gmail.com.com"`) — naive by design. If unwelcome, refine to "if the
  token already ends with one of the TLD strings, suppress stage-2 chips."
  Tradeoff: the simpler design matches the user's stated behavior ("if they
  ignore that and write @something"); refine only if user feedback comes in.
- **`isEmailField` is set only in `onStartInputView` / the new
  `onStartInput`.** Verify the flow doesn't read `isEmailField` in any other
  code path that could fire BEFORE `onStartInput` (e.g., during the keyboard
  open animation, or `onWindowShown`). A grep during implementation will
  surface any read of `isEmailField` outside `KeyRow.kt:69` and the new code.
- **Amharic layout keys in email fields.** Routing typing through
  `englishComposer` even when `isAmharic == true` means the Amharic key rows
  still display fidel-corner previews (per AGENTS: the corner preview is
  computed live from `AmharicTable.bareFormOf`) while producing Latin output —
  visually inconsistent. An alternative is to also switch the visible layout to
  `EnglishLayout` while `isEmailField`. Decision: leave the layout alone for
  now (the user's stated need is just the chips + no transliteration), and
  revisit if the visual mismatch is jarring in practice.
- **Scope cap for point 1:** the user explicitly chose "verify/fix regardless"
  rather than the heuristic-detection option (login forms using plain text
  fields would still auto-capitalize after this plan). Out of scope by explicit
  choice; logged here so it's not forgotten.

## Verification matrix

| Concern | How verified |
|---|---|
| Auto-cap suppressed in declared email fields | existing code (covered) + new JVM regression test |
| Stale `fieldAllowsAutoCap` across rebind | `onStartInput` mirror of `resolveAutoCap` |
| Armed-shift leaks from prior field into email | `resetShift()` on entering email in `onStartInputView` |
| Stage-1 chips correct | `EmailSuggestionsTest` + on-device smoke |
| Stage-2 chips correct | `EmailSuggestionsTest` + on-device smoke |
| Tap commits the full email (replaces composing span) | on-device smoke; `WordComposer.commitSuggestion` unchanged |
| Amharic mode + email field produces Latin, no transliteration | on-device smoke (instrumented test deferred) |