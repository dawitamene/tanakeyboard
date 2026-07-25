# Low-end performance and startup recovery plan

## Goal

Make Addiyon Keyboard start reliably after an in-place app update and remain
responsive on devices with less than 1 GB of RAM, without requiring users to
clear cache or app data. Improve typing latency, suggestion latency, Compose
recomposition, draw cost, resident memory, and install/storage footprint.

This document is a discussion plan only. No implementation should begin until
the priorities and open questions at the end are agreed.

## Current evidence

### Priority-zero startup failure

The highest-confidence startup defect is in
`app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`.
`isLowRam` calls `getSystemService()` in a property initializer. Android runs
property initializers while constructing `InputMethodService`, before attaching
its base `Context`. That is the same unsafe lifecycle point the service already
avoids for dictionaries, n-grams, emoji, and voice objects.

This can fail before `onCreate()` and before the broad `safeApply` around
`onCreate()` can log or recover. It also fits the timing of the regression:
the property was added with the recent performance work.

This must still be confirmed from a failing device's logcat before changing
code. The expected stack contains service construction, `getSystemService`, and
an unattached `ContextWrapper`.

### SQLite startup and update weaknesses

The copied database path has several independent reliability problems:

- A copied file is considered valid when it merely exists and has non-zero
  length. It has no schema version, asset version, expected length, checksum,
  required-table check, or recovery policy.
- Asset copying writes directly to the final path. Process death, low storage,
  or I/O failure can leave a partial file that every later start keeps trying to
  open.
- A future app update can bundle a new schema while continuing to reuse an old
  cache file.
- Dictionary and n-gram wrappers copy the same language asset to different
  files. The 35 MB Amharic database therefore becomes about 70 MB in private
  storage and can occupy two independent page caches.
- Database copy/open threads use normal thread priority and can compete with
  the IME main thread during its first frame.
- Load state is represented by independent booleans and is not an atomic
  lifecycle. Rapid language toggles can race open, release, and callbacks.
- Database connections are not released in service `onDestroy()` and there is
  no `onTrimMemory()` policy.
- Failure currently degrades into a callback from a partially initialized
  loader without a defined `Failed` state or retry/backoff behavior.

Clearing cache removes a stale or partial copy, so it can appear to repair some
of these cases, but the app must self-repair without asking the user to do that.

### Runtime database and suggestion costs

Static inspection and SQLite query plans show:

- Every keystroke runs suggestion database work synchronously from the service
  main thread.
- The common English prefix `a` currently scans about 13,846 rows and creates a
  temporary B-tree to satisfy `ORDER BY freq DESC LIMIT 3`.
- Amharic one-character prefixes also scan thousands of rows.
- Bigram and trigram lookups use a temporary B-tree for weight ordering.
- Amharic ranking calls `frequencyOf()` once per candidate reading and calls a
  separate completion query for each reading.
- N-gram prediction performs separate word-ID queries and then an N+1
  `vocabWord()` lookup for successors.
- `PRAGMA cache_size=-2000` is requested independently on both copies of the
  active database. The current `rawQuery(...).use {}` form should also be
  verified because Android cursors execute lazily.
- The 256-entry database result cache is not capability-tiered.
- `topAmharicCandidate()` can redo transliteration and dictionary work at commit
  time after the same buffer was just ranked for the suggestion strip.

### Compose, drawing, and package observations

- `KeyboardScreen` reads suggestions, email chips, voice state, layout state,
  preferences, theme-related values, and emoji state in a broad parent scope.
  Suggestion changes therefore restart more of the keyboard composition than
  necessary.
- `KeyRow` and individual keys receive the entire mutable service object and
  create many action lambdas. Whether these children skip effectively depends
  heavily on compiler strong-skipping behavior.
- The Kotlin Android plugin is 2.0.21 while the Compose compiler plugin is
  declared as 2.0.0. These should be version-aligned before trusting stability
  and skipping results. Strong skipping is default from Kotlin 2.0.20, but the
  project should prove it with compiler reports.
- Static layout classes contain ordinary `List` values, which Compose normally
  treats as unstable. They are immutable in practice, but this is not expressed
  to the compiler.
- Every visible key is a Material 3 `Card` with elevation/shadow, its own
  interaction source, pressed-state collection, and potentially a coroutine
  collecting press events. These choices may be acceptable, but the cost must
  be measured on the target device before keeping or replacing them.
- Row metrics are still recomputed for some rows inside composition, and the
  email comma remap creates a copied `KeyData.Character`.
- The Tana background builds `Path` and gradient objects in draw code rather
  than caching size-dependent geometry.
- Emoji data is loaded automatically after the active n-gram model even when a
  low-memory user never opens the emoji panel.
- The project includes `material-icons-extended` for a finite set of icons.
  The current debug APK is about 46 MB compressed and contains roughly 64 MB of
  uncompressed DEX, much of which is consistent with the extended icon set.
  Release shrinking removes much of it, but debug startup and developer
  measurements are distorted, and release still pays build/download costs for
  anything retained.
- `profileinstaller` is present, but there is no app Baseline Profile or
  Macrobenchmark module.

### Existing result worth preserving

The SQLite conversion already reduced measured Java heap from roughly 45 MB to
5–7 MB and native heap from roughly 30 MB to 10 MB. The next work should not
undo that gain by reintroducing a large in-memory trie or unbounded caches.

## Success criteria

### Reliability and upgrade compatibility

- The IME starts successfully in 100 consecutive cold-start cycles on the
  constrained test device.
- Install the new build over the last production build with `adb install -r`;
  retain preferences, enabled-IME state, recents, and selected theme; the app
  and keyboard work without `pm clear` or clearing cache.
- Repeat the upgrade test with a stale database, a truncated database, a
  zero-byte file, a wrong schema version, and an unavailable/low-storage copy
  destination.
- A missing or unusable dictionary never prevents the core keyboard from
  drawing and typing. Suggestions may temporarily be unavailable.
- No loader callback from an old language/session can publish into the current
  language/session.

### Low-memory release targets

Capture a fresh release baseline after the startup hotfix, then use both a
relative and an absolute gate:

- Median steady-state PSS at least 25% below that release baseline.
- Median Java heap no higher than 10 MB after both languages have been used.
- Median native heap no higher than 12 MB after both languages have been used.
- No monotonic PSS or heap growth across 50 show/hide cycles, 20 language
  switches, emoji open/close cycles, and repeated long typing/backspace runs.
- One open SQLite store and one page cache for the active language.
- No user-visible low-memory kill during the scripted 768 MB and 1 GB tests.

The final PSS cap should be set from the new minified-release baseline. The
earlier proposed 25 MB total-PSS assertion is not realistic for a Compose IME
and should not become a flaky test.

### Responsiveness targets

On the constrained release build:

- Warm keyboard show-to-first-frame p95 under 300 ms.
- Cold process start plus keyboard first frame p95 under 800 ms.
- Key-down to composing-text update p95 under 16 ms and p99 under 32 ms.
- Key-down to updated suggestions p95 under 80 ms, with no stale result flashes.
- No frame over 100 ms during ordinary typing.
- No more than 1% slow/frozen frames in the scripted typing and emoji journeys.

Exact absolute thresholds may need one adjustment after the first real-device
baseline, but regression thresholds must then remain fixed.

### Storage and package targets

- Only one installed copy per language database.
- Raw source `.dat` files used to generate databases are not also packaged in
  the release.
- Remove `material-icons-extended`.
- Record APK/AAB download size, installed size, database-copy size, and DEX size
  before and after.

## Recommended execution order

## Phase 0 — Ship the startup and upgrade-safety hotfix

Do this as the smallest independent release. Do not mix it with ranking or UI
behavior changes.

### 0.1 Confirm the startup stack

1. Capture logcat from a failing existing-user device before clearing anything.
2. Record app version, Android version, free storage, enabled/default IME state,
   and whether opening the launcher activity or only the keyboard fails.
3. Confirm whether the first failure is service construction. If a separate
   database exception is also present, retain both stacks as regression
   fixtures.

### 0.2 Move device capability detection to a valid lifecycle point

1. Replace constructor-time context access with a safe default-backed field.
2. Resolve the device tier after `super.onCreate()` when the service context is
   attached.
3. Define low-memory as `ActivityManager.isLowRamDevice` or total physical RAM
   at/below the agreed threshold. The existing 1 GB AVD plan expects a
   `totalMem` fallback, but the implementation currently checks only the OS
   flag.
4. Pass an immutable capability value into caches, database configuration, and
   optional-feature gates.
5. Keep typing behavior available if capability detection itself fails.

Recommended first threshold: OS low-RAM classification or total RAM less than
or equal to 1 GiB. Evaluate a 1.5 GiB conservative tier only after measurements.

### 0.3 Introduce a versioned, atomic database asset installer

Create one installer/store boundary instead of letting dictionary and n-gram
classes copy files independently.

1. Generate database metadata containing:
   - format/schema version;
   - language;
   - build/content ID;
   - expected byte length and checksum;
   - required tables and indexes.
2. Store generated databases in `noBackupFilesDir` or the app database
   directory, not in cloud-backup data. Prefer reliability over a
   system-reclaimable cache for the active IME data.
3. Copy to a unique temporary file on the background executor.
4. Flush/close the temporary file, verify its length/checksum, open it read-only,
   and perform lightweight schema/metadata checks.
5. Atomically rename it to the final versioned path only after validation.
6. On open failure, quarantine/delete only the derived database file, recopy
   once, and fall back to `Unavailable` if the retry fails.
7. Run `quick_check` only for a new copy or recovery from an open/integrity
   failure; do not scan the full 35 MB database on every warm start.
8. Remove legacy `cacheDir/dict-*.db` and `cacheDir/ngram-*.db` files only after
   the new store opens successfully.
9. Exclude generated database files and temporary files from backup/device
   transfer rules.

### 0.4 Make failure non-fatal and observable

Use a single state machine such as `Closed`, `Installing`, `Ready`, `Failed`,
and `Released`, guarded by a lock or serialized executor. State transitions and
callbacks must carry a generation token.

- `Failed` means typing continues and the toolbar is usable.
- Avoid an infinite loading animation on permanent failure.
- Retry on the next explicit keyboard show or language switch with bounded
  backoff, not on every keystroke.
- Log a compact reason code and database metadata, never typed text.
- Narrow lifecycle exception handling. A broad `safeApply` around all of
  `onCreate()` can hide partial initialization and leave a broken service alive.
  Protect optional subsystems individually and keep required lifecycle calls
  explicit.

### 0.5 Hotfix tests

- Instrumented service-start smoke test on API 24 and current API.
- Upgrade install from the last shipped build without data/cache clearing.
- JVM tests for the installer using temporary directories and injected asset
  streams: fresh install, valid reuse, version replacement, truncated copy,
  interrupted temp file, checksum mismatch, open failure, and low-storage I/O
  failure.
- Device test that writes a bad derived database into target storage, restarts
  the process, and observes automatic repair.
- Core-typing test with the database forced unavailable.

### Phase 0 affected files

- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteDictionary.kt`
- `app/src/main/java/com/addiyon/keyboard/suggestion/SQLiteNgramModel.kt`
- new shared database installer/store and capability-policy files
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- unit and instrumented startup/upgrade tests

## Phase 1 — Establish trustworthy release measurements

Performance changes after the hotfix should be accepted only when a trace or
metric demonstrates the gain.

### 1.1 Add benchmark infrastructure

1. Add a `:benchmark` or `:baselineprofile` module using Macrobenchmark and
   UIAutomator.
2. Drive these critical journeys:
   - launcher cold start;
   - cold IME creation and first keyboard frame;
   - warm show/hide;
   - English typing and suggestions;
   - Amharic typing, alternates, commit, and backspace;
   - rapid typing followed immediately by space;
   - language switching;
   - emoji panel open, scroll, search, and close;
   - long-press delete.
3. Collect startup timing, frame timing, memory snapshots, and custom trace
   sections.
4. Measure minified release/profileable builds. Keep debug measurements only
   for diagnostics.
5. Use the 1 GB AVD for repeatability, add a 768 MB/one-core AVD for stress, and
   use a real low-end physical device for final timing because emulator absolute
   timings are host-dependent.

### 1.2 Add targeted tracing

Add debug/profileable trace sections around:

- service construction and `onCreate`;
- `onCreateInputView` through first draw;
- database validate/copy/open;
- per-keystroke composing update;
- transliteration lattice generation;
- exact frequency batch;
- completion query;
- n-gram query;
- ranking;
- result publication;
- first emoji parse/open.

Extend `MemoryProbe` to record graphics, code, private-dirty, and database
milestones where available. Keep all probes out of normal release execution.

### 1.3 Inspect Compose stability before editing UI

1. Align the Kotlin and Compose compiler plugin versions.
2. Generate Compose compiler metrics and stability reports.
3. Record skippability of `KeyboardScreen`, `KeyRow`, `CharacterKey`,
   `KeyButton`, suggestion strips, and emoji cells.
4. Use Layout Inspector recomposition/skip counts while typing the same script.
5. Capture Perfetto traces with composition/layout/draw slices.

### Phase 1 affected files

- `settings.gradle.kts`
- root and module Gradle files
- `gradle/libs.versions.toml`
- new benchmark/baseline-profile module
- debug/profileable tracing helpers
- updated deterministic typing scripts and measurement summaries in `plans/`

## Phase 2 — Consolidate and optimize the SQLite store

### 2.1 One store per active language

Replace the two wrappers' independent files/connections with one
`LanguageSuggestionStore` per language.

- One validated file path.
- One read-only `SQLiteDatabase` connection.
- Dictionary and n-gram APIs become thin methods/facades over that store.
- One serialized background executor with
  `Process.THREAD_PRIORITY_BACKGROUND`.
- Explicit close on service destruction.
- Generation-safe open/release/language-switch transitions.
- Persist both installed language files if storage permits, but keep only the
  active language open. This avoids repeated 35 MB copying while preserving a
  single active page cache.

### 2.2 Fix schema and query plans

Change the generator and prove every hot query with `EXPLAIN QUERY PLAN`.

1. Set `application_id` and `user_version`.
2. Add weight-ordered covering indexes:
   - bigram `(ctx, weight DESC, succ, casing)`;
   - trigram `(ctx, weight DESC, succ)`;
   - vocab lookup covering the returned ID/text where beneficial.
3. Join successor rows directly to `vocab` so prediction has no N+1 lookups.
4. Add a batch exact-frequency API for all Amharic candidate readings instead
   of one SQL query per candidate.
5. Add a batch completion API for the distinct reading prefixes.
6. Precompute a compact top-completions table for short normalized prefixes
   (initial candidate: one to three characters). This removes the 13k-row scan
   for common English prefixes and multi-thousand-row Amharic scans. Confirm
   storage growth before selecting the exact prefix depth and retained top-K.
7. Use a direct indexed range query for longer prefixes where the candidate
   range is already small.
8. Preserve deterministic ranking and normalization with golden tests.
9. Apply PRAGMAs through an API that actually executes them and verify the
   effective values.
10. Benchmark a smaller page-cache budget, for example 512 KiB on the low tier
    and 1–2 MiB otherwise. Select the smallest value that keeps p95 query latency
    within target.

Avoid WAL because these shipped stores are read-only.

### 2.3 Reduce packaged and installed data

1. Generate databases into a Gradle generated-assets directory rather than
   writing outputs into `src/main/assets`.
2. Move source `.dat` inputs outside the packaged asset source set or explicitly
   exclude them from release assets.
3. Remove the broad `assetsDir` task input if it includes the task's own
   database outputs.
4. Make generation deterministic and incremental.
5. Compare normal tables, covering indexes, and `WITHOUT ROWID` layouts using
   actual database size and query traces before choosing the final schema.
6. Add release artifact tests that open the APK/AAB and assert:
   - both required `.db` assets are present;
   - raw generator inputs are absent;
   - schema/content IDs match the generated metadata;
   - size stays within a fixed budget.

### 2.4 Cache policy

- Keep only caches with measured hit rates.
- Reduce the SQLite suggestion cache on low-memory devices.
- Clear ranking/completion caches at word boundaries and language changes.
- Clear optional caches on memory pressure.
- Never cache typed text beyond the already-required bounded, in-session
  functionality.

### Phase 2 affected files

- `buildSrc/src/main/kotlin/DictionaryDbGenerator.kt`
- `app/build.gradle.kts`
- generated asset configuration
- SQLite dictionary/n-gram classes, or their replacement store
- `SuggestionCache.kt`
- database contract, size, schema, query-plan, and artifact tests

## Phase 3 — Remove database work from the typing main thread

This is the most important smoothness change after database consolidation.

### 3.1 Separate immediate typing from optional suggestions

1. Keep key handling, case resolution, and `setComposingText` on the main thread
   so the pressed key appears immediately.
2. Build an immutable `SuggestionRequest` containing language, raw buffer,
   input-session ID, word-generation ID, capability tier, and the already
   captured context needed for ranking.
3. Execute SQLite lookups, transliteration alternatives, prefix synthesis,
   fuzzy matching, and ranking on the single suggestion worker.
4. Publish only when session, language, generation, and raw buffer still match.
5. Cancel or coalesce superseded work. Use `CancellationSignal` for a stale SQL
   query where supported.
6. Never call `InputConnection` from the worker. Capture the small context window
   once at the word boundary on the main thread.

### 3.2 Preserve commit correctness

Return a `SuggestionResult` containing both visible chips and the exact
commit candidate for that raw buffer.

- Reuse the matching result when space/enter commits.
- Cover the "type very quickly and press space before the worker finishes"
  case with one bounded batch exact-frequency query or another agreed
  deterministic fallback.
- Do not silently change the current rule that an exact dictionary reading may
  beat the greedy transliteration.
- Test result arrival after backspace, cursor movement, field switch, keyboard
  hide, and language switch.

### 3.3 Reduce allocation only where traces justify it

Likely candidates:

- Parse the transliteration unit lattice once per raw buffer and derive greedy,
  vowel alternate, consonant alternate, candidate readings, and last-unit
  boundaries from it.
- Reuse the already-produced candidate readings at commit time.
- Replace `groupBy`/`values`/`map` ranking chains with a bounded single-pass
  best-per-word map and top-K selection.
- Give `EthiopicNormalizer.normalize(String)` a no-change fast path that returns
  the original string.
- Reuse fuzzy matcher row buffers within one worker and add early row-abandon
  when the minimum possible distance exceeds the edit budget.

Do not add broad `ThreadLocal` pools by default. They retain memory on a
long-lived IME thread and should be introduced only when allocation traces show
a net win.

### Phase 3 affected files

- `AddiyonKeyboardService.kt`
- new suggestion request/engine/coordinator files
- `CandidateRanker.kt`
- `Transliterator.kt`
- `EthiopicNormalizer.kt`
- `FuzzyMatcher.kt`
- concurrency, stale-result, ranking-golden, and latency benchmark tests

## Phase 4 — Compose recomposition and draw optimization

### 4.1 Align compiler and prove strong skipping

1. Use one Kotlin version for the Android and Compose compiler plugins.
2. Confirm strong skipping in compiler reports.
3. Express static key/layout/metric models as immutable only where that promise
   is true. Prefer immutable UI models or persistent collections; do not add
   annotations merely to silence a report.
4. Re-run reports after each structural UI change.

### 4.2 Isolate state reads

Split the keyboard into independently invalidated scopes:

- theme host;
- suggestion/voice toolbar;
- emoji panel/search;
- key layout and metrics;
- individual shift/enter/language-state controls;
- individual pressed key.

Specific changes:

1. Move suggestion, prediction, email, voice, and loading-state reads into a
   suggestion-area wrapper so a new suggestion list does not restart key-row
   composition.
2. Stop passing the mutable service through every key. Pass a small immutable UI
   state plus a stable remembered `KeyboardActions` callback holder.
3. Read `shiftState`, `enterAction`, email-field remapping, and language state in
   the smallest subtree that renders them.
4. Precompute/remember row render models and all row metrics by layout, width,
   number-row setting, and height scale.
5. Avoid copying `KeyData.Character` for the email comma remap; pass a display
   and output override.
6. Keep key identity stable across suggestion updates.

Acceptance is a measured reduction in recompositions/skips and frame work, not
simply more `remember` calls.

### 4.3 Reduce per-key draw cost conditionally

Profile the current key grid first. If shadows/Card rendering is a top draw
cost:

1. Replace per-key Material `Card` with a lightweight `Box`/custom key surface
   using cached shapes and colors.
2. Remove or simplify elevation on the low-memory tier, or use one inexpensive
   custom shadow treatment with visual-regression approval.
3. Keep press feedback localized to the pressed key.
4. Hoist constant `TextStyle`, shape, and size objects.
5. Cache static background paths/brushes with `drawWithCache`.
6. Evaluate whether per-key press collectors can be replaced by a shared,
   accessibility-correct input modifier. Do not combine click/long-press/swipe
   logic until interaction tests cover cancellation and gesture conflicts.

Take light/dark screenshots for every palette and compare key bounds, labels,
corner previews, caps lock, press preview, emoji, and numeric layouts.

### 4.4 Optional UI payload reductions

1. Replace the finite icon set with local vector assets and remove
   `material-icons-extended`.
2. Load emoji data on first emoji-panel use on the low tier rather than after
   database startup.
3. Release emoji search/browse data under memory pressure and recreate it on
   demand.
4. Inspect font usage and consider a subset/variable font only if package or
   font-memory traces show meaningful savings.
5. Keep language-switch animation lightweight and stop it on failed load.

### Phase 4 affected files

- `AddiyonKeyboardView.kt`
- `KeyboardScreen.kt`
- `KeyRow.kt`
- `KeyButton.kt`
- `ui/keys/KeyComposables.kt`
- `SuggestionBar.kt`
- emoji UI/repository files
- `ui/theme/Theme.kt`
- new local icon file(s)
- Gradle dependency catalog
- Compose UI and screenshot tests

## Phase 5 — Lifecycle-aware memory release

Implement a policy tied to actual IME visibility and memory pressure:

- `onDestroy`: cancel worker tasks, invalidate generations, close the active
  store, clear caches, stop voice, and release optional repositories.
- `onTrimMemory(RUNNING_LOW/CRITICAL)`: clear suggestion, normalization, fuzzy,
  and emoji caches while preserving the composing buffer.
- Background/UI-hidden pressure: close the read-only database if measurement
  shows meaningful native/PSS recovery; reopen lazily on the next show.
- Keep normal warm hide/show fast when the system is not under pressure.
- Never hold an Activity, view, `InputConnection`, or service in a process-wide
  static. Retain `currentInstance` only in the debug source set if it is needed
  solely by the benchmark broadcast receiver.
- Ensure voice and emoji objects are lazy and released after use on the low
  tier.

Add heap-dump/leak checks across repeated input-view creation and destruction.

## Phase 6 — Baseline and startup profiles

After hot paths stabilize:

1. Generate a Baseline Profile from real critical journeys, including cold IME
   creation, first composition, English/Amharic typing, language toggle, and
   first emoji open.
2. Mark only true startup work for the Startup Profile so DEX layout is not
   diluted.
3. Compare Macrobenchmark runs with the profile required versus disabled.
4. Verify the profile is packaged in the minified release APK/AAB.
5. Test the actual Play-delivered build; do not use debug APK timing as the
   release acceptance signal.

Android's current guidance recommends Macrobenchmark for measuring profile
benefits and reports that Baseline Profiles can improve first-launch code
execution substantially:

- https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
- https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile

Compose stability and phase guidance used for this plan:

- https://developer.android.com/develop/ui/compose/performance/stability/diagnose
- https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- https://developer.android.com/develop/ui/compose/performance/bestpractices
- https://developer.android.com/develop/ui/compose/performance/phases

SQLite and memory guidance:

- https://developer.android.com/topic/performance/sqlite-performance-best-practices
- https://developer.android.com/topic/performance/memory

## Phase 7 — Verification and rollout

### Automated gates

- All existing JVM tests.
- New installer, store-state, query-plan, cancellation, and ranking tests.
- Compose UI tests for all layouts and suggestion states.
- `installDebug` for developer smoke testing.
- `assembleDebug` for the timestamped local APK required by the repository.
- Minified release assembly/bundle and artifact-content checks.
- Macrobenchmark comparison on the fixed constrained-device configuration.

### Upgrade matrix

Test each supported Android/API tier with:

1. clean install;
2. update from the last production build;
3. update with populated preferences/recents/history;
4. update while Amharic is active;
5. update while English is active;
6. app cache manually cleared before start;
7. stale database version;
8. corrupt/partial database;
9. low free storage;
10. process death during first database copy.

### Manual behavior matrix

- English and Amharic character entry, shift, caps lock, punctuation, commit,
  cursor resume, suggestions, predictions, and backspace.
- Email, password, search, numeric, multiline, and unsupported editor fields.
- Language switching during load and during fast typing.
- Voice start/stop and keyboard return.
- Emoji first load, search, tones, recents, and scroll.
- Theme, dark mode, height scale, number row, sound, and vibration.
- Hide/show, rotate/configuration change, switch to another IME and back.

### Rollout

1. Release Phase 0 to internal testing first.
2. Run the in-place upgrade matrix with the same signing key.
3. Use a small staged Play rollout.
4. Watch startup crash rate, ANR rate, user-perceived LMK rate, cold/warm startup,
   and affected-device models before expanding.
5. Land later performance phases separately so regressions can be attributed and
   rolled back without losing the startup recovery.

## Risks and open questions for discussion

1. **Crash evidence:** can we capture the exact logcat from a failing device
   before clearing cache? The constructor-time context call is the strongest
   current root cause, but database corruption may be a second failure.
2. **Last shipped artifact:** which exact version/APK/AAB should be used for the
   upgrade test, and is its signing key available locally?
3. **Low-memory threshold:** use only Android's low-RAM flag plus `<=1 GiB`, or
   apply the reduced-feature tier through 1.5 GiB as a safety margin?
4. **Suggestion degradation:** recommended behavior is immediate typing with an
   empty toolbar while the database is unavailable. Confirm that this is better
   than delaying keyboard display.
5. **Rapid type-plus-space:** if the asynchronous ranking result is not ready,
   should commit perform one small synchronous batch query to preserve exact
   ranking, or commit the deterministic greedy reading immediately?
   Recommendation: preserve behavior with the bounded batch query, then measure
   its p99.
6. **Visual shadows:** if per-key elevation is a measured GPU hotspot, is a
   flatter low-tier rendering acceptable, or must every tier remain pixel
   identical?
7. **Persistent database storage:** a reliable versioned copy uses private
   non-backup storage. Confirm the installed-storage budget after schema
   compaction and removal of packaged source `.dat` files.
8. **Fuzzy matching:** it is already disabled on low-RAM devices. Keep that
   policy unless user testing shows a critical quality loss.

## Recommended discussion decision

Approve Phase 0 as an immediate isolated hotfix, with graceful no-suggestion
fallback and atomic versioned database installation. Then capture a minified
release baseline before choosing how far to proceed through Phases 2–6.
