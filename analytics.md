# Analytics & Crash Reporting — Implementation Plan (IME-safe)

**Scope:** Firebase **Crashlytics only** (no Analytics / no usage tracking),
**opt-in and OFF by default**. Nothing about what a user types — or that they
typed at all — ever leaves the device. The only thing that can be uploaded, and
only after the user explicitly opts in, is a crash stack trace plus device
metadata.

This document is the step-by-step guide for implementing A2 of the
closed-testing checklist. Follow the sections in order.

---

## 0. Why this is done this way

Tana Keyboard is an `InputMethodService`. A keyboard that transmits typed
content is the fastest way to get pulled from Play. So:

- **Crashlytics only.** No `firebase-analytics` dependency, no event logging.
  The smallest possible privacy surface — crash reports and nothing else.
- **Opt-in, OFF by default.** Auto-collection is disabled in the manifest and
  only turned on if the user flips a "Share crash reports" toggle. A fresh
  install collects nothing.
- **Single choke point.** All Crashlytics access goes through one
  `CrashReporting` object, so the whole integration can be audited by reading
  one file.

### Rule #1 — never transmit typed content or keystrokes

The following identifiers hold (or may hold) typed content and **must never**
be passed to any Crashlytics call (`log`, `setCustomKey`, `recordException`, or
an exception message):

```
latin, output, text, word, prefix, raw, display, buffer,
suggestions, amharicBufferLatin,
and any InputConnection / EditorInfo text
```

Content flows through exactly three areas — keep all three Crashlytics-free:
`TanaKeyboardService` key handlers → `WordComposer` (`buffer`/`raw`/`display`)
→ `Transliterator` (pure, stateless).

---

## 1. One-time Firebase Console setup

1. Go to <https://console.firebase.google.com> and create (or select) a
   project.
2. **Add app → Android.** Package name: **`com.addiyon.tanakeyboard`**
   (must match `applicationId`/`namespace` in `app/build.gradle.kts`).
3. Download **`google-services.json`** and place it at
   **`app/google-services.json`** (module root, not repo root).
4. In the console left nav, open **Crashlytics** and enable it for the app.
   (Analytics can be left disabled — Crashlytics does not require it.)
   The first crash report shows up only after a real crash **and** the next app
   launch (Crashlytics uploads pending reports on the following start).

### `google-services.json` and version control

It is not a secret, but to keep the repo portable, add it to `.gitignore`:

```gitignore
# Firebase config — each build environment supplies its own
app/google-services.json
```

Any other machine/CI that builds the app needs its own copy of this file, or
the `google-services` plugin build step will fail. If a CI build must succeed
without Firebase, gate the two plugins behind the file's existence (optional;
not required for local dev).

---

## 2. Gradle wiring

### 2.1 `gradle/libs.versions.toml`

Add versions, the Firebase BOM library + Crashlytics library, and the two
plugin aliases. (Pin exact versions; the ones below are known-compatible with
AGP 8.13.2 / Kotlin 2.0.21. Bump to the latest if you verify compatibility.)

```toml
[versions]
# ...existing...
googleServices = "4.4.2"
firebaseCrashlyticsPlugin = "3.0.2"
firebaseBom = "33.7.0"

[libraries]
# ...existing...
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
# NOTE: intentionally NO firebase-analytics — Crashlytics works without it.

[plugins]
# ...existing...
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlyticsPlugin" }
```

### 2.2 Root `build.gradle.kts`

Declare the new plugins `apply false`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
```

### 2.3 `app/build.gradle.kts`

Apply the plugins and add the dependencies:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

dependencies {
    // ...existing...
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
}
```

**Release note:** the release build already sets `isMinifyEnabled = true` and
`isShrinkResources = true`. The Crashlytics Gradle plugin automatically uploads
the R8 mapping file so release stack traces deobfuscate — no extra config is
needed. Just don't disable mapping upload.

---

## 3. Manifest changes

`app/src/main/AndroidManifest.xml`:

1. Add the INTERNET permission (currently absent — without it nothing can
   upload):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

2. Register the Application class and disable Crashlytics auto-collection, so
   **nothing is collected until the user opts in**:

```xml
<application
    android:name=".TanaApplication"
    android:allowBackup="true"
    ... >

    <meta-data
        android:name="firebase_crashlytics_collection_enabled"
        android:value="false" />

    <!-- existing service / activities unchanged -->
</application>
```

---

## 4. Application class + the single choke point

### 4.1 `TanaApplication.kt` (new)

`app/src/main/java/com/addiyon/tanakeyboard/TanaApplication.kt`

```kotlin
package com.addiyon.tanakeyboard

import android.app.Application
import com.addiyon.tanakeyboard.ui.settings.KeyboardPrefs

class TanaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Manifest disables auto-collection; honor the user's saved opt-in.
        CrashReporting.setEnabled(this, KeyboardPrefs.crashReporting(this))
    }
}
```

### 4.2 `CrashReporting.kt` (new) — the ONLY file allowed to touch Crashlytics

`app/src/main/java/com/addiyon/tanakeyboard/CrashReporting.kt`

```kotlin
package com.addiyon.tanakeyboard

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * The single choke point for crash reporting. Nothing else in the app may
 * import FirebaseCrashlytics — this makes the "no typed content" guarantee
 * auditable by reading one file.
 *
 * Content-free by construction: no function here accepts user text, and no
 * caller may pass typed content (see the blocklist in analytics.md §0).
 */
object CrashReporting {

    /** Enable/disable collection at runtime (mirrors the opt-in toggle). */
    fun setEnabled(context: Context, enabled: Boolean) {
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
    }

    /** Fixed literal breadcrumb tags only — never a variable holding text. */
    fun breadcrumb(tag: String) {
        FirebaseCrashlytics.getInstance().log(tag)
    }

    /** Report a caught throwable. Ensure its message carries no typed text. */
    fun record(t: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(t)
    }
}
```

> Breadcrumbs are optional. With "Crashlytics only," uncaught crashes are the
> primary signal. If `breadcrumb(...)` is ever used, pass only fixed string
> literals such as `"lang_switch"` or `"layout_switch"` — never a variable that
> could hold `latin`, `raw`, `word`, etc.

---

## 5. The opt-in preference

### 5.1 `ui/settings/KeyboardPrefs.kt`

Follow the existing per-key pattern (mirrors `KEY_VIBRATE` etc.):

```kotlin
const val KEY_CRASH_REPORTING = "crash_reporting"

fun crashReporting(context: Context): Boolean =
    prefs(context).getBoolean(KEY_CRASH_REPORTING, false) // default OFF = opt-in

fun setCrashReporting(context: Context, value: Boolean) =
    prefs(context).edit().putBoolean(KEY_CRASH_REPORTING, value).apply()
```

### 5.2 `ui/settings/SoundVibrationScreen.kt` (labeled "Preferences")

Add a `ToggleRow` alongside the existing Vibrate / Sound / Number-row rows,
bound to `crashReporting`/`setCrashReporting`. Its `onToggle` must **also** call
`CrashReporting.setEnabled(context, value)` so the change takes effect
immediately without restarting the app:

```kotlin
ToggleRow(
    title = strings.crashReporting,
    subtitle = strings.crashReportingDesc,
    checked = KeyboardPrefs.crashReporting(context),
    onCheckedChange = { value ->
        KeyboardPrefs.setCrashReporting(context, value)
        CrashReporting.setEnabled(context, value)
    },
)
```

### 5.3 `ui/i18n/AppStrings.kt` — new strings (EN + AM)

```kotlin
// English
val crashReporting = "Share crash reports"
val crashReportingDesc = "Anonymous crash diagnostics. Never includes what you type."

// Amharic — provide equivalent translations
```

---

## 6. Reword the privacy strings (keep the promise literally true)

In `ui/i18n/AppStrings.kt`, the current EN strings claim "never collects any
data" — untrue the moment an optional crash report exists. Update EN **and** AM:

- **`aboutPrivacy`** →
  "Your privacy: Tana Keyboard never collects what you type — everything you
  type stays on your device. Optional crash reports (off by default) contain
  only technical diagnostics, never your text."
- **`activateFootnote`** → a concise version of the same.

Also reconcile the wording in the hosted `privacy.html` (and the drafts in
`plan.md` / `claude-design-promt.md`) so the promise "never used to reconstruct
what you typed" reads consistently: it is still true — crash reports carry no
typed content — but the "never collects any data" absolute must go.

---

## 7. Content-safety audit checklist

Run before shipping:

```bash
# Every hit must be inside CrashReporting.kt or TanaApplication.kt,
# and must pass only string literals / Throwables — never typed content.
grep -rnE "Crashlytics|recordException|setCustomKey|\.breadcrumb\(|\.log\(" app/src
```

- [ ] All Crashlytics access is confined to `CrashReporting.kt` /
      `TanaApplication.kt`.
- [ ] No key handler in `TanaKeyboardService.kt` (`onCharacter`, `onDelete`,
      `commitText`, `onSpace`, `onEnter`, `onSuggestionTapped`,
      `updateSuggestions`, `englishSuggestions`, `amharicSuggestions`,
      `resumeEnglishWordIfAny`) passes its args/results to `CrashReporting`.
- [ ] `WordComposer.kt` and `Transliterator.kt` import nothing from Crashlytics.
- [ ] Default is OFF: a fresh install with the toggle untouched collects
      nothing.

---

## 8. Verification

1. **Build:** `./gradlew compileDebugKotlin` then `./gradlew assembleDebug`
   succeed with the new plugins/deps and `app/google-services.json` present.
2. **Opt-in respected (off):** Install; do **not** touch the toggle. Force a
   test crash (e.g. a temporary `throw RuntimeException("test")` behind a debug
   gesture), relaunch. Confirm **no** report appears in the Crashlytics console.
3. **Opt-in respected (on):** Enable "Share crash reports" in Preferences,
   repeat the crash, relaunch. Confirm the report now appears — and inspect it
   in-console to verify it contains **only** a stack trace and device metadata,
   with **no typed text, buffer, or suggestions**.
4. **Runtime disable:** Toggle off again; confirm collection stops
   (`setEnabled(false)` takes effect without restart).
5. **No regressions:** `./gradlew testDebugUnitTest` — transliteration and
   suggestion tests still pass (the core pipeline is untouched).
6. Remove the temporary test-crash code before release.

---

## 9. Files touched

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Firebase BOM + Crashlytics libs, 2 plugin aliases |
| `build.gradle.kts` (root) | 2 plugins `apply false` |
| `app/build.gradle.kts` | apply plugins, add BOM + Crashlytics deps |
| `app/src/main/AndroidManifest.xml` | INTERNET perm, `android:name`, collection-disabled meta-data |
| `app/google-services.json` | **new** (from console) + `.gitignore` entry |
| `app/src/main/java/.../TanaApplication.kt` | **new** — init/opt-in gate |
| `app/src/main/java/.../CrashReporting.kt` | **new** — single choke point |
| `app/src/main/java/.../ui/settings/KeyboardPrefs.kt` | `KEY_CRASH_REPORTING` getter/setter |
| `app/src/main/java/.../ui/settings/SoundVibrationScreen.kt` | opt-in `ToggleRow` |
| `app/src/main/java/.../ui/i18n/AppStrings.kt` | new strings + reworded privacy strings (EN + AM) |
