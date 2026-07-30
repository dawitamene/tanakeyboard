# Production launch checklist — Addiyon Keyboard

Written 2026-07-28, when Play **production access** was granted. From here the next
upload goes to everyone, so this is the list to work through before pressing publish.

**Scope of this document:** what stands between the current tree and a public release —
store assets, ASO, legal artifacts, and the release build gate.

**Companion document:** [`google-play-production-access.md`](google-play-production-access.md)
covers the Play *process* (closed-test evidence, the production-access questionnaire, the
manual keyboard test matrix). It is not duplicated here; where it already covers something
properly, this file points at it. Note that its §1 "current app-specific blockers" predates
this pass — several are now resolved (see below).

**Stale, do not work from:** `closed-testing.md` at the repo root. It names package
`com.addiyon.tanakeyboard` (now `com.addiyon.keyboard`), SDK 35 (now 36), version 1.1.0
(now 2.0.0), and predates the current Firebase and permission controls. It now carries a
superseded marker; use this checklist and `analytics.md` instead.

---

## 0. Already done — do not redo

Fixed in the 2026-07-28 pass, listed so nothing here gets re-litigated:

- [x] **Launcher icons rebranded.** `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.webp` were
  still the stock Android Studio green robot, untouched since the initial commit — only
  `mipmap-anydpi-v26/` had ever been rebranded, so every Android 7.0/7.1 device
  (`minSdk = 24`) showed the default mascot. Regenerated all ten from
  `logo_play_store_fullbleed.svg`, the same source as the 512px Play icon.
- [x] **IME subtypes declared.** `res/xml/method.xml` was a bare `<input-method/>` with no
  subtypes — the app never told Android it typed Amharic, and declared nothing
  ASCII-capable. Now declares `am-ET` and `en-US`, with `isAsciiCapable` on English.
- [x] **Subtype changes wired.** `AddiyonKeyboardService.onCurrentInputMethodSubtypeChanged`
  maps the system switcher's selection onto `isAmharic` via `SubtypeLanguagePolicy`, so the
  system switcher and the in-keyboard globe key cannot disagree. Covered by
  `SubtypeLanguagePolicyTest`.
- [x] **IME service labelled** — `android:label="@string/ime_name"`, plus `values-am/` so
  the language names localize in system settings.
- [x] **Privacy copy corrected.** The app claimed it "never collects any data" while
  holding `RECORD_AUDIO` and handing audio to `SpeechRecognizer`. Reworded in both
  languages to separate local typing from voice.
- [x] **Privacy policy written** — [`site/privacy.html`](../site/privacy.html), plus an
  in-app link from the About screen (Play requires in-app reachability, not just a Console
  URL).
- [x] **Store listing consolidated** — [`play-store-listing.md`](play-store-listing.md) is
  now the single source; the conflicting root copy is deleted.
- [x] **`testers.csv` untracked** (four real third-party emails in a repo with a GitHub
  remote). Still on disk, now gitignored. See §8 for the history caveat.

---

## 1. Hard blockers — cannot submit without these

- [ ] **Deploy the privacy policy.** Upload `site/privacy.html` to
  **`https://keyboard.addiyon.com/privacy.html`**. Must be public, HTTPS, and not
  geofenced. Google will fetch it. This exact URL is hardcoded in
  `ExternalActions.PRIVACY_POLICY_URL` — if you deploy elsewhere, change it there too and
  rebuild, or the in-app link 404s.
- [ ] **Enter that URL** in Play Console → App content → Privacy policy.
- [ ] **Capture screenshots.** Minimum 2, target 4–6. Shot list and captions are in
  [`play-store-listing.md`](play-store-listing.md#screenshots). `plans/capture/` is empty.
- [ ] **Produce the feature graphic**, 1024×500. Currently missing; only prompt drafts
  exist (`plans/feature-image-prompt.md`, `plans/feature-image2.md`).
- [ ] **Complete the Data Safety form** — see §4. This is where most keyboard apps get
  rejected.
- [ ] **Complete the content rating questionnaire.**
- [ ] **Verify the release artifact** — see §6.
- [ ] **Supply and pin the production Firebase identity.** Put exactly one downloaded
  production config at `app/google-services.json` or
  `app/src/release/google-services.json`, then copy its `mobilesdk_app_id` and
  `project_id` into the matching blank fields in `version.properties`. Do not hand-edit
  the Firebase JSON.

---

## 2. ASO

Copy is drafted and character-counted in
[`play-store-listing.md`](play-store-listing.md). What to actually do:

- [ ] **Set the title to the 30-char budget.** Play weights title > short description >
  full description, so the title is the highest-value real estate you own.
  `Addiyon Amharic Keyboard ፊደል` is 28/30.
- [ ] **Publish the am-ET localized listing.** *This is the single highest-leverage ASO
  action available and it is not currently set up.* Play indexes and ranks each locale's
  listing separately — the English listing cannot rank for "የአማርኛ ኪቦርድ" no matter how
  good it is. Copy is written and ready to paste. Diaspora users search in Latin,
  in-country users increasingly search in Ethiopic; you need both listings to reach both.
- [ ] **Burn captions into the screenshots.** Play has no caption field for phone
  screenshots, and captions are what a browsing user actually reads.
- [ ] **Set category to Tools** and add keyword-relevant tags.
- [ ] **Confirm the in-app review prompt fires.** Ratings volume moves ranking more than
  any copy change. The prompt already exists (`review/`, gated by a usage counter in
  `KeyboardPrefs.KEY_USAGE_SESSIONS`) — it is the most valuable ASO asset already built,
  and worthless if it never triggers. Verify on a real device.
- [ ] **Do not** keyword-stuff, name competitors, or use superlatives ("best", "#1"). Each
  is grounds for listing rejection; competitor names can also draw a policy strike.
- [ ] **Plan to iterate after launch**, not before. Play Console → Store performance shows
  which search terms actually convert. Change one field at a time.

---

## 3. Store assets

- [ ] App icon 512×512 — `play_store_icon_512.png` exists and shares a source with the
  in-app icon. Confirm it is the one uploaded.
- [ ] Phone screenshots ×4–6, portrait, ≥1080×1920, clean status bar.
- [ ] Feature graphic 1024×500.
- [ ] *(Optional)* 7-inch and 10-inch tablet screenshots. Without them Play flags the app
  as not designed for tablets, which suppresses it in tablet search. Cheap to add from an
  emulator, worth doing if the keyboard lays out sanely at that size.
- [ ] *(Optional)* Promo video. Skip for v1.

---

## 4. Data Safety form and privacy consistency

Three surfaces must tell the same story: the in-app copy (`ui/i18n/AppStrings.kt`), the
hosted policy (`site/privacy.html`), and the Data Safety form. A mismatch is a classic IME
rejection, and keyboards get scrutinized harder than most categories.

The verified facts to answer from:

| Fact | Evidence |
|---|---|
| Typed/editor content is never accepted by telemetry APIs | `TelemetryApiPrivacyTest`, enum/boolean-only facade |
| Analytics and Crashlytics are independent opt-ins and off by default | manifest defaults, `TelemetryPrefs`, consent UI/tests |
| `INTERNET`, network state, and wake lock are intentional Firebase requirements | merged release manifest; exact allowlist in `plans/verify-release-artifact.sh` |
| Advertising ID and AdServices permissions are rejected | manifest removals and release-script deny checks |
| Typing and suggestions are fully on-device | bundled `.db` assets; `suggestion/`, `transliteration/` |
| Voice audio goes to the device's speech service | `voice/VoiceInputController.kt` delegates to `SpeechRecognizer` |
| Diagnostics consent is never backed up or transferred | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` |

- [ ] **Update Play Data safety for Firebase.** Do not claim "No data collected." Declare
  optional app interactions/lifecycle analytics, crash logs, diagnostics, Firebase and
  Crashlytics installation identifiers, device metadata, and approximate region if the
  final console configuration derives it from network information.
- [ ] **Declare the microphone/voice path honestly.** Do not tick "No data collected" and
  move on. Addiyon itself neither stores nor transmits audio, but it does hand audio to
  another app that may transmit it. Answer for what the app does, and let the policy
  explain the handoff — which it now does in detail.
- [ ] **Note that `EXTRA_PREFER_OFFLINE` is never set** (`VoiceInputController.kt:170`),
  so on most devices Google's recognizer processes speech online. Either keep the policy
  wording as-is (accurate today), or set that extra if you want to make offline the
  default — a product decision, not a blocker.
- [ ] **Declare the Android Backup behaviour** if the form asks about data transfer:
  settings and recent emoji sync to the user's own Google account when they have backup
  enabled; diagnostics consent is explicitly excluded.
- [ ] **Finish Firebase console privacy setup.** Use separate production/development
  projects, two-month Analytics event-level retention, no Google Signals, no ad
  personalization or Ads links, least-privilege roles, MFA, and Crashlytics alerts.
- [ ] **Verify revocation.** Analytics off resets local data; Crashlytics off deletes
  queued reports. Disabling cannot remove reports already uploaded.
- [ ] **Re-read the `AppStrings` diagnostics fields and `activateFootnote`** against
  whatever you submit, and against both localized listing privacy paragraphs. Keep those
  surfaces aligned with the hosted policy.

---

## 5. Licensing and attribution

The shipped dictionaries are derived from seven external sources. Only two have a license
recorded in [`tools/README.md`](../tools/README.md), and one of those imposes an obligation
that is currently unmet.

**Content actually copied into the shipped `.db` assets:**

| Source | License | Action |
|---|---|---|
| hermitdave *FrequencyWords* (English, OpenSubtitles) | **MIT** | ✅ permissive — needs the copyright notice reproduced |
| orgtre *google-books-ngram-frequency* (trigrams) | **CC BY** | ⚠️ **attribution to end users is required** — a developer README does not satisfy it |
| Norvig `count_2w.txt` (Google Web Trillion Word Corpus) | not recorded | ❓ confirm |
| CACO / Contemporary Amharic Corpus | not recorded | ❓ confirm |
| yididiyan *amharic_spell_corrector* frequencies | not recorded | ❓ confirm |
| abdulmunimjemal *AmharicSpellCheckerEngine* wordlist | not recorded | ❓ confirm — words are merged into the asset at frequency 1 |

**Used only as a filter, not copied** (materially lower risk, but confirm):

| Source | License | Note |
|---|---|---|
| Hunspell am_ET expansion | not recorded | Attestation-only — it decides which corpus words survive, and none of its content ships. Hunspell am_ET is commonly GPL/LGPL/MPL tri-licensed; worth confirming since copyleft on a *shipped* list would be a real problem. |

- [ ] **Confirm each unknown license** and record it in `tools/README.md` next to the
  source, so this never has to be re-derived.
- [ ] **Add a `THIRD_PARTY_NOTICES.md`** to the repo listing every source, its license, and
  its upstream URL.
- [ ] **Surface attribution in the app.** CC BY requires the credit reach end users. The
  About screen is the natural home — it already has the structure, and a "Credits &
  licenses" link next to the new privacy-policy link is a small change.
- [ ] **Add a repo `LICENSE`** for your own code. There is none, which leaves the project's
  own terms undefined.

> This is a genuine legal obligation rather than housekeeping, but it is not a Play
> submission blocker — Google does not check it at review. If you want to ship first and
> resolve it in the next update, that is a defensible call; just make it deliberately.

---

## 6. Release build verification

The repo already has a strong gate — use it rather than eyeballing the upload.

- [ ] **Commit everything first.** `plans/verify-release-artifact.sh` records
  `sourceStatus`, and the last recorded candidate came from a dirty tree.
- [ ] **Confirm `versionName=2.0.0`** in `version.properties` is the intended public
  version. The generated version code is the greater of `versionCodeFloor=68` and the Git
  commit count; record it from the fresh candidate and compare it with Play Console.
- [ ] Build the bundle:

```bash
./gradlew bundleRelease
```

- [ ] Run the gate against that exact artifact:

```bash
REQUIRE_CLEAN_RELEASE=1 ./plans/verify-release-artifact.sh
```

  It asserts the AAB entries, that raw `*_words.dat`/`*_ngrams.dat` are not packaged,
  the exact merged permission allowlist and advertising-permission denylist, production
  the exact pinned Firebase app/project identity, Firebase resources and Crashlytics
  mapping metadata, targetSdk 36,
  `jarsigner -verify`, the signing certificate against `releaseCertificateSha256`, and
  that release code contains no debug crash trigger or benchmark-only profile classes.
- [ ] **Do not trust existing candidate metadata or build outputs.** Regenerate
  `app/build/outputs/release-candidate.properties` from the exact frozen source and verify
  its version name, generated version code, source status, hashes, and certificate before
  upload.
- [ ] **Install and test the Play-delivered build**, not just a local release APK. Only the
  Play-delivered artifact exercises split-APK delivery and Play App Signing.
- [ ] **Confirm suggestions and diagnostics work after a fresh install of the minified
  build.** R8 and `shrinkResources` are both on. The verifier confirms the dictionary
  assets and mapping are present, while static contract tests pin the narrow reflection
  keep rules. Only running the exact artifact confirms the assets load and Firebase
  initializes after consent.
- [ ] Check Play's App bundle explorer for unexpected permissions, delivery warnings, or
  download size.
- [ ] Confirm the R8 mapping file reached Play, so release crash traces stay readable.

---

## 7. Device test pass

The full matrix is in
[`google-play-production-access.md` §4](google-play-production-access.md). Additionally,
specifically because of the changes made in this pass:

- [ ] **Launcher icon on an API 24 or 25 emulator** — this is the exact configuration that
  was showing the Android robot, and the only one the `anydpi-v26` icon never covered.
- [ ] **Settings → Languages & input → On-screen keyboards** — confirm the IME is named
  "Addiyon Keyboard" and now lists **two** languages (Amharic, English).
- [ ] **Switch subtype from the system language switcher mid-word** with an uncommitted
  Amharic buffer. The composer is committed on the way through; confirm no text is lost or
  duplicated. This is the riskiest behaviour change in this pass.
- [ ] **Globe key still switches language** and stays in agreement with the system
  switcher.
- [ ] **Password / ASCII-only fields** — the new `isAsciiCapable` English subtype should
  make the keyboard eligible where it may previously have been skipped.
- [ ] **About → Privacy policy link** opens the deployed page.
- [ ] **A device with no browser and no mic** — both new paths degrade to a toast rather
  than crashing.

---

## 8. Repo hygiene

- [x] `testers.csv` untracked and gitignored.
- [ ] **Decide about git history.** `testers.csv` and its four real email addresses remain
  in past commits. If `dawitamene/tanakeyboard` is or may become public, that is
  third-party PII exposure. Rewriting history (`git filter-repo`) is destructive and
  breaks every existing clone — your call, and not something to do casually.
- [ ] `.opencode/` (with `node_modules/`) is untracked and unignored — add it to
  `.gitignore` or delete it.
- [ ] The repo root holds 20+ loose planning `.md` files, four logo SVGs, and three 512px
  PNGs. None of it ships, but it buries the documents that matter. Worth a tidy into
  `docs/` and `plans/` at some point.

---

## 9. After launch

Optional Firebase Analytics and Crashlytics are implemented behind independent,
off-by-default consent controls. Android Vitals remains authoritative for Play stability;
Firebase dashboards cover only devices whose users enabled the corresponding diagnostic
choice.

- [ ] **Roll out staged**, not to 100%. 20% → hold a few days → 50% → 100%. Staged rollout
  is the only mechanism that lets you halt a bad release.
- [ ] **Watch Android Vitals daily for the first week.** Play's bad-behaviour thresholds
  are 1.09% user-perceived crash rate and 0.47% user-perceived ANR rate; exceeding them
  suppresses store visibility.
- [ ] **Read every 1- and 2-star review in the first two weeks.** For a keyboard, early
  reviews are also your best bug tracker.
- [ ] **Watch consent-bounded Firebase dashboards and alerts.** Validate safe event
  volumes, sanitized non-fatals, fatal deobfuscation, and Crashlytics velocity alerts
  without treating opt-in coverage as the install population.
- [ ] **Set up CI.** The JVM, instrumented, privacy-contract, and macrobenchmark suites
  have no `.github/workflows/`, so none of them runs automatically. Everything you built
  is only as good as the last time someone remembered to run it.
- [ ] **Baseline lint.** No `lint.xml`, no `lint-baseline.xml`, no `lint {}` block —
  `./gradlew lintDebug` has never been triaged, so its current state is unknown.
