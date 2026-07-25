# Google Play Production Access Application

App: Addiyon Keyboard
Package: `com.addiyon.keyboard`
Prepared: July 25, 2026

## Read this before applying

These answers are based on the two Testers Community PDFs, the additional
testing information supplied by the developer, the current project, and
Google's current official guidance.

No wording can guarantee approval. The safest application is specific,
truthful, and supported by real testing evidence. Do not paste a claim until
it is true for the exact release you intend to publish.

Google's current help page describes eight standard application items. The
provider PDF also includes a recruitment question and a "what did you do
differently" question, which may be older or conditional. All ten are covered
below.

## Short paste-ready answers

Google does not publish the text-field limit in its help article, but current
developer reports commonly show a 300-character limit. The following versions
are each no more than 300 characters. Use the expanded section afterward if
your Play Console allows more text.

### 1. Recruitment - 275 characters

I recruited testers through a paid testing provider and through friends and
family. The provider broadened device coverage, while friends and family
tested the keyboard and gave detailed feedback by phone about Amharic/English
typing, suggestions, performance, and usability.

### 2. Recruitment difficulty

**Easy** - select a different option if that is not accurate.

### 3. Engagement - 275 characters

The provider group tested across Android devices. Friends and family also used
the keyboard and discussed results with me by phone. Collectively, feedback
covered core Amharic/English typing, suggestions, emoji and settings, and
identified specific bugs and missing features.

### 4. Feedback and collection - 291 characters

Feedback came from phone calls with friends/family and the provider's written
report. Testers requested emoji search and height controls, reported slow
low-end performance, word-entry bugs and edge cases, and identified missing
dictionary words. Other feature requests remain on the roadmap.

### 5. Intended audience - 286 characters

Addiyon is for Android users who type in Amharic, especially native speakers
in Ethiopia and the diaspora who prefer entering word sounds with Latin
letters instead of finding individual Fidel characters. It also serves
bilingual students, professionals, writers and social-media users.

### 6. User value - 286 characters

Addiyon converts Latin key sequences to Ethiopic Fidel as users type. It also
offers local Amharic/English suggestions, an English layout, emoji, optional
system voice input, themes and height controls. Core typing works without an
account and typed words are not sent to the developer.

### 7. Expected first-year installs

Recommended: **1,000-10,000** - choose the range you genuinely expect.

### 8. Changes made - 295 characters

Based on feedback, I added emoji search and height controls, expanded Amharic
suggestions/dictionary coverage, fixed word-composition edge cases, and
improved low-memory startup and rendering. I also hardened input, voice and
lifecycle error paths and added automated tests for changed behavior.

### 9. Production readiness - 286 characters

Use this only after every statement is true:

After fixing high-priority typing, stability and performance issues, I retested
the exact release on fresh installs, low-memory devices and varied text fields.
Unit/instrumented tests passed, and I reviewed Play pre-launch, pre-review,
crash and ANR results. No release blocker remains.

### 10. What was different

First application - 297 characters:

This is my first access request. Unlike earlier informal checks, I ran a
structured closed test using a provider plus friends/family, collected phone
feedback, tracked concrete issues, fixed priority typing/performance/stability
problems, and retested the release across devices and failure cases.

After a previous refusal - 296 characters:

Since the prior request, I continued closed testing, added friends/family as
representative users, gave clearer test scenarios and recorded phone feedback.
I fixed reported typing, suggestion, performance and stability problems,
uploaded the revised build and had that exact release tested again.

## Expanded questionnaire answers and guidance

### 1. How did you recruit users for your closed test?

I recruited testers in two ways. I used a paid testing provider to obtain
coverage from a broader group of Android users and devices. I also invited
friends and family to test the keyboard. The friends and family group gave me
direct feedback by phone, which helped me understand Amharic and English typing
problems in more detail.

### 2. How easy was it to recruit testers for your app?

Recommended selection: **Easy**

I was able to meet the testing requirement by combining a paid testing
provider with friends and family. Select a different option if "Easy" does not
accurately describe your experience.

### 3. Describe the engagement you received from testers during your closed test.

The testers provided useful, concrete feedback rather than only installing the
app. The paid testing group provided broader device coverage, while friends
and family used the keyboard themselves and discussed their experience with me
by phone. Collectively, their feedback covered Amharic typing and
transliteration, word suggestions, performance, keyboard sizing, emoji use,
and general usability. Not every tester used every optional feature, but the
group exercised the core typing experience and identified specific bugs, edge
cases, and missing features that I could investigate.

### 4. Summarize the feedback you received and explain how you collected it.

I collected feedback through phone calls with friends and family and through
the paid testing provider's written report. Testers requested emoji search and
keyboard-height adjustment. They reported slow performance on some low-end
phones, bugs and edge cases where particular words were not entered correctly,
and missing Amharic words in the suggestion dictionary. They also made other
feature requests. The provider's report additionally recommended clearer
onboarding, an easier feedback or rating path, accessibility and customization
improvements, and a clearer Play Store description. I grouped the feedback by
severity and frequency, prioritized crashes, typing correctness, and
performance first, and kept lower-priority feature requests for future
updates.

### 5. Who is the intended audience for your app?

Addiyon Keyboard is intended primarily for Android users who communicate in
Amharic, including native speakers in Ethiopia and the diaspora. It is
especially useful for people who know the sound or Latin spelling of an
Amharic word but prefer not to search for individual Fidel characters. It also
serves bilingual users who regularly switch between Amharic and English,
including students, professionals, writers, and people using messaging,
email, search, notes, and social media.

### 6. How does your app provide value to users?

Addiyon Keyboard makes Amharic typing faster and easier by transliterating
Latin key sequences into Ethiopic Fidel while the user types. It includes an
English layout, Amharic and English word suggestions, emoji input, optional
voice input, keyboard-height controls, numbers, symbols, and theme options.
The core transliteration and suggestion features run locally, so users can
type without creating an account or sending their typed words to the
developer. Voice input is handled by the Android speech-recognition service
available on the user's device.

### 7. How many installs do you expect in the first year?

Recommended selection: **1,000-10,000**

This is a realistic initial estimate for a new, independently released
Amharic keyboard without an established paid acquisition campaign. Choose
`10,000-100,000` only if you have a concrete launch, distribution, or
marketing plan that makes that range genuinely likely. Google states that a
rough estimate is acceptable.

### 8. What changes did you make based on what you learned during the closed test?

I used the feedback to prioritize changes that directly affect daily typing. I
added emoji search and keyboard-height adjustment, improved Amharic word
suggestions and dictionary coverage, and fixed typing and word-composition
edge cases. I also optimized startup, dictionary loading, and keyboard
rendering for lower-memory devices and added additional safeguards around the
input connection, voice recognizer, asset loading, and activity lifecycles to
reduce crashes. I added or updated automated tests for the affected behavior.
Lower-priority feature requests remain on the roadmap rather than being
presented as completed work.

### 9. How did you decide that the app is ready for production?

Paste the following only after every verification it mentions has actually
been completed:

I decided the app was ready after reviewing the closed-test feedback, fixing
the highest-priority typing, stability, and performance issues, and retesting
the updated release. I verified the exact release build on fresh installs and
in normal keyboard use, including Amharic and English typing, suggestions,
emoji, voice-permission grant and denial, keyboard resizing, low-memory
conditions, and different text-field types. I also reviewed the automated test
results, the Play Console pre-launch and pre-review checks, and the available
closed-test crash and ANR information. No known release-blocking issue remains,
and unresolved feature requests are optional improvements rather than failures
of the app's core keyboard function.

### 10. What did you do differently this time?

If this is the first production-access application:

This is my first production-access application. Compared with my earlier
informal development testing, I ran a structured closed test and combined a
paid testing provider with friends and family who could describe their
real-world experience by phone. I recorded specific feedback themes, converted
the high-priority items into fixes, and added tests for the areas that changed.
I also expanded testing to include low-end performance, typing edge cases,
permission denial, and release-build stability instead of checking only the
basic happy path.

If this question appears after a previous production-access refusal, use this
version only after doing the stated work:

Since the previous application, I continued the closed test, recruited
additional representative users through friends and family, gave testers
clearer scenarios to exercise, and collected detailed feedback by phone. I
fixed the reported typing, suggestion, performance, and stability issues,
uploaded the revised build to the closed track, and had the exact revised
release tested again. I also kept a clearer record of tester engagement,
feedback, fixes, and retest results.

## Claims from the provider PDFs that should not be copied

- Do not say feedback was collected through surveys or formal usability
  sessions unless those events really occurred. The confirmed channels are
  phone calls and the provider's written report.
- Do not claim that no bugs were encountered. The real feedback included bugs,
  word-entry edge cases, low-end performance problems, and dictionary gaps.
- Do not say every function worked on every device or SDK. That is broader than
  the available evidence.
- Do not claim a walkthrough, rating feature, ASO work, or another feature was
  completed solely because the provider recommended it. Mention only changes
  actually present in the release.
- Do not hide the paid provider. The form asks how testers were recruited, so
  transparent wording is safer than an answer that appears misleading.

# Pre-submission checklist

## 1. Current app-specific blockers

- [ ] Freeze a release candidate and identify it by Git commit, version name,
  version code, and AAB checksum. The working tree currently contains
  uncommitted changes, so the exact source and artifact are not yet frozen.
- [ ] Upload that exact candidate to the closed track and retest it after the
  recent stability and low-memory changes. Do not rely only on an older build
  that completed the 14-day period.
- [ ] Fix or verify every known crash, word-entry failure, and serious
  low-end-device problem. Optional feature requests can remain on the roadmap.
- [ ] Publish a real privacy policy on a stable, public, non-geofenced HTTPS
  webpage. A live privacy-policy URL could not be verified from this project.
- [ ] Make the privacy policy accessible inside the app. Google requires a
  privacy-policy link or text in the app as well as the Play Console URL.
- [ ] Replace the in-app absolute claim that Addiyon "never collects any data."
  The app requests `RECORD_AUDIO` and accesses microphone audio for optional
  voice input. Use precise language that distinguishes local typing from audio
  handled by the device's speech-recognition provider.
- [ ] Ensure the policy explains that voice input uses the Android speech
  service selected on the device and may be processed locally or online under
  that provider's practices. State what Addiyon itself stores, transmits,
  retains, and does not collect.
- [ ] Confirm the Data safety answers match the final app, privacy policy, SDKs,
  backup behavior, and voice-input flow. Do not blindly select "No data
  collected" without evaluating the speech service and every dependency.

Current project observations:

- `targetSdk` and `compileSdk` are 36, which already satisfy the Android 16
  target required for new submissions from August 31, 2026.
- The main manifest currently requests only `RECORD_AUDIO`; no `INTERNET`
  permission or analytics/crash-reporting SDK was found in the production
  source review.
- Core transliteration and dictionary suggestions are local. Voice input uses
  Android's `SpeechRecognizer`.
- Release builds use R8 minification and resource shrinking, so a successful
  debug test is not sufficient evidence that the production artifact works.

## 2. Closed-test eligibility and evidence

- [ ] Confirm at least 12 testers are still opted in and have been opted in for
  at least 14 consecutive days. A tester who opted out and returned does not
  keep the earlier consecutive-day count.
- [ ] Keep the closed test active and ask testers not to opt out while the
  production-access request is being reviewed.
- [ ] Confirm testers did more than install or open the app. Google explicitly
  considers tester engagement when reviewing access.
- [ ] Keep a simple evidence log containing tester identifier, device model,
  Android version, test dates, app version, features exercised, feedback,
  resulting change, and retest result.
- [ ] Save the phone-call notes, provider report, Play testing feedback, crash
  information, and release notes. Google may not request the files, but the
  questionnaire should be traceable to them.
- [ ] Make sure the tester set includes real target users and a useful range of
  devices, Android versions, screen sizes, and memory classes.
- [ ] Do not buy installs, public reviews, or positive ratings. Payment for a
  testing service does not justify fabricated engagement or incentivized
  public reviews.
- [ ] Keep answers natural and app-specific. Avoid pasting the provider's
  generic statements about "all devices," "all SDKs," or "no discrepancies."

## 3. Build and release verification

- [ ] Increase `versionCode` above every previously uploaded artifact and
  confirm the intended `versionName`.
- [ ] Confirm the release upload key and Play App Signing configuration. Back
  up the upload keystore and credentials securely in separate locations.
- [ ] Build a signed Android App Bundle:

  ```bash
  ./gradlew bundleRelease
  ```

- [ ] Run the complete JVM unit-test suite:

  ```bash
  ./gradlew testDebugUnitTest
  ```

- [ ] Run Android lint and resolve release-relevant errors:

  ```bash
  ./gradlew lintDebug
  ```

- [ ] Run the available instrumented tests on representative emulators or
  devices:

  ```bash
  ./gradlew connectedAndroidTest
  ```

- [ ] Build and install locally during final device testing as required by the
  project workflow:

  ```bash
  ./gradlew installDebug
  ./gradlew assembleDebug
  ```

- [ ] Test a release-signed APK and, most importantly, install the Play-delivered
  closed-track build. This catches signing, R8, shrinking, split-APK, and asset
  packaging differences.
- [ ] Verify the generated Amharic and English dictionary databases are present
  in the release and that suggestions work after a fresh install.
- [ ] Check Play's App bundle explorer for unexpected permissions, supported
  devices, delivery warnings, large download size, and signing problems.
- [ ] If any native `.so` libraries appear in the final bundle, confirm Play
  reports 16 KB page-size compatibility. This is required for new apps and
  updates targeting Android 15 or later.
- [ ] Upload the R8 deobfuscation mapping for the release if Play does not
  receive it automatically, so release crash traces remain actionable.
- [ ] Wait for all Play pre-review checks. Fix required errors and investigate
  warnings rather than using "proceed anyway" without understanding them.

## 4. Manual keyboard test matrix

Test the exact release candidate, not a debug build from a different commit.

- [ ] Fresh install: launch the app, complete onboarding, enable Addiyon in
  Android Settings, select it as the current keyboard, and type successfully.
- [ ] Existing install: upgrade from the closed-test version and confirm
  preferences and dictionaries still work.
- [ ] App relaunch and process death: force-stop the app, switch away from and
  back to the keyboard, reboot the phone, and confirm the IME recovers.
- [ ] Android versions: test the oldest supported API 24 device or emulator,
  intermediate versions, and current API 35/36 devices.
- [ ] Hardware range: include at least one low-RAM/low-end phone and one modern
  device. Watch for slow first-open, frozen keys, blank suggestions, ANRs, and
  out-of-memory crashes.
- [ ] Host apps: type in several independent apps such as Messages, Gmail or
  another email client, Chrome, Notes, search, and a document editor.
- [ ] Field types: normal text, multiline, search, email, URL, number, phone,
  and password fields. Password fields must not leak text into suggestions,
  logs, previews, or saved history.
- [ ] Amharic: transliteration families, uppercase-sensitive forms, bare
  vowels, digraphs, repeated keys, spaces, punctuation, cursor movement,
  selection replacement, suggestion acceptance, and backspace at word edges.
- [ ] English: shift, caps lock, sentence case, apostrophes, suggestions,
  email-domain suggestions, delete repeat, space, and enter actions.
- [ ] Layouts: English, Amharic, numbers, symbols, additional symbols, Ge'ez
  numerals, orientation changes, narrow screens, display/font scaling, and
  light/dark themes.
- [ ] Emoji: open and close the panel, search, empty search, recent emoji,
  skin-tone selection, deletion, and return to letters.
- [ ] Keyboard height: test every size, rotation, gesture-navigation and
  three-button-navigation devices, and screens with bottom insets. Confirm the
  keyboard never overlaps or hides its controls.
- [ ] Voice: microphone granted, denied, denied permanently, revoked later,
  no recognizer installed, unsupported language, network unavailable, silence,
  rapid start/stop, cursor movement, activity recreation, and switching apps.
  Every failure must be recoverable and must not crash the keyboard.
- [ ] Rapid and abnormal input: very fast typing, long words, long sessions,
  repeated delete, repeated language switching, background/foreground cycles,
  and low-memory process eviction.
- [ ] Accessibility: TalkBack labels for non-text controls, readable contrast,
  touch-target sizes, large font/display settings, and no clipped setup text.

## 5. Crashes, ANRs, and Play quality signals

- [ ] Open **Play Console > Monitor and improve > Android vitals > Crashes and
  ANRs** and inspect every available cluster from the closed test.
- [ ] Resolve all reproducible startup, setup, IME-service, and typing crashes.
  Record why any remaining non-reproducible cluster is considered understood.
- [ ] Review the pre-launch report's Stability, Performance, Accessibility, and
  Screenshots sections.
- [ ] Remember that an automated crawler cannot fully enable and exercise an
  IME like a human user. Addiyon has a launcher activity, so Play can crawl its
  setup UI, but the keyboard itself still requires manual testing.
- [ ] Confirm startup is responsive and the UI thread is not blocked by
  dictionary loading. Investigate slow startup, skipped frames, memory
  warnings, and ANRs.
- [ ] Do not write "zero crashes" in the application merely because the paid
  report says so. State that no known release-blocking issue remains only after
  reviewing actual results.

## 6. Privacy, security, and microphone handling

- [ ] The privacy policy identifies Addiyon Keyboard and the developer, gives
  a privacy contact, and explains data access, collection, use, sharing,
  security, retention, and deletion.
- [ ] The privacy URL is HTTPS, public worldwide, not a PDF, not editable by
  visitors, and has no login, geographic restriction, placeholder text, or
  broken links.
- [ ] The in-app privacy text, Play Data safety form, store listing, and policy
  use consistent language.
- [ ] Request microphone permission only when the user taps voice input, after
  a clear explanation of why it is needed. A denial must leave normal keyboard
  use fully functional.
- [ ] Audit release source and dependencies to confirm no typed text,
  composing buffers, passwords, voice transcripts, or dictionary queries are
  sent to logs, analytics, crash reports, feedback links, or remote services.
- [ ] Review the final merged manifest rather than only the source manifest;
  dependencies can add permissions and components.
- [ ] Review every third-party SDK in the final AAB using Play SDK Index and
  its Data safety guidance. The developer remains responsible for SDK behavior.
- [ ] Review Android backup configuration. Ensure no typed-content history or
  other sensitive keyboard data is backed up. Disclose any relevant backup or
  synchronization behavior.
- [ ] If the app has no accounts, say so accurately. Do not add an account
  deletion declaration that implies accounts exist.

## 7. Play Console app content and store listing

- [ ] Complete every item under **Policy and programs > App content**:
  privacy policy, Data safety, ads, app access, target audience, content
  rating, and every declaration shown for this app.
- [ ] Declare ads accurately. Do not mark "No ads" if an advertising SDK is
  later added.
- [ ] Explain reviewer access even though there is no login. Suggested note:
  "No account is required. Open Addiyon Keyboard, follow the setup screen to
  enable it under Android's on-screen keyboard settings, select it as the
  current keyboard, then open any text field. Microphone access is optional and
  requested only after tapping voice input."
- [ ] Ensure the target audience is consistent with the content-rating and
  Families declarations. Do not claim the app is designed for children unless
  you have intentionally met the Families requirements.
- [ ] Check the title is at most 30 characters and all descriptions are clear,
  readable, and free of keyword stuffing, unsupported superlatives, rankings,
  pricing claims, and anonymous testimonials.
- [ ] Make every feature claim true in the uploaded release. Verify any numeric
  claims such as dictionary word counts before publishing them.
- [ ] Remove or qualify the store-listing statement that the app "never
  collects any data." Explain local typing and the separate system
  speech-recognition path accurately.
- [ ] Use current screenshots from the exact release. Show real setup and
  keyboard screens; do not advertise a layout, theme, feature, or language not
  present in the build.
- [ ] Verify the app icon, feature graphic, phone screenshots, short
  description, full description, category, support email, website, and privacy
  link on both mobile and desktop Play Store previews.
- [ ] Confirm countries/regions, free or paid status, and device availability
  are intentional. Pricing cannot later change from free to paid for the same
  app.
- [ ] Resolve every issue under **Policy status**, **Publishing overview**, and
  pre-review checks before sending the application.

## 8. Account, signing, and operational readiness

- [ ] Confirm the Play developer identity, public developer details, contact
  email, phone, and payment profile are verified and current.
- [ ] Protect the Play Console account with two-step verification and limit
  production permissions to trusted users.
- [ ] Confirm Play has registered or will auto-register
  `com.addiyon.keyboard` for Android developer verification. Play packages must
  be registered by September 30, 2026.
- [ ] Preserve the package name and signing identity. Both are part of the
  permanent update path once the app is published.
- [ ] Prepare a support process for privacy questions, bug reports, and urgent
  releases. Verify the support email and Telegram destination work.
- [ ] Keep the previous good artifact, release notes, mapping file, test
  results, and a rollback or halt procedure.

## 9. Immediately before clicking Apply

- [ ] Re-read every answer beside the evidence log and remove anything that
  cannot be demonstrated.
- [ ] Confirm the application describes both tester sources: paid provider and
  friends/family.
- [ ] Confirm it names the actual feedback channels: phone calls and the
  provider's written report.
- [ ] Confirm it describes specific feedback and corresponding changes rather
  than generic statements about usability.
- [ ] Confirm the exact revised build has been tested after the changes.
- [ ] Confirm 12 qualifying testers remain continuously opted in.
- [ ] Save a copy of the final answers and screenshots of the eligibility and
  submission pages for your records.
- [ ] Complete the full form in one session or preserve the text externally;
  Google's guidance warns that discarding or leaving without applying may not
  save the answers.

## 10. After production access is approved

- [ ] Remember that production access does not publish the app automatically.
  Create and review a production release only when the final checklist remains
  true.
- [ ] Use managed publishing if you need control over the exact go-live time.
- [ ] A staged rollout is not available for an app's first public release, so
  the closed-test release must be treated as the final safety gate.
- [ ] Monitor Android vitals, reviews, support messages, install failures, and
  policy status closely after launch.
- [ ] If a critical problem appears, halt the affected release in Play Console
  and publish a higher-version-code fix. Use staged rollouts for later updates.
- [ ] Continue closed or internal testing for future updates before promoting
  them to production.

## Official references

- [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Complete the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Use a pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)
- [Core app quality guidelines](https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality)
- [Store listing best practices](https://support.google.com/googleplay/android-developer/answer/13393723)
- [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Detect issues with pre-review checks](https://support.google.com/googleplay/android-developer/answer/14807773)
- [Register Play package names](https://support.google.com/googleplay/android-developer/answer/16984799)
