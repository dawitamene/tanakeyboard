# Addiyon Keyboard production release checklist

Prepared 2026-07-28 after Google Play production access was granted.

This is the operational go/no-go list for the first public release of
`com.addiyon.keyboard`. Work from top to bottom. Do not publish until every item in
**Release blockers** is checked.

Companion references:

- Detailed launch analysis: [`../docs/production-launch-checklist.md`](../docs/production-launch-checklist.md)
- Full physical-device test matrix: [`../docs/final-physical-phone-test-checklist.md`](../docs/final-physical-phone-test-checklist.md)
- Store listing copy and asset brief: [`../docs/play-store-listing.md`](../docs/play-store-listing.md)
- Release artifact verifier: [`verify-release-artifact.sh`](verify-release-artifact.sh)

## Current repo snapshot

Verified from the repository on 2026-07-28:

- [x] Google Play production access has been granted.
- [x] Application ID is `com.addiyon.keyboard`.
- [x] `targetSdk` and `compileSdk` are 36. This already meets Google Play's API 36
  requirement that begins on 2026-08-31.
- [x] `minSdk` is 24.
- [x] Intended version name is currently `1.10.2`.
- [x] The generated release manifest currently reports version code `74`.
- [x] Release minification and resource shrinking are enabled.
- [x] A release signing certificate fingerprint is pinned in `version.properties`.
- [x] A privacy-policy page exists in the repo and an in-app link points to
  `https://keyboard.addiyon.com/privacy.html`.
- [x] A 512×512 Play Store icon exists at `play_store_icon_512.png`.
- [ ] The working tree is clean and all intended release changes are reviewed and
  committed.
- [ ] A feature graphic and final phone screenshots exist.
- [ ] The privacy-policy URL is confirmed publicly reachable over HTTPS.
- [ ] All Play Console declarations are completed and consistent.
- [ ] Third-party dictionary licenses and required attribution are resolved.
- [ ] A fresh release AAB has passed the complete gate below.

## Release blockers

### 1. Freeze one exact release candidate

- [ ] Review every modified, deleted, and untracked file in `git status`.
- [ ] Separate intentional release changes from local IDE files and unrelated work.
- [ ] Commit the exact source intended for production.
- [ ] Confirm the working tree is clean before generating the candidate.
- [ ] Confirm `versionName=1.10.2` is the intended public version; change it before the
  freeze if it is not.
- [ ] In Play Console, find the highest version code already uploaded.
- [ ] Confirm the generated version code is strictly higher than that Play Console value.
- [ ] Write final English and Amharic release notes for this version.
- [ ] Record the candidate commit SHA and name one person as the final release owner.
- [ ] After sign-off, do not rebuild or make a small last-minute change. Any change creates
  a new candidate that must repeat the build, Play-delivery, and device gates.

The AAB currently under `app/build/outputs/bundle/release/` was built on 2026-07-25. It
predates the current uncommitted changes and must not be treated as the final candidate.

### 2. Repair the release verifier before trusting it

- [ ] Resolve the permission-count contradiction in
  `plans/verify-release-artifact.sh`.

Current evidence:

- The merged release manifest contains:
  - `android.permission.RECORD_AUDIO`
  - `android.permission.VIBRATE`
  - `com.addiyon.keyboard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
- The verifier requires `RECORD_AUDIO` and the AndroidX dynamic-receiver permission, but
  then requires the total `<uses-permission>` count to be two.
- Because `VIBRATE` is also intentionally present, that assertion cannot pass as written.

The repaired check should compare the exact expected allowlist, including the internal
signature-protected AndroidX permission, rather than relying only on a count.

- [ ] Rebuild after repairing the verifier; do not validate against stale intermediates.
- [ ] Confirm the final merged manifest has only the intended permissions.
- [ ] Confirm no debug receiver or benchmark host is enabled or exported in release.

### 3. Resolve privacy and policy declarations

- [ ] Deploy `site/privacy.html` to
  `https://keyboard.addiyon.com/privacy.html`.
- [ ] Test the URL while signed out, in a private browser window, and on mobile data.
- [ ] Confirm it loads over HTTPS without a login, redirect loop, geographic restriction,
  or downloadable-file prompt.
- [ ] On a Play-delivered build, open **About → Privacy policy** and confirm the same page
  opens.
- [ ] Enter that exact URL in Play Console → Policy and programs → App content → Privacy
  policy.
- [ ] Complete the Data safety form based on the final AAB, including third-party SDK and
  Android Backup behavior.
- [ ] Review the optional voice flow carefully: Addiyon has no `INTERNET` permission, but
  Android's selected speech-recognition provider may process voice off-device. Ensure the
  Data safety answers, hosted policy, in-app disclosure, permission prompt, and store
  description all tell the same accurate story.
- [ ] Confirm the microphone permission is requested only after the user intentionally
  starts voice typing, with understandable context and a usable deny path.
- [ ] Confirm the keyboard remains fully usable when microphone access is denied or
  permanently denied.
- [ ] Declare **Ads: No**.
- [ ] Complete the target audience and content questionnaire accurately; do not select a
  child-directed audience unless the app and listing meet Families requirements.
- [ ] Complete the content-rating questionnaire and save the issued rating.
- [ ] Complete App access as **no login or restricted access**, if that remains accurate.
- [ ] Answer the account-deletion questions as **no account creation**, if that remains
  accurate.
- [ ] Clear every item under Play Console → App content → **Needs attention**.
- [ ] Re-open the completed declarations after uploading the final AAB and verify that no
  new permission or policy form appeared.

### 4. Resolve licensing and attribution

- [ ] Verify the license and permitted redistribution terms for every source used to
  generate the shipped Amharic and English dictionary databases.
- [ ] Resolve the unknown-license sources identified in
  `docs/production-launch-checklist.md`.
- [ ] Preserve required MIT notices.
- [ ] Provide the required CC BY attribution to end users, not only in a developer README.
- [ ] Add a complete third-party notices artifact and expose it from the app's About area
  when a source license requires user-visible attribution.
- [ ] Confirm whether the app's own source code needs a repository license and add the
  intended license deliberately.
- [ ] Do not publish while the right to redistribute any content embedded in
  `amharic.db` or `english.db` remains unknown.

This is a legal ownership check, not merely Play Console paperwork. Escalate uncertain
licenses to qualified legal review.

### 5. Finish the store listing

- [ ] Use the approved public app name consistently in the installed app, IME picker,
  listing, privacy policy, and support material.
- [ ] Paste and proofread the English title, short description, and full description from
  `docs/play-store-listing.md`.
- [ ] Add and proofread the `am-ET` localized listing.
- [ ] Verify every listing claim against the final build, especially dictionary size,
  privacy, voice typing, language support, and theme support.
- [ ] Upload the intended 512×512 icon and confirm its preview matches the installed icon.
- [ ] Create the required 1024×500 feature graphic as a JPEG or 24-bit PNG without alpha.
- [ ] Capture at least two current phone screenshots; use four to six to explain the core
  product clearly.
- [ ] Ensure screenshots show the final UI, contain no tester messages or personal data,
  and have clean status bars.
- [ ] Add concise, accurate alt text for visual assets where Play Console offers it.
- [ ] Set the category, tags, support email, website, and privacy-policy URL.
- [ ] Verify the support email is monitored and can receive mail.
- [ ] Review country/region availability and confirm the intended launch market.
- [ ] Confirm whether the app will be free. Remember that a published free app cannot
  later be changed to paid under the same package name.
- [ ] Preview the listing on a phone-sized screen in both English and Amharic.
- [ ] Remove unsupported superlatives, competitor names, keyword stuffing, and claims
  that cannot be demonstrated in the shipped build.

### 6. Build and verify the candidate

Run these from the clean, frozen commit:

```bash
./gradlew testDebugUnitTest
./gradlew lintRelease
./gradlew connectedAndroidTest
./gradlew bundleRelease
PLAY_MAX_VERSION_CODE=<highest-code-in-play> REQUIRE_CLEAN_RELEASE=1 \
  ./plans/verify-release-artifact.sh
```

- [ ] All JVM unit tests pass.
- [ ] Release lint has no unresolved fatal or high-confidence release issue.
- [ ] Instrumented tests pass on an emulator or connected device.
- [ ] `bundleRelease` succeeds with the intended upload key.
- [ ] The repaired artifact verifier passes against the newly generated AAB.
- [ ] The verified package, version name, version code, target SDK, certificate, AAB hash,
  mapping hash, and source commit are recorded in
  `app/build/outputs/release-candidate.properties`.
- [ ] The AAB contains `amharic.db`, `english.db`, `emoji.dat`, and the dictionary
  manifest.
- [ ] The AAB excludes raw `*_words.dat` and `*_ngrams.dat` inputs.
- [ ] The AAB contains the R8 mapping and baseline profile.
- [ ] The signing certificate matches `releaseCertificateSha256`.
- [ ] Store a recoverable backup of the upload keystore and its credentials in a secure
  location separate from the development machine.
- [ ] Confirm Play App Signing is enabled and the upload certificate shown in Play
  Console matches the intended upload key.
- [ ] Upload only the recorded AAB hash. If another AAB is built, restart this section.

### 7. Test the Play-delivered artifact

Do not sign off only on a locally installed APK. Google Play generates device-specific
split APKs from the AAB, so install the uploaded candidate through an internal or closed
test track.

- [ ] Upload the exact verified AAB to an internal or closed track.
- [ ] Review App bundle explorer for unexpected permissions, unsupported devices,
  warnings, and download size.
- [ ] Confirm the deobfuscation mapping is present in Play Console.
- [ ] Wait for processing and review the pre-launch report.
- [ ] Treat a generic crawler's inability to exercise the IME itself as expected; still
  resolve real stability, compatibility, performance, or accessibility findings in the
  launcher/settings app.
- [ ] Fresh-install the Play-delivered build on at least one physical phone.
- [ ] Test an update from the latest tester version without clearing app data.
- [ ] Verify version name and code from the installed Play build.
- [ ] Complete every applicable item in
  `docs/final-physical-phone-test-checklist.md`.

Minimum keyboard-specific smoke pass:

- [ ] Fresh onboarding enables and selects the IME successfully.
- [ ] Amharic and English subtypes appear correctly in Android settings.
- [ ] The in-keyboard globe and system subtype switcher remain synchronized.
- [ ] Latin-to-Fidel transliteration, suggestions, suggestion commits, space, enter, and
  backspace behave correctly.
- [ ] English typing, shift, caps lock, autocapitalization, suggestions, and punctuation
  behave correctly.
- [ ] Password, email, URL, number, phone, multiline, and no-suggestions fields use safe
  layouts and do not leak previews or suggestions.
- [ ] Emoji search, recent emoji, skin tones, and emoji backspace work.
- [ ] Voice typing works after granting permission.
- [ ] Voice denial, no recognizer, offline/network failure, cancellation, and rapid
  start/stop do not crash or trap the keyboard.
- [ ] Themes, height, number row, sound, and vibration apply and persist.
- [ ] Suggestions load on a fresh install of the minified release build.
- [ ] Switching apps, locking/unlocking, rotating, and replacing the current
  `InputConnection` do not lose, duplicate, or corrupt text.
- [ ] API 24 or 25 shows the branded legacy launcher icon.
- [ ] A current Android 16/API 36 device or emulator completes onboarding and typing.
- [ ] A low-memory or older supported device can open the keyboard and type without an
  ANR.
- [ ] No blocker remains: crash, ANR, blank screen, missing keyboard, corrupted text,
  private-field leak, broken core input, or inaccessible setup.

## Final Play Console review

- [ ] The production release contains the exact AAB SHA-256 that passed testing.
- [ ] Release name and localized release notes are correct.
- [ ] Play Console shows no unresolved error, policy declaration, device-compatibility
  surprise, or release warning.
- [ ] Store listing, app content declarations, pricing, and countries are reviewed one
  final time.
- [ ] Managed publishing is configured deliberately if review approval and public
  go-live should happen at different times.
- [ ] The release owner and support contact are available during the launch window.
- [ ] A hotfix path is ready: upload key available, build documented, and version code
  capable of increasing.
- [ ] Record screenshots of the final release summary and declarations for the release
  record.

## Important first-release rollout note

Google Play percentage-based staged rollouts apply to **updates**, not an app's first
production publication. Do not plan a `20% → 50% → 100%` staged rollout for this first
public release.

For the first release:

- [ ] Reduce risk by promoting the exact Play-tested artifact, not a rebuilt equivalent.
- [ ] Use managed publishing to control the go-live time if needed.
- [ ] If a soft launch is a real product decision, choose a limited country set before
  publishing and confirm that it still serves the intended Ethiopian and diaspora users.
- [ ] Understand the rollback limit: unpublishing prevents new discovery but does not
  remove the app from existing users' devices. A defect affecting installed users needs
  a higher-version-code hotfix.
- [ ] Use staged rollout percentages for subsequent updates.

## Launch-day checks

- [ ] Publish during a window when the release owner can monitor for several hours.
- [ ] After the listing becomes public, find it from a signed-out Play Store session.
- [ ] Install from the public listing on a device that was not enrolled as a tester.
- [ ] Confirm the displayed developer name, app name, icon, screenshots, descriptions,
  Data safety section, content rating, support contact, and privacy-policy link.
- [ ] Enable the keyboard and complete a short Amharic, English, emoji, and voice smoke
  test from that public install.
- [ ] Confirm feedback email and Telegram links open the intended destinations.
- [ ] Save the production version code, commit SHA, AAB SHA-256, publish time, countries,
  and release owner in the release record.

## First week after launch

- [ ] Check Play Console review status and policy messages daily.
- [ ] Check Android Vitals, crashes, and ANRs daily.
- [ ] Investigate immediately if user-perceived crash rate approaches 1.09% or
  user-perceived ANR rate approaches 0.47%, including device-specific clusters.
- [ ] Read and respond constructively to early reviews and support messages.
- [ ] Reproduce reports on the same Android version, device class, app, input-field type,
  and language mode where possible.
- [ ] Track activation/onboarding failures separately from typing defects.
- [ ] Do not change Data safety answers, privacy text, or store claims without comparing
  all three surfaces again.
- [ ] For the first update, use a staged rollout and hold each percentage long enough to
  observe Android Vitals before expanding.
- [ ] Hold a short post-launch review after seven days and capture fixes for the next
  version.

## Final go/no-go sign-off

- [ ] All release blockers above are checked.
- [ ] Exact candidate AAB SHA-256: __________________________________________
- [ ] Git commit SHA: _______________________________________________________
- [ ] Version name / version code: __________________________________________
- [ ] Play track used for final validation: __________________________________
- [ ] Physical devices tested: ______________________________________________
- [ ] Play Console reviewed by: _____________________________________________
- [ ] Release owner: ________________________________________________________
- [ ] Decision: **GO / NO-GO**
- [ ] Decision date and time: _______________________________________________

## Official references

- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Prepare an app for Play review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Complete the Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Upload an Android App Bundle](https://developer.android.com/studio/publish/upload-bundle)
- [App signing and Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [Play Store preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Use Play pre-launch reports](https://support.google.com/googleplay/android-developer/answer/9842757)
- [Staged rollout rules](https://support.google.com/googleplay/android-developer/answer/6346149)
- [Monitor Android Vitals](https://support.google.com/googleplay/android-developer/answer/9844486)
