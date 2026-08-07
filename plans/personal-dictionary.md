# Personal Dictionary — Auto-Learning Emails & Frequent Words

## Context

Users expect the keyboard to remember what they have typed and offer it next time without any settings toggle.

Requested examples:

- `dawitamene@gmail.com` typed once → typing `dawit…` later should surface `dawitamene@gmail.com`.
- Frequently typed names / company names should surface as completions.

Constraint: **no option or setting** — always-on, zero UI.

## Current State (grounded in repo)

| Area | What exists | Gap |
|------|-------------|-----|
| `suggestion/PersonalDictionary.kt` | Frequency-ranked `LinkedHashMap<String,Int>` capped at 512 entries, `learn(word)` rejects whitespace/multi-token strings, `completions(prefix,limit)` and `ranked()` are case-insensitive prefix scans sorted by `frequency desc → alpha`, `emailAddresses()` filters keys containing `@`, `encode()`/`decode()` is `count<TAB>word` per line. | Ready for reuse. |
| `ui/settings/KeyboardPrefs.kt` | `KEY_PERSONAL_DICTIONARY` persisted as an opaque `String` (`readString`/`setPersonalDictionary`, capped at `MAX_OPAQUE_PREF_LENGTH = 65536`). | Ready. |
| `AddiyonKeyboardService.kt` | `lateinit var personalDictionary` init in `onCreate()` via `PersonalDictionary.decode(KeyboardPrefs.personalDictionary(this))`. `rememberWord(word)` is wired as `TypingController(..., onWordCommitted = ::rememberWord)`, skips `isPrivateField` and `isNumberMode`, calls `learn` + `setPersonalDictionary(encode())`. `englishSuggestions()` prepends `personalDictionary.completions(typed, ENGLISH_SUGGESTION_LIMIT)` (with `matchCase`). `schedulePredictionComputation()` for English merges `personalDictionary.ranked(limit)` into empty-context predictions. `updateSuggestions()` for `isEmailField` calls `EmailSuggestions.emailChipsFor(token, personalDictionary.emailAddresses())` and caps to 3 chips. | **Amharic suggestions never query the personal dictionary.** Typing an email in a non-email field does not surface it — email completions are gated on `isEmailField`. Frequencies are not surfaced for Amharic `rankAmharic` tier. |
| `suggestion/EmailSuggestions.kt` | Pure `emailChipsFor(token, savedEmails)` — `saved` filtered by `startsWith(token, ignoreCase)`, merged with static `DOMAIN_CHIPS` / `TLD_CHIPS`, capped at 3. | Caller controls `savedEmails` source; non-email fields never pass personal emails. |
| `composing/TypingController.kt` + `composing/Composition.kt` + `composing/ResumableWord.kt` | Every committed word flows through `Composition.commit()` / `replaceWith()` → `onCommit(raw, display)` → `resumedWords.remember` + `onWordCommitted(display)` → `rememberWord`. Word boundaries are `onSpace`, `onEnter`, punctuation in `onCharacter`, and `onSuggestionTap`. Cursor-relative only, no absolute offsets. `ResumableWord` extracts the word ending at caret for adoption / caret-word suggestion reads (`latinWordEndingAtCursor`, `amharicWordEndingAtCursor`, `emailWordEndingAtCursor`). | Learning path is complete — if `rememberWord` allows it, any word that reaches the field is learned. |
| `suggestion/SQLiteDictionary.kt` + `CandidateRanker.kt` | Static corpus completions + n-gram boost; Amharic path uses transliteration → `rankAmharic`, English uses `rankByContext` + fuzzy. | Need to decide tier for personal completions — must not break `EXACT > LITERAL > COMPLETION > FUZZY` tiering. |

## Goals

1. Typing an email once (e.g. `dawitamene@gmail.com`) makes it reappear as a suggestion when the user later types its prefix, in email fields **and** when the user starts typing an email-like prefix in a normal field.
2. Frequently typed ordinary words (names, company names) surface at the top of the completion strip in the language they were typed in.
3. Behaviour is automatic, has no settings UI, respects existing privacy gate (`isPrivateField`, `isNumberMode`).
4. No absolute-offset composing code; all editor writes stay cursor-relative.

## Non-Goals

- No UI to view/clear the personal dictionary (follow-up if requested).
- No cloud sync of personal words.
- No per-app or per-field allow-list.
- No change to the single-character key label rule or to `AmharicTable` / `Transliterator` matching.

## Design

### Learning (write path)

Keep `rememberWord` as the single write entry point. No new call sites.

Filtering stays inside `PersonalDictionary.learn`:

- `trim` + reject empty or any `isWhitespace()` — already prevents multi-word learns.
- Keep `@` as part of a single token (existing) — emails are one word in the email profile; in Latin/Amharic profiles punctuation ends the word, so an email typed in a normal field will currently be split by `.` and `@` if the field is not classified as email. To guarantee `dawitamene@gmail.com` is learned as one key regardless of field, extend the learn gate to accept `@` + `.` tokens when they were committed via the word-boundary path. Simplest fix: when `rememberWord` receives a token containing `@`, learn it as-is even if it would have been split; the email-field path already does, the fix is to ensure the Latin profile's `rememberWord` still fires for that trailing token after punctuation boundaries (no change to `isWordCharacter` needed — the commit granularity already emits the whole token in email fields; in non-email fields we add a narrow carve-out: if the just-committed `display` contains `@`, also learn the full email reconstructed from the two `getTextBeforeCursor` reads at commit time, or keep learning as the `@`-split tail and rely on the email-strip merging below — see decision in step 2).

Concurrency: `rememberWord` runs on the main thread after `commit()` succeeds (`accepted == true`). `encode()` + `setPersonalDictionary` is synchronous `SharedPreferences.apply()` inside `writeSafely` — safe for per-word writes (512 entries ≈ ≤ 10 KB). No background migration needed.

Privacy: existing skip conditions remain:

- `isPrivateField` (password, incognito) → do not learn.
- `isNumberMode` → do not learn.
- Optionally also skip when `isLowRam && isEmergencyMode` — not needed; `enterEmergencyMode` does not gate `rememberWord` today, keep as-is.

Frequency cap: `MAX_WORDS = 512` with FIFO eviction (`remove(keys.first())`). Keep — bounds pref size and scan cost.

### Suggestion (read path)

Three strip contexts:

#### 1. Email field (unchanged shape, completed wiring)
In `updateSuggestions() → isEmailField` branch:
- Already passes `personalDictionary.emailAddresses()` to `EmailSuggestions.emailChipsFor`.
- Keep. Verify distinct + case-insensitive `startsWith` + `it != token` filtering already prevents echoing the exact typed token.

#### 2. English (Amharic == false, isEmailField == false)
- **Completions** (`englishSuggestions`): existing `merged` list seeded with `personalDictionary.completions(typed, limit)` before `rankByContext` pool. Keep prepending so personal hits win over corpus. Ensure `matchCase(typed, word)` preserves user's casing.
- **Predictions** (empty `typed`): existing path merges `personalDictionary.ranked(limit)` before `topFrequentWords` fallback. Keep. Consider filtering `ranked` for the current language tag to avoid surfacing an Amharic fidel string in an English prediction strip — `PersonalDictionary` stores raw display forms, so a Latin-vs-Fidel check (`isEthiopic`) can partition if needed.
- **Email-as-completion in a non-email field**: add a small additive step before returning `merged`: if `typed` contains `@` or its prefix matches a stored email's prefix (e.g. `dawit` → `dawitamene@gmail.com`), include `personalDictionary.emailAddresses().filter { it.startsWith(typed, ignoreCase) }` mapped through `matchCase` into `merged` (distinct, capped). This satisfies "type email anywhere, see it again" without showing email chips in the toolbar-style prediction row.

#### 3. Amharic (`isAmharic == true`)
- **Gap**: `amharicSuggestions` currently never consults `personalDictionary`.
- Fix: inside `amharicSuggestions`, after building `readings` / `readingFrequencies`, query `personalDictionary.completions(latin_or_fidel, limit)` appropriate to the script. Two options; pick (a):
  - (a) Query with the raw Latin buffer (`latin`) — covers Latin-typed personal entries that happen to be Latin strings while in Amharic mode (rare). Better: also query with the fidel readings prefix — `personalDictionary` stores fidel display forms for Amharic commits, so filtering by `startsWith(fidelPrefix)` directly finds them. Implement as: collect `personalDictionary.ranked(limit = AMHARIC_SUGGESTION_LIMIT)` filtered by `it.startsWith(reading, ignoreCase == false for fidel)` for each reading, union, or add a `fidelCompletions` helper mirroring `completions` but case-sensitive for Ethiopic.
  - Tiering: insert personal Amharic hits at the **COMPLETION** tier but with a high `frequencyScore` so they outrank corpus completions. Simplest: prepend personal fidel words to the `scored` list with `COMPLETION_BONUS` or higher, or merge them into the `completionsForPrefix` map before `rankAmharic` consumes it. Do not inject them as exact-reading wins unless they equal a reading (then they are exact words and should score as `EXACT_WORD_BONUS`).

Ranking interaction: personal entries should not break the LITERAL tier rule (greedy reading pinned at `LITERAL_BONUS` when not an exact word). Keep `greedyIsExactWord` computation over the static dictionary only; personal completions sit in the completion tier, so they appear right after the greedy literal, which is the desired UX (user's own completions next).

### Data Model

No schema change. `PersonalDictionary` stays a `LinkedHashMap<String,Int>` encoded as `count<TAB>word` lines. Existing prefs key reused. Migration is decode of existing value → same type.

If email-in-non-email-field learning needs the full token before split, add a one-line reconstruct helper in `rememberWord` / commit path, but prefer to keep the model as single tokens with `@` (simplest, no new encoding).

## Affected Files

- `suggestion/PersonalDictionary.kt` — may add an overload `completionsFidel` or a `rankedFidel` that is case-sensitive for Ethiopic; otherwise no structural change. Keep `learn` whitespace rule. Consider raising `MAX_OPAQUE_PREF_LENGTH` guard usage but value unchanged.
- `AddiyonKeyboardService.kt` — `rememberWord` (email-token carve-out if needed), `updateSuggestions` (email-field branch already done), `englishSuggestions` (add email-in-normal-field merge), `amharicSuggestions` (add personal fidel merge), `schedulePredictionComputation` (optional language-partitioned `ranked` filtering), `onCreate` decode stays.
- `suggestion/EmailSuggestions.kt` — no change unless we reuse it for non-email-field email chips; could extend a helper `emailCompletionsFor(token, savedEmails)` used by English completions.
- `composing/*` — no change (cursor-relative invariant preserved; adoption already skips interior carets).
- `ui/settings/KeyboardPrefs.kt` — no change (key exists). Do not add a preference for this feature per spec.
- Tests — `suggestion/PersonalDictionaryTest.kt`, `composing/TypingControllerTest.kt`, service-adjacent suggestion tests, plus a new `PersonalDictionaryIntegrationTest` / `EmailInNormalFieldTest` covering the gaps.

## Step-by-Step Breakdown

1. **Inventory & repro**
   - Confirm existing `rememberWord` is called for each boundary listed above (add a JVM probe via `TypingController` + `FakeEditor`).
   - Write a failing JVM test: type `dawitamene@gmail.com` + space via `TypingController`, inspect `PersonalDictionary.emailAddresses()` contains it; then simulate `englishSuggestions("dawit", ...)` and assert it surfaces.

2. **Learning fix (if needed) for non-email fields**
   - Reproduce typing `dawitamene@gmail.com` in a Latin (non-email) field: because `TypingProfile.isWordCharacter` for Latin splits on `@` and `.`, the controller will commit punctuation-separated segments. Verify what `rememberWord` currently receives.
   - If the full email is not learned as one entry, add a minimal reconstruct in `rememberWord` / `TypingController.onWordCommitted` that, when `display` contains `@`, reads the full email token via `ResumableWord.emailWordEndingAtCursor` (or `editorGateway.textBeforeCursor` + current committed word) and learns that token. Keep cursor-relative reads, no absolute offsets. If the existing email-field path already covers the primary use (user types email inside an email field), this step may be reduced to "document that non-email-field emails are still suggested via the English email-merge below once at least the local-part is learned."

3. **Wire Amharic personal completions**
   - In `amharicSuggestions`, after `directCompletions` cache construction, collect `personalDictionary.ranked(prefix = reading, ...)` for each reading (case-sensitive for Ethiopic). Union, deduplicate, and inject into `completionsForPrefix` result or into a new `personalCompletions` list scored at completion tier.
   - Ensure the per-word caches (`amharicSuggestionCache`, `composingNgramBoost`) still key on `latin` alone — personal dict scan is ~512 × prefix check, cheap enough to run on every keystroke without caching separately.

4. **Wire English email-in-normal-field emails**
   - In `englishSuggestions`, before `rankByContext` pool merge, compute `emailPrefills = personalDictionary.emailAddresses().filter { it.startsWith(typed, ignoreCase = true) && it != typed }.take(ENGLISH_EXACT_LIMIT)` and insert them into `merged` (with `matchCase`) ahead of corpus pool. Distinct-check keeps the strip at `ENGLISH_SUGGESTION_LIMIT`.

5. **Prediction strip hygiene**
   - In `schedulePredictionComputation` English branch, partition `personalDictionary.ranked` by script (`isEthiopic` check) so fidel entries do not appear in English predictions and vice versa. Alternatively add `PersonalDictionary.rankedForLanguage(isAmharic: Boolean)`.

6. **Amharic transliteration edge**
   - Decide bare-vowel / labialized handling for personal fidel entries: store fidel exactly as committed (`topAmharicCandidate`), lookup is exact fidel prefix, no transliteration round-trip needed. This matches the "no reverse-transliteration" rule for Amharic adoption.

7. **Tests & thresholds**
   - Extend `PersonalDictionaryTest` for email-in-normal-field merge and Amharic fidel completion.
   - Add `TypingControllerTest` cases: word boundary commits trigger `onWordCommitted`; private field does not learn; backspace-then-retype updates frequency.
   - Add a service-level JVM test (or `connectedAndroidTest` if needed) for `englishSuggestions` / `amharicSuggestions` with a seeded `PersonalDictionary`.

8. **Cleanup**
   - Run `./gradlew testDebugUnitTest --tests "*PersonalDictionaryTest*" --tests "*TypingControllerTest*"` and `./gradlew compileDebugKotlin`.
   - Manual install: `./gradlew installDebug` and type `dawitamene@gmail.com` once, verify second prefix surfaces as chip and tap replaces correctly (cursor-relative `commitText`).

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Learning garbage (single letters, numbers, punctuation fragments) inflates the dictionary and pollutes completions. | `learn` already rejects whitespace; add minimum length guard (`word.length >= 2` and at least one letter) or rely on frequency ranking to keep them low. Do not over-filter emails (they contain `.`/`@`). |
| Privacy: personal words leak into screenshots / predictions after the user expects incognito. | Gate on `isPrivateField` and `isNumberMode` already; audit that `isEmailField` emails still respect private-field skip. No new file or export. |
| Dictionary grows without bound. | `MAX_WORDS = 512` FIFO eviction bounds memory and pref size; 512 × ~20 chars ≈ 10 KB well under `MAX_OPAQUE_PREF_LENGTH`. |
| Case divergence: `Dawit` learned but `dawit` prefix query must match. | `completions` already uses `ignoreCase = true`; `amharicSuggestions` fidel path intentionally uses case-sensitive (Ethiopic has no case). |
| Flash / flicker when personal completions arrive async. | Reuses existing `activeCompletionKey` guard and chip-carry logic in `updateSuggestions`; personal completions are synchronous (`LinkedHashMap` scan) so they do not add async latency. |
| Cross-language leakage (Latin personal words in Amharic strip). | Partition prediction/completion merges by script; test with mixed-language typing. |
| Editor personalities that misreport `getTextBeforeCursor`. | All reads stay cursor-relative and optional (`optional = true`); suggestion fetch degrades to corpus-only when the read is null. |

## Open Questions

- Should an email typed in a non-email field be learned as one token or is "email-field-only" learning sufficient for the primary use case? Decision drives step 2 complexity. Recommend ship with reconstruct-if-`@` so the spec example works in any field.
- Do we want frequency decay or just monotonic counts? Current `counts[value] += 1` is fine for 512 entries; decay is a later optimization.
- Should company names typed once immediately outrank corpus completions, or only after 2+ commits? Current `completions` sorted by `frequency desc → alpha`, so a single learn (`count = 1`) still appears but alphabetically among other `1`s; boosting `count` to `2` on first learn would make single learns more prominent — probably not needed.
- No settings toggle is requested — confirm product does not need a "Clear personal dictionary" action (can be follow-up).

## Verification

- Focused: `./gradlew testDebugUnitTest --tests "com.addiyon.keyboard.suggestion.PersonalDictionaryTest" --tests "com.addiyon.keyboard.composing.TypingControllerTest"`
- Broader: `./gradlew testDebugUnitTest` and `./gradlew compileDebugKotlin`
- Manual: `./gradlew installDebug`, type email and frequent word, verify second typing surfaces chip, tap commits via cursor-relative `commitText` and does not reproduce absolute-offset cursor bugs.
