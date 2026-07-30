# Firebase telemetry and privacy operations

Addiyon Keyboard supports Firebase Analytics and Crashlytics through the single
`com.addiyon.keyboard.telemetry` package. Analytics and crash reporting are independent
opt-ins and both are off by default.

## Privacy boundary

The public telemetry facade accepts only enums and booleans. It has no API for strings,
character sequences, bundles, editor objects, arbitrary maps, user IDs, breadcrumbs, or
free-form properties. Firebase imports are confined to `FirebaseTelemetryBackend.kt`.

Never add telemetry to character, Delete, Space, Enter, transliteration, composition,
cursor/selection, email-suggestion, clipboard, emoji-content, or voice-transcript paths.
Custom events must be suppressed in private/password fields. Do not send target package,
field metadata, suggestion contents, word lengths, timing/cadence, or exception messages.

Caught failures pass through `SafeLog` with a fixed coarse category. The remote report is a
new synthetic exception with no message, cause, or suppressed exceptions and only
allowlisted app/framework frames. Reports are category-rate-limited and
`OutOfMemoryError` is never uploaded. Fatal crashes remain Crashlytics-managed only after
crash consent is enabled.

## Build configuration

Current verified versions:

- Firebase Android BoM `34.16.0`
- Google Services Gradle plugin `4.5.0`
- Crashlytics Gradle plugin `3.0.7`

The app remains buildable without Firebase configuration. The Firebase SDK libraries,
Google Services plugin, and Crashlytics plugin activate only when a structurally valid
configuration is present:

- production: exactly one of `app/google-services.json` or
  `app/src/release/google-services.json`, package `com.addiyon.keyboard`;
- development: `app/src/debug/google-services.json`, package
  `com.addiyon.keyboard.debug`.

Download those files from their separate Firebase projects. Do not fabricate, rename, or
hand-edit them. Copy the production config's `mobilesdk_app_id` and `project_id` into
`firebaseProductionAppId` and `firebaseProductionProjectId` in `version.properties`;
these non-secret pins make the release gates reject a valid package registered in the
wrong Firebase project. `verifyProductionFirebaseConfig` and `verifyReleaseArtifact`
reject a missing, duplicated, unpinned, or mismatched production configuration.
Benchmark collection is forced off.

## Consent and revocation

Consent is stored in `addiyon_telemetry_prefs.xml`, explicitly excluded from cloud backup
and device transfer. Consent changes use checked synchronous persistence; collection is
never enabled when that persistence fails. If a revocation write fails, the live
backends are disabled first and the app clears the dedicated consent store as a
fail-closed fallback. If neither storage operation succeeds, both backends remain off
for the current process and the UI requires the user to retry instead of claiming the
change was saved. On a fresh install:

- Analytics is off;
- Crashlytics is off;
- the app presents a short diagnostics choice;
- both choices remain available under Privacy & diagnostics.

A confirmed Analytics disable stops future collection and resets local Analytics data.
A confirmed Crashlytics disable stops future collection and deletes queued unsent
reports. Disabling either cannot retroactively remove reports already uploaded.

## Reviewed custom schema

| Event | Parameters |
|---|---|
| `analytics_first_enable` | none |
| `ime_session_start` | language enum |
| `language_switch` | destination enum |
| `layout_open` | layout enum |
| `suggestion_accept` | completion/prediction enum |
| `voice_start` | language enum |
| `voice_finish` | result and allowlisted error enum |
| `onboarding_complete` | none |
| `setting_change` | setting enum and boolean |

No custom user properties are currently set.

## Firebase console setup

For both projects, disable Google Signals, ad personalization, Google Ads links, and
unnecessary data sharing. Choose the shortest suitable retention and use least-privilege
roles with MFA. Review whether Crash Insights sharing should remain enabled. The source
manifest removes Advertising ID and AdServices permissions and disables advertising-ID
collection and ad-personalization signals.

Play Console remains authoritative for installs. Firebase `first_open` means first use
after install or reinstall, not the instant of download.

## Verification

1. Clear development app data and install the debug build.
2. Confirm neither collection toggle is enabled.
3. Enable Analytics, enable Analytics debug mode for
   `com.addiyon.keyboard.debug`, and inspect DebugView.
4. Confirm typed characters, deletion, Space, cursor changes, private fields, email text,
   suggestions, and voice transcripts emit nothing.
5. Trigger `telemetry_non_fatal` through the debug-only test receiver and verify a fixed
   category, coarse throwable class, sanitized stack, null message/cause, and rate limit.
6. Trigger `telemetry_fatal`, relaunch, and verify the report contains no editor or typed
   data.
7. Repeat with consent off and verify no custom event/non-fatal uploads; revoke crash
   consent with a queued report and verify deletion.
8. Upload a minified internal build and confirm source file/line deobfuscation.
9. Inspect the merged release manifest and run `verifyReleaseArtifact`; confirm no debug
   receiver/crash trigger, `AD_ID`, or AdServices permission is present.

Production Realtime normally covers recent minutes; standard Analytics reports can take
up to 24 hours. Disable Analytics debug mode after verification.

## Privacy incident verification and response

Treat collection without consent, telemetry from a private field, typed/editor content in
any event or report, or an unsanitized caught error as a privacy incident.

1. Stop the rollout and contain collection for the affected Firebase service or app
   version. If console controls are insufficient, ship a telemetry-disabled hotfix.
2. Record only the project, app version, event/report category, timestamp, consent state,
   and affected release range. Keep any payload evidence access-controlled and do not
   copy suspected typed content into chat, issue trackers, or routine logs.
3. Notify the release/privacy owner, determine which consent-bounded records were
   affected, and request deletion through the Firebase-supported console or support path
   where available. Record retention and deletion outcomes.
4. Assess user, Play, and legal notification obligations with the responsible owner; do
   not resume rollout on an engineering-only decision.
5. Before resuming, rerun the privacy-contract tests and the verification sequence above,
   confirm the hosted policy, Data Safety form, and store listing still match reality, and
   document the corrective action and sign-off.
