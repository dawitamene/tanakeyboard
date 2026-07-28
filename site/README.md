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
| "does not request the `INTERNET` permission" | `AndroidManifest.xml` requesting only `RECORD_AUDIO` and `VIBRATE`; asserted by `plans/verify-release-artifact.sh` |
| No analytics / crash / ads SDKs | the dependency list in `app/build.gradle.kts` |
| Voice audio is handled by the device's speech service | `voice/VoiceInputController.kt` delegating to `SpeechRecognizer` |
| Backup covers two named prefs files only | `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` |
| The listed on-device settings | the `KEY_*` constants in `ui/settings/KeyboardPrefs.kt` |

The matching in-app wording lives in `ui/i18n/AppStrings.kt` (`aboutPrivacy`,
`activateFootnote`), in both English and Amharic.
