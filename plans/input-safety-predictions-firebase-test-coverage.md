# Input safety, fast predictions, Firebase telemetry, and complete test-coverage plan

## Status and approval gate

This document is a plan only. No implementation, dependency, manifest, Firebase, or test
changes should begin until this plan is approved.

Approval authorizes the phases below, but three choices in
[Decisions required at approval](#decisions-required-at-approval) still need an explicit
answer because they materially change privacy behavior, build reproducibility, or the
pending-prediction UI.

## Goals

1. Make cursor moves, taps, drags, and selection changes incapable of deleting,
   replacing, joining, or otherwise changing committed editor text.
2. Remove the toolbar-to-prediction flicker after Space and make warm next-word
   predictions appear within the existing 80 ms suggestion target.
3. Add Firebase Analytics and Crashlytics through one privacy-auditable layer that
   can never receive typed text, surrounding editor text, suggestion contents, email
   data, voice transcripts, or target-app identity.
4. Turn the current test suite into a feature-complete regression system: every
   user-visible behavior has an automated happy path, boundary/failure coverage where
   automation is reliable, and an explicit physical-device/manual test where it is not.

## Repository audit snapshot

Audit date: 30 July 2026.

### Existing safeguards worth preserving

- `EditorGateway` already scopes operations to the current `InputConnection` and fails
  closed when a connection changes, rejects a write, throws, or becomes too slow.
- `WordComposer.finish()` and `WordComposer.abandon()` finalize visible composing text
  with `finishComposingText()` instead of clearing or re-committing it.
- Private fields already disable suggestions and voice, and input-type policies are
  pure Kotlin.
- Suggestion work is off the IME main thread and publishes through generation checks.
- Dictionaries and n-grams are indexed SQLite assets, preserving the low-RAM design.
- The real-IME test host already exercises an actual `InputMethodService` and can inject
  slow, null, rejected, and throwing `InputConnection` behavior.
- Release builds already use R8, resource shrinking, generated baseline/startup
  profiles, and an artifact-verification script.

### Confirmed cursor-risk path

The English cursor path currently violates the desired no-mutation invariant:

1. `AddiyonKeyboardService.onUpdateSelection()` calls
   `maybeResumeWordAtCursor()` when no composer is active.
2. `maybeResumeWordAtCursor()` reads the committed word around the caret and calls
   `EditorGateway.setComposingRegion()`.
3. It then calls `englishComposer.resume()`, which immediately calls
   `setComposingText()` and writes the whole word back to the editor.
4. Those writes generate more asynchronous/re-entrant selection callbacks.
5. `EditorToken` validates the input session and connection identity, but not the
   selection generation. A callback/read that was valid for one caret position can
   therefore act after a later caret move on the same connection.

That means a tap which should be observational can perform multiple editor mutations.
Host editors differ in composing-span behavior and callback ordering, so a stale region
or composing replacement can include or overwrite an adjacent space. Committed Amharic
normally avoids this path because committed Fidel is not automatically resumed, which
also explains why the issue is most visible in English.

### Confirmed prediction flicker and latency causes

- `updateSuggestions()` publishes an empty list immediately at an empty word buffer,
  then schedules predictions asynchronously.
- `SuggestionArea` maps that empty list directly to the toolbar. When results arrive,
  predictions replace it, producing the exact toolbar -> predictions flash.
- `onSpace()` already documents that some host editors do not expose a just-committed
  space synchronously. Nevertheless, `updateSuggestions()` re-reads the editor after
  Space. The first context can be empty and a later `onUpdateSelection()` callback may
  be what finally triggers a valid prediction.
- Completion, fuzzy matching, and next-word prediction share one single-thread,
  background-priority executor. Clearing the queue cannot preempt a completion/fuzzy
  task that is already running, so a Space prediction can wait behind obsolete work.
- `composingNgramBoost` and `composingPredictionCasing` are populated and cleared but
  never read. The intended per-word cache therefore does not exist in practice, and
  each prefix repeats editor reads and n-gram work.
- The dictionary and n-gram facades share one `SQLiteLanguageStore`, but the load path
  registers two ready callbacks and refreshes twice, causing avoidable cancel/requeue
  churn.
- A warm local query spot-check was approximately 0.1 ms for vocabulary ID lookup and
  0.9 ms for the indexed successor query. This is not a phone benchmark, but it points
  to orchestration and stale context—not the SQL schema—as the primary repeated delay.
- First-install copying/checksumming/opening of the approximately 13 MB English and
  37 MB Amharic databases can legitimately be slower. That cold path needs a stable
  loading state, but should not explain a delay after every Space.

### Firebase and privacy state

- Production application ID: `com.addiyon.keyboard`.
- Debug application ID: `com.addiyon.keyboard.debug`.
- No `google-services.json` exists.
- No Firebase SDK or Firebase Gradle plugin is configured.
- The manifest has no `INTERNET` permission.
- `AddiyonApp` has a local uncaught-exception logging wrapper but no remote reporter.
- `analytics.md` is stale: it proposes Crashlytics only, uses the old
  `com.addiyon.tanakeyboard` package, and names old classes and dependencies.
- `site/privacy.html`, `site/README.md`, in-app English/Amharic copy, release
  documentation, and `plans/verify-release-artifact.sh` explicitly assert that the app
  has no internet access, analytics, or crash reporting. Those claims must change
  before a telemetry-enabled build is distributed.

### Current automated-test inventory

- 51 JVM test classes with 395 `@Test` methods.
- 10 instrumentation test classes with 39 `@Test` methods.
- 7 benchmark/profile `@Test` methods.

The suite is strong in pure transliteration, ranking, emoji parsing/backspace, composer
operations, policies, layout invariants, and several real-IME smoke/fault paths. The
largest missing areas are selection-only editor invariants, asynchronous suggestion
coordination, concrete SQLite n-gram/store behavior, voice/update/review platform
controllers, full settings/localization persistence, and telemetry privacy.

## Non-negotiable acceptance criteria

### Editor-content safety

- With no keyboard action, the editor's UTF-16 text is byte-for-byte unchanged after
  any number of caret taps, range selections, selection drags, copy handles, or
  framework selection callbacks.
- `onUpdateSelection()` never calls `setComposingRegion`, `setComposingText`,
  `commitText`, `deleteSurroundingText`, or `setSelection` merely to adopt committed
  text.
- The only permitted write while leaving an already-owned active composing region is
  `finishComposingText()`, whose content-preserving behavior is covered on a real
  editor.
- Any deferred edit snapshot is scoped to the input session, selection generation,
  language, field policy, original span, and original text. It is revalidated before
  an explicit key or suggestion action can use it.
- Failed validation falls back to ordinary insertion/deletion at the live caret; it
  never guesses an old span.
- No operation from an old field, old `InputConnection`, or old selection can mutate a
  new field.

### Suggestions and performance

- A valid word boundary transitions atomically from word completions to a
  non-interactive prediction-pending surface and then to predictions. The full toolbar
  is never rendered as an intermediate frame.
- The pending surface uses stable blank prediction slots plus the mic. A subtle loading
  indicator may appear only after 120 ms; old completion chips are never left
  clickable.
- The toolbar appears only when there is no valid prediction context or a completed,
  current request returns no results.
- Warm next-word request-to-publication p95 is below 80 ms on the agreed physical
  reference phone for both English and Amharic.
- First-install database preparation is measured separately; it never blocks typing
  and never causes toolbar flicker.
- A prediction request cannot wait behind obsolete fuzzy work.
- Old completions/predictions cannot overwrite a newer language, field, caret,
  composing prefix, private-field state, or input session.
- The low-RAM/emergency paths remain bounded and functional.

### Analytics and crash reporting

- Firebase `first_open` is treated as "first use after install/reinstall," not as an
  exact Play download/install notification. Play Console remains authoritative for raw
  installs.
- Development verification is seconds-level through DebugView. Production Realtime is
  expected within minutes and covers the last 30 minutes; normal reports may still
  take up to 24 hours.
- Firebase imports exist only inside the telemetry backend/package.
- No telemetry API accepts `String`, `CharSequence`, `Bundle`, `EditorInfo`,
  `InputConnection`, arbitrary key/value maps, or exception messages from callers.
- No event is emitted from `onCharacter`, `onDelete`, `onSpace`, transliteration,
  composition, cursor/selection callbacks, email suggestions, or voice transcripts.
- All custom events are suppressed in private/password fields.
- Advertising ID collection, ad-personalization signals, Google Signals, and ad links
  are disabled. The merged release manifest must not request `AD_ID` or an AdServices
  permission.
- Crashlytics receives fatal crashes automatically only when permitted by the approved
  collection policy. Caught non-fatals use sanitized fixed categories, contain no
  original message/cause/suppressed exception, are rate-limited, and skip
  `OutOfMemoryError`.
- No Analytics or Crashlytics user ID is set.
- A controlled minified internal-test crash appears with correct source file/line
  deobfuscation and no typed/editor data.

### Test completeness

- Every behavior in `docs/final-physical-phone-test-checklist.md` is linked to an
  automated test or explicitly marked manual-only with a reason, device, and expected
  result.
- Every new bug fix begins with a failing regression test.
- Pure core packages are held to at least 90% line and 85% branch coverage after the
  initial measured baseline; thresholds are ratcheted upward, never silently lowered.
- Coverage numbers supplement the feature matrix; they do not replace behavioral
  assertions.
- No release is accepted with a skipped P0 editor, private-field, telemetry-privacy,
  prediction-state, or crash-deobfuscation check.

## Implementation phases

## Phase 0 — Freeze evidence and write failing regressions

1. Preserve the user's existing uncommitted in-app-update and UI changes. The Firebase
   work overlaps `app/build.gradle.kts`, `gradle/libs.versions.toml`,
   `MainActivity.kt`, and `AppStrings.kt`, so those files must be merged rather than
   overwritten.
2. Run the current focused and full suites to record the pre-change baseline.
3. Add a real-IME regression that:
   - seeds an English paragraph with single/multiple spaces and line breaks;
   - moves the caret repeatedly inside every word and on both sides of every boundary;
   - makes forward/reverse selections;
   - invokes no keyboard action;
   - asserts the complete text after every move.
4. Repeat the invariant for Amharic, mixed English/Amharic, apostrophes, punctuation,
   tabs, newlines, non-breaking spaces, emoji/surrogate pairs, combining marks, and a
   several-thousand-character document.
5. Extend the test host's `InputConnection` wrapper with a mutation ledger. Assert that
   selection-only sequences issue no content-mutating calls.
6. Add deterministic callback-order tests with stale composing bounds, missing
   candidates (`-1`), duplicate callbacks, out-of-order callbacks, connection swaps,
   and host-side autofill/paste replacements.
7. Add a failing Compose state-transition test proving the toolbar is currently visible
   between completions and predictions.
8. Add trace sections/timestamps for:
   - boundary/context capture;
   - queue delay;
   - n-gram query;
   - main-thread publication;
   - first database install/open.
9. Record warm/cold English and Amharic timings before optimization.

Deliverable: reproducible failing tests for both reported issues plus baseline timing,
with no behavior fix yet.

## Phase 1 — Make selection changes content-inert

### 1.1 Separate observation from mutation

Refactor `onUpdateSelection()` into a small selection coordinator/policy:

- note the new selection generation;
- update an active composer's internal caret only when the framework bounds still match
  the owned composing region;
- finish and clear an active composition when the user leaves it;
- invalidate stale suggestion/edit snapshots;
- schedule read-only context/suggestion work;
- never adopt or rewrite committed text.

Split the current `maybeResumeWordAtCursor()` responsibilities:

- a read-only "word snapshot at caret" path for selection/suggestion display;
- an explicit-action resume/edit path used only after a keyboard Delete, character,
  or suggestion tap.

### 1.2 Introduce selection-scoped editor tokens

Extend editor/session coordination with a monotonically increasing selection
generation. A snapshot must contain:

- `EditorGateway` session generation and connection identity;
- selection generation and collapsed/range selection;
- absolute word span;
- original word/token;
- language and field policy;
- whether the source was an explicit Delete or a read-only cursor observation.

Every multi-read or deferred write validates this complete token. A synchronous
selection callback during a batch invalidates remaining old-token operations.

Prefer one selection-bearing `getSurroundingText()` snapshot on API levels that support
it, with a bounded before/after plus `ExtractedText` fallback. Do not combine a caret
offset from one callback with surrounding text read at a later caret.

### 1.3 Preserve cursor-aware English editing without tap-time writes

- Extract the English/email word around the caret for read-only suggestions.
- Do not call `setComposingRegion()` or `setComposingText()` during the tap callback.
- If the next action is a character, Delete, or suggestion tap, re-read and revalidate
  the snapshot.
- For a character/Delete that intentionally resumes editing, mark the exact existing
  span as composing only after validation.
- Add a `WordComposer` adoption operation that seeds the buffer/caret for an already
  existing composing region without immediately re-pushing identical text. The first
  actual edit then publishes the changed buffer.
- For a suggestion tap on a committed word, revalidate the span and replace it in one
  explicit, batched transaction. If validation fails, do not replace an old word.
- Keep committed Amharic Fidel non-resumable except the existing explicit post-Delete
  behavior; never attempt general reverse transliteration.
- Before an explicit character mutates an already-active composer, perform the same
  full prefix/suffix/session consistency check that Delete performs today. A delayed or
  missing selection callback must not let an old buffer rewrite the new caret.

### 1.4 Harden involuntary composition exit

- Make ownership of composing start/end and expected callback generations explicit.
- Require active-composer cursor callbacks to match the already-owned composing
  start/end exactly; `moveCursor()` must never rebind the composer to a different region
  merely because a stale region has the same length.
- Replace length-only stale-callback heuristics where they can confuse equal-length
  words/regions.
- Guarantee `beginBatchEdit()` / `endBatchEdit()` balance even on reject/throw.
- Begin the editor session and clear old composers in `onStartInput()`, not only
  `onStartInputView()`, because Android may switch fields while keeping the same input
  view. Add complementary `onFinishInput()` cleanup; keep view lifecycle work in the
  view callbacks.
- Clear composer/snapshots on field switch, language change, private/numeric mode,
  keyboard hide, connection failure, and destroy.

### 1.5 Cursor regression matrix

Add focused tests for:

- repeated taps in an English paragraph;
- caret on word start/end, before/after spaces, and inside apostrophes;
- multiple spaces, tabs, line breaks, punctuation, emoji, combining sequences, and
  malformed surrogate tails;
- range selection, replacement, copy/paste, autofill, and host-side text changes;
- active composition -> inside move, outside move, range selection, and hide/show;
- rapid alternating taps across words;
- callbacks with old composing bounds and same-length words;
- rapid equal-length-word taps without the current test helper's settling delay;
- slow/null/rejected/throwing editor reads and writes;
- `setComposingRegion()` returning false, throwing, or returning true without actually
  applying a span;
- new field/new connection arriving during a read;
- same-view field switches, `restartInput`, and `onStartInput()` without a matching
  `onStartInputView()`;
- English, Amharic, email, URI, password, number, and phone fields;
- classic `EditText` and a Compose/custom-editor host;
- deterministic randomized selection sequences using a recorded seed;
- no mutation calls when no keyboard action occurred.

Deliverable: selection-only actions are provably content-inert while explicit mid-word
editing and suggestion replacement still work.

## Phase 2 — Make next-word predictions fast and flicker-free

### 2.1 Replace loosely coupled lists/flags with one UI state

Introduce a sealed `SuggestionUiState`, with states equivalent to:

- `Toolbar`;
- `LoadingLanguage`;
- `LoadingPredictions`;
- `WordCompletions`;
- `NextWordPredictions`;
- `EmailSuggestions`;
- private/disabled and voice states where appropriate.

The service publishes a single immutable state instead of coordinating
`suggestions`, `suggestionsArePredictions`, `emailSuggestions`, and loading booleans
independently.

`SuggestionArea` renders the state in this order, so loading can never be hidden behind
the empty-list toolbar branch. Pending prediction slots are non-interactive and old
completion chips disappear atomically.

### 2.2 Use deterministic context at the boundary

- `onSpace()` already reads text before inserting the space for sentence case. Expand
  that same bounded read to the n-gram window and derive prediction context from
  `beforeSpace + " "` locally.
- Do not immediately depend on a host re-read that may omit the newly committed space.
- Use the same rule for a tapped suggestion: combine the validated pre-word context
  with the committed suggestion locally.
- Reuse a boundary snapshot only inside the same editor session and selection
  generation.
- Invalidate on cursor movement, host edit, language/field switch, private field,
  rejected editor mutation, or service teardown.

### 2.3 Remove head-of-line blocking

- Put latency-sensitive indexed next-word lookups on a lazy, bounded prediction worker.
- Keep fuzzy/completion ranking on the existing worker.
- Give both workers independent generations and common lifecycle cancellation.
- Do not attempt to interrupt SQLite unsafely; make surrounding work cooperatively
  cancellable and ignore stale publications.
- On low-RAM devices, compare the second worker's PSS/SQLite contention with a
  cooperative-cancellation fallback before finalizing the design.

### 2.4 Make the existing per-word cache real

- Capture the prior-word context once when composition begins.
- Query its n-gram boost and prediction casing once.
- Reuse those immutable values across subsequent prefixes and backspaces within that
  word.
- Do not couple the context request generation to every raw-prefix generation.
- Add a small thread-safe LRU for normalized `(language, prev2, prev1, limit)` prediction
  results, including empty results.
- Clear or trim it on database release, language data replacement, trim-memory, and
  emergency mode.

### 2.5 Remove duplicate store-ready refreshes

- Load the shared `SQLiteLanguageStore` once per active language.
- Publish one ready transition for both dictionary and n-gram facades.
- Ensure store failure reaches a terminal non-loading state and leaves basic typing
  available.

### 2.6 Prediction regression and performance tests

Add tests for:

- completions -> loading predictions -> predictions without any toolbar frame;
- completed empty prediction -> toolbar;
- non-clickable loading slots and no stale chip taps;
- delayed loading indicator threshold;
- language-loading visibility with empty items;
- editor that does not surface a committed space immediately;
- suggestion tap -> immediate prediction request;
- rapid `word + Space + next character`;
- repeated framework callbacks deduplicating one request;
- cursor jump, language switch, field switch, private/email/numeric mode, store
  failure, trim memory, and destroy;
- LRU hit/miss/normalization/eviction/empty-result behavior;
- SQLite trigram-first/bigram-backoff ordering, de-duplication, casing, limits, failure,
  and query-plan/index contracts;
- real-service English and Amharic predictions;
- warm p50/p95, cold install/open, rapid typing backlog, and low-RAM performance.

Update the IME benchmark journey to wait for real completion and next-word results so
the baseline profile includes dictionary, n-gram, fuzzy, and ranking paths.

Precomputing the next word while the current word is still being typed is a fallback,
not the first change. Add it only if deterministic context, worker isolation, and
caching still miss the 80 ms target.

## Phase 3 — Add privacy-bounded Firebase Analytics and Crashlytics

### 3.1 Firebase build wiring

At implementation time, recheck the official versions. The versions documented on the
plan date are:

- Firebase Android BoM `34.16.0`;
- Google Services Gradle plugin `4.5.0`;
- Crashlytics Gradle plugin `3.0.7`.

Planned changes:

- add version-catalog aliases for the BoM, `firebase-analytics`,
  `firebase-crashlytics`, Google Services plugin, and Crashlytics plugin;
- declare both plugins `apply false` in the root build;
- apply them to the app when valid Firebase config is present;
- use the main Firebase modules, not deprecated `-ktx` artifacts;
- keep release mapping upload enabled;
- disable runtime collection and mapping upload for benchmark/test variants;
- do not add Crashlytics NDK because the app owns no native code;
- preserve `SourceFile` and `LineNumberTable` for useful deobfuscation without adding
  blanket Firebase keep rules.

Configuration:

- production config for `com.addiyon.keyboard`;
- separate development Firebase project/config for
  `com.addiyon.keyboard.debug`;
- benchmark collection forced off even though it inherits the production application
  ID;
- a missing production config fails the release-verification task clearly;
- config files are downloaded from Firebase and never fabricated or hand-edited.

### 3.2 Manifest and permission controls

- Add `android.permission.INTERNET`.
- Explicitly remove the transitive `com.google.android.gms.permission.AD_ID`.
- Disable Analytics Advertising ID collection.
- Disable ad-personalization signals by default.
- Inspect and reject unnecessary advertising/AdServices permissions in the merged
  manifest.
- Set Analytics and Crashlytics collection defaults according to the approved consent
  decision.
- Update the release permission allowlist from the actual merged manifest and add
  explicit deny checks for advertising permissions.

### 3.3 One auditable telemetry package

Create:

- `telemetry/Telemetry.kt`;
- `telemetry/TelemetryBackend.kt`;
- `telemetry/FirebaseTelemetryBackend.kt`;
- `telemetry/TelemetryPolicy.kt`;
- `telemetry/TelemetryPrefs.kt`;
- `telemetry/SanitizedNonFatal.kt`.

Architecture rules:

- the public facade exposes typed methods backed by enums/booleans only;
- there is no generic `logEvent`, free-form breadcrumb/log, arbitrary parameter map, or
  arbitrary user property API;
- Firebase types/imports remain inside the backend;
- an injected no-op/fake backend keeps unit tests deterministic;
- consent lives in a separate, non-backed-up `addiyon_telemetry_prefs.xml`, so a restored
  phone does not silently inherit another device's consent;
- `AddiyonApp` initializes the facade and applies saved consent;
- remove the redundant hand-installed uncaught handler after Crashlytics owns that
  responsibility.

### 3.4 Privacy-safe event schema

Automatic Analytics events provide acquisition/app lifecycle signals:

- `first_open`;
- `app_update`;
- `session_start`;
- `user_engagement`;
- Activity `screen_view`.

Allowlisted custom events:

| Event | Allowed data |
|---|---|
| `analytics_first_enable` | no parameters |
| `ime_session_start` | language enum; once per non-restarting, non-private session |
| `language_switch` | destination enum |
| `layout_open` | letters/numbers/symbols/keypad/emoji enum |
| `suggestion_accept` | completion/prediction enum only |
| `voice_start` | language enum |
| `voice_finish` | completed/cancelled/error plus allowlisted error category |
| `onboarding_complete` | no parameters |
| `setting_change` | allowlisted setting enum plus boolean/coarse enum |

Allowlisted user properties, if retained after privacy review:

- keyboard language;
- app UI language;
- theme enum;
- number-row on/off.

Never log:

- characters, keypresses, Space/Delete/Enter timing, or typing cadence;
- text, buffers, words, word length, suggestions, email/domain data, clipboard data,
  emoji choices/recents, or voice transcripts;
- cursor/selection positions or surrounding editor text;
- target application/package, field labels/hints, raw input type, or raw IME options;
- any custom event in a private/password field;
- stable custom user/account/device IDs;
- arbitrary exception messages.

### 3.5 Safe crash and non-fatal reporting

- Fatal crashes are left to Crashlytics after consent/collection policy is applied.
- Keep `SafeLog` as the single caught-failure integration point.
- Map call sites to a fixed failure-category enum.
- Create a synthetic non-fatal with a fixed category, coarse throwable class, sanitized
  Addiyon/framework stack frames, no original message, no cause, and no suppressed
  exceptions.
- Rate-limit and deduplicate by category.
- Never report `OutOfMemoryError`.
- Do not call `setUserId` or free-form Crashlytics logging.
- Add debug-only fatal/non-fatal test commands to the existing debug test receiver;
  no production crash trigger is permitted.

### 3.6 User controls and revocation

Under the recommended policy:

- Analytics and Crashlytics are two independent opt-ins, both off by default.
- Add a short first-run diagnostics choice and a dedicated Privacy & diagnostics
  settings screen.
- Turning Analytics off stops future collection and resets local Analytics data/app
  instance state where supported.
- Turning Crashlytics off deletes locally queued unsent reports and stops future
  collection.
- Copy states clearly that disabling collection cannot retroactively delete reports
  already uploaded; the privacy policy gives the retention/contact details.

### 3.7 Telemetry privacy tests

Add:

- `TelemetrySchemaTest`: legal names, reserved-prefix rejection, fixed parameters, and
  limits;
- `TelemetryApiPrivacyTest`: public API has no text/editor/Bundle/map parameters;
- `TelemetryPolicyTest`: private fields and disallowed integration points emit nothing;
- `TelemetryConsentPolicyTest`: fresh defaults, independent choices, malformed prefs,
  enable/disable/revoke, and queued-report deletion;
- `SanitizedNonFatalTest`: strips messages/causes/suppressed/foreign frames, rate
  limits, and skips OOM;
- `FirebaseManifestContractTest`: collection defaults, intentional network permission,
  no Advertising ID/ad permissions;
- fake-backend service tests for one session event and safe enum-only action events;
- UI/instrumented tests for diagnostics controls, localization, persistence, and the
  privacy-policy link;
- static source/dependency checks that Firebase imports are confined to the telemetry
  package and prohibited content-bearing APIs do not exist;
- startup/IME before-and-after benchmarks to catch Firebase initialization cost.

## Phase 4 — Close the remaining feature-test gaps

Create `docs/test-coverage-matrix.md` as the maintained source of truth. Each row records
the behavior, unit/instrumented/benchmark/manual test, failure modes, and last verified
release.

### Feature coverage matrix

| Area | Strong coverage already present | Missing coverage to add |
|---|---|---|
| Transliteration and tables | table family costs, normalization, repeated keys, golden corpus, candidate branching, layout-key mapping | exhaustive family/order/alias and shifted-key corpus, normalization idempotence, punctuation/boundary/property tests, malformed Unicode/pass-through |
| Composer and editor gateway | append/resume/middle edit/backspace/commit/finish/abandon, connection rejection/throw/swap/slow reads | full editor-model state assertions, selection generation, equal-length stale callbacks, balanced batches, host reentrancy, cursor-only no-mutation matrix |
| English/Amharic service typing | real-service field/action smoke, suggestions, Amharic delete, active middle edit | table-driven character/punctuation/space/enter/delete behavior in both languages, long/mixed documents, session and host-edit interleavings |
| Suggestions/ranking | ranker, fuzzy matcher, case, prefix completion, context parser, email chips, cache concurrency | coordinator generations/cancellation/UI state, concrete SQLite model/store queries, cold/warm performance, empty/failure/loading paths, prediction acceptance |
| SQLite assets/store | generated metadata, DB fold contract, size budgets, corruption policy | atomic install/restore, checksum/schema failure, callback concurrency, release/reload, disk-space failure, query-plan indexes, deterministic generator output |
| Auto-cap/input/enter | sentence-case and input-type policy, Search/Send/Done real IME | extract/test complete enter policy for Go/Next/Previous/None/multiline/flag conflicts, restart/cursor behavior, URI/email/private integration |
| Layouts/modes/keys | grid invariants, key count/shape, number-row metrics, a few Compose dispatch tests | every control-key dispatch and mode transition, keypad auto-selection, Geez/symbol pages, feedback/haptic paths, accessibility/touch-target semantics |
| Emoji | parser/search/store/tone persistence/backspace unit tests, one real commit | repository async load/cancel/release/OOM, category navigation, tone popup UI, recents/search edit/selection/Enter, low-memory and session transitions |
| Voice | composer, retry policy, session ownership | `VoiceInputController` callback/timer/watchdog races with fake recognizer, permission activity, duplicate/late partial/final results, interruption, language switch, unavailable/network/silence, real-device provider checks |
| Settings/onboarding/localization | primary screen actions, toggles, height/theme persistence, onboarding phases, leaf-screen smoke | all `KeyboardPrefs` keys/repair/defaults, `LanguagePrefs`, navigation/back/recreation/new intent, every onboarding tour action, English/Amharic string completeness and fallback |
| Themes/visual/accessibility | metrics and palette contrast logic, dark/large-font activity smoke | screenshot matrix for palettes/light/dark/orientation/font scale, RTL/locales, content descriptions, focus order, minimum touch sizes, TalkBack manual pass |
| Review/update/external actions | review/update pure policies, external-intent runner and instrumentation | injectable `ReviewManager` and `AppUpdateManager` lifecycle, dismissal/retry/downloaded/recreation/listener cleanup, update-ready bar, Play-delivered manual flows |
| Lifecycle/fault/memory | real IME field switches/recreation/visibility, hostile connections, memory policies | hide/show with composition/emoji/voice, trim levels, process death/restore, worker shutdown/late callbacks, lock/interruption, deterministic long-run stress |
| Metadata/release/privacy | IME metadata, artifact signing/assets/profiles script | Firebase config/resources, merged permissions, AD_ID deny, mapping-upload/deobfuscation, SDK dependency audit, privacy-copy consistency |
| Performance | startup and broad IME journey macrobenchmarks | next-word latency distribution, cursor no-op cost, rapid typing backlog, first DB install, Firebase startup delta, explicit release budgets/regression reporting |

### Test-infrastructure changes

1. Add a deterministic in-memory editor model that implements composing spans,
   selection, replacement, UTF-16 offsets, and a mutation log.
2. Extend the real-IME host with:
   - mutation counters by `InputConnection` method;
   - controllable stale/delayed callback delivery;
   - host-side autofill/paste/replacement;
   - long/mixed/Unicode fixtures;
   - exact timestamps for suggestion requests/publications.
3. Introduce small platform adapters for SpeechRecognizer, Play Review, Play Update,
   Firebase, clock, handlers, and executors so controller behavior is JVM-testable with
   fakes.
4. Use manual executors/clocks in async tests; never depend on sleeps for correctness.
5. Add deterministic fuzz/property loops with printed seeds for selection, composer,
   transliteration, and Unicode deletion invariants.
6. Add compatible line/branch coverage reporting and package thresholds. Measure first,
   then enable the core thresholds and ratchet them.
7. Keep macrobenchmarks separate from correctness tests and record device/API/RAM/build
   identity with results.
8. Do not mark real hardware/Play/SpeechRecognizer behavior as unit-tested; keep those
   cases in the physical-phone checklist with explicit evidence.

## Phase 5 — Documentation, Play, Firebase, and release verification

### Repository documentation changes

Before telemetry ships:

- replace or rewrite stale `analytics.md`;
- update `site/privacy.html` and its effective date;
- update `site/README.md`;
- update English and Amharic in-app privacy/consent copy;
- update `docs/production-launch-checklist.md`;
- update privacy claims in `docs/play-store-listing.md`;
- mark historical documents with stale no-analytics claims as superseded rather than
  leaving contradictory instructions;
- add Firebase operations and incident-verification instructions;
- update the next release notes;
- add the maintained test-coverage matrix.

### Release verification changes

- Build and inspect the merged release manifest.
- Update the exact permission allowlist for intentional Firebase requirements.
- Explicitly fail on `AD_ID` and advertising/AdServices permissions.
- Verify production Firebase resources/app ID are packaged.
- Verify the Crashlytics mapping task exists and mapping metadata is generated.
- Confirm benchmark/debug test components and crash triggers are absent from release.
- Keep all current APK/AAB asset, signing, version, profile, and checksum checks.

## Manual actions for the owner

These cannot be completed correctly from source code alone.

### Firebase projects and apps

1. Create separate production and development Firebase projects with Google Analytics
   enabled.
2. Register the production Android app exactly as `com.addiyon.keyboard`.
3. Register the development Android app exactly as
   `com.addiyon.keyboard.debug`.
4. Download the production config to the agreed production path and the development
   config to `app/src/debug/google-services.json`.
5. Do not rename package IDs or hand-edit either JSON file.
6. Decide whether the non-secret Firebase config files are committed for reproducible
   builds or injected by CI. If injected, configure CI before the release plugin is
   enabled.
7. Enable Crashlytics for both projects.
8. Optionally add Play App Signing and local debug SHA-1/SHA-256 fingerprints; these are
   not required for basic Analytics/Crashlytics but help keep app registrations
   complete.

### Firebase/Analytics privacy configuration

1. Set the Analytics property timezone and currency.
2. Choose the shortest suitable retention.
3. Disable Google Signals, ad personalization, Google Ads links, and unnecessary data
   sharing.
4. Disable granular location/device collection where the console permits it.
5. Add custom definitions only for the fixed safe parameters that need reporting.
6. Configure developer-traffic filtering so debug/test devices do not pollute
   production reports.
7. Decide whether Crash Insights data sharing remains enabled.
8. Use least-privilege Firebase roles, MFA, and Crashlytics email/velocity alerts.
9. Link the production Firebase app to Google Play so Crashlytics can show Play-track
   context.

### Google Play and privacy disclosures

1. Deploy the updated `site/privacy.html` before uploading the telemetry-enabled build.
2. Update Play Console -> App content -> Data safety. Do not continue claiming
   "No data collected."
3. Review at minimum:
   - app interactions/lifecycle analytics;
   - crash logs;
   - diagnostics;
   - app-instance, Firebase installation, and Crashlytics identifiers;
   - device metadata;
   - approximate location derived from masked IP, if applicable to the final Firebase
     configuration.
4. Mark data as required or optional according to the approved consent policy.
5. Confirm whether Firebase is treated as a service provider and whether any enabled
   sharing/integration changes the Play "shared" answer.
6. State encryption in transit and the real retention/deletion behavior accurately.
7. Reconcile the Play form, hosted policy, in-app copy, and store listing word-for-word
   on typed text, Analytics, Crashlytics, identifiers, and voice processing.

### Analytics verification

1. Install/clear data on the debug app.
2. Enable Analytics consent if using the recommended opt-in model.
3. Enable Firebase Analytics debug mode for `com.addiyon.keyboard.debug`.
4. Verify safe events in Logcat and Firebase DebugView.
5. Confirm no event appears for characters, deletion, Space, cursor movements, private
   fields, typed content, email text, suggestion text, or voice transcripts.
6. Disable debug mode afterward.
7. On an internal Play build, verify `first_open`/`analytics_first_enable` and
   `ime_session_start` in Realtime.
8. Interpret `first_open` correctly: it is the first launch/use after install, not the
   instant someone downloads the app. Normal Analytics reports are not guaranteed live.

### Crashlytics verification

1. Enable crash consent on the development device.
2. Trigger the debug-only fatal command.
3. Relaunch the app/keyboard so the pending report uploads.
4. Confirm the fatal and safe Analytics breadcrumbs contain no editor/typed data.
5. Trigger a sanitized non-fatal and inspect its category, stack, missing
   message/cause, and rate limit.
6. Repeat with consent off and verify nothing uploads.
7. Revoke consent with a queued report and verify the queued report is deleted.
8. Upload a minified internal-test build, cause one controlled crash, and confirm
   deobfuscated file/line information.
9. Confirm the production crash trigger is absent.

## Verification cadence during implementation

After each focused behavior change:

1. run the focused JVM/instrumented test class;
2. run `./gradlew compileDebugKotlin`;
3. run `./gradlew installDebug`;
4. run `./gradlew assembleDebug` to generate the timestamped shared APK.

After each phase:

1. `./gradlew testDebugUnitTest`;
2. `./gradlew connectedAndroidTest` on the configured emulator;
3. relevant macrobenchmarks/profile generation;
4. `./gradlew installDebug`;
5. `./gradlew assembleDebug`.

Before telemetry release:

1. full JVM and instrumented suites;
2. cursor, prediction, private-field, consent, and telemetry static/privacy gates;
3. startup/IME/prediction benchmarks on the reference low-end and normal devices;
4. release bundle and artifact verification;
5. merged-manifest/dependency audit;
6. Crashlytics mapping/deobfuscation verification;
7. Firebase DebugView and Realtime verification;
8. complete physical-phone checklist and cross-app editor matrix.

## Affected files

### Cursor/composition/editor

- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
- `app/src/main/java/com/addiyon/keyboard/EditorGateway.kt`
- `app/src/main/java/com/addiyon/keyboard/composing/WordComposer.kt`
- `app/src/main/java/com/addiyon/keyboard/composing/CompositionSelectionPolicy.kt`
- `app/src/main/java/com/addiyon/keyboard/composing/ResumableWord.kt`
- `app/src/main/java/com/addiyon/keyboard/composing/DeleteResumeGuard.kt`
- new selection snapshot/coordinator policy files

### Suggestions/performance

- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/NgramContext.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteNgramModel.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteLanguageStore.kt`
- new `SuggestionUiState` and prediction coordinator/cache files
- benchmark journey and a new prediction-latency benchmark

Dictionary assets and generator schema should not change for the first fix unless
query-plan tests reveal a regression.

### Firebase/telemetry/privacy

- `gradle/libs.versions.toml`
- root `build.gradle.kts`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- Firebase config paths supplied manually
- `app/src/main/java/com/addiyon/keyboard/AddiyonApp.kt`
- `app/src/main/java/com/addiyon/keyboard/SafeLogging.kt`
- `app/src/main/java/com/addiyon/keyboard/SafeOps.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/settings/KeyboardPrefs.kt`
- onboarding/settings/navigation and English/Amharic string files
- new `telemetry/` package
- debug-only IME test command receiver
- backup/data-extraction rules
- `plans/verify-release-artifact.sh`
- hosted privacy, store, launch, analytics, operations, and release documentation

### Tests

- existing composer/editor/suggestion/unit tests
- `RealImeCrashTest` and IME test host/fault connection
- `SuggestionAreaUiTest`
- new telemetry unit, manifest contract, static privacy, and UI tests
- new SQLite model/store integration tests
- new controller tests for voice/review/update
- expanded screen/accessibility/localization tests
- coverage configuration and `docs/test-coverage-matrix.md`
- macrobenchmarks and the physical-device checklist

## Risks and mitigations

1. **Host-editor diversity:** a fix that works only in `EditText` can still fail in
   WebView, Compose, browser, chat, and document editors. Mitigation: make selection
   callbacks write-free by design, add adversarial test connections, and retain a
   cross-app physical matrix.
2. **Loss of cursor-aware suggestions:** removing automatic resume could reduce editing
   quality. Mitigation: read-only snapshots plus explicit-action, revalidated adoption
   preserve the feature without tap-time writes.
3. **Re-entrant callbacks:** even batched writes can cause synchronous callbacks.
   Mitigation: selection-generation tokens and fail-closed validation at every step.
4. **Prediction worker memory:** a second worker may increase SQLite concurrency/PSS on
   1 GB devices. Mitigation: lazy bounded thread, measured low-RAM gate, cooperative
   cancellation fallback.
5. **Stale/loading UI:** old chips could remain clickable or loading could stick.
   Mitigation: one sealed immutable state, generation tests, terminal failure state,
   lifecycle invalidation.
6. **Firebase startup cost:** Firebase providers run in the IME app process.
   Mitigation: before/after startup and first-key benchmarks, no work in per-key paths,
   benchmark collection disabled.
7. **Typed data in crash messages:** third-party/editor exceptions may embed content.
   Mitigation: never upload caught originals; synthesize category-only non-fatals and
   statically audit telemetry APIs/call sites.
8. **Privacy-policy mismatch:** adding `INTERNET` and Firebase invalidates prominent
   current claims. Mitigation: disclosure/Play changes are a release blocker, not
   follow-up documentation.
9. **Opt-in blind spots:** privacy-first opt-in cannot report every install/crash.
   Mitigation: state the metric honestly and keep Play Console as the authoritative
   install source.
10. **Broad test expansion becoming brittle:** over-specified pixel/timing tests can
    slow development. Mitigation: pure policies and fake clocks for logic, semantic UI
    assertions, device-specific performance budgets, and manual-only labels where the
    platform cannot be deterministic.

## Decisions required at approval

1. **Telemetry consent**
   - Recommended: Analytics and Crashlytics independently opt-in, both off by default.
     This is safest for an IME, but Firebase will represent consenting first uses and
     crashes rather than every install.
   - Alternative: minimal telemetry on by default with a clear first-run disclosure and
     opt-out. This gives broader `first_open`/crash coverage but is a larger privacy and
     Play-policy change.
2. **Firebase config handling**
   - Recommended: separate production/development Firebase projects and commit their
     non-secret config files for reproducible local/CI builds.
   - Alternative: keep configs untracked and inject them in CI; release configuration
     must be completed before Firebase build plugins are enforced.
3. **Prediction pending visual**
   - Recommended: stable blank three-slot prediction surface plus mic, with a subtle
     indicator only after 120 ms.
   - Alternative: immediate subtle spinner/skeleton. In either case, the toolbar never
     flashes.

## Implementation order after approval

1. Phase 0 failing regressions and measurements.
2. Phase 1 cursor/content safety.
3. Phase 2 prediction orchestration, UI state, and latency.
4. Phase 3 Firebase/consent/privacy integration after the manual config and consent
   decisions are supplied.
5. Phase 4 remaining feature-test matrix in risk order.
6. Phase 5 full release, Firebase, Play, cross-app, and physical-device verification.

The cursor fix is the first production priority because it can alter user data. The
prediction fix follows immediately because it shares the selection/context pipeline.
Firebase comes only after typed-content safety and the disclosure/consent policy are
locked.

## Official references used for the telemetry portion

- [Add Firebase to Android](https://firebase.google.com/docs/android/setup)
- [Get started with Analytics on Android](https://firebase.google.com/docs/analytics/android/get-started)
- [Configure Analytics data collection](https://firebase.google.com/docs/analytics/android/configure-data-collection)
- [Log Analytics events](https://firebase.google.com/docs/analytics/android/events)
- [Get started with Crashlytics on Android](https://firebase.google.com/docs/crashlytics/android/get-started)
- [Customize Crashlytics reports and opt-in collection](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports)
- [Google Analytics automatically collected events](https://support.google.com/analytics/answer/9234069)
- [Google Play Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Firebase privacy and retention information](https://firebase.google.com/support/privacy/)
