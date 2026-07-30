# site/

Public web content that is **deployed, not shipped**. Nothing in this directory is
packaged into the APK or AAB — it is served from the web.

## privacy.html

The privacy policy for the Play listing. Google requires a live, publicly reachable,
non-geofenced HTTPS policy for every input-method (keyboard) app, and requires it to be
reachable from inside the app as well as from the Console.

**Deploy to:** `https://keyboard.addiyon.com/privacy.html`

That exact URL is hardcoded in two places that must be kept in sync:

- `ExternalActions.PRIVACY_POLICY_URL` — the About screen's "Privacy policy" link
  (`app/src/main/java/com/addiyon/keyboard/ExternalActions.kt`)
- Play Console → App content → Privacy policy

The file is a single self-contained HTML page with no external assets, so deploying it
is a file copy. It styles itself for light and dark browsers.

## Keeping it accurate

The policy makes specific factual claims that are currently true of the app. If any of
these change, the policy has to change with them — an inaccurate policy is worse than a
vague one, and a mismatch between the policy, the in-app copy, and the Data Safety form
is a common cause of IME review rejection.

| Claim in the policy | What it depends on |
|---|---|
| Typed/editor content is never sent | typed enum/boolean-only `telemetry/Telemetry.kt` API and static privacy tests |
| Analytics and Crashlytics are independent opt-ins, off by default | manifest collection defaults, `TelemetryPrefs`, and `TelemetryConsentPolicyTest` |
| No advertising ID, AdServices, signals, or ad personalization | manifest removals/defaults and `plans/verify-release-artifact.sh` deny checks |
| Voice audio is handled by the device's speech service | `voice/VoiceInputController.kt` delegating to `SpeechRecognizer` |
| Diagnostics consent is not restored | explicit exclusions in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` |
| The listed on-device settings | the `KEY_*` constants in `ui/settings/KeyboardPrefs.kt` |

The matching in-app wording lives in `ui/i18n/AppStrings.kt` and
`ui/settings/PrivacyDiagnosticsScreen.kt`, in both English and Amharic. Before deployment,
confirm the production Firebase property uses two-month event-level Analytics retention,
Google Signals and ad personalization are disabled, and the Play Data safety form matches.
