# Play Console edge-to-edge and R8 remediation plan

## Objective

Resolve the three Google Play recommendations reported for release `2.0.0`:

1. Make every user-facing activity and the IME window behave correctly edge-to-edge on
   Android 15 and later, while preserving backward-compatible behavior on supported
   devices down to API 24.
2. Remove app-owned uses of deprecated system-bar color APIs and stop executing the
   obsolete `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` path on Android 15+.
3. Move the build to Android Gradle Plugin 9.x so optimized resource shrinking is enabled
   by default, without breaking release signing, R8, generated dictionary assets,
   baseline profiles, benchmarks, or the timestamped APK copy to `/Users/dev/Shared`.

This plan intentionally separates UI/window changes from the AGP migration. Each
workstream should produce a passing build before they are combined, which makes failures
and visual regressions easier to isolate.

## Implementation result

Implemented on July 28, 2026 with:

- Android Gradle Plugin `9.3.1`, Gradle `9.5.0`, and built-in Kotlin with Compose
  Compiler `2.3.21`;
- Activity Compose `1.13.0`;
- Core KTX `1.18.0` and compile SDK `36.1`, the newest Core/SDK combination available in
  the workspace without installing API 37;
- Benchmark/Baseline Profile `1.5.0-alpha07`, required because stable `1.4.1` still uses
  the legacy AGP DSL;
- a signed, optimized, resource-shrunk release AAB that passes
  `plans/verify-release-artifact.sh` and the AGP R8 configuration analyzer;
- 40 passing app instrumentation tests on API 36, including the new edge-to-edge safe
  bounds test.

## Current repository findings

### SDK and edge-to-edge state

- `app` and `benchmark` already use `compileSdk = 36` and `targetSdk = 36`, so Android 15
  edge-to-edge enforcement already applies on Android 15+ devices.
- `MainActivity`, `ThemesActivity`, `ManualActivity`, and `FeedbackActivity` call
  `androidx.activity.enableEdgeToEdge()`.
- Those calls currently happen after `super.onCreate()`. The current AndroidX guidance
  shows edge-to-edge setup before `super.onCreate()` so the launch window and first
  content frame use one consistent window configuration.
- The app resolves `androidx.activity:activity-compose:1.9.3`, even though the Compose BOM
  and Compose UI are much newer.
- `AppBrandHeader` explicitly applies `WindowInsets.statusBars`.
- Material 3 `TopAppBar` and `Scaffold` provide inset support on the remaining screens,
  but every screen still needs a real-device overlap audit because nested inset handling
  can either miss or double-apply padding.
- `KeyboardScreen` reserves the bottom system-bar inset with
  `WindowInsets.systemBars.only(WindowInsetsSides.Bottom)`. The surrounding
  `CustomKeyboardTheme` already paints the keyboard tray background through that padded
  area, which can replace the IME service's direct navigation-bar coloring.
- `MainActivity` and `ManualActivity` host text fields/search UI, but their production
  manifest entries do not explicitly use `android:windowSoftInputMode="adjustResize"`.

### Retraced Play Console deprecated-API locations

The current release mapping at `app/build/outputs/mapping/release/mapping.txt` identifies
the Play Console symbols as follows:

| Play Console symbol | Retraced origin | Meaning |
| --- | --- | --- |
| `b.v.Z` | `androidx.activity.EdgeToEdgeApi26.setUp()` | AndroidX Activity 1.9.3 compatibility code sets status and navigation bar colors |
| `b.x.Z` | `androidx.activity.EdgeToEdgeApi29.setUp()` | AndroidX Activity 1.9.3 compatibility code sets system-bar colors and contrast behavior |
| `a2.r.q` | R8 synthetic `WindowManager.LayoutParams` setter | Reached by the old Activity edge-to-edge cutout path that uses `SHORT_EDGES` |
| `com.addiyon.keyboard.AddiyonKeyboardService.f0` | `AddiyonKeyboardService.updateSystemNavigationBar()` | App-owned direct assignment to `imeWindow.navigationBarColor` |

There are also app theme attributes that must be removed:

- `app/src/main/res/values/themes.xml` defines `android:statusBarColor` in the base and
  splash themes and `android:navigationBarColor` in the splash theme.
- `app/src/main/res/values-night/themes.xml` defines `android:statusBarColor`.

The release should be retraced again after the dependency and window changes; obfuscated
names are not stable between R8 builds.

### R8 and build-tool state

- AGP is `8.13.2`; the wrapper is Gradle `8.13`.
- Release already has `isMinifyEnabled = true`, `isShrinkResources = true`, and uses
  `proguard-android-optimize.txt`.
- Optimized resource shrinking exists in AGP 8.12/8.13 but is opt-in with
  `android.r8.optimizedResourceShrinking=true`; that property is not present.
- AGP 9 enables optimized resource shrinking by default when resource shrinking is
  enabled.
- The preferred current stable upgrade is AGP `9.3.0` with Gradle `9.5.0` and JDK 17 or
  newer. This machine has JDK 21, which satisfies the runtime requirement. If the
  installed Android Studio version cannot sync AGP 9.3, use AGP `9.2.1` with Gradle
  `9.4.1` or upgrade Android Studio; do not remain on AGP 8.x merely to avoid the IDE
  update.
- AGP 9 enables built-in Kotlin and removes access to the legacy variant API. The current
  build applies `org.jetbrains.kotlin.android`, uses `android.kotlinOptions`, adds
  Android variant source directories through `kotlin.sourceSets`, and uses
  `applicationVariants`. All four patterns need migration.
- The legacy `applicationVariants` block implements the repository-required timestamped
  APK copy. It must be replaced, not deleted.
- `app/proguard-rules.pro` currently contains only template comments and no effective
  keep rules. AGP 9's stricter R8 defaults are still a runtime risk because dependencies
  can contribute consumer rules.
- The benchmark/Baseline Profile plugin is `1.4.1`; it must be proven compatible with the
  selected AGP 9 version before the upgrade is accepted.

## Desired end state

- All normal activities opt into edge-to-edge in one consistent, lifecycle-correct way.
- Important and interactive UI stays inside `safeDrawing`/Material component insets while
  backgrounds and scrolling content can draw behind system bars.
- Text fields resize and remain visible when the IME opens.
- Gesture navigation stays transparent and three-button navigation remains legible.
- The IME's tray is the content behind the navigation area; no code assigns
  `statusBarColor` or `navigationBarColor`.
- No app resource theme sets `android:statusBarColor` or `android:navigationBarColor`.
- Android 15+ uses `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` through the maintained AndroidX
  implementation.
- AGP 9.x and its matching Gradle version build the project with built-in Kotlin and the
  public Variant/Artifacts API.
- Release code optimization and optimized resource shrinking are enabled and verified
  from build outputs, not assumed from configuration.
- The release AAB still contains the generated dictionaries, emoji data, mapping file,
  baseline profile, and startup profile, and excludes the raw dictionary inputs.
- Play Console clears the three recommendations after processing a newly uploaded
  internal-track AAB, or any remaining warning is proven to originate solely in the
  latest official AndroidX backward-compatibility implementation and is documented with
  an upstream issue.

## Affected files

### Expected edits

- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle.properties`
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `benchmark/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/addiyon/keyboard/MainActivity.kt`
- `app/src/main/java/com/addiyon/keyboard/ThemesActivity.kt`
- `app/src/main/java/com/addiyon/keyboard/ManualActivity.kt`
- `app/src/main/java/com/addiyon/keyboard/FeedbackActivity.kt`
- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/theme/Theme.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/androidTest/java/com/addiyon/keyboard/ActivityBoundarySmokeTest.kt`
- A focused new edge-to-edge instrumentation test under
  `app/src/androidTest/java/com/addiyon/keyboard/`
- `plans/verify-release-artifact.sh`

### Conditional edits

- Other screen composables under `app/src/main/java/com/addiyon/keyboard/ui/` if the
  inset audit finds missing or duplicated padding.
- `app/proguard-rules.pro` only if release tests demonstrate a real reflection/resource
  reachability problem.
- `buildSrc/build.gradle.kts` or `buildSrc/src/main/kotlin/DictionaryDbGenerator.kt` only
  if Gradle 9 exposes an incompatibility.
- Baseline profile files only if dependency/build changes materially alter startup or IME
  critical paths and regeneration is justified.

## Implementation sequence

### Phase 1: Freeze a measurable baseline

1. Start from a reviewed working tree without discarding the user's existing `.idea` or
   `.opencode` changes.
2. Record:
   - resolved Activity, Core, AppCompat, Material, Compose, Benchmark, and ProfileInstaller
     versions;
   - current APK and AAB byte sizes;
   - release `mapping.txt`, `configuration.txt`, `resources.txt`, and `usage.txt` where
     generated;
   - the current AAB entry list;
   - screenshots for every entry activity and the real IME in light/dark mode.
3. Build a release artifact and preserve its mapping so pre- and post-change Play symbols
   can be retraced accurately.
4. Run the current release verification script and record its known permission-count
   failure separately; do not treat that pre-existing verifier defect as an AGP
   regression.
5. Capture a small device matrix before changes:
   - API 28 or 29 for legacy edge-to-edge behavior;
   - API 34 for pre-enforcement behavior;
   - API 35 or 36 for enforced edge-to-edge;
   - gesture and three-button navigation where the emulator/device supports switching.

### Phase 2: Update the window compatibility libraries

1. Consolidate the duplicate Activity version entries in `gradle/libs.versions.toml` and
   update `activity-compose` from 1.9.3 to the current stable Activity release
   (`1.12.4` at the time this plan was written).
2. Update the direct `core-ktx` dependency from 1.10.1 to the current stable Core release
   (`1.19.0` at the time this plan was written).
3. Re-run `dependencyInsight` and confirm the release runtime classpath resolves one
   coherent version for each Activity and Core atomic group.
4. Build the minified release and retrace the new edge-to-edge call sites. Confirm Android
   15+ selects the API 35 implementation and API 30+ uses
   `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`.
5. Do not add a local reimplementation of AndroidX's API-level branching unless a
   reproducible bug remains after updating. The maintained AndroidX implementation is the
   source of truth for the unavoidable older-platform compatibility calls.

### Phase 3: Normalize activity edge-to-edge setup

1. Introduce one small activity-window setup function or repeat one identical minimal
   sequence in all four full-screen Compose activities.
2. Move edge-to-edge configuration before `super.onCreate()` in:
   - `MainActivity`;
   - `ThemesActivity`;
   - `ManualActivity`;
   - `FeedbackActivity`.
3. Preserve `MainActivity`'s splash-to-real-theme transition. Validate the exact order:
   set the real theme, configure edge-to-edge, call `super.onCreate()`, then install
   Compose content.
4. Leave `VoicePermissionActivity` out of the normal full-screen treatment unless device
   testing shows its transparent permission host creates a visible bar flash. It has no
   app content and should not acquire layout behavior merely for consistency.
5. Keep system-bar icon appearance tied to actual foreground/background contrast. Test
   both system themes and avoid hard-coding icons to one style during configuration
   changes.

### Phase 4: Remove deprecated theme parameters

1. Delete all `android:statusBarColor` and `android:navigationBarColor` items from the
   light, dark, and splash themes.
2. Keep the branded splash drawable/background, but allow the edge-to-edge window and
   Compose content to provide the visible system-bar background.
3. If the launch frame flashes an incorrect bar color on pre-Android-15 devices, migrate
   the launch theme to `androidx.core:core-splashscreen:1.2.0` rather than restoring the
   deprecated color attributes.
4. Verify light/dark system-bar icons during cold launch, warm launch, activity
   recreation, and return from system keyboard settings.

### Phase 5: Make activity inset ownership explicit

1. Add `android:windowSoftInputMode="adjustResize"` to activities that can show editable
   content, at minimum `MainActivity` and `ManualActivity`.
2. Audit every root screen reached from `MainActivity`:
   - onboarding;
   - settings;
   - typing guide;
   - preferences;
   - keyboard height preview;
   - test keyboard;
   - about;
   - themes.
3. For screens using Material 3 `Scaffold`:
   - apply the supplied `innerPadding` to content;
   - consume it where nested inset-aware children would otherwise apply it again;
   - keep `TopAppBar`'s default top/horizontal insets unless the screen deliberately owns
     them itself.
4. Keep `AppBrandHeader` as the explicit status-bar inset owner on settings/onboarding,
   and ensure no enclosing `Scaffold` also adds the same top inset.
5. Preserve `KeyboardHeightScreen`'s intentional empty `contentWindowInsets`; its keyboard
   replica should continue to use the same bottom-inset source as the real keyboard.
6. Check horizontal safe areas in landscape and on a display-cutout emulator. Do not
   solve cutouts with hard-coded status-bar heights.
7. Ensure all scrollable screens can scroll their final control above the navigation bar
   and above the IME when a text field is focused.

### Phase 6: Remove the IME's deprecated navigation-bar coloring

1. Refactor `AddiyonKeyboardService.updateSystemNavigationBar()` into an appearance-only
   operation:
   - remove `imeWindow.navigationBarColor = ...`;
   - remove direct status/navigation color writes everywhere;
   - avoid changing deprecated system-bar contrast properties;
   - retain `WindowInsetsControllerCompat` only if explicit icon contrast is still needed.
2. Let `CustomKeyboardTheme` paint the tray through the bottom inset area. Preserve the
   existing safe distance between keys and the system navigation affordance.
3. Prefer `WindowInsets.navigationBars` for safe key placement and use
   `WindowInsets.tappableElement` only if a separate three-button protection area is
   required. Gesture navigation should not receive an opaque, separately colored bar.
4. Determine icon brightness from the actual selected palette/tray contrast, not only
   from the system dark-mode flag. Fixed dark palettes can be selected while the system is
   light and vice versa.
5. Re-test all palette changes while the IME is visible. The tray and navigation area
   should update without hiding the Home/Back/IME-switch controls.
6. If an API 24-34 device cannot draw the tray behind its navigation area without a
   legacy color call, isolate that behavior in a clearly API-gated compatibility path and
   compare it against the latest AndroidX recommendation. Do not reintroduce an unguarded
   write merely to preserve exact tint matching.

### Phase 7: Add edge-to-edge regression coverage

1. Extend `ActivityBoundarySmokeTest` or add `EdgeToEdgeInstrumentedTest` to launch every
   normal activity and verify:
   - the activity survives recreation;
   - important clickable nodes do not intersect unsafe status/navigation/cutout regions;
   - the first and last interactive controls remain reachable;
   - text fields remain visible after the IME opens.
2. Add focused coverage for:
   - settings/onboarding's custom `AppBrandHeader`;
   - a standard `AppPageTopBar`;
   - the typing-guide search field;
   - the Test Keyboard screen with the real IME open;
   - the keyboard-height replica's intentionally custom inset handling.
3. Extend the real-IME test path to open the keyboard under both a light and dark palette
   and confirm it stays responsive through configuration and navigation-mode changes.
4. Keep automated assertions geometry-based. Screenshot comparison can supplement the
   tests but should not be the only protection because system-bar rendering differs by
   API and navigation mode.

### Phase 8: Prepare the build scripts for AGP 9

Complete these migrations while still on AGP 8.13.2 where possible, then prove the
existing toolchain still builds:

1. Replace `applicationVariants.all` in `app/build.gradle.kts` with
   `androidComponents.onVariants`.
2. Rebuild the `/Users/dev/Shared` copy hook using the public Variant/Artifacts API:
   - consume the built APK directory via `SingleArtifact.APK` and
     `BuiltArtifactsLoader`;
   - read the actual produced APK metadata instead of hard-coding
     `$buildDir/outputs/apk/...`;
   - preserve variant name, version name, version code, timestamp, and the existing no-op
     behavior when `/Users/dev/Shared` is absent;
   - wire the copy task to run after the matching assemble task so
     `./gradlew assembleDebug` continues to create the timestamped APK automatically.
3. Replace remaining `buildDir` reads with `layout.buildDirectory` providers.
4. Move the extra Kotlin variant source directories from the top-level
   `kotlin.sourceSets` block to `android.sourceSets.<name>.kotlin`.
5. Remove the empty `variant.outputs.all` block.
6. Confirm dictionary generation still runs before asset merging and lint, and that it
   does not cause Gradle 9 configuration-cache or task-validation errors.

### Phase 9: Upgrade to AGP 9 and built-in Kotlin

1. Select one supported stable pair:
   - preferred: AGP `9.3.0` + Gradle `9.5.0`;
   - fallback for an older supported Android Studio: AGP `9.2.1` + Gradle `9.4.1`.
2. Update the Gradle wrapper and AGP version together.
3. Confirm Gradle runs on JDK 17 or newer in the terminal, Android Studio, and CI/local
   release process.
4. Adopt AGP built-in Kotlin in both `app` and `benchmark`:
   - remove `org.jetbrains.kotlin.android` from both modules and the version catalog;
   - remove `android.kotlinOptions`;
   - use `compileOptions.targetCompatibility` as the Kotlin JVM target, or configure
     `kotlin.compilerOptions` only when a non-default setting is required;
   - keep the Compose compiler plugin and align its Kotlin version with the KGP version
     selected by AGP 9;
   - verify the `composeCompiler` reports/metrics configuration still works.
5. Keep the new AGP DSL enabled. Do not add `android.newDsl=false` or
   `android.builtInKotlin=false` as a permanent workaround.
6. Verify third-party plugin compatibility:
   - `androidx.baselineprofile`;
   - `com.android.test`;
   - `org.gradle.toolchains.foojay-resolver-convention`;
   - the Compose compiler plugin.
7. Address new AGP defaults deliberately:
   - confirm both modules have unique namespaces;
   - confirm non-final app `R` fields are not used where constants are required;
   - confirm only the intended debug unit-test component is created;
   - confirm all referenced ProGuard files exist.

### Phase 10: Enable and prove optimized resource shrinking

1. Keep `isMinifyEnabled = true`, `isShrinkResources = true`, and
   `proguard-android-optimize.txt` during the first AGP 9 migration to minimize unrelated
   DSL change.
2. Do not set `android.r8.optimizedResourceShrinking=false`. On AGP 9, the optimized
   pipeline should be enabled by default.
3. Optionally perform one comparison build on AGP 8.13.2 with
   `android.r8.optimizedResourceShrinking=true` before the upgrade. This isolates
   resource-shrinker behavior from AGP/Kotlin/Gradle migration behavior. Remove the
   temporary property after reaching AGP 9.
4. On AGP 9.3, run `./gradlew :app:analyzeReleaseR8Config` and review:
   - unexpectedly broad dependency keep rules;
   - rules that disable optimization or obfuscation;
   - app classes/resources retained only by obsolete rules.
5. Compare post-upgrade `resources.txt`, `usage.txt`, `configuration.txt`, APK size, and
   AAB size with the Phase 1 baseline.
6. Do not add broad keep rules to make a crash disappear. Add the narrowest rule only
   after identifying a reflection/JNI/serialization entry point and add a release-mode
   regression test for it.
7. After the AGP migration is stable, consider adopting AGP 9.3's
   `optimization { enable = true }` DSL as a separate cleanup. Do not combine that cleanup
   with the initial toolchain upgrade.

### Phase 11: Release and runtime verification

Run, at minimum:

```bash
./gradlew compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew lintRelease
./gradlew connectedAndroidTest
./gradlew installDebug
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
./gradlew :app:analyzeReleaseR8Config
```

If release signing credentials are available, also run:

```bash
PLAY_MAX_VERSION_CODE=<highest-code-in-play> REQUIRE_CLEAN_RELEASE=1 \
  ./gradlew verifyReleaseArtifact
```

Verification checklist:

1. Confirm `assembleDebug` still writes a timestamped APK to `/Users/dev/Shared`.
2. Confirm the release AAB contains:
   - `amharic.db`;
   - `english.db`;
   - `dictionary_manifest.properties`;
   - `emoji.dat`;
   - R8 mapping metadata;
   - compiled baseline profile metadata.
3. Confirm raw `*_words.dat` and `*_ngrams.dat` files remain excluded.
4. Confirm the merged release manifest still declares only intended components and
   permissions.
5. Install a minified release or benchmark build and exercise:
   - onboarding and return from system settings;
   - all settings screens;
   - typing guide search;
   - the real IME in normal, password, email, URL, multiline, numeric, and search fields;
   - palette changes and light/dark mode;
   - voice permission grant and denial;
   - emoji panel and dictionary suggestions.
6. Repeat visual checks in portrait/landscape, gesture/three-button navigation,
   light/dark mode, a display cutout, and at least one large-font setting.
7. Inspect the final mapping and DEX for app-owned direct calls to
   `setStatusBarColor`, `setNavigationBarColor`, and an Android-15 `SHORT_EDGES` path.
8. Upload the exact verified AAB to an internal Play track, wait for App Bundle Explorer
   and pre-launch processing, and confirm all three recommendations are cleared before
   promoting the artifact.

## Acceptance criteria

### Edge-to-edge

- No important text, top-bar action, bottom control, dialog action, or text field is
  obscured by a status bar, display cutout, navigation bar, taskbar, or IME.
- Cold launch has no incorrect system-bar color/icon flash in either theme.
- Gesture and three-button navigation controls remain visible on every keyboard palette.
- The keyboard does not gain duplicate bottom padding or change height unexpectedly when
  switching navigation modes, opening emoji, or changing the keyboard-height preference.
- App source and themes contain no direct status/navigation bar color assignment.
- The Play report no longer points to `AddiyonKeyboardService.updateSystemNavigationBar`.

### AGP/R8

- The selected AGP version is 9.0 or newer and uses its required Gradle/JDK versions.
- Built-in Kotlin is enabled without legacy DSL opt-outs.
- Debug, release, benchmark, baseline-profile generation, and instrumented tests build.
- Optimized resource shrinking is enabled for release and the resulting app passes all
  minified runtime smoke tests.
- No dictionary, emoji, launcher, splash, font, or theme resource required at runtime is
  missing.
- Release signing, version generation, mapping output, profile packaging, and the
  `/Users/dev/Shared` APK hook still work.
- The new AAB passes the repaired release artifact verifier and Play processing.

## Risks and mitigations

- **Latest AndroidX still contains old-platform compatibility calls.** AndroidX may retain
  deprecated color APIs in API 23-29 implementations because there is no equivalent
  older-platform mechanism. Mitigation: update to current stable AndroidX, prove Android
  15+ selects the maintained API 35 path, remove all app-owned calls, and use the processed
  Play report as the final arbiter. If Play still reports only current AndroidX code,
  capture the retraced mapping and file an upstream AndroidX/Play issue instead of
  forking the compatibility implementation.
- **Removing IME navigation-bar tint can reduce visual matching on old devices.**
  Mitigation: draw the tray behind the bottom inset, preserve icon contrast, test API
  24-34 physically/emulated, and isolate any unavoidable legacy fallback by API level.
- **Double-applied Compose insets can create large blank bands.** Mitigation: designate one
  owner for each edge and add geometry assertions for representative screens.
- **AGP 9 breaks the legacy APK hook.** Mitigation: migrate to `androidComponents` before
  changing AGP and add an explicit assertion that `assembleDebug` produces the shared APK.
- **Built-in Kotlin can conflict with the Compose compiler plugin.** Mitigation: align the
  Compose plugin/KGP versions selected for AGP 9 and remove only the Android Kotlin plugin,
  not the Compose compiler plugin.
- **R8's stricter defaults can expose missing keep rules.** Mitigation: test the minified
  artifact, analyze R8 configuration, and add only evidence-based narrow rules.
- **Optimized shrinking can remove indirectly referenced resources.** Mitigation: exercise
  all manifest, theme, reflection, and dynamic-resource entry points in a release build
  and compare packaged resources with the baseline.
- **Android Studio may not support the newest AGP.** Mitigation: update to a compatible
  Studio release or use AGP 9.2.1 rather than opting out of the new DSL.

## Open questions to resolve during implementation

1. Which Android Studio version is used for the release workflow, and does it support AGP
   9.3.0? This selects the final AGP/Gradle pair.
2. Does the IME window on API 24-34 expose the Compose tray behind the navigation area
   after the direct color write is removed? This determines whether a guarded legacy
   fallback is necessary.
3. Should fixed-color keyboard palettes choose system navigation icon brightness from
   calculated tray luminance or store an explicit light/dark icon preference per palette?
4. After updating Activity/Core, does Play still report AndroidX's older-platform
   compatibility classes? If so, the final mapping and AndroidX version must accompany an
   upstream report.
5. Does the Baseline Profile Gradle plugin 1.4.1 pass all AGP 9.3 tasks, or must it move to
   a newer stable version before the toolchain upgrade?

## Official references

- [Android 15 edge-to-edge behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15)
- [Set up edge-to-edge in Compose](https://developer.android.com/develop/ui/compose/system/setup-e2e)
- [Set up Compose window insets](https://developer.android.com/develop/ui/compose/system/insets-ui)
- [Material 3 inset handling](https://developer.android.com/develop/ui/compose/system/material-insets)
- [AndroidX Activity release notes](https://developer.android.com/jetpack/androidx/releases/activity)
- [AndroidX Core release notes](https://developer.android.com/jetpack/androidx/releases/core)
- [AGP 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [AGP and Gradle compatibility](https://developer.android.com/build/releases/about-agp)
- [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Enable app optimization with R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
