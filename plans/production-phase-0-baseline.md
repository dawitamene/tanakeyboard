# Production Phase 0 Baseline

Captured: 2026-07-25, Africa/Addis_Ababa

## Decision

Phase 0 local verification is complete, but this working tree is **not yet a
frozen production candidate**.

The local build, test, signing, packaging, update-install, typing smoke, and
bounded stress gates passed. Production submission remains a no-go until the
source tree is reviewed and committed, the Play closed-track artifact is tested
as both a fresh install and an update, and the missing physical/low-memory
measurements are collected.

No commit or tag was created because the working tree already contained
uncommitted product changes before this phase started.

## Source identity

| Field | Value |
| --- | --- |
| Branch | `main` |
| HEAD | `9205a23e487fe84143de931f6abc496b00f09b47` |
| Git commit count | `69` |
| Source status | Dirty |
| Package | `com.addiyon.keyboard` |
| Version name | `1.9.2` |
| Version code | `69` |
| Minimum SDK | `24` |
| Target SDK | `36` |

The release version code is `max(versionCodeFloor=68, gitCommitCount=69)`.
Because uncommitted source is included in the artifacts, the commit hash alone
does not reproduce this build.

## Corrections made during verification

1. `KeyboardScreen.kt` now uses an explicit `else` block around
   `KeyboardSuggestionArea`. This resolves the only release-lint error without
   changing behavior.
2. `OnboardingScreenUiTest` now explicitly marks the one-time feature tour as
   seen before testing the completed-tour path. The test no longer depends on
   preferences left behind by another device test.

## Verification gates

| Gate | Result | Evidence |
| --- | --- | --- |
| JVM unit tests | Pass | 324 tests, 0 failures, 0 errors, 0 skipped |
| Release lint | Pass | 0 errors, 45 warnings |
| Focused onboarding device tests | Pass | 3 tests |
| Full connected device tests | Pass | 23 tests, 0 failures, 0 errors, 0 skipped |
| Debug install | Pass | Installed on `TanaLowRam(AVD) - 15` |
| Debug assembly | Pass | Timestamped APK copied to `/Users/dev/Shared` |
| Minified release APK | Pass | Built and R8-processed |
| Signed release AAB | Pass | Built and JAR signature verified |
| Production APK update install | Pass | Installed version `1.9.2 (69)` |
| Post-stress crash/ANR scan | Pass | No matching crash, ANR, SQLite corruption, leak, or OOM entry |

Commands used:

```text
./gradlew testDebugUnitTest --rerun-tasks
./gradlew lintRelease
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addiyon.keyboard.ui.onboarding.OnboardingScreenUiTest
./gradlew connectedAndroidTest
./gradlew installDebug assembleDebug assembleRelease bundleRelease
```

The remaining lint warnings are non-blocking dependency-update, unused-resource,
Compose modifier/autoboxing, icon mirroring, and API deprecation findings.
Dependency upgrades were not mixed into the release-freeze phase.

## Release artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug APK | 46,506,392 | `1860c1ed7c76ff417b38ebd0bfafa2a19abc4dc1f61f3395dcd6c5803acd6af2` |
| Release APK | 24,501,752 | `2839ceada2d5602c96807311dd7e5105cbd5d2cb23acf053850503d9f9449db9` |
| Release AAB | 27,166,711 | `44657a639dd29dca76e8b4c44e6ebe78e1d73dd1dcd0771225129443d31be689` |
| R8 mapping | 39,696,769 | `c954e15e0d47610935ab81fa8fa4218c6693655dedf7e1b95b00fed0df928c5b` |
| Universal diagnostic APK set | 24,621,844 | `b2705ab485d17f3aefc368b6f7fc52b165a4f799ba774c75529f359b019733dd` |
| Device diagnostic APK set | 25,814,325 | `879547889ace75d8fff8d1e671438eb35f474147ba6aae282625a6791b226b09` |
| Extracted universal diagnostic APK | 24,621,539 | `a8320062970cecd88cd2d9fe687f5dad8808edfa9d0f8b21ef2a203984dc6e71` |

The fresh artifacts were produced at approximately 15:20-15:37 EAT. The
repository hook also produced:

- `/Users/dev/Shared/addiyon-debug-v1.9.2-debug-69-2026-07-25-03-30-PM.apk`
- `/Users/dev/Shared/addiyon-release-v1.9.2-69-2026-07-25-03-32-PM.apk`
- `/Users/dev/Shared/addiyon.apk`

No native debug-symbol archive was produced. The only native library is the
third-party `libandroidx.graphics.path.so`.

## Signing and package validation

The release APK verifies with APK Signature Scheme v2. The AAB verifies with
`jarsigner`.

Expected and actual release certificate SHA-256:

```text
7277455ac812a6a8eeec2f7b3e8097861d3bd17b0febebf39c8211a57cdb99cf
```

The certificate is a 2048-bit RSA self-signed upload certificate valid through
2053-11-22. The AAB's self-signed-chain and missing-timestamp warnings are
expected for this upload certificate; its cryptographic signature still
verifies.

Bundletool 1.18.1 generated:

- a universal diagnostic APK set;
- an Android 15 arm64/English/xxhdpi device APK set.

Bundletool uses the Android debug keystore unless release credentials are
provided. Android correctly rejected an attempt to update the already
production-signed installed app with that debug-signed diagnostic set. The
fresh production-signed release APK then updated successfully.

The diagnostic APK sets are not substitutes for the Play-delivered artifact.

## Artifact contents

Verified in both the release APK and AAB:

| Content | Size in AAB |
| --- | ---: |
| `amharic.db` | 39,108,608 bytes |
| `english.db` | 13,750,272 bytes |
| `dictionary_manifest.properties` | 261 bytes |
| `emoji.dat` | 45,298 bytes |
| Binary baseline profile | 5,183 bytes |
| Binary baseline profile metadata | 582 bytes |
| Embedded R8 mapping | 39,696,769 bytes |

The dictionary manifest reports schema version 5 and hashes matching the
packaged databases. No raw `*_words.dat` or `*_ngrams.dat` payload was found.

The APK passes:

```text
zipalign -c -P 16 -v 4
```

All `LOAD` segments of `libandroidx.graphics.path.so` for arm64-v8a,
armeabi-v7a, x86, and x86_64 use `2**14` alignment, so the packaged native
library satisfies 16 KiB page-size alignment.

## Manifest review

Expected permissions:

- `android.permission.RECORD_AUDIO`;
- the AndroidX-generated non-exported dynamic receiver permission scoped to
  `com.addiyon.keyboard`.

Expected exported components:

- `AddiyonKeyboardService`, protected by
  `android.permission.BIND_INPUT_METHOD`;
- `MainActivity`, the launcher;
- AndroidX `ProfileInstallReceiver`, protected by
  `android.permission.DUMP`.

The settings, themes, manual, feedback, voice-permission, and Play Core wrapper
activities are not exported. The AndroidX Startup provider is not exported.
No unexpected network, storage, wake-lock, overlay, or broad package-access
permission is present.

## Baseline profile finding

The compiled profile is present in the final APK and AAB. The hand-maintained
source profile still contains:

```text
Lcom/addiyon/keyboard/transliteration/AmharicComposer;
```

No matching source class exists. This does not create a runtime crash, but it
means the profile is partly stale and cannot be treated as a measured startup
optimization. Replace it with a generated profile and fail verification on
removed app classes during the benchmark/profile phase.

## Emulator baseline

Device:

| Field | Value |
| --- | --- |
| AVD label used by tests | `TanaLowRam(AVD) - 15` |
| Model | `sdk_gphone64_arm64` |
| Android | 15 |
| API | 35 |
| Resolution | 1080 x 2220 |
| Density | 440 dpi |
| Reported RAM | 2,532,368 KiB, approximately 2.42 GiB |

Despite its name, the running emulator is not a 1 GiB device. These results
therefore do not satisfy the plan's 1 GiB acceptance environment.

### Launcher timing

Five `am start -W` samples were captured for each state.

| State | Samples | Median | Mean | Range |
| --- | --- | ---: | ---: | ---: |
| Cold total time | 2183, 1384, 777, 1061, 913 ms | 1061 ms | 1264 ms | 777-2183 ms |
| Warm total time | 547, 519, 487, 571, 598 ms | 547 ms | 544 ms | 487-598 ms |
| Hot-resume wait time | 141, 97, 179, 249, 106 ms | 141 ms | 154 ms | 97-249 ms |

The emulator startup numbers are regression evidence only. Absolute release
thresholds require Macrobenchmark on fixed physical hardware.

### Real-key typing smoke

The production IME was selected and exercised through actual on-screen key
coordinates in the in-app test field.

- Amharic sequence `s`, `e`, `l`, `a`, `m`, space produced `ሰላም `.
- After switching language, `h`, `e`, `l`, `l`, `o`, space produced
  `ሰላም hello `.
- The bounded stress sequence added 15 English words, 15 Amharic sequences,
  delete repeat, six language switches, and emoji open/close.
- The service stayed alive and the post-run crash/ANR scan was empty.

### Frame timing

The ADB-coordinate stress run rendered 695 frames:

| Metric | Value |
| --- | ---: |
| Deadline-missed frames | 608 / 695, 87.48% |
| 50th percentile | 53 ms |
| 90th percentile | 93 ms |
| 95th percentile | 109 ms |
| 99th percentile | 150 ms |
| Missed vsync events | 218 |
| High-input-latency events | 178 |
| Slow UI-thread events | 375 |

This is a serious regression baseline signal, but not a production
pass/fail measurement: rapid serialized ADB taps and the emulator's graphics
pipeline contaminate the result. Phase 4 must replace it with repeatable
Macrobenchmark frame and trace metrics on a physical device.

### Memory and SQLite after stress

Three post-idle samples:

| Metric | Sample 1 | Sample 2 | Sample 3 |
| --- | ---: | ---: | ---: |
| Total PSS | 41,774 KiB | 45,762 KiB | 45,710 KiB |
| Total RSS | 158,904 KiB | 163,512 KiB | 163,460 KiB |
| Java heap | 6,492 KiB | 6,708 KiB | 6,660 KiB |
| Native heap | 18,616 KiB | 18,184 KiB | 18,184 KiB |

The process had 28 threads and 174 open file descriptors.

SQLite diagnostics showed:

- one open read-only Amharic dictionary connection;
- maximum connection count 1;
- no acquired connection and no waiter;
- 149 statements with 3 ms total reported execution time;
- copied Amharic database size 39,108,608 bytes;
- copied English database size 13,750,272 bytes.

No SQLite corruption or open failure was present in the captured logs.

## Unmeasured or externally blocked gates

The following are required before production approval:

1. Review the pre-existing service/composer/voice/UI diffs, choose the release
   scope, commit it, and rebuild from that clean commit.
2. Upload the AAB to a Play closed track and verify both fresh install and
   update install from Play-generated APKs.
3. Repeat the lifecycle/stress matrix on a true 1 GiB API 24/35/36 emulator
   set and on at least one low-end physical device.
4. Capture cold/warm keyboard show-to-first-frame, key-to-composing latency,
   key-to-suggestion latency, and interaction traces with benchmark
   instrumentation. Existing tools cannot produce trustworthy values.
5. Replace the stale hand-authored profile with a generated Baseline/Startup
   Profile and compare profile-enabled and profile-disabled runs.
6. Establish crash/ANR monitoring and retain the matching R8 mapping for every
   uploaded version.

Phase 1 should not start from these artifacts until item 1 produces a clean,
reproducible candidate.
