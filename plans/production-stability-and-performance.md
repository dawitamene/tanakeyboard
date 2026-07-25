# Production stability and performance hardening plan

## Goal

Prepare Addiyon Keyboard for production access without relying on a clean Play
pre-launch report as proof that the IME itself is safe. Prevent startup,
lifecycle, input-connection, database, voice, and low-memory crashes; remove
remaining main-thread work from the typing path; establish repeatable release
benchmarks; and verify the exact Play-delivered artifact before applying for
production access.

This is a planning document only. It does not authorize implementation in this
turn.

## Important distinction: what Play will and will not test

Google Play's pre-launch report installs, launches, and crawls the main launch
activity. It reports stability, compatibility, performance, and accessibility
issues, but Google explicitly says it cannot guarantee that it will find every
issue. Keyboard apps without a main launch activity cannot be crawled at all.
Addiyon has `MainActivity`, so Play can crawl onboarding and settings, but the
crawler is not dependable coverage for enabling Addiyon as the system IME,
switching to it, and exercising real typing in other apps.

Therefore the release gate has two independent parts:

1. A clean Play pre-launch report for the launcher/onboarding/settings surface.
2. A separate automated and manual IME test suite that enables the exact
   release keyboard and types into real editor fields.

Official references:

- [Use a pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)
- [Understand a pre-launch report](https://support.google.com/googleplay/android-developer/answer/9844487)
- [Android vitals](https://developer.android.com/topic/performance/vitals)
- [App startup time](https://developer.android.com/topic/performance/vitals/launch-time)
- [Slow rendering and frozen frames](https://developer.android.com/topic/performance/vitals/render)
- [Benchmarking overview](https://developer.android.com/topic/performance/benchmarking/benchmarking-overview)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)

## Current-state audit

### Safeguards already present and worth preserving

- Device-memory detection now runs after `InputMethodService.onCreate()`, not
  during service construction.
- Dictionary databases are copied and opened on a background executor.
- The database installer uses content metadata, length and SHA-256 checks,
  schema/application IDs, required-table validation, an atomic temporary file,
  a read-only connection, retry backoff, and a no-suggestions fallback.
- Only the active language store is kept open, with smaller SQLite and
  suggestion caches on low-RAM devices.
- Suggestion ranking runs on a single worker, clears superseded queued work,
  and uses generation/raw-buffer checks before publishing.
- Fuzzy suggestions are disabled on low-RAM devices.
- Emoji loading is asynchronous and optional.
- Voice recognition is lazy and has generation/timer protections.
- `onTrimMemory()` clears optional caches and closes inactive resources.
- Release builds use R8 and resource shrinking.
- A baseline profile is packaged in the existing release APK.
- The project has broad pure-Kotlin coverage: 39 JVM test files and 324 test
  methods are currently present.
- Production code has no `INTERNET` permission, wake lock, scheduled background
  worker, analytics SDK, or custom native code. Preserve that low battery and
  privacy risk unless a separately reviewed feature requires a change.

### Release blockers and remaining gaps

1. **The source tree is not frozen.** There are uncommitted changes in the IME
   service, composer, delete gesture, voice composer, Compose key UI, and their
   tests. Those changes must be treated as unfinished until the full release
   matrix passes.
2. **The existing AAB is stale.** The on-disk release AAB predates the newer
   release APK and current source. Its assets differ from the current APK:
   the old AAB contains raw dictionary `.dat` inputs, while the newer APK
   contains generated `.db` assets. Neither artifact should be uploaded as the
   final candidate without a clean rebuild from the frozen commit.
3. **Cached test output is incomplete evidence.** The current JVM report shows
   no failures, and the most recent connected report covers four delete-key UI
   tests. It is not evidence that all 23 instrumented tests, the real IME
   service, release shrinking, and Play delivery have passed together.
4. **No benchmark module exists.** There is no Macrobenchmark or generated
   Baseline Profile module, no profileable benchmark build, and no recorded
   startup/frame/typing latency baseline.
5. **The hand-authored baseline profile is stale.** It contains an
   `AmharicComposer` class entry that no longer exists and is not generated
   from measured critical journeys.
6. **No current lint report is present.** Release lint and merged-manifest
   checks are not yet a demonstrated gate.
7. **The real service lifecycle is not instrumented end to end.** Existing
   Compose tests instantiate `AddiyonKeyboardService` as a UI state holder;
   they do not prove framework service creation, input-session replacement,
   process death, editor binder failure, or teardown.
8. **Several broad `Throwable` catches hide partial failure.** Catching every
   `Error` can leave a partially initialized service running, turn a fatal
   failure into a blank keyboard, and skip later cleanup within the same
   `safeApply` block.
9. **Some callbacks remain unguarded.** For example,
   `SQLiteLanguageStore.loadAsync()` posts the already-ready callback directly
   to the main handler, while the install-completion path wraps callbacks.
   Every path must have the same destroyed/session/generation/error contract.
10. **Cleanup is not guaranteed after the first teardown exception.**
    `onDestroy()` performs all cleanup inside one block after
    `super.onDestroy()`. One thrown unregister/stop/release call can prevent
    later resources from being closed.
11. **`currentInstance` is a production static reference to the service.** It
    exists only for a debug broadcast receiver and should not be retained in
    the production source set.
12. **The `onCreateInputView()` fallback is a blank `View`.** It avoids one
    exception but leaves a visibly broken keyboard, which is not a successful
    recovery and may be classified as broken functionality.
13. **Activity-level crash recovery is incomplete.** The `try/catch` around
    `setContent` cannot catch later Compose recomposition/coroutine failures.
    Some system settings, picker, market, browser, and chooser launches are
    still unguarded on unusual OEM/test-lab images.
14. **Preference and system-service reads are not migration-safe everywhere.**
    A restored preference with the wrong type can throw, and OEM
    `InputMethodManager` calls can fail outside the narrow security exception
    already handled.
15. **Password/private fields are not a distinct policy.** Auto-cap is
    disabled, but suggestions, context reads, cursor resume, voice, and key
    preview are not centrally disabled. Besides privacy risk, suppressing
    optional work in private fields reduces the crash and latency surface.
16. **Optional main-thread work remains in key handling.**
    `nextWordPredictions()` performs editor reads and SQLite n-gram queries
    synchronously when a word ends, and `topAmharicCandidate()` can perform a
    synchronous database query on space/enter/other commit paths when the
    worker result is not cached.
17. **The key path still has measurable UI risks.** Each visible key has an
    interaction source and pressed state; letter presses create/destroy a
    `Popup`; sound-enabled presses look up `AudioManager`; and some rows/models
    are copied during recomposition. These are candidates for measurement, not
    blind rewriting.
18. **Nonessential infinite animations exist.** The typing-guide demo, voice
    pulse, and loading indicator must stop with lifecycle/visibility and be
    simplified on the low-RAM tier.
19. **Backup rules are placeholder defaults.** Generated dictionaries must
    never be restored, and keyboard-sensitive preferences need an explicit
    backup decision.

## Severity and execution order

### P0 — Must complete before applying for production access

- Freeze and identify one release candidate.
- Make service initialization/teardown deterministic.
- Guard all asynchronous callbacks and stale-session publication.
- Replace blank/silent failure with a defined core-typing fallback.
- Add real IME lifecycle and adversarial `InputConnection` tests.
- Disable optional/private behavior in password fields.
- Run the full unit, instrumented, lint, minified release, and Play-delivered
  test matrix.
- Review every Play pre-launch crash, ANR, memory, compatibility, and slow
  startup warning.

### P1 — Complete before the first public release

- Add Macrobenchmark and generated Baseline/Startup Profiles.
- Move remaining SQLite/ranking/prediction work off the IME main thread.
- Measure and reduce Compose recomposition, popup, draw, and animation costs.
- Complete low-memory, corrupt-database, low-storage, process-death, and voice
  stress tests.
- Establish operational crash/ANR monitoring and deobfuscation.

### P2 — Optimize after the release gates are stable

- Reduce package/icon/font payload where measurement shows value.
- Refine database copy/storage size and first-language load time.
- Add continuous benchmark regression checks on a fixed physical device.

## Phase 0 — Freeze a trustworthy release baseline

### 0.1 Resolve the in-progress working tree

1. Review the existing composer/delete/voice/service diffs as one behavior
   change, not as independent formatting edits.
2. Decide which changes belong in the production candidate.
3. Finish or revert them through normal review; do not build the candidate from
   a mixed dirty tree.
4. Record the final Git commit, `versionName`, `versionCode`, signing identity,
   and source status.

### 0.2 Build artifacts only after the source is frozen

1. Build a signed minified release APK and AAB from the same commit.
2. Calculate SHA-256 checksums for the APK, AAB, R8 mapping, and native debug
   symbol archive if present.
3. Use `bundletool` to generate the device APK set and a universal diagnostic
   APK from that AAB.
4. Confirm the final artifact contains:
   - `amharic.db`, `english.db`, the dictionary manifest, and `emoji.dat`;
   - the generated baseline profile;
   - no raw `*_words.dat` or `*_ngrams.dat`;
   - only expected permissions and exported components;
   - R8 mapping metadata;
   - the expected package/version/signing certificate.
5. Confirm fresh-install and update-install behavior from the Play-delivered
   closed-track build, not only a locally assembled APK.

### 0.3 Capture before-change measurements

On one fixed 1 GB emulator and one low-end physical device, record:

- launcher cold/warm/hot startup;
- cold and warm keyboard show-to-first-frame;
- key-to-composing-text latency;
- key-to-suggestion latency;
- frame timing during typing, delete repeat, language switch, emoji, and guide
  scrolling;
- PSS, Java heap, native heap, graphics memory, open SQLite connections, and
  thread count;
- APK/AAB download size, installed size, and copied database size.

Do not approve an optimization without comparing it to this frozen baseline.

## Phase 1 — Make service failure and cleanup deterministic

### 1.1 Replace broad catch-all lifecycle wrappers

Refactor the service into explicit required and optional initialization stages.

Required state:

- framework `super` lifecycle;
- saved-state/lifecycle owners;
- core keyboard state;
- basic English/Amharic character handling.

Optional state:

- dictionary/n-gram stores;
- suggestions;
- emoji;
- voice;
- review/feedback integrations.

Rules:

1. Never swallow `VirtualMachineError`, `ThreadDeath`, `LinkageError`, or
   coroutine cancellation as if the operation succeeded.
2. Catch the narrow exceptions expected at each Android/SQLite/editor boundary.
3. If an optional subsystem fails, mark it unavailable and keep basic typing
   usable.
4. If required initialization fails, use one defined recovery path; do not
   continue with uninitialized `lateinit` fields.
5. Track `created`, `inputViewCreated`, `inputStarted`, `visible`, and
   `destroyed` state explicitly so callbacks can reject invalid transitions.

### 1.2 Guarantee teardown

1. Put each cleanup action in an independent best-effort block.
2. Invalidate input-session, language-load, suggestion, emoji, and voice
   generations before releasing resources.
3. Cancel handler messages/runnables owned by the service.
4. Stop/destroy the speech recognizer.
5. Shut down the suggestion executor and reject future submissions.
6. Close both language stores.
7. Release emoji data and clear bounded caches.
8. Unregister the preferences listener only if registration succeeded.
9. Clear the debug service reference in a `finally` path.
10. Transition the Compose lifecycle to destroyed even if another release step
    fails.

Add a teardown test where every resource throws independently and prove all
later resources are still released exactly once.

### 1.3 Make every asynchronous callback lifecycle-safe

All handler/executor/listener/recognizer/Play callbacks must:

- capture a service lifecycle generation and input-session ID;
- no-op after destroy, field switch, language switch, or cancellation;
- wrap both "already ready" and "finished loading" paths identically;
- avoid holding an `Activity`, `View`, or `InputConnection`;
- never publish Compose state from a worker thread;
- never log typed text, suggestions, voice transcripts, or surrounding text.

Change `SQLiteLanguageStore.loadAsync()` so its immediate-ready, backoff, and
install-complete callbacks use the same safe-main dispatch helper.

Use a bounded/coalescing queue for suggestion work. A stale running SQL query
may finish, but it must not enqueue additional work or publish.

### 1.4 Remove the production service singleton

Move `currentInstance` and the language-toggle broadcast hook completely to the
debug source set, or replace the debug mechanism with a test-only interface.
The release service must have no process-static strong reference.

### 1.5 Define the fallback keyboard

The fallback must be visibly functional, not an empty `View`.

Recommended behavior:

- render a minimal, dependency-light English keyboard surface with characters,
  delete, space, and enter;
- do not initialize suggestions, emoji, voice, animation, or custom popup
  previews;
- show one compact non-sensitive status indicator;
- allow the system to recreate the full input view on the next session.

If maintaining a second keyboard surface is considered too risky, prefer a
controlled process/service restart over swallowing a fatal UI error and
returning a blank view. Make this decision before implementation and test it
under forced UI construction failure.

### 1.6 Centralize private-field behavior

Extend `InputTypePolicy` into a single immutable `InputFieldPolicy` that
classifies:

- normal text;
- email/URI;
- password/visible password/web password;
- numeric/phone;
- multiline/search/action fields;
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS` and OEM private options where applicable.

For private/password fields:

- no suggestions or next-word prediction;
- no surrounding-text reads for language modeling;
- no cursor word resume or commit history;
- no voice input;
- no key preview balloon;
- no persistence or logging of field-derived content;
- direct basic typing only.

Add JVM policy tests plus instrumented password-field tests.

## Phase 2 — Harden editor and component boundaries

### 2.1 Treat `InputConnection` as unreliable

Create a small session-scoped editor gateway instead of spreading calls
throughout the service and composers.

It must handle:

- a null or replaced connection;
- methods returning `false`;
- methods returning `null` or stale text;
- `RemoteException`/runtime failure from a dead editor;
- selection/composing-region disagreement;
- editor process death between a read and write;
- extremely slow optional reads;
- UTF-16, combining marks, ZWJ emoji, and malformed surrogate tails.

Keep immediate key output on the main thread, but strictly limit optional
binder round-trips. Never call an `InputConnection` from the suggestion worker.

### 2.2 Add adversarial editor tests

Provide fake connections for:

- every method throwing;
- every mutating method returning `false`;
- null text/selection/extracted text;
- delayed surrounding-text reads;
- connection identity changing mid-session;
- selection inside and outside composition;
- external deletion/replacement of the composing region;
- host editor finalizing composition without a matching callback.

Acceptance: the keyboard may abandon composition and suppress suggestions, but
must not crash, duplicate text, erase text without an explicit delete action,
or keep rewriting a stale field.

### 2.3 Harden database recovery

1. Recognize all Android SQLite corruption/open-failure forms, not only one
   exception subtype.
2. Keep the last known-good version until the replacement is fully installed
   and validated.
3. Write checksum metadata atomically and fsync it as part of the install
   transaction.
4. Cancel or ignore superseded language installations.
5. Bound callbacks and retry count; never retry on every keystroke.
6. Prove graceful typing with missing assets, corrupt metadata, truncated DB,
   wrong schema, full storage, read-only storage, interrupted copy, and process
   death during copy.
7. Explicitly exclude derived databases and temp/checksum files from backup.

### 2.4 Harden activities and external intents

1. Wrap system input-method settings and picker actions with safe resolution
   and a visible fallback message.
2. Make rate/share/browser/email/Telegram launches tolerate images with no
   matching activity.
3. Make `KeyboardStatus` return a conservative snapshot when an OEM binder or
   security call fails.
4. Add a preference migration/sanitization layer that tolerates missing,
   unknown, wrong-type, non-finite, and out-of-range values.
5. Test every launcher activity directly, after rotation/recreation, in dark
   mode, with large fonts, and with missing external handlers.
6. Do not rely on `MainActivity.onCreate` catching errors thrown later by a
   Compose effect or recomposition.

### 2.5 Harden voice ownership

Test and enforce one owner and one generation for each recognition session:

- grant, denial, permanent denial, and permission revocation;
- no recognizer and recognizer creation failure;
- unsupported language and network unavailable;
- start/stop/restart races;
- callback after field switch, keyboard hide, or service destruction;
- silence watchdog and repeated server/busy errors;
- cursor movement during partial dictation;
- host editor rejecting composing updates;
- low memory during recognition.

Voice failure must finalize only text already visible in the field, release the
recognizer, return to basic typing, and never create a restart loop.

## Phase 3 — Add real IME crash automation

### 3.1 Service lifecycle instrumentation

Create instrumentation that installs/enables/selects Addiyon and types into a
dedicated activity containing:

- normal single-line text;
- multiline text;
- search/send/done actions;
- email and URI fields;
- password fields;
- number and phone fields;
- a field whose `InputConnection` deliberately fails.

Cover:

- fresh service start;
- 100 keyboard show/hide cycles;
- 100 field switches without destroying the input view;
- activity recreation and configuration changes;
- process kill and automatic service recreation;
- switching to another IME and back;
- device rotation, split screen, gesture navigation, and three-button
  navigation;
- English/Amharic typing, shift/caps, punctuation, language switch, all number
  pages, suggestion tap, cursor movement, selection deletion, emoji, delete
  repeat, and enter actions.

After each journey, scan logcat for `FATAL EXCEPTION`, `ANR`, `AndroidRuntime`,
SQLite corruption, window-token/popup errors, leaked receiver/window, and
StrictMode violations.

### 3.2 Stress and fault matrix

Run the exact minified release behavior under:

- API 24, one intermediate API, API 35, and API 36;
- 768 MB and 1 GB constrained emulators;
- at least one current physical device and one real low-end physical device;
- repeated `RUNNING_LOW`, `RUNNING_CRITICAL`, `BACKGROUND`, and process-death
  conditions;
- low storage during first dictionary install;
- invalid/restored preferences;
- rapid typing immediately followed by space/enter;
- language toggle during database load;
- emoji open/close/search during memory pressure;
- voice activity/permission recreation.

Evolve `plans/low-ram-stress.sh` and `plans/typing-script.sh` into deterministic
test tools, but make the release package, host activity, device profile, output
directory, and pass/fail conditions explicit.

### 3.3 Launcher crawler and accessibility automation

Use a release build with:

- an AndroidX Test Orchestrator or equivalent isolation strategy for
  instrumented tests;
- UI Automator/Compose tests for every onboarding/settings/manual/theme/
  feedback path;
- a Monkey or Firebase Robo crawl with external intents disabled or safely
  handled;
- Accessibility Scanner checks for labels, contrast, clipping, and touch
  targets;
- small screens, landscape, RTL, Amharic/English locales, large font, and large
  display scaling.

The crawler must not be bypassed or shown fake functionality. Its launcher path
should be the same path a new user sees.

## Phase 4 — Establish measured performance gates

### 4.1 Add benchmark infrastructure

Add a `:benchmark` or `:baselineprofile` module and a non-debuggable,
profileable target variant. Use Macrobenchmark/UI Automator for:

- launcher cold, warm, and hot start;
- navigation to the in-app test field;
- cold IME service creation and first keyboard frame;
- warm show/hide;
- English typing and suggestions;
- Amharic typing, commit, alternates, and backspace;
- rapid type-plus-space;
- language switching;
- long-press delete;
- emoji first open, scroll, search, and close;
- typing-guide open and scroll.

Capture `StartupTimingMetric`, `FrameTimingMetric`, trace sections, and memory
snapshots. Absolute timing acceptance must come from physical devices; emulator
results are useful only for regression comparisons.

### 4.2 Generate Baseline and Startup Profiles

1. Replace the hand-maintained profile with one generated from the benchmark
   journeys.
2. Include launcher startup, IME creation, first composition, both language
   key paths, language switch, and first emoji open.
3. Keep the Startup Profile limited to actual cold-start paths.
4. Compare profile-required and profile-disabled benchmark runs.
5. Verify the final AAB and Play-delivered APK contain the generated profile.
6. Fail artifact verification when the profile references removed app classes.

### 4.3 Internal performance targets

Use stricter internal targets than Play's excessive-startup thresholds:

- launcher cold-start TTID p95 under 1.5 seconds on the low-end physical device;
- cold IME show-to-first-frame p95 under 800 ms;
- warm IME show-to-first-frame p95 under 300 ms;
- key-down to composing-text update p95 under 16 ms and p99 under 32 ms;
- key-down to current suggestion publication p95 under 80 ms;
- no frozen frame over 700 ms;
- no frame over 100 ms in ordinary typing;
- no ANR in any automated or manual journey;
- no main-thread SQLite query in a key handler;
- no main-thread file copy/checksum/emoji parse;
- no stale suggestion flash after backspace, field change, language switch, or
  keyboard hide.

Google currently considers cold startup excessive at 5 seconds, warm startup at
2 seconds, and hot startup at 1.5 seconds. Those are alert thresholds, not
acceptable product targets.

### 4.4 Memory targets

- No OOM or user-visible low-memory kill on the 768 MB and 1 GB scenarios.
- No monotonic heap/PSS growth across 100 show/hide cycles, 50 language
  switches, 50 emoji cycles, and a 30-minute typing loop.
- After a forced GC and idle period, PSS must return within 10% of the stable
  post-start baseline.
- At most one active language database connection/page cache.
- Optional caches and emoji data are released under memory pressure without
  losing the active composing buffer.
- Missing/corrupt/unavailable dictionaries produce basic typing with no
  suggestions, not a crash or blank keyboard.

## Phase 5 — Remove remaining main-thread typing work

### 5.1 Make suggestion requests complete and immutable

At the word boundary, capture only the small editor context needed for ranking
on the main thread. Build an immutable request containing:

- service lifecycle generation;
- input-session ID;
- language generation;
- word generation;
- raw buffer;
- normalized context;
- field policy;
- low-RAM/emergency tier.

Run transliteration alternatives, SQLite lookups, n-gram prediction, fuzzy
matching, ranking, and commit-candidate selection on the suggestion worker.

### 5.2 Remove synchronous prediction queries

`updateSuggestions()` must not query n-grams on the main thread after space,
enter, delete, or field start. Publish an empty/previous-safe toolbar state
immediately, then publish predictions asynchronously if the request is still
current.

### 5.3 Define rapid commit behavior

Space/enter may arrive before the worker has ranked the latest Amharic buffer.
Choose and test one bounded rule:

Recommended:

1. Use the cached worker commit candidate when it matches the exact session,
   generation, and raw buffer.
2. If unavailable by the commit moment, commit the deterministic greedy
   transliteration immediately.
3. Never block the IME main thread on SQLite to preserve ranking.

If product correctness requires a synchronous exact-frequency lookup, measure
it on the cold page cache and impose a hard latency budget before accepting
that alternative. Document the tradeoff because it affects visible
transliteration behavior.

### 5.4 Reduce editor round-trips

Measure and consolidate the current reads:

- next-word context;
- sentence auto-cap context;
- composer-integrity checks;
- selection checks;
- emoji-cluster deletion;
- cursor word resume.

Reuse a read only when its session and selection generation are known current.
Do not cache surrounding text across unrelated fields.

## Phase 6 — Optimize Compose and input rendering from traces

### 6.1 Verify Compose stability

1. Generate Compose compiler stability and skippability reports.
2. Measure recomposition/skip counts for `KeyboardScreen`, suggestion area,
   `KeyRow`, `CharacterKey`, `KeyButton`, and emoji cells.
3. Confirm suggestion publication does not recompose all key rows.
4. Keep key identity stable across suggestion and voice updates.
5. Precompute row render models and avoid per-row `KeyboardUiState.copy()` only
   if traces show meaningful composition work.

### 6.2 Profile the press path

Measure:

- `MutableInteractionSource`/pressed-state work;
- popup window creation and disposal;
- shadow/text layout cost;
- haptic and sound dispatch;
- swipe plus click gesture arbitration;
- delete-repeat coroutine scheduling.

Potential improvements, applied only when measured:

- replace per-press popup windows with a lifecycle-safe preview overlay;
- suppress previews on low-RAM and private fields;
- cache `AudioManager` at service creation rather than resolving it per press;
- hoist constant shapes/text styles;
- use cached draw geometry;
- ensure a cancelled pointer gesture cancels feedback and repeat exactly once.

Add a regression test for keyboard hide/window loss while a preview popup is
visible, because that is a common source of window-token crashes.

### 6.3 Limit animation work

- Stop every infinite animation when its screen/input view is not resumed and
  visible.
- Disable or simplify nonessential pulses/demos on the low-RAM tier and when
  system animations are disabled.
- Keep the language-loading UI static after permanent failure.
- Benchmark the typing guide with and without its repeating demo before
  retaining it.

### 6.4 Package-size cleanup

After release behavior is stable:

- replace the finite Material extended icon set with local vector resources and
  remove `material-icons-extended`;
- verify unused fonts and resources are removed by R8/resource shrinking;
- preserve raw dictionary inputs as build inputs but never package them;
- track download, installed, and copied-data size as release metrics.

## Phase 7 — Release verification and production gate

### 7.1 Automated commands

Run from the frozen commit:

```bash
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest
./gradlew lintRelease
./gradlew assembleRelease
./gradlew bundleRelease
./gradlew installDebug
./gradlew assembleDebug
```

Also run the new benchmark/baseline-profile tasks and artifact-verification
task. The final two debug commands remain required by this repository's normal
post-change workflow; they do not replace release testing.

### 7.2 Release artifact tests

Fail the release when:

- the bundle is unsigned or signed by the wrong upload certificate;
- `versionCode` is not higher than every Play upload;
- raw dictionary source assets are present;
- generated databases/metadata are missing or inconsistent;
- an unexpected permission/exported component/SDK appears;
- the baseline profile is absent or stale;
- R8 mapping is unavailable;
- install/launch/IME enablement fails on a fresh device;
- an update from the last closed-test release requires clearing app data.

### 7.3 Play/Firebase validation

1. Upload the frozen AAB to internal or closed testing.
2. Install it through Play on representative devices.
3. Wait for the pre-launch report.
4. Review every device's stability, performance, accessibility, screenshot, and
   compatibility detail.
5. Reproduce failures using the same API, model/RAM class, orientation, and
   locale where possible.
6. Run the custom IME instrumentation separately in Firebase Test Lab or the
   local device farm.
7. Rebuild with a higher version code after any source change; never apply with
   a report from an older artifact.

### 7.4 Go/no-go criteria

Production-access application is a go only when:

- the exact commit/AAB/checksum/signing identity are recorded;
- the full current unit and instrumented suites pass;
- release lint has no unresolved release-relevant error;
- release APK/AAB asset and manifest checks pass;
- all P0 fault, lifecycle, and private-field cases pass;
- there are zero reproducible crashes or ANRs in the candidate matrix;
- the pre-launch report has no unexplained error and no ignored memory/startup
  warning;
- the Play-delivered build passes real English and Amharic typing across host
  apps and field types;
- low-memory and corrupt/missing database cases retain basic typing;
- performance and memory targets are met or a documented exception is approved;
- closed-test Android vitals and support feedback show no unresolved
  release-blocking cluster.

Google's current store-visibility bad-behavior thresholds are 1.09% overall
user-perceived crash rate and 0.47% overall user-perceived ANR rate, with 8%
per-phone-model thresholds for both. These are maximum visibility thresholds,
not launch goals. The candidate goal is no known reproducible crash or ANR, and
post-launch alerts should be set well below the published thresholds.

## Monitoring without increasing keyboard privacy risk

For the first release, use:

- Play pre-launch reports;
- Android vitals crash/ANR/LMK/startup/rendering data;
- uploaded R8 mapping;
- Play Console alerts;
- tester device/version/steps/logcat evidence;
- support reports with a version code.

The current uncaught-exception handler logs locally and then delegates; it does
not prevent a crash or create actionable remote evidence.

If a crash-reporting SDK is later proposed, handle it as a separate privacy and
policy change:

- never attach typed text, composing buffers, surrounding text, suggestions,
  email/password content, or voice transcripts;
- use only coarse lifecycle/component reason codes;
- update the manifest, privacy policy, Data safety form, dependency audit, and
  consent/collection decision before shipping;
- prove the SDK does not initialize or perform network work in the per-key path.

Do not add analytics merely to make the production-access application sound
more complete.

## Affected files

### Core lifecycle and crash boundaries

- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
- `app/src/main/java/com/addiyon/keyboard/AddiyonApp.kt`
- `app/src/main/java/com/addiyon/keyboard/SafeOps.kt`
- `app/src/main/java/com/addiyon/keyboard/SafeLogging.kt`
- `app/src/main/java/com/addiyon/keyboard/InputTypePolicy.kt`
- new service/session/editor gateway and fallback UI files

### Composition, suggestions, storage, and voice

- `app/src/main/java/com/addiyon/keyboard/composing/WordComposer.kt`
- `app/src/main/java/com/addiyon/keyboard/composing/ResumableWord.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteLanguageStore.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteDictionary.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteNgramModel.kt`
- `app/src/main/java/com/addiyon/keyboard/voice/VoiceInputController.kt`
- `app/src/main/java/com/addiyon/keyboard/voice/VoiceComposer.kt`
- `app/src/main/java/com/addiyon/keyboard/emoji/EmojiRepository.kt`

### Launcher and keyboard UI

- `app/src/main/java/com/addiyon/keyboard/MainActivity.kt`
- standalone activity files
- `app/src/main/java/com/addiyon/keyboard/KeyboardStatus.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/settings/KeyboardPrefs.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/KeyRow.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/KeyButton.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/keys/RepeatingClickable.kt`
- suggestion, emoji, manual, and theme composables

### Build, profiles, manifests, and tests

- `settings.gradle.kts`
- root and app Gradle files
- `gradle/libs.versions.toml`
- `app/src/main/baseline-prof.txt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- new benchmark/baseline-profile module
- new release artifact verification
- JVM and instrumented test sources
- `plans/low-ram-stress.sh`
- `plans/typing-script.sh`

## Risks and open decisions

1. **In-progress behavior changes:** confirm whether the current composer,
   delete-repeat, and voice diffs are intended for the candidate before any
   production freeze.
2. **Fallback UI:** decide whether to maintain a minimal native fallback
   keyboard or allow a controlled service/process restart on required UI
   failure. A blank view is not acceptable.
3. **Amharic rapid commit:** approve the recommended immediate greedy fallback,
   or retain exact ranked commit with a measured strict synchronous-query
   budget.
4. **Private fields:** confirm that voice, prediction, resume, history, and key
   previews should all be disabled in password fields. This is the recommended
   production policy.
5. **Backup:** choose between disabling backup entirely and explicitly
   whitelisting non-sensitive preferences. Derived database/temp data must be
   excluded either way.
6. **Telemetry:** recommended initial choice is Play vitals plus tester
   evidence, avoiding a new network/privacy surface immediately before
   production access.
7. **Device lab:** identify the real low-end phone that will be the absolute
   performance gate; emulator timing alone is not sufficient.
8. **Dependency upgrades:** do not combine broad AndroidX/Compose upgrades with
   final hardening unless lint, Play SDK Index, or a known defect requires it.
   Any required upgrade should be isolated and rerun through the full matrix.

## Recommended first implementation slice

Start with a small P0 stability change set:

1. explicit service initialization/destroy state;
2. guaranteed independent cleanup;
3. lifecycle-safe callbacks;
4. production removal of `currentInstance`;
5. private-field policy;
6. real IME service smoke tests;
7. clean signed release artifact verification.

Only after that slice passes should benchmark-driven main-thread and Compose
optimizations begin. This keeps crash hardening reviewable and prevents a
performance rewrite from hiding lifecycle regressions.
