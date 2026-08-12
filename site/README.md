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
  (`apps/textrevamp/src/main/java/com/addiyon/keyboard/ExternalActions.kt`)
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
| Typed/editor content is never sent | Addiyon has no network dependency or `INTERNET` permission; typing and suggestions use the shared local runtime |
| No usage analytics or crash-reporting SDK | Addiyon's dependency graph and merged manifest |
| Voice audio is handled by the device's speech service | `voice/VoiceInputController.kt` delegating to `SpeechRecognizer` |
| The listed on-device settings | the `KEY_*` constants in `ui/settings/KeyboardPrefs.kt` |

The matching in-app wording lives in the shared app-shell resources. Before deployment,
confirm the Play Data safety form matches the shipped dependency graph, permissions, and
speech-provider handoff.
