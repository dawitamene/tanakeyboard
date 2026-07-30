# Test Coverage Matrix

This is the maintained behavioral source of truth for the release checklist. Every
checkbox in `docs/final-physical-phone-test-checklist.md` has one row below. A row is
not release evidence until its `Last release` cell names the exact build that passed.

## How to maintain this matrix

- `A` means the checklist behavior is fully deterministic and automated.
- `H` means deterministic logic is automated but a physical-phone residual remains.
- `M` means the behavior is manual-only because it depends on real hardware, Android
  system UI, a third-party host, visual judgment, or sustained real-time use.
- Add the exact test class or manual profile when behavior changes. Never replace a
  behavioral assertion with a coverage percentage.
- Change `Pending 2.0.0` only after the exact release artifact passes the linked
  automation and any listed manual residual.

## Measured core coverage gate

- Run `./gradlew verifyCoreDebugUnitTestCoverage` to enforce the gate, or
  `./gradlew jacocoCoreDebugUnitTestReport` to regenerate the XML and HTML reports.
- The 30 July 2026 baseline covers 1,344 of 1,376 lines (97.67%) and 944 of 1,052
  branches (89.73%).
- The enforced minimum is 90% line and 85% branch coverage independently for every
  included package, not only for the aggregate bundle. These values may only move
  upward; changing the measured class or package scope requires an explicit review in
  this file.
- The scope includes all transliteration, composing, and model classes plus the
  JVM-testable suggestion, telemetry-policy, and preference-sanitizer classes listed in
  `app/build.gradle.kts`.
- Android framework adapters, Compose UI, the IME service, generated classes, and
  SQLite-backed runtime facades remain outside this JVM metric. Their behavior is
  covered by the instrumented, real-IME, SQLite contract, benchmark, and manual rows
  below rather than being hidden behind JVM exclusions.
- XML:
  `app/build/reports/jacoco/coreDebugUnitTest/coreDebugUnitTest.xml`. HTML:
  `app/build/reports/jacoco/coreDebugUnitTest/html/index.html`.

## Automated evidence catalog

| ID | Level | Tests |
|---|---|---|
| `J-Enter` | JVM | `model/EnterActionPolicyTest`, `InputTypePolicyTest`, `SentenceCaseTest` |
| `J-Translit` | JVM | `transliteration/TransliteratorTest`, `TransliterationGoldenCorpusTest`, `TransliterationPropertyTest`, `AmharicTableTest`, `EthiopicNormalizerTest`, `RepeatedKeyInputTest` |
| `J-Compose` | JVM | `composing/WordComposerTest`, `WordComposerCrashTest`, `CompositionSelectionPolicyTest`, `DeleteResumeGuardTest`, `ResumableWordTest`, `EditorGatewayTest` |
| `J-Suggest` | JVM | suggestion ranker, fuzzy, case, completion, n-gram context, per-word context capture, prediction/suggestion cache, email, SQLite asset/query-plan, and failure-policy tests under `suggestion/` |
| `J-Layout` | JVM | `layout/KeyboardLayoutInvariantTest`, `ui/KeyboardMetricsTest`, `model/ShiftStateTest`, `ui/keys/CharacterKeyStyleTest` |
| `J-Emoji` | JVM | `emoji/EmojiDataTest`, `EmojiSearchTest`, `EmojiBackspaceTest`, `RecentEmojiStoreTest`, `SkinToneStoreTest` |
| `J-Voice` | JVM | `voice/VoiceComposerTest`, `VoiceRecoveryPolicyTest`, `VoiceSessionOwnershipTest`, `VoiceInputControllerTest` fake-recognizer/timer race coverage |
| `J-Prefs` | JVM | `ui/settings/PreferenceValueSanitizerTest`, `review/ReviewPromptPolicyTest`, `ui/theme/KeyboardPaletteTest` |
| `J-I18n` | JVM | `ui/i18n/AppStringsContractTest`, `ImeMetadataContractTest`, `SubtypeLanguagePolicyTest` |
| `J-External` | JVM | `ExternalActionRunnerTest`, `ui/feedback/FeedbackDestinationsTest`, update/review policy tests, `ReviewPromptControllerTest`, `UpdateLifecycleControllerTest` |
| `J-Privacy` | JVM | `InputTypePolicyTest` and telemetry API, schema, policy, consent, sanitization, manifest-contract, and forbidden content-path call-site tests |
| `I-Onboard` | Instrumented Compose | `ui/onboarding/OnboardingScreenUiTest` |
| `I-Settings` | Instrumented Compose | `ui/settings/SettingsScreensUiTest`, including independent diagnostics controls, persistence-failure disclosure, Amharic diagnostics copy, and policy-link dispatch |
| `I-Privacy` | Instrumented storage | `telemetry/TelemetryInstrumentationPolicyTest` actual SharedPreferences/controller reinitialization, durable all-off clearing, and malformed-value repair |
| `I-Leaf` | Instrumented Compose | `ui/LeafScreensUiTest` |
| `I-Keyboard` | Instrumented Compose | `ui/KeyboardScreenUiTest`, `ui/keys/DeleteKeyUiTest` |
| `I-Suggest` | Instrumented Compose | `ui/SuggestionAreaUiTest` |
| `I-IME` | Real IME instrumentation | `RealImeCrashTest` |
| `I-Store` | Instrumented SQLite | `suggestion/SQLiteLanguageStoreInstrumentedTest` isolated install, concurrent callbacks, checksum reinstall, interrupted-swap restore, release/reload, and low-space failure |
| `I-Activity` | Activity instrumentation | `ActivityBoundarySmokeTest`, `EdgeToEdgeLayoutTest` |
| `I-External` | Intent instrumentation | `ExternalActionsInstrumentedTest` |
| `B-Startup` | Macrobenchmark | `benchmark/StartupBenchmark` |
| `B-IME` | Macrobenchmark/profile | `benchmark/ImeJourneyBenchmark`, `BaselineProfileGenerator.criticalJourneys` |
| `B-Prediction` | Macrobenchmark | `benchmark/PredictionLatencyBenchmark.warmEnglishNextWordRequestToPublication`, `warmAmharicNextWordRequestToPublication` |

## Manual evidence profiles

Each profile explicitly supplies the reason, device, and base expected result. The row
adds its behavior-specific expected result.

| ID | Why automation is insufficient | Required device | Base expected result |
|---|---|---|---|
| `M-REL` | End-to-end IME/system integration and real rendered pixels differ by Android build and vendor. | The physical phone recorded in the checklist, running the exact release artifact. | The stated behavior works once per action with no crash, text corruption, clipping, or stale state. |
| `M-VIS` | Contrast, animation quality, clipping, touch comfort, and perceived flicker need human visual judgment. | The release phone plus its smallest display setting, largest supported font/display setting, portrait, landscape, light, and dark modes as applicable. | All stated content is readable, reachable, stable, and responsive. |
| `M-HW` | Haptics, audio, gesture cancellation, microphone, and hardware latency are not faithfully represented by fakes. | The release phone with speaker, vibration motor, microphone, gesture navigation, and button navigation available. | Exactly one intended hardware response occurs and stops when the gesture/session stops. |
| `M-VOICE` | Android permission UI, the installed speech provider, acoustics, and network failures are external services. | The release phone with the production speech provider, a quiet room, and controllable connectivity. | Permission and recognition states recover cleanly; text is inserted once in the requested language. |
| `M-XAPP` | Host editors implement selection, composing spans, autofill, and editor actions differently. | The release phone with the named production messaging, notes, browser, email, dialer, and web-form apps. | Only the active field changes, the cursor remains correct, and the named host action occurs once. |
| `M-LIFE` | Boot, process death, lock screen, IME switching, and low-memory recreation require the Android lifecycle. | The release phone after reboot, force-stop, lock/unlock, IME switch, and memory pressure as named. | State restores within policy and the next input is accepted without duplicate or lost text. |
| `M-STRESS` | Sustained timing, thermal load, ANR behavior, and human rapid input are real-time device properties. | The release phone with model, API, RAM, build, and test duration recorded. | No crash, ANR, freeze, growing lag, missed terminal release, or state leak occurs during the stated duration. |
| `M-STORE` | Store/browser resolution and Play-delivered behavior depend on installed production components. | The release phone with the intended store/browser and exact Play-delivered release where required. | The correct external destination opens once, or the documented graceful failure is shown. |

## Release rule

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| No blockers | M | — | `M-STRESS`: complete all P0 rows; expected no crash, ANR, blank/frozen UI, missing IME, corrupt text, or private-field leak. | Cross-feature release blockers. | Pending 2.0.0 |
| Failures recorded | M | — | `M-REL`: for every failed row record steps, host app/screen, build, and useful image/video evidence. | Untriageable or silently waived failures. | Pending 2.0.0 |

## 1. Fresh install and onboarding

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Fresh launch | H | `I-Onboard`, `B-Startup` | `M-LIFE`: clear data/install fresh; expected activation onboarding, no crash or blank first frame. | Bad initial state, startup crash, blank frame. | Pending 2.0.0 |
| App identity | H | `I-Leaf`, `I-Activity` | `M-VIS`: expected sharp Addiyon name/logo/copy/buttons with no clipping. | Wrong identity, raster blur, clipping. | Pending 2.0.0 |
| English app language | H | `J-I18n` | `M-VIS`: select English in onboarding; expected every visible string changes and fits. | Missing/blank string, stale locale, clipping. | Pending 2.0.0 |
| Amharic app language | H | `J-I18n`, `I-Settings.amharicDiagnosticsCopyRendersAndPrivacyPolicyOpensBrowsableUrl` | `M-VIS`: select Amharic in onboarding; expected Ethiopic copy throughout and no fallback/clipping. | Missing translation, wrong fallback, glyph clipping. | Pending 2.0.0 |
| Open keyboard settings | M | — | `M-REL`: tap activation; expected Android input-method settings opens once. | Missing activity, blocked intent, duplicate launch. | Pending 2.0.0 |
| Return after enabling | H | `I-Onboard` | `M-REL`: enable Addiyon in system settings and return; expected picker step without restart. | Stale enabled status, wrong step. | Pending 2.0.0 |
| Open keyboard picker | M | — | `M-REL`: tap switch; expected Android IME picker opens once. | Picker unavailable, duplicate launch. | Pending 2.0.0 |
| Select Addiyon | H | `I-Onboard` | `M-REL`: choose Addiyon; expected default status detected immediately. | Stale default-IME detection. | Pending 2.0.0 |
| All-set screen | H | `I-Onboard.defaultKeyboardWithCompletedTourShowsAllSetThenCallsDone` | `M-VIS`: expected one confirmation transition with responsive action and no freeze. | Missing/duplicate confirmation, frozen transition. | Pending 2.0.0 |
| Feature tour Next | M | — | `M-VIS`: traverse every page; expected indicators, examples, animations, and Next/Start actions remain correct. | Broken page order, stale indicator, animation jank. | Pending 2.0.0 |
| Feature tour Skip | M | — | `M-REL`: fresh-data rerun, tap Skip; expected settings home opens once. | Skip ignored, tour loop. | Pending 2.0.0 |
| Tour persistence | H | `J-Prefs` sanitization covers restored values | `M-LIFE`: complete/skip, force-close, reopen; expected tour does not return. | Lost or malformed preference, repeated tour. | Pending 2.0.0 |
| Enabled but not default | H | `I-Onboard.enabledButNotDefaultKeyboardShowsPickerStep` | `M-REL`: select another IME and reopen; expected picker step. | Wrong onboarding branch. | Pending 2.0.0 |
| Disabled after setup | H | `I-Onboard.disabledKeyboardShowsActivationStep` | `M-REL`: disable Addiyon and reopen; expected safe activation step. | Stale enabled state, crash. | Pending 2.0.0 |

## 2. Main Activity and navigation

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Main screen load | H | `I-Settings`, `B-Startup` | `M-VIS`: expected promptly usable home with no flicker, overlap, or missing cards. | Slow/blank frame, overlap, missing content. | Pending 2.0.0 |
| App language toggle | H | `J-I18n` | `M-VIS`: toggle both ways; expected every visible label changes immediately. | Partial recomposition, stale copy. | Pending 2.0.0 |
| Language persistence | H | `J-I18n`, `J-Prefs` | `M-LIFE`: relaunch after each choice; expected selected UI language survives. | Bad code/fallback, lost persistence. | Pending 2.0.0 |
| Themes navigation | H | `I-Settings.settingsRowsCallNavigationCallbacksAndFeedbackSheetOpens` | `M-REL`: open and top-back; expected settings home. | Wrong route/back stack. | Pending 2.0.0 |
| Typing Guide navigation | H | `I-Settings`, `I-Leaf` | `M-REL`: open and top-back; expected settings home. | Wrong route/back stack. | Pending 2.0.0 |
| Preferences navigation | H | `I-Settings` | `M-REL`: open and top-back; expected settings home. | Wrong route/back stack. | Pending 2.0.0 |
| Test Keyboard navigation | H | `I-Settings`, `I-Leaf` | `M-REL`: test top and system back; expected one-level return. | Double pop, activity exit. | Pending 2.0.0 |
| About navigation | H | `I-Settings`, `I-Leaf` | `M-REL`: test top and system back; expected one-level return. | Wrong activity/back behavior. | Pending 2.0.0 |
| System back behavior | H | `I-Leaf` callback assertions | `M-REL`: from every child expect one-level return; from home expect app exit. | Back-loop, skipped level. | Pending 2.0.0 |
| Rapid navigation | M | — | `M-STRESS`: open/close each child five times; expected no duplicate screen, freeze, or crash. | Duplicate destinations, navigation race. | Pending 2.0.0 |
| Background restore | M | — | `M-LIFE`: background a child for one minute; expected same usable screen on return. | Lost route/state, dead controls. | Pending 2.0.0 |
| Rotation/recreation | H | `I-Activity.entryActivitiesSurviveDarkLargeFontRecreation` | `M-VIS`: rotate home and every child; expected valid layout and navigation state. | State loss, clipping, duplicate route. | Pending 2.0.0 |
| Small-screen scrolling | H | `I-Activity`, `I-Leaf` | `M-VIS`: smallest display setting; expected every row reachable. | Content below fold unreachable. | Pending 2.0.0 |
| Large text | H | `I-Activity.entryActivitiesSurviveDarkLargeFontRecreation` | `M-VIS`: largest supported font/display; expected important copy and controls readable/tappable. | Truncation, overlap, tiny targets. | Pending 2.0.0 |
| Light/dark system mode | H | `I-Activity`, `J-Prefs` palette logic | `M-VIS`: switch modes while open; expected immediate readable colors. | Stale palette, low contrast. | Pending 2.0.0 |

## 3. Themes

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Theme list | H | `I-Settings.themesScreenPersistsSelectedPaletteAndCallsBack` | `M-VIS`: scroll all groups/cards; expected complete smooth list. | Missing group, bad preview, scroll jank. | Pending 2.0.0 |
| Theme selection marker | H | `I-Settings.themesScreenPersistsSelectedPaletteAndCallsBack` | `M-VIS`: select several; expected exactly one current marker. | Multiple/no markers, stale selection. | Pending 2.0.0 |
| Live keyboard theme | H | `J-Prefs`, `I-Settings` | `M-VIS`: open IME after selection; expected tray, normal/special/accent keys use palette. | Partial/stale theme application. | Pending 2.0.0 |
| Theme persistence | H | `I-Settings`, `J-Prefs` | `M-LIFE`: force-close app/IME; expected palette survives both. | Preference loss or bad fallback. | Pending 2.0.0 |
| Minimal theme modes | H | `J-Prefs` navigation-icon contrast | `M-VIS`: Minimal in light/dark; expected clear labels and controls. | Low contrast, wrong system-bar icon tone. | Pending 2.0.0 |
| Colored theme contrast | M | — | `M-VIS`: sample Pastel, Bold, Nature, Vibrant, Dark; expected every key label readable. | Contrast failure by palette/role. | Pending 2.0.0 |
| Tana effect | M | — | `M-VIS`: use Tana while typing; expected smooth stable effect with no tap latency. | Flashing, dropped frames, input delay. | Pending 2.0.0 |
| Toolbar theme shortcut | M | — | `M-REL`: open Themes from toolbar and select; expected direct return to original field with new theme. | Wrong return route, lost field, stale theme. | Pending 2.0.0 |

## 4. Preferences

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Vibration on | H | `I-Settings.soundVibrationTogglesPersistToKeyboardPrefs`, `J-Prefs` | `M-HW`: type several keys; expected one short consistent vibration per press. | No/double/long feedback. | Pending 2.0.0 |
| Vibration off | H | `I-Settings`, `J-Prefs` | `M-HW`: expected ordinary presses produce no vibration. | Stale enabled state. | Pending 2.0.0 |
| Sound on | H | `I-Settings`, `J-Prefs` | `M-HW`: expected one reasonable-volume sound per ordinary press. | No/double/excessive sound. | Pending 2.0.0 |
| Sound off | H | `I-Settings`, `J-Prefs` | `M-HW`: expected ordinary presses are silent. | Stale enabled state. | Pending 2.0.0 |
| Whole-row toggles | H | `I-Settings.soundVibrationTogglesPersistToKeyboardPrefs` | `M-REL`: tap label and switch separately; expected each tap changes value exactly once. | Double toggle, dead label. | Pending 2.0.0 |
| Number row on | H | `J-Layout`, `I-Settings` | `M-REL`: both languages; expected visible digits that commit their labels. | Missing row, wrong output. | Pending 2.0.0 |
| Number row off | H | `J-Layout`, `I-Settings` | `M-VIS`: expected row disappears with no empty gap. | Stale row, dead space. | Pending 2.0.0 |
| Preference persistence | H | `I-Settings`, `J-Prefs` malformed-value/default bounds | `M-LIFE`: relaunch app and IME; expected vibration/sound/number row retained. | Loss, type corruption, bad repair. | Pending 2.0.0 |
| Keyboard height slider | H | `I-Settings.keyboardHeightDragPersistsScaleToKeyboardPrefs`, `J-Layout` | `M-VIS`: drag min-to-max; expected smooth percent and preview. | Jumping/clamped wrong value, stale preview. | Pending 2.0.0 |
| Keyboard height handle | H | `I-Settings.keyboardHeightDragPersistsScaleToKeyboardPrefs` | `M-HW`: drag preview edge; expected finger tracking within limits. | Gesture loss, out-of-range scale. | Pending 2.0.0 |
| Keyboard preview match | H | `J-Layout`, `I-Settings` | `M-VIS`: vary language/number row/theme; expected preview matches all three. | Stale model or palette. | Pending 2.0.0 |
| Minimum height typing | H | `J-Layout` lower-bound metrics | `M-VIS`: save minimum and type; expected all rows visible and tappable. | Clipped rows, undersized targets. | Pending 2.0.0 |
| Maximum height typing | H | `J-Layout` upper-bound metrics | `M-VIS`: save maximum and type; expected IME fits screen and retains controls. | Self-covered controls, viewport overflow. | Pending 2.0.0 |
| Height persistence | H | `I-Settings`, `J-Prefs` finite/clamped restoration | `M-LIFE`: restart app/IME; expected saved height remains. | Lost/corrupt/out-of-range value. | Pending 2.0.0 |
| Height back path | H | `I-Settings.preferencesKeyboardHeightRowNavigates` | `M-REL`: back from height; expected Preferences, not home. | Incorrect nested back stack. | Pending 2.0.0 |

## 5. Guide, test field, About, sharing, and feedback

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Guide content | H | `J-Translit`, guide model tests, `I-Leaf.manualScreenRendersAndFiltersTheTransliterationTable` | `M-VIS`: scroll end-to-end; expected correct examples, headings, diagrams. | Missing family, wrong order, clipping. | Pending 2.0.0 |
| Guide interactions | H | guide rail/search JVM tests, `I-Leaf` | `M-HW`: expand/tap/drag; expected correct response without navigation. | Gesture conflict, accidental route. | Pending 2.0.0 |
| Guide toolbar shortcut | M | — | `M-REL`: open from IME and back; expected return directly to original typing app/field. | Wrong activity stack, lost editor. | Pending 2.0.0 |
| Test field auto-open | H | `I-Leaf.testKeyboardScreenShowsScratchFieldAndBackAction` | `M-REL`: open screen; expected focused field and Addiyon IME automatically visible. | Focus/IME race. | Pending 2.0.0 |
| Test field editing | H | `J-Compose`, `I-IME` middle-edit tests | `M-XAPP`: enter multiline text, move/select/replace/delete; expected standard editor behavior. | Wrong range, stale composing span. | Pending 2.0.0 |
| About identity | H | `I-Leaf.aboutScreenShowsIdentityPrivacyPolicyAndBackAction`, `J-I18n` | `M-VIS`: expected correct logo, installed version, descriptions, privacy and creator copy. | Wrong build version/copy, clipping. | Pending 2.0.0 |
| Share App | H | `J-External` runner | `M-STORE`: expected chooser with valid Addiyon link; cancel returns safely. | Invalid URI, no handler, crash. | Pending 2.0.0 |
| Rate App | H | `J-External` runner | `M-STORE`: expected store/browser once, or graceful error. | Missing handler, duplicate launch. | Pending 2.0.0 |
| Feedback sheet | H | `I-Settings.settingsRowsCallNavigationCallbacksAndFeedbackSheetOpens`, `I-Leaf` | `M-HW`: dismiss by swipe/back/outside and reopen; expected one usable sheet each time. | Undismissable/stale sheet. | Pending 2.0.0 |
| Feedback email | H | `J-External`, `I-External.feedbackEmailLaunchesDirectlyWithRecipientAndSubject` | `M-STORE`: expected mail chooser with reviewed subject or graceful error. | Wrong recipient/subject, no-handler crash. | Pending 2.0.0 |
| Feedback Telegram | H | `J-External`, `I-External` Telegram deep-link/fallback tests | `M-STORE`: expected intended account in Telegram/browser or graceful error. | Bad destination, fallback loop. | Pending 2.0.0 |
| Keyboard feedback shortcut | M | — | `M-REL`: open/cancel from toolbar; expected original field and text remain intact. | Lost focus/text, wrong return stack. | Pending 2.0.0 |

## 6. Keyboard startup, layout, and lifecycle

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| First keyboard open | H | `I-IME`, `B-IME` | `M-LIFE`: first focus after boot/install; expected complete IME without a long blank state. | Service/startup failure, blank view. | Pending 2.0.0 |
| Repeated open/close | H | `I-IME.serviceSurvivesVisibilityFieldSwitchRecreationAndImeSwitching` | `M-STRESS`: repeat 20 times; expected no crash, blank view, or growing delay. | Lifecycle leak, recreation race. | Pending 2.0.0 |
| App switching | H | `J-Compose`, `I-IME` session/connection swaps | `M-XAPP`: type across two apps; expected input only in active field. | Stale connection write. | Pending 2.0.0 |
| Field switching | H | `J-Compose`, `I-IME` field switching | `M-XAPP`: switch rapidly; expected no text in prior field. | Old-session mutation, stale callback. | Pending 2.0.0 |
| Keyboard switching | H | `I-IME` IME-switch survival | `M-LIFE`: switch away/back; expected usable layout and current preferences. | Destroy/recreate state loss. | Pending 2.0.0 |
| Language persistence | H | `J-Prefs`, `I-IME` | `M-LIFE`: close/reopen IME; expected selected English/Amharic mode retained. | Lost/malformed preference. | Pending 2.0.0 |
| Portrait layout | H | `J-Layout`, `I-Keyboard`, `I-Activity` | `M-VIS`: expected all rows/toolbar controls aligned and tappable. | Clipping, overlap, bad target. | Pending 2.0.0 |
| Landscape layout | H | `J-Layout` compact landscape metrics | `M-VIS`: expected no clipped keys and responsive typing. | Height/width overflow. | Pending 2.0.0 |
| System navigation area | H | `J-Prefs` navigation-icon palette logic, `I-Activity` insets | `M-VIS`: gesture/button modes; expected themed unobstructed navigation area. | Wrong contrast, inset overlap. | Pending 2.0.0 |
| Key preview | H | `I-Keyboard` preview lifecycle | `M-VIS`: hold keys; expected readable correctly placed preview that disappears. | Stuck/wrong glyph, bad position. | Pending 2.0.0 |
| Edge-key preview | M | — | `M-VIS`: hold far-edge keys; expected preview remains on-screen. | Window clipping. | Pending 2.0.0 |
| Cancelled press | H | `I-Keyboard` disposal safety, `I-Keyboard` Delete cancellation | `M-HW`: drag character press away; expected no insertion. | Cancel interpreted as tap. | Pending 2.0.0 |
| Fast typing | M | — | `M-STRESS`: type one minute; expected ordered input with no misses/doubles. | Event loss, duplication, jank. | Pending 2.0.0 |
| Toolbar stability | H | `I-Suggest` state transitions | `M-STRESS`: open/close repeatedly; expected stable size/content. | Resize flicker, stale mode. | Pending 2.0.0 |
| AI/clipboard placeholders | M | — | `M-REL`: tap each; expected no crash and no editor mutation. | Stub action mutates text/crashes. | Pending 2.0.0 |

## 7. English typing

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Basic alphabet | H | `J-Layout`, `I-IME` Latin typing, `I-IME.composingRegionFaultsInsertExplicitCharacterAtLiveCaret` | `M-REL`: type `a-z`; expected each lowercase character once in order. | Missing/wrong/doubled key output; rejected, throwing, or spanless composing-region host. | Pending 2.0.0 |
| One-shot Shift | H | `J-Layout`, `I-Keyboard.shiftChangesKeyLabelsThenACharacterTapConsumesOneShotShift` | `M-REL`: expected one uppercase then OFF. | Shift not consumed. | Pending 2.0.0 |
| Shift cancel | H | `J-Layout` shift state cycle | `M-REL`: two slow taps; expected lowercase and no text. | Accidental caps/text. | Pending 2.0.0 |
| Caps Lock | H | `J-Layout`, `I-IME` | `M-REL`: double-tap, type, unlock; expected uppercase until unlock. | Wrong transition/persistence. | Pending 2.0.0 |
| Auto-cap first word | H | `J-Enter` sentence start cases | `M-XAPP`: fresh normal field; expected first English letter uppercase. | Unarmed/stale auto-cap. | Pending 2.0.0 |
| Auto-cap sentence | H | `J-Enter` terminator/space cases | `M-XAPP`: sentence, period, space; expected next letter uppercase. | Early/late capitalization. | Pending 2.0.0 |
| Comma/period | H | `J-Layout`, `I-IME` punctuation | `M-REL`: expected punctuation after word with no deletion. | Composition replacement, wrong order. | Pending 2.0.0 |
| Space | H | `I-IME`, `J-Compose` | `M-REL`: repeated taps; expected exactly one space each. | Dropped/double space. | Pending 2.0.0 |
| Enter/newline | H | `J-Enter`, `I-IME` multiline | `M-XAPP`: multiline field; expected one newline. | Wrong editor action, double newline. | Pending 2.0.0 |
| Long-press letter | M | — | `M-HW`: long-press; expected direct character exactly once. | Tap+long-press double commit. | Pending 2.0.0 |
| Word backspace | H | `J-Compose`, `I-Keyboard.completedTapDispatchesExactlyOneDelete`, `I-IME.composingRegionFaultsDeleteBeforeLiveCaret` | `M-REL`: repeated taps; expected one visible character each. | Unit/cursor mismatch; rejected, throwing, or spanless composing-region host. | Pending 2.0.0 |
| Hold Delete | H | `I-Keyboard.heldDeleteRepeatsOnlyUntilRelease` | `M-HW`: long paragraph; expected smooth repeat and immediate stop. | Runaway/slow deletion. | Pending 2.0.0 |
| Delete cancellation | H | `I-Keyboard.cancelledDeleteGestureDispatchesNothing` | `M-HW`: drag away; expected repeat stops/no background delete. | Lost release/cancel. | Pending 2.0.0 |
| Resume edited word | H | `J-Compose`, `I-IME.insertingInsideComposingWordKeepsWholeWordAsSuggestionQuery` | `M-XAPP`: edit existing word; expected only that word changes. | Adjacent-space deletion, stale span. | Pending 2.0.0 |

## 8. Amharic typing and transliteration

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Basic example | A | `J-Translit` golden `selam` regression | None. | Greedy segmentation/table drift. | Pending 2.0.0 |
| Bare vowels | A | `J-Translit` lowercase/uppercase bare-vowel corpus | None. | Bare/consonant vowel table conflation. | Pending 2.0.0 |
| Consonant orders | A | `J-Translit.TransliterationPropertyTest` exhaustive family/order test | None. | Wrong order index or longest vowel. | Pending 2.0.0 |
| Digraphs | A | `J-Translit` digraph and split-reading tests | None. | Non-greedy family selection. | Pending 2.0.0 |
| Case-sensitive families | A | `J-Translit` shifted family corpus | None. | Incorrect case-fold fallback. | Pending 2.0.0 |
| Shifted previews | H | `J-Translit` every layout key both shifts, `I-Keyboard` preview render | `M-VIS`: toggle shift; expected affected Fidel preview family updates. | Stale/wrong corner glyph. | Pending 2.0.0 |
| One-shot Amharic Shift | H | `J-Layout`, `I-Keyboard` | `M-REL`: type shifted key; expected shifted family once then OFF. | Shift not consumed. | Pending 2.0.0 |
| Amharic Caps Lock | H | `J-Layout` | `M-REL`: double-tap/type/unlock; expected shifted families persist only while locked. | Wrong state/family. | Pending 2.0.0 |
| Amharic punctuation | A | `J-Translit` Ethiopic punctuation mapping | None. | ASCII punctuation leak. | Pending 2.0.0 |
| Commit on space | H | `J-Compose`, `I-IME` Amharic word+space | `M-XAPP`: expected unchanged Fidel, ended composition, one space. | Re-render/corruption, live span. | Pending 2.0.0 |
| Commit on mode change | H | `J-Compose`, `I-IME` language transition | `M-REL`: switch mid-word; expected completed Fidel remains intact. | Composition cleared/replaced. | Pending 2.0.0 |
| Fidel-unit delete | H | `J-Compose` unit deletion, `I-IME` Amharic delete regressions | `M-REL`: type `she`, delete; expected whole rendered unit removed. | Latin-char instead of Fidel-unit delete. | Pending 2.0.0 |
| Mixed text | H | `J-Translit` boundary/Unicode properties, `I-IME` mixed typing | `M-XAPP`: expected Amharic/English/numbers remain ordered and spaced. | Cross-mode stale buffer. | Pending 2.0.0 |
| Cursor edit | H | `J-Compose`, `I-IME` committed Amharic cursor/delete tests | `M-XAPP`: edit middle; expected surrounding Fidel unchanged. | Resume/caret corruption. | Pending 2.0.0 |
| Long-press Amharic key | M | — | `M-HW`: long-press preview key; expected displayed Fidel exactly once. | Preview/output mismatch, double commit. | Pending 2.0.0 |
| Unknown sequence safety | H | `J-Translit` deterministic Unicode, malformed-surrogate, boundary and pass-through properties | `M-STRESS`: long unusual sequence rapidly; expected responsive usable lossless output. | Hang, loss, malformed UTF-16. | Pending 2.0.0 |

## 9. Suggestions and predictions

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| English suggestions | H | `J-Suggest` completion/ranking/SQLite contracts and `PerWordCacheTest`, `I-Store`, `I-Suggest`, `I-IME.composingPrefixesAndBackspacesCapturePriorContextOnlyOncePerWord` | `M-REL`: common prefix; expected relevant nonblocking chips. | Empty/irrelevant/blocking results; store install/recovery failure; repeated editor-context reads while editing one word. | Pending 2.0.0 |
| English typo correction | H | `J-Suggest` fuzzy matcher/ranker | `M-REL`: small misspelling; expected sensible near match. | Unbounded/irrelevant fuzzy result. | Pending 2.0.0 |
| English suggestion tap | H | `J-Compose.commitSuggestion`, `I-Suggest.suggestionTapCallsTheProvidedHandler`, `I-IME.staleSuggestionTapCannotMutateANewerStateOrPrivateField` | `M-XAPP`: expected active word replaced once with correct case/space. | Double commit, wrong span, stale-generation commit, mutation of a newer/private field. | Pending 2.0.0 |
| English next-word prediction | H | `J-Suggest` n-gram/cache assets, `I-Suggest` pending-to-prediction state, `I-IME.realEnglishAndAmharicPredictionsPublishAndCommitOnce`, `I-IME.sameCaretHostReplacementInvalidatesThePredictionBoundary`, `B-Prediction.warmEnglishNextWordRequestToPublication` | `M-STRESS`: common word+Space on the recorded reference phone; expected prediction appears and inserts once, with warm request-to-publication p95 below 80 ms across the benchmark run. | Toolbar flicker, stale/slow context, stale result after same-caret same-length host replacement; latency trace separates queue, n-gram query, and publication. | Pending 2.0.0 |
| Amharic alternatives | A | `J-Translit` candidate lattice and ranker tests | None. | Missing/deduplicated alternate. | Pending 2.0.0 |
| Amharic completion | H | `J-Suggest` prefix completion/ranking | `M-REL`: common prefix; expected useful completion that inserts once. | Bad prefix/family ranking. | Pending 2.0.0 |
| Amharic next-word prediction | H | `J-Suggest` Amharic n-gram/cache assets, `I-IME.realEnglishAndAmharicPredictionsPublishAndCommitOnce`, `B-Prediction.warmAmharicNextWordRequestToPublication` | `M-STRESS`: common Fidel word+Space on the same recorded reference phone; expected prediction appears and inserts once, with warm request-to-publication p95 below 80 ms across the benchmark run. | Missing/slow Amharic publication, stale context, language leakage, latency regression. | Pending 2.0.0 |
| Suggestion after delete | H | `J-Suggest` cache/generation primitives, `J-Compose`, `I-IME` delete suggestions | `M-STRESS`: delete/retype rapidly; expected only current-text results. | Stale publication. | Pending 2.0.0 |
| Rapid language switch | H | `J-Suggest` language-separated cache, `I-Suggest` state | `M-STRESS`: switch while loading; expected no old-language result. | Generation/language leak. | Pending 2.0.0 |
| Long input performance | H | `J-Suggest` bounded fuzzy/cache limits | `M-STRESS`: long nonword; expected responsive typing and no frozen strip. | Unbounded work/backlog. | Pending 2.0.0 |
| No suggestion overwrite | H | `J-Compose` explicit commit semantics | `M-REL`: ignore chips and continue; expected no automatic word replacement. | Implicit commit/autocorrect. | Pending 2.0.0 |
| Selection safety | H | `J-Compose`, `I-IME.staleSuggestionTapCannotMutateANewerStateOrPrivateField`, `I-IME.selectionChangesNeverMutateCommittedUnicodeText` | `M-XAPP`: select/type/tap suggestion; expected only current selection/word changes. | Stale range/generation, adjacent text overwrite, committed mixed-Unicode mutation from forward, reverse, or range selection callbacks. | Pending 2.0.0 |
| Email suggestions | H | `J-Suggest.EmailSuggestionsTest`, `I-Suggest.emailStateShowsSuffixAndCommitsFullAddress` | `M-XAPP`: type local part+`@`; expected domain chip completes whole address once. | Partial token, wrong suffix/case. | Pending 2.0.0 |

## 10. Language, number, symbol, and keypad modes

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Language key | H | `J-Layout`, `I-IME` | `M-REL`: expected one English/Amharic toggle per tap. | Double/missed transition. | Pending 2.0.0 |
| Space swipe left | M | — | `M-HW`: swipe left; expected language changes with no space. | Gesture commits space/misses mode. | Pending 2.0.0 |
| Space swipe right | M | — | `M-HW`: swipe right; expected language changes with no space. | Gesture commits space/misses mode. | Pending 2.0.0 |
| Short space drag | M | — | `M-HW`: short drag; expected no language change or accidental commit beyond tap policy. | Threshold too small. | Pending 2.0.0 |
| Number mode entry | H | `J-Layout`, `I-Keyboard` numeric cycle | `M-REL`: enter from both languages; expected standard number page. | Wrong page/language state. | Pending 2.0.0 |
| Number keys | H | `J-Layout` key inventory | `M-REL`: type every label; expected matching digit/operator once. | Label/output mismatch. | Pending 2.0.0 |
| Amharic number page | H | `J-Layout`, `I-Keyboard.amharicNumberAndSymbolsTogglesCycleThroughAllNumericPages` | `M-REL`: expected representative Ethiopic numerals/punctuation. | Missing/wrong page. | Pending 2.0.0 |
| Symbol page cycle | H | `J-Layout`, `I-Keyboard` | `M-REL`: cycle pages; expected deterministic number/Ethiopic/more-symbol sequence. | Skipped/stuck page. | Pending 2.0.0 |
| More symbols | H | `J-Layout` inventory | `M-REL`: test listed symbols; expected label-matching commits. | Missing/incorrect symbol. | Pending 2.0.0 |
| Return to letters | H | `J-Layout` control invariants | `M-REL`: from every page; expected active-language letters directly. | Wrong intermediate/page/language. | Pending 2.0.0 |
| Language in symbols | H | `J-Layout` | `M-REL`: toggle language on symbol page; expected consistent mode/layout. | Mode-language desync. | Pending 2.0.0 |
| Phone-style keypad | H | `J-Layout` keypad geometry/inventory | `M-REL`: expected digits/controls commit correctly. | Wrong grid/output. | Pending 2.0.0 |
| Exit keypad | H | `J-Layout` | `M-REL`: symbol toggle; expected full number/symbol page. | Stuck keypad. | Pending 2.0.0 |
| Numeric field auto-layout | H | `J-Enter` input classification, `I-IME` number/phone hosts | `M-XAPP`: phone/number/date-time fields; expected suitable numeric layout. | Wrong initial mode. | Pending 2.0.0 |
| Number-row interaction | H | `J-Layout`, `I-Settings` | `M-REL`: both languages and emoji search; expected digits go to intended target. | Field/search routing error. | Pending 2.0.0 |

## 11. Emoji

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Emoji open | H | `J-Layout`, `B-IME` journey | `M-VIS`: expected panel replaces keys without window-height change or editor mutation. | Height jump, stale composition. | Pending 2.0.0 |
| Initial loading | H | `J-Emoji` parser/load data | `M-REL`: first open; expected loading resolves to grid without crash. | Asset/load failure, stuck state. | Pending 2.0.0 |
| Category tabs | M | — | `M-VIS`: tap every tab; expected grid reaches matching section. | Wrong index/scroll target. | Pending 2.0.0 |
| Emoji scrolling | M | — | `M-STRESS`: rapid end-to-end scroll; expected populated smooth grid. | Blank/stuck cells, jank. | Pending 2.0.0 |
| Emoji commit | H | `J-Emoji`, `I-IME` emoji journey | `M-XAPP`: several categories; expected each emoji once at caret. | Double/wrong-position commit. | Pending 2.0.0 |
| Emoji after word | H | `J-Compose` finalization | `M-XAPP`: open mid-composition; expected word finalizes then emoji follows. | Word loss/replacement. | Pending 2.0.0 |
| Skin tone | H | `J-Emoji.SkinToneStoreTest` | `M-HW`: long-press and select each tone; expected chosen variant once. | Gesture/tone mapping error. | Pending 2.0.0 |
| Skin-tone persistence | H | `J-Emoji.SkinToneStoreTest`, `J-Prefs` Unicode-safe value bounds | `M-LIFE`: reopen panel/IME; expected chosen tone remains. | Lost/corrupt stored emoji. | Pending 2.0.0 |
| Recents | H | `J-Emoji.RecentEmojiStoreTest` | `M-LIFE`: use/reopen; expected sensible deduplicated order. | Ordering/eviction/persistence error. | Pending 2.0.0 |
| Emoji search | H | `J-Emoji.EmojiSearchTest` | `M-REL`: common term; expected relevant tappable results. | Bad normalization/ranking. | Pending 2.0.0 |
| No-result search | H | `J-Emoji.EmojiSearchTest` | `M-VIS`: nonsense query; expected clear prompt empty state without lag. | Stale results, blank ambiguity. | Pending 2.0.0 |
| Search editing | H | `J-Emoji` search/backspace primitives | `M-XAPP`: move/select/clear/delete query; expected only query changes. | Host field mutation, stale cursor. | Pending 2.0.0 |
| Search Shift/numbers | H | `J-Layout` shift/row behavior | `M-REL`: expected query receives correct case/digits. | Routing into host editor. | Pending 2.0.0 |
| Exit search | M | — | `M-REL`: exit to browse; expected categories remain usable. | Stuck search/stale filter. | Pending 2.0.0 |
| Emoji Delete | H | `J-Emoji.EmojiBackspaceTest`, `I-Keyboard` Delete dispatch | `M-XAPP`: expected preceding grapheme/text deleted safely. | Split emoji/surrogate, runaway delete. | Pending 2.0.0 |
| Emoji back/close | H | `B-IME` journey | `M-REL`: expected prior-language keyboard restored. | Wrong language/mode/height. | Pending 2.0.0 |

## 12. Voice input

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| First permission request | M | — | `M-VOICE`: unset permission then tap mic; expected one Android prompt. | Missing/repeated request. | Pending 2.0.0 |
| Permission grant | M | — | `M-VOICE`: grant; expected stable visible listening state. | Lost result, hidden/blank IME. | Pending 2.0.0 |
| Permission denial | M | — | `M-VOICE`: deny on fresh-data run; expected clear state and ordinary typing. | Dead keyboard, prompt loop. | Pending 2.0.0 |
| English dictation | H | `J-Voice` composition/refinement | `M-VOICE`: dictate English; expected partial/final sentence once. | Duplication, wrong locale/spacing. | Pending 2.0.0 |
| Amharic dictation | H | `J-Voice` Ethiopic punctuation/composition | `M-VOICE`: dictate Amharic; expected `am-ET` result once. | Wrong locale, duplicate text. | Pending 2.0.0 |
| Pause/resume | H | `J-Voice` session ownership/finalization | `M-VOICE`: pause/resume; expected visible text retained and not duplicated. | Late callback, lost partial. | Pending 2.0.0 |
| Exit voice mode | H | `J-Voice` invalidation | `M-VOICE`: back arrow; expected recognition stops and keys/suggestions return. | Live recognizer/stale UI. | Pending 2.0.0 |
| Type during voice mode | H | `J-Voice` ownership | `M-VOICE`: press key while active; expected voice ends and key inserts once. | Race/duplicate commit. | Pending 2.0.0 |
| Switch mode during voice | H | `J-Voice` invalidation | `M-VOICE`: language/emoji/numbers; expected dictation stops safely. | Late result into new mode. | Pending 2.0.0 |
| Silence/network error | H | `J-Voice.VoiceRecoveryPolicyTest` | `M-VOICE`: trigger both; expected recoverable message and no crash. | Retry storm, fatal UI. | Pending 2.0.0 |
| Repeated voice sessions | H | `J-Voice` generation/ticket tests | `M-STRESS`: ten sessions; expected no stuck mic or duplicate result. | Ownership leak, late callbacks. | Pending 2.0.0 |

## 13. Field types, actions, and privacy

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Password field | H | `J-Privacy`, `I-IME.realServiceTypesAcrossFieldKindsAndEditorActions` | `M-XAPP`: expected suggestions/email chips/previews/mic hidden or disabled. | Private UI/data exposure. | Pending 2.0.0 |
| Password isolation | H | `J-Privacy` private telemetry/suggestion policy, `I-IME.staleSuggestionTapCannotMutateANewerStateOrPrivateField` | `M-XAPP`: unique secret then normal field; expected it never appears elsewhere. | Cache/telemetry leak; stale chip mutation after switching to a private field. | Pending 2.0.0 |
| Visible/web password | A | `J-Privacy.InputTypePolicyTest` covers both variations | None for classification; cross-host rendering remains under Browser row. | Missed private variation. | Pending 2.0.0 |
| Email Latin output | H | `J-Enter` email classification, `I-IME` email host | `M-XAPP`: start in Amharic, type address; expected Latin only. | Transliteration in address. | Pending 2.0.0 |
| Email capitalization | H | `J-Enter` email no-auto-cap | `M-XAPP`: expected lowercase start/address. | Auto-cap leak. | Pending 2.0.0 |
| URI field | H | `J-Enter` URI no-auto-cap | `M-XAPP`: expected no unexpected capitalization/transliteration. | URL corruption. | Pending 2.0.0 |
| Single-line Done | H | `J-Enter`, `I-IME` Done action | `M-XAPP`: expected Done once and no newline. | Wrong action/double/newline. | Pending 2.0.0 |
| Search action | H | `J-Enter`, `I-IME` Search action | `M-XAPP`: expected Search exactly once. | Swallowed/double action. | Pending 2.0.0 |
| Send action | H | `J-Enter`, `I-IME` Send action | `M-XAPP`: expected Send exactly once. | Swallowed/double action. | Pending 2.0.0 |
| Next/Previous actions | H | `J-Enter` exhaustive mapping | `M-XAPP`: multi-field form; expected focus moves once in named direction. | Wrong ID/direction. | Pending 2.0.0 |
| Go action | H | `J-Enter` exhaustive mapping | `M-XAPP`: URL/navigation field; expected Go once. | Wrong ID/newline. | Pending 2.0.0 |
| Multiline override | H | `EnterActionPolicyTest.noEnterActionFlagOverridesEveryDeclaredAction`, `explicitActionWinsWhenHostAlsoSetsMultilineFlag`, and `multilineWithoutAnExplicitActionResolvesToNewline` (`J-Enter`) | `M-XAPP`: verify representative multiline hosts; expected `IME_FLAG_NO_ENTER_ACTION` to produce newline, otherwise an explicit editor action wins even when multiline is set, and multiline with no explicit action produces newline. | Incorrect precedence among `NO_ENTER_ACTION`, explicit actions, and multiline input flags. | Pending 2.0.0 |
| Read-only/no editor | M | — | `M-XAPP`: tap non-editable area; expected IME absent and no crash. | Invalid/no connection handling. | Pending 2.0.0 |
| Fresh diagnostics defaults | H | `J-Privacy.TelemetryConsentPolicyTest`, `I-Settings.diagnosticsChoicesStartOffAndPersistIndependently` | `M-LIFE`: clear release app data; expected independent Analytics/Crash controls, both off. | Accidental default collection, coupled controls. | Pending 2.0.0 |
| Diagnostics persistence | H | `J-Privacy` independent consent policy and fail-closed save-failure coverage, `I-Privacy.realPreferencesPersistIndependentChoicesAcrossControllerReinitialization`, `clearingDedicatedConsentStoreIsDurablyAllOffAcrossReinitialization`, `malformedPersistedValuesAreRepairedToOff`, `I-Settings.diagnosticsPersistenceFailureStaysOnScreenAndShowsRetryMessage` | `M-LIFE`: enable each choice separately and force-stop/relaunch the exact release; expected only selected choices persist on this device. | Lost or coupled consent; failed revocation re-enabling after restart; unreported storage failure; malformed values enabling collection or remaining unrepaired. | Pending 2.0.0 |
| Consent is not restored | H | `J-Privacy.FirebaseManifestContractTest` verifies backup/transfer exclusions | `M-LIFE`: restore or transfer to another release phone; expected diagnostics off while eligible ordinary settings may restore. | Consent silently inherited by another device. | Pending 2.0.0 |
| Analytics privacy | H | `J-Privacy` enum-only API, reviewed schema, private-field suppression, and static negative audits for raw key/Space/Delete, transliteration, composition, cursor, email, and voice-transcript paths; `I-IME.telemetryEmitsOncePerNonRestartingNonPrivateSessionAndOnlyTypedActions` fake-backend service integration | `M-REL`: opt in and inspect Firebase DebugView during the complete stated input sequence; expected no content-bearing event and no private-field custom event. | Duplicate/restart/private session events; typed/editor/private data exposure. | Pending 2.0.0 |
| Analytics revocation | H | `J-Privacy.TelemetryConsentPolicyTest` verifies collection disable and local reset calls | `M-REL`: revoke while watching DebugView; expected no future events and reset local Analytics state. | Post-revocation collection or stale identity. | Pending 2.0.0 |
| Sanitized non-fatal | H | `J-Privacy.SanitizedNonFatalTest`, telemetry API/static tests | `M-REL`: with crash consent, run debug-only command and inspect Firebase; expected fixed category/coarse class, allowlisted frames, and no message/cause/suppressed data. | Original exception content or foreign frames uploaded. | Pending 2.0.0 |
| Crash revocation | H | `J-Privacy.TelemetryConsentPolicyTest` verifies queued-report deletion call | `M-LIFE`: queue a report, revoke, relaunch; expected queued deletion and no later upload. | Post-revocation report transmission. | Pending 2.0.0 |
| Minified fatal deobfuscation | M | Debug/internal-only fatal command and R8 source/line attributes exist; no console proof is automatable locally | `M-REL`: exact minified internal artifact on release phone; expected deobfuscated file/line and no typed/editor data after relaunch/upload. | Missing mapping upload, obfuscated stack, sensitive crash payload. | Pending 2.0.0 |
| Release telemetry surface | H | `J-Privacy.FirebaseManifestContractTest`, `plans/verify-release-artifact.sh` static gates | `M-REL`: pin the production Firebase app/project IDs in `version.properties`, then inspect the config-enabled merged manifest and AAB; expected the exact pinned Firebase identity, no advertising permissions, debug components, or crash commands. | Wrong Firebase app/project, transitive permission, test surface in release. | Pending 2.0.0 |

## 14. Cross-app compatibility

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Messages/chat | M | — | `M-XAPP`: exercise typing/edit/suggest/emoji/voice/send; expected each action once in active chat field. | Host composition/action incompatibility. | Pending 2.0.0 |
| Notes/document | M | — | `M-XAPP`: long mixed multiline middle edits; expected exact surrounding text retained. | Long-document/caret corruption. | Pending 2.0.0 |
| Browser | M | — | `M-XAPP`: address/search/email/password; expected correct privacy/layout/action policy per field. | WebView/input-type variance. | Pending 2.0.0 |
| Email client | M | — | `M-XAPP`: recipient/subject/body; expected Latin recipient and normal multiline body. | Field-policy carryover. | Pending 2.0.0 |
| Phone/dialer | M | — | `M-XAPP`: phone field; expected keypad and safe Delete. | Wrong layout/action. | Pending 2.0.0 |
| Web form | M | — | `M-XAPP`: text/number/email/password/Next/Done; expected correct field isolation and one action each. | Web editor variance, stale field. | Pending 2.0.0 |
| Copy/paste selection | H | `J-Compose`, `I-IME.selectionChangesNeverMutateCommittedUnicodeText` and selection/session coverage | `M-XAPP`: host select/copy/paste then type; expected correct live caret and unchanged unrelated text. | Stale snapshot/span; callback-driven mutation of mixed Unicode. | Pending 2.0.0 |
| Autofill interaction | M | — | `M-XAPP`: accept Android autofill then type; expected autofilled text/caret preserved and new input at live cursor. | Stale composition overwrites autofill. | Pending 2.0.0 |

## 15. Stress, recovery, and performance

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Cold app launch | H | `B-Startup.coldStartupWithProfile`, `coldStartupWithoutProfile` | `M-STRESS`: force-stop/open the exact release and record p50/p95. On the same phone compare a no-op-config build with the Firebase-configured build, including first-key latency; expected no material diagnostics startup regression or blank frame. | Startup/first-key regression, Firebase provider cost, blank frame. | Pending 2.0.0 |
| Cold keyboard launch | H | `B-IME`, `I-IME` recreation, `I-Store.badChecksumIsAtomicallyReinstalledAndInterruptedBackupIsRestored` | `M-STRESS`: clear app data, force-stop, then focus English and Amharic in separate fresh runs. Record `Addiyon.database_install_open` duration and time-to-usable keyboard for both databases; expected typing remains usable while preparation completes, with no blank/crash loop. | Service restart, unmeasured first database install/open, checksum, interrupted-swap recovery, or typing-path block. | Pending 2.0.0 |
| Cold database preparation | H | `I-Store` install/checksum/restore/low-space correctness plus the `Addiyon.database_install_open` trace | `M-STRESS`: in separate fresh-data English and Amharic runs, record the install/open duration and time-to-usable keyboard; expected preparation stays off the typing path and reaches ready or a terminal fallback. | Unbounded copy/checksum/open, blocked typing, stuck loading, language-specific cold regression. | Pending 2.0.0 |
| Prediction latency | H | `B-Prediction` English and Amharic trace metrics | `M-STRESS`: run both methods on the same recorded reference phone; each warm request-to-publication p95 must be below 80 ms. Emulator dry-runs validate wiring only. | English/Amharic latency regression, queue blocking, misleading emulator evidence. | Pending 2.0.0 |
| Firebase startup delta | M | — | `M-STRESS`: compare no-op-config and production-Firebase-configured builds on the same phone; record cold app, cold keyboard, and first-key p50/p95 and reject a material regression. | Provider/startup cost hidden by a no-op local build. | Pending 2.0.0 |
| Rapid key stress | M | — | `M-STRESS`: alternate keys for 30 seconds; expected no freeze/ANR/missing burst. | Queue overload/event loss. | Pending 2.0.0 |
| Language stress | H | `J-Suggest` separated caches/generations | `M-STRESS`: switch 20 times while typing; expected correct layout/results only. | Stale language work. | Pending 2.0.0 |
| Mode stress | H | `J-Layout`, `B-IME` | `M-STRESS`: cycle all modes 20 times; expected correct layout each cycle. | State-machine desync. | Pending 2.0.0 |
| Settings stress | H | `I-Settings`, `J-Prefs` | `M-STRESS`: change theme/height/row/sound/vibration with IME active; expected safe live updates. | Listener/race/stale prefs. | Pending 2.0.0 |
| Large text stress | H | `J-Compose` bounded windows, `I-IME.selectionChangesNeverMutateCommittedUnicodeText` verifies selection-only invariance across at least 4,000 UTF-16 code units of mixed Unicode | `M-STRESS`: perform several-thousand-character middle/end edits on the release phone; expected responsive exact text. | Full-document work, stale offsets; selection-only corruption is automated, edit performance and fidelity remain physical residuals. | Pending 2.0.0 |
| Long Delete stress | H | `I-Keyboard.continuousHoldKeepsRepeatingUntilRelease` | `M-STRESS`: delete hundreds; expected responsive immediate stop. | Runaway repeat/ANR. | Pending 2.0.0 |
| Low-memory return | H | `DeviceMemoryPolicyTest`, `J-Suggest` bounded caches, `I-Store.concurrentCallbacksShareOneInstallAndReleaseReloadsTheStore` | `M-LIFE`: apply memory pressure and return; expected recreated IME accepts input. | OOM, dead worker/view, or unreloadable released store. | Pending 2.0.0 |
| Screen lock | M | — | `M-LIFE`: lock/unlock with composition; expected no duplicate/corrupt text and continued typing. | Token/session mismatch. | Pending 2.0.0 |
| Incoming interruption | M | — | `M-LIFE`: notification/call overlay; expected return to correct field with safe composition. | Focus/connection swap race. | Pending 2.0.0 |
| Airplane/offline mode | H | Local asset/query tests prove no network dependency for core features | `M-REL`: offline non-voice pass; expected typing/transliteration/suggestions/themes/emoji work. | Hidden network dependency. | Pending 2.0.0 |
| Reboot persistence | M | — | `M-LIFE`: reboot; expected default IME and saved preferences correct. | Boot/persistence loss. | Pending 2.0.0 |
| One-hour normal use | M | — | `M-STRESS`: default IME across normal apps for one hour; expected no crash, ANR, or worsening lag. | Thermal/memory/lifecycle degradation. | Pending 2.0.0 |

## Final sign-off

| Checklist behavior | Status | Automated evidence | Manual residual / expected result | Failure modes covered | Last release |
|---|---:|---|---|---|---|
| Main Activity passed | M | Sections 1–5 automation | All Sections 1–5 rows must name the exact release and have no blocker. | Incomplete app/onboarding/settings evidence. | Pending 2.0.0 |
| Keyboard passed | M | Sections 6–14 automation | All Sections 6–14 rows must name the exact release and have no blocker. | Incomplete typing/mode/privacy evidence. | Pending 2.0.0 |
| Reliability passed | M | Section 15 benchmarks/tests | All Section 15 rows must name the exact release and have no blocker. | Incomplete lifecycle/stress evidence. | Pending 2.0.0 |
