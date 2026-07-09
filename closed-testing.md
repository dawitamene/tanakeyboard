# Play Store Testing — Pre-Submission Readiness Checklist

## Context

Tana Keyboard (`com.addiyon.tanakeyboard`) is being submitted to Google Play testing.
Even testing-track builds are scanned by Google Play review, and **input-method (IME) / keyboard
apps get extra scrutiny** because a keyboard can see everything a user types. The goal is to reach
review with zero policy mismatches so the app isn't rejected or held.

Two decisions that shape this checklist:
1. **Analytics WILL be added** for this release (crash reports + anonymous usage) — the app today has
   **no `INTERNET` permission and no analytics SDK**, so this is net-new code + new disclosures.
2. **The privacy-policy page is not yet hosted** — a live HTTPS privacy URL is a hard prerequisite
   for any keyboard app, so deploying it is step one.

Current state confirmed from the repo:
- `AndroidManifest.xml`: only the IME `service` + activities; **no `<uses-permission>` at all**.
- `app/build.gradle.kts`: `minSdk 24`, `targetSdk 35`, `compileSdk 35`; release build is R8-minified
  + resource-shrunk; release signing driven by gitignored `app/keystore.properties`.
- No analytics/crash/`INTERNET`/http references anywhere in `app/src/main`.
- Store copy (`play-store-listing.md`) and the website privacy template (`plan.md` §6.3) already
  claim "basic anonymous analytics / crash reports" — so implementing analytics makes the copy true.

---

## A. Hard blockers (fix before uploading)

### A1. Deploy the privacy policy to a public HTTPS URL — *step one*
Google **requires** a working privacy-policy link for all IME/keyboard apps. The full site spec is
in `plan.md`; at minimum `privacy.html` must be live over HTTPS.
- Deploy the site (or just a standalone privacy page) to a stable domain, e.g.
  `https://tanakeyboard.addiyon.com/privacy.html`.
- Update the privacy page's **effective date** and reconcile its wording with what analytics actually
  collects (see A2). Remove the "Developer note" placeholder callout before publishing.
- Paste that URL into **Play Console → App content → Privacy policy**.

### A2. Implement analytics correctly — the IME-safe way
This is the highest-risk area for a keyboard. Rule #1: **never transmit typed content or keystrokes.**
- Add `<uses-permission android:name="android.permission.INTERNET"/>` to `AndroidManifest.xml`
  (currently absent — analytics can't upload without it).
- Add an SDK (Firebase Crashlytics + Analytics is the conventional choice) via
  `app/build.gradle.kts` + the Google Services plugin, or a lighter alternative.
- **Audit that nothing logs the composing buffer / typed text.** `WordComposer`, `Transliterator`,
  and `TanaKeyboardService.onCharacter` handle raw typed content — no analytics event may include
  that. Log only coarse, non-content events (crashes, feature toggles, layout switches) — never the
  words. This is the single thing most likely to get a keyboard pulled.
- Make crash reporting opt-in or disclosed. Keep the privacy policy's existing promise
  ("never used to reconstruct what you typed") literally true in code.

### A3. Data Safety form must match reality (and the privacy policy)
Because analytics is now present, you **cannot** mark "No data collected." Declare:
- **Crash logs** and **App diagnostics/performance** as *collected*, **not shared** with third
  parties (or list the analytics vendor if it counts as sharing), and processed for analytics/bug-fix.
- Mark data as **anonymous / not linked to identity**, encrypted in transit, and that no typed
  content / message text is collected. The Data Safety answers must be consistent with A1/A2 — an
  inconsistency here is a common rejection reason.

### A4. Build a signed release **App Bundle (.aab)**, not an APK
Play requires an `.aab`. Confirm `app/keystore.properties` exists so the `release` signing config
materializes, then `./gradlew bundleRelease`. Enroll in **Play App Signing** (default for new apps).

### A5. Verify the R8/minified release build actually works
Release is `isMinifyEnabled = true` + `isShrinkResources = true`. Bugs here won't show in the debug
keyboard you've been testing.
- Install the **signed release** build and confirm: IME enables, typing works in ≥2 apps, both
  languages, suggestions appear (⇒ the gzip `.dat` dictionary **assets weren't stripped**), themes,
  number row, backspace/commit.
- If suggestions break, add `proguard-rules.pro` keep rules for the trie/dictionary/asset code.

---

## B. Play Console "App content" declarations (all must be completed)

- **Privacy policy** — URL from A1.
- **Data safety** — per A3.
- **Ads** — declare **No ads** (no ad SDK present; keep it that way).
- **Content rating** — complete the IARC questionnaire (utility app → expect "Everyone").
- **Target audience & content** — general audience; **not** directed at children under 13 (matches the
  privacy policy's "Children" section). Do not opt into the Designed for Families program.
- **Government / financial / health / news / COVID** — all "No".
- **Data deletion** — no account, so nothing to delete server-side; state on-device data clears on
  uninstall (matches privacy policy "Your rights").

## C. Store listing assets (required to publish the listing)

- **App name**: "Tana Keyboard" (matches `strings.xml`).
- **Short description** (≤80) + **Full description** (≤4000): already drafted in
  `play-store-listing.md`. Before pasting: keep the PRIVACY paragraph consistent with A2 (crash
  analytics exists but excludes typed content), and keep it free of "best/#1", competitor names, and
  keyword stuffing (already flagged in that file's checklist).
- **App icon** 512×512 PNG (you have adaptive `mipmap` launcher icons; export a 512 version).
- **Feature graphic** 1024×500.
- **Phone screenshots**: at least 2 (up to 8), min dimension 320px — reuse the layout screenshots
  referenced in `plan.md` (english/amharic/special layouts).
- **App category**: Tools / Productivity.

## D. Testing track setup

- Create the release, upload the `.aab` from A4.
- Add testers: you already have `testers.csv` — add those emails (or a Google Group) to the track.
  (Closed testing has a **12-tester / 14-day** requirement before you can promote to production —
  internal testing does not, and is the fastest path if you just need reviewers on the build.)
- Share the opt-in URL with testers; they must accept before they can install.
- Note: testing tracks still run Google review, but it's fast and the app isn't publicly visible.

## E. Build / config hygiene (quick verifications, not blockers)

- **`applicationId = com.addiyon.tanakeyboard` is permanent** once uploaded — confirm it's final.
- **versionCode**: auto-derived from `git rev-list --count HEAD`; ensure the number is higher than any
  prior upload to this app (first upload: any value is fine). `versionName` is `1.1.0`.
- **targetSdk 35** satisfies the current "new apps" requirement. Be aware Google's API-36 deadline is
  expected ~Aug 2026 — fine for this submission, plan an eventual bump.
- The `/Users/dev/Shared` APK-copy hook in `build.gradle.kts` is host-guarded and a no-op on any
  other machine/CI — leave it; it doesn't affect the bundle.
- `method.xml` declaring no subtypes is acceptable for review (optional future polish: declare an
  am/en subtype for the system language picker) — **not** a blocker.

---

## Verification (before hitting "Submit")

1. `./gradlew testDebugUnitTest` passes (transliteration/suggestion logic intact).
2. `./gradlew bundleRelease` produces a signed `.aab` (keystore.properties present).
3. Sideload the **release** build (`assembleRelease` APK) and manually exercise the keyboard end-to-end
   per A5 — this is the real gate, since minification only affects release.
4. Open the deployed privacy URL (A1) in a browser over HTTPS — loads, no placeholder note.
5. Grep the codebase to prove no analytics event references the composing buffer / typed text (A2).
6. In Play Console, confirm every "App content" item shows a green check and Data Safety matches the
   live privacy policy word-for-word on the analytics claim.
