# Addiyon Keyboard — Implementation Plan

Concrete, code-grounded plan for four changes to the keyboard app (currently
`com.addiyon.tanakeyboard`, "Tana Keyboard"):

1. **Rebrand + package rename** → "Addiyon Keyboard", `com.addiyon.keyboard`
2. **Fix mid-word autocomplete** (resuming a word you return to)
3. **Voice input** (speech → text, Amharic + English)
4. **Fix ANR / unresponsiveness on slow phones**

## Recommended sequencing

Do them in this order to avoid churn:

1. **Feature 4 (ANR)** first — it's the launch-blocker, and it touches
   `onCreate`/dictionary loading + build config that the other features build on.
2. **Feature 2 (autocomplete)** — small, isolated to the service + composer.
3. **Feature 3 (voice)** — additive; new files + one manifest permission + one toolbar button.
4. **Feature 1 (rename)** **last** — it's a mechanical rename across the whole
   tree; doing it last means features 2–4 aren't rebased onto renamed symbols
   mid-flight, and there's a single "big rename" commit at the end.

> If you'd rather brand first, that's fine too — just expect every later diff to
> reference `Addiyon*` symbols. The plan below is written so Feature 1 can run
> either first or last.

---

# Feature 1 — Rebrand to "Addiyon Keyboard" + package `com.addiyon.keyboard`

## What changes

| Thing | From | To |
|---|---|---|
| `namespace` + `applicationId` (`app/build.gradle.kts`) | `com.addiyon.tanakeyboard` | `com.addiyon.keyboard` |
| Source dirs (`main`, `test`, `androidTest`) | `.../java/com/addiyon/tanakeyboard/` | `.../java/com/addiyon/keyboard/` |
| `package` / `import` statements (all `.kt`) | `com.addiyon.tanakeyboard` | `com.addiyon.keyboard` |
| Service class | `TanaKeyboardService` | `AddiyonKeyboardService` |
| Compose host view | `TanaKeyboardView` | `AddiyonKeyboardView` |
| App theme | `TanaBrandTheme` | `AddiyonBrandTheme` |
| XML style names | `Theme.TanaKeyboard`, `Theme.TanaKeyboard.Splash` | `Theme.AddiyonKeyboard`(+`.Splash`) |
| Drawables | `ic_tana_background/foreground/icon` | `ic_addiyon_background/foreground/icon` |
| Color names (`colors.xml`) | `*tana*` | `*addiyon*` |
| `app_name` (`strings.xml`) | `Tana Keyboard` | `Addiyon Keyboard` |
| User-facing strings ("Tana", "Tana Keyboard") | — | "Addiyon" / "Addiyon Keyboard" |
| `rootProject.name` (`settings.gradle.kts`) | `Tana Keyboard` | `Addiyon Keyboard` |

**Decision made in this plan:** keep `namespace` == `applicationId` ==
`com.addiyon.keyboard` (best practice; avoids a split between the R-class package
and the install id).

## ⚠️ Two things to confirm BEFORE doing this

1. **Play Store identity.** `applicationId` *is* the app's identity on Google
   Play. If `com.addiyon.tanakeyboard` has already been uploaded (there's a
   `closed-testing.md` and `testers.csv` in the repo, so a closed track may
   exist), changing to `com.addiyon.keyboard` makes it a **brand-new app** in
   Play Console:
   - The existing closed-testing track, reviews, and installs do **not** carry over.
   - Existing testers must install the new package from scratch.
   - You must re-do the store listing under the new id.

   This is the right move *only if done before the public launch*. If the intent
   is just a display-name change and the package can stay, keeping the
   `applicationId` and only changing `app_name` + user-facing text is far less
   disruptive. **Recommendation:** rename the package now (pre-launch), and
   create the Play listing fresh under `com.addiyon.keyboard`.

2. **IME component id changes.** The keyboard is identified to the system by
   `ComponentName(context, AddiyonKeyboardService)`. Renaming the package AND the
   service class changes that id, so anyone who already has the keyboard enabled
   must **re-enable it** after updating. Acceptable pre-launch. `KeyboardStatus`
   (`KeyboardStatus.kt`) already derives the component from the class reference,
   so it updates automatically with the rename — no string id is hardcoded.

## Step-by-step

> All `sed -i` examples use BSD/macOS syntax (`sed -i ''`). **Scope every command
> to `app/src` (and the three gradle files) — never touch `app/build/`**, which is
> full of generated `tana` references that get regenerated on the next build.
> Best done with Android Studio's *Refactor → Rename* for the package + classes
> (it fixes imports and references safely); the shell commands below are the
> manual equivalent / checklist.

### 1. Move the source directories (preserve git history)

```bash
cd /Users/dev/AndroidStudioProjects/TanaKeyboard
for set in main test androidTest; do
  git mv app/src/$set/java/com/addiyon/tanakeyboard app/src/$set/java/com/addiyon/keyboard
done
```

(If `git mv` complains about the intermediate dir, `mkdir -p` the target parent
first. There's also a stray non-source file `app/src/main/java/.../output.txt` —
delete it; it doesn't belong in the source tree.)

### 2. Rewrite package + import paths in every Kotlin file

```bash
grep -rl 'com\.addiyon\.tanakeyboard' app/src \
  | xargs sed -i '' 's/com\.addiyon\.tanakeyboard/com.addiyon.keyboard/g'
```

### 3. Rename classes/symbols (do these as IDE renames, or careful sed)

- `TanaKeyboardService` → `AddiyonKeyboardService` (also rename the file, and the
  `android:name=".TanaKeyboardService"` in `AndroidManifest.xml`).
- `TanaKeyboardView` → `AddiyonKeyboardView` (rename file too).
- `TanaBrandTheme` → `AddiyonBrandTheme` (in `ui/theme/Theme.kt` and its callsite
  in `MainActivity.kt`).

```bash
grep -rl 'TanaKeyboardService\|TanaKeyboardView\|TanaBrandTheme' app/src \
  | xargs sed -i '' -e 's/TanaKeyboardService/AddiyonKeyboardService/g' \
                    -e 's/TanaKeyboardView/AddiyonKeyboardView/g' \
                    -e 's/TanaBrandTheme/AddiyonBrandTheme/g'
git mv app/src/main/java/com/addiyon/keyboard/TanaKeyboardService.kt \
       app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt
git mv app/src/main/java/com/addiyon/keyboard/TanaKeyboardView.kt \
       app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardView.kt
```

### 4. Rename theme styles + drawables + colors

- **Styles** (`res/values/themes.xml`, `res/values-night/themes.xml`): rename
  `Theme.TanaKeyboard` → `Theme.AddiyonKeyboard` and `Theme.TanaKeyboard.Splash`.
  Update references in `AndroidManifest.xml` (`android:theme=...`) and
  `MainActivity.kt` (`R.style.Theme_TanaKeyboard` → `R.style.Theme_AddiyonKeyboard`).
- **Drawables**: `git mv` `res/drawable/ic_tana_{background,foreground,icon}.xml`
  → `ic_addiyon_*.xml`; update references in `mipmap-anydpi-v26/ic_launcher*.xml`
  and `res/drawable/splash_screen.xml`.
- **Colors** (`res/values/colors.xml`, `values-night/colors.xml`): rename any
  `tana*` color names → `addiyon*` and update their references (in themes.xml and
  Compose `ui/theme/`). Search: `grep -rin 'tana' app/src/main/res`.

### 5. Gradle + settings

- `app/build.gradle.kts`: `namespace = "com.addiyon.keyboard"` and
  `applicationId = "com.addiyon.keyboard"`.
- `settings.gradle.kts`: `rootProject.name = "Addiyon Keyboard"`.
- (Optional cosmetic) `app/keystore.properties` has a `tana` keyAlias — leave the
  keystore alias as-is unless you're regenerating the signing key; renaming an
  alias requires a new key and re-signing.

### 6. Brand text

- `res/values/strings.xml`: `app_name` → `Addiyon Keyboard`.
- Replace user-facing "Tana Keyboard" / "Tana" in the Compose UI strings. Hot
  spots (from `grep -rin 'tana' app/src/main/java`):
  `ui/i18n/AppStrings.kt` (13), `ui/settings/AboutScreen.kt`,
  `ui/settings/SettingsScreen.kt`, `ui/onboarding/OnboardingScreen.kt`,
  `ui/home/HomeScreen.kt`, `ui/AppBrandHeader.kt`, `ui/feedback/FeedbackContent.kt`,
  `ui/manual/ManualScreen.kt`. **Read each in context** — some are the wordmark
  ("Tana Keyboard"), some are prose ("with Tana you can…"). The i18n layer
  (`AppStrings.kt`) has English + Amharic strings; update both languages.

### 7. Verify

```bash
grep -rin 'tana' app/src            # must return nothing (or only intentional)
./gradlew compileDebugKotlin        # fast compile check
./gradlew testDebugUnitTest         # unit tests still green (test package moved)
./gradlew assembleDebug             # full build; confirms manifest/res wiring
```

Then install and confirm: launcher label reads "Addiyon Keyboard", the IME shows
as "Addiyon Keyboard" in Settings › On-screen keyboards, and typing still works.

## Also update (docs, non-code)

`CLAUDE.md`, `AGENTS.md`, `play-store-listing.md`, `closed-testing.md`,
`analytics.md`, and the icon assets `tana_play_icon.svg` / `play_icon_512.png`
still say "Tana". Rename/rewrite these separately (not build-critical, but do it
before the store listing goes live).

---

# Feature 2 — Fix mid-word autocomplete when returning to a word

## Reproduction (what the user reports)

Type `info`, press space, type another word, move the caret back to the **end of
`info`**, and continue typing (`rmation`). Expected: suggestions complete
`information`. Actual: it treats the new keystrokes as a fresh word.

## Root cause (from the code)

`AddiyonKeyboardService.onCharacter()` already has a resume path — but **only for
English**:

```kotlin
isAmharic -> amharicComposer.onCharacter(output)          // NO resume
else -> {
    if (!englishComposer.isComposing) resumeEnglishWordIfAny()   // English only
    englishComposer.onCharacter(output)
}
```

- `resumeEnglishWordIfAny()` (service, ~line 566) works by reading the trailing
  word characters before the caret straight out of the field, deleting them, and
  re-inserting them as a composing region (`WordComposer.resume`). This works for
  English because **the field text == the composer's raw buffer**.
- **Amharic is the default language** (`isAmharic = true`) and has **no resume
  path at all**. Returning to a fidel word and typing starts a fresh buffer, so
  suggestions key off only the new suffix, and space commits the suffix fidel
  appended after the existing word instead of merging. This is almost certainly
  the failure the user hit (their `info` example most likely typed in the default
  Amharic mode, or the general "come back to a word" behavior).
- Amharic can't use the English trick: the field holds **fidel**, and recovering
  the original SERA Latin from fidel is ambiguous (documented in
  `WordTrie`/`WordComposer`) — so we can't reconstruct the raw buffer from the
  field.

## Fix

### 2a. Amharic resume via a committed-word memory (sidesteps reverse transliteration)

We don't need to reverse fidel in general — only to resume words **we ourselves
committed this session**, whose raw Latin we still had at commit time. Keep a
bounded map from committed fidel → its raw Latin:

- In `AddiyonKeyboardService`, add:
  ```kotlin
  // Bounded LRU of fidel -> raw Latin for words committed this session, so the
  // user can walk the caret back to an earlier Amharic word and keep typing it.
  private val amharicCommitHistory = object : LinkedHashMap<String, String>(64, 0.75f, true) {
      override fun removeEldestEntry(e: Map.Entry<String, String>) = size > 200
  }
  ```
- Record on every Amharic commit. The cleanest seam is a callback from
  `WordComposer.commit()`/`commitSuggestion()`, or record in the service right
  before delegating to the composer. Store `display` (fidel) → `raw` (Latin) for
  the Amharic composer only.
- Add `resumeAmharicWordIfAny()`, mirroring the English one:
  ```kotlin
  private fun resumeAmharicWordIfAny() {
      val ic = currentInputConnection ?: return
      val after = ic.getTextAfterCursor(1, 0)
      if (!after.isNullOrEmpty() && isFidelWordChar(after[0])) return
      val before = ic.getTextBeforeCursor(MAX_RESUME_PREFIX, 0)?.toString() ?: return
      val fidelWord = before.takeLastWhile { isFidelWordChar(it) }
      if (fidelWord.isEmpty()) return
      val rawLatin = amharicCommitHistory[fidelWord] ?: return   // only words we committed
      ic.beginBatchEdit()
      ic.deleteSurroundingText(fidelWord.length, 0)   // Ethiopic is BMP: 1 char == 1 code unit
      amharicComposer.resume(rawLatin)                // composesInline=false -> just seeds buffer
      ic.endBatchEdit()
  }
  ```
  `isFidelWordChar` = Ethiopic block check, e.g. `it in 'ሀ'..'፿'`.
- Wire it into `onCharacter`:
  ```kotlin
  isAmharic -> {
      if (!amharicComposer.isComposing) resumeAmharicWordIfAny()
      amharicComposer.onCharacter(output)
  }
  ```
- Because Amharic composes out-of-field, after `resume` the deleted fidel is gone
  from the field and the buffer (raw Latin) drives the preview strip + suggestions
  again; the next `commit()` writes the full fidel back. Only words typed this
  session are resumable (pasted/older fidel falls through to a fresh word — an
  acceptable limitation, and it never *corrupts* anything).

### 2b. Harden the English path + add regression tests

- Confirm the English case actually reproduces. Tracing the current code, the
  simple `info` → move back → `rmation` case *should* already work via
  `resumeEnglishWordIfAny()`. If it doesn't in practice, the likely culprits are:
  (a) the caret-after guard (`getTextAfterCursor`) mis-firing, or (b) an
  `onUpdateSelection` `abandon()` racing the resume. Verify with an instrumented
  test before changing behavior.
- Add tests:
  - **Unit** (`composing/WordComposerTest.kt`, new): `resume(prefix)` seeds the
    buffer and subsequent `onCharacter` extends it; `commit` emits the full word.
  - **Instrumented** (`androidTest`) or a fake-`InputConnection` unit harness:
    simulate `info`+space+`x`+space, move caret to end of `info`, type `rmation`,
    assert suggestions contain `information` and commit yields `information` in
    place — for **both** English and Amharic (using known SERA→fidel words, e.g.
    `selam` → ሰላም).

### Files touched
`AddiyonKeyboardService.kt` (resume logic + history), possibly
`composing/WordComposer.kt` (a commit callback), new test files.

---

# Feature 3 — Voice input (speech → text), Amharic + English

## Recommendation: Android `SpeechRecognizer` backed by Google's engine

After researching the options:

| Option | Amharic quality | Offline | Cost/keys | Fit for an IME |
|---|---|---|---|---|
| **Android `SpeechRecognizer` (Google Speech Services)** | **Good** — same engine as Gboard voice typing; Amharic (`am-ET`) is officially supported and also in Cloud Speech | Online (Amharic on-device pack generally unavailable) | Free, no API key | **Best** — this is exactly what Gboard does |
| Vosk (offline) | **No Amharic model exists** (20+ langs, not Amharic) | Yes | Free | English-only fallback at best |
| Whisper on-device (whisper.cpp) | Poor out-of-the-box for Amharic; needs a fine-tuned model; large + slow on low-end | Yes | Free (model shipped) | Heavy; conflicts with Feature 4's perf goals |
| Google/Azure/AWS Cloud STT | Good | No | **Paid + API key + backend** | Overkill, adds cost + privacy surface |

**Decision:** use `android.speech.SpeechRecognizer` with Google's recognizer,
`EXTRA_LANGUAGE = "am-ET"` (Amharic) or `"en-US"` (English), driven off the
current keyboard language (`service.isAmharic`). This is free, needs no backend
or API keys, gives the best available Amharic quality, and matches user
expectations (it *is* the Gboard voice-typing engine). Amharic will require a
network connection (same as Gboard) — that's acceptable and expected.

> Optional future enhancement: bundle Vosk's small English model for offline
> English dictation, falling back to `SpeechRecognizer` for Amharic. Not in scope
> for v1 — keep it simple.

## The hard part: an IME can't request runtime permissions

`SpeechRecognizer` needs `RECORD_AUDIO`, which is a **runtime** permission
(Android 6+). An `InputMethodService` has no Activity, so it **cannot call
`requestPermissions()` directly**. Standard solution (used by AOSP/Gboard): a
tiny transparent Activity that requests the permission and returns.

## Architecture

```
Mic button (SuggestionArea toolbar)
        │  service.onVoiceInput()
        ▼
AddiyonKeyboardService.onVoiceInput()
   ├─ if RECORD_AUDIO not granted -> launch VoicePermissionActivity (transparent),
   │     which requests it and finishes; on return the service re-checks + starts
   └─ if granted -> VoiceInputController.start(language = if (isAmharic) "am-ET" else "en-US")
        ▼
VoiceInputController (wraps SpeechRecognizer + RecognitionListener, main thread)
   ├─ onPartialResults -> show live text in a status area (reuse BufferPreviewStrip
   │     or a small "listening…" overlay)
   └─ onResults        -> commit final text via activeComposer.commit() first,
                          then currentInputConnection.commitText(finalText, 1)
```

## Implementation steps

### 3a. Manifest
- Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />`.
- On Android 11+ add a `<queries>` element for `android.speech.RecognitionService`
  so `SpeechRecognizer.isRecognitionAvailable()` / intent resolution works under
  package-visibility restrictions.
- Register the new `VoicePermissionActivity` (transparent theme, `exported=false`).

### 3b. `VoicePermissionActivity` (new, tiny)
- Transparent, no-UI Activity. In `onCreate`, if `RECORD_AUDIO` is already
  granted, `finish()` immediately; otherwise `requestPermissions(...)`. In
  `onRequestPermissionsResult`, `finish()`. It just flips the permission; the
  service re-checks on its next `onVoiceInput` / when the input view regains focus.
- Launched from the service with `FLAG_ACTIVITY_NEW_TASK` (same pattern as
  `openAppScreen`/`openFeedbackScreen`).

### 3c. `VoiceInputController` (new)
- Owns a `SpeechRecognizer` (create/destroy with the input view lifecycle;
  **all calls on the main thread** per the API contract).
- `start(languageTag)` builds a `RecognizerIntent`:
  - `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`
  - `EXTRA_LANGUAGE = languageTag`
  - `EXTRA_PARTIAL_RESULTS = true`
- `RecognitionListener`:
  - `onReadyForSpeech`/`onBeginningOfSpeech` → set a "listening" UI state.
  - `onPartialResults` → surface interim text (preview).
  - `onResults` → take best hypothesis, insert via the service.
  - `onError` → map `ERROR_NO_MATCH`, `ERROR_NETWORK`, `ERROR_INSUFFICIENT_PERMISSIONS`,
    `ERROR_RECOGNIZER_BUSY` to a short toast/inline message; always reset UI.

### 3d. Service wiring (`AddiyonKeyboardService`)
- Replace the existing `onAiAction()` stub, or add `onVoiceInput()`, as the entry
  point (there's already a toolbar with placeholder actions — see `onAiAction`/
  `onClipboardAction` and `SuggestionArea`'s `onAi`/`onClipboard` params in
  `KeyboardScreen.kt`).
- Insert final text: `activeComposer.commit()` (flush any in-flight word), then
  `currentInputConnection?.commitText(text, 1)`. Respect capitalization/spacing.
- Add observable `isListening` state (`mutableStateOf`) so the mic button can
  animate.
- Tear down the recognizer in `onFinishInputView`/`onDestroy`.

### 3e. UI
- Add a **microphone button** to the suggestion toolbar (`ui/SuggestionBar.kt` /
  `SuggestionArea`), next to the existing quick-actions. Use the existing
  `material-icons` (`Icons.Filled.Mic`) — but **see Feature 4**: if we drop
  `material-icons-extended`, draw the mic as a small vector instead.
- A "listening…" indicator: reuse `BufferPreviewStrip` styling or a small overlay
  above the keys; show interim transcript there.

### 3f. Edge cases / decisions
- **No recognizer available** (some devices/ROMs lack Google Speech Services):
  `SpeechRecognizer.isRecognitionAvailable(context)` false → hide/disable the mic
  button and toast "Voice input isn't available on this device."
- **Amharic offline**: `am-ET` typically needs network. On `ERROR_NETWORK`, tell
  the user Amharic voice needs a connection.
- **Language follows the keyboard toggle** — Amharic mode dictates `am-ET`,
  English mode `en-US`. (Optional: a long-press on the mic to pick a language.)
- **Privacy**: audio goes to Google's recognizer (online) — note this in the
  privacy policy / store listing (Feature-1 docs). Play "Data safety" form must
  disclose microphone/audio usage.

### Files
New: `VoiceInputController.kt`, `VoicePermissionActivity.kt`. Edited:
`AndroidManifest.xml`, `AddiyonKeyboardService.kt`, `ui/SuggestionBar.kt`,
(possibly) `ui/BufferPreviewStrip.kt`, string resources (`AppStrings.kt` for both
languages).

---

# Feature 4 — Fix ANR / unresponsiveness on slow phones

Symptoms reported: app UI can't be tapped ("app not responding"), and the
**keyboard won't appear** in input fields on low-end devices. There are two
distinct main-thread stalls; both need fixing.

## Suspect A — Compose cold-start (the keyboard not showing)

`AddiyonKeyboardView` is an `AbstractComposeView`; its first composition + layout
run **on the main thread** when `onCreateInputView()` returns the view and it's
attached. Compose's first-frame cost (class loading, initial composition) is
notoriously heavy on low-end devices — this is the classic "Compose IME takes
seconds to appear / ANRs." Contributing factors here:

- **`material-icons-extended` dependency** (`libs.androidx.compose.material.icons.extended`)
  — a very large artifact that inflates DEX and class-loading time. The app only
  uses a handful of icons.
- **No Baseline Profile** — with `minSdk 24` and no `profileinstaller`, cold start
  is JIT-only.
- **Debug builds** (what testers likely sideload — note the `assembleProvider`
  hook in `app/build.gradle.kts` copies the **debug** APK to `/Users/dev/Shared`)
  are un-minified and much slower than release.

### Fixes
1. **Add Baseline Profiles** — the single biggest win for Compose startup:
   - Add `androidx.profileinstaller` dependency.
   - Generate a baseline profile (a `:baselineprofile` Macrobenchmark module that
     drives app launch + opening the keyboard, or a hand-written
     `baseline-prof.txt` covering the IME + Compose hot paths).
   - Ship it so ART pre-compiles the hot paths on install.
2. **Drop `material-icons-extended`.** Replace the few icons used with
   `material-icons-core` equivalents or small hand-drawn `ImageVector`s (the repo
   already hand-draws `ui/icons/ShiftIcon.kt`). Removes a large class-loading tax.
3. **Make testers use the release/minified build.** Release already has
   `isMinifyEnabled=true` + `isShrinkResources=true`. Point closed-testing at the
   release APK/AAB, not the debug convenience build. (Confirm ProGuard keeps the
   IME service + Compose.)
4. **Optional:** show a lightweight placeholder view first frame, or warm Compose
   off the critical path — but (1)+(2) usually suffice.

## Suspect B — Dictionary loading starving the UI thread

`AddiyonKeyboardService.onCreate()` kicks off **two** `WordDictionary.loadAsync`
calls. Each (`WordDictionary.kt`):

```kotlin
Thread { val loaded = load(); mainHandler.post { trie = loaded; onReady() } }.start()
```

Problems on slow phones:
- A plain `Thread` runs at **default priority**, competing with the UI thread.
- `load()` builds a `mutableListOf<Pair>` of **all** ~182k + ~250k entries **and
  then** builds the trie — double the peak memory, and the trie is hundreds of
  thousands of `HashMap`-bearing `Node` objects. That allocation storm triggers
  **GC pauses that freeze the main thread** (GC is stop-the-world-ish for the
  allocating process), which on a low-RAM device reads as ANR / the keyboard
  failing to draw its first frame.
- Both dictionaries load eagerly even though only the **active** language is
  needed to start typing.

### Fixes
1. **Lower thread priority**: in the loader thread, call
   `Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)` so it yields to
   the UI thread. (Or move to a single-thread `Executor` with a background-priority
   `ThreadFactory`.)
2. **Build the trie while streaming** — skip the intermediate `mutableListOf`.
   Feed lines straight into `WordTrie.build` (or a streaming `insert`) so peak
   memory ≈ the trie alone, roughly halving allocation churn.
3. **Load lazily / sequentially**: load only the **current** language's dictionary
   at startup (default is Amharic), and defer the other until the user switches
   languages (or load them one-after-another on a single background thread, not
   two in parallel, to avoid two simultaneous allocation storms).
4. **Guard the recursion**: `WordTrie.computeSubtreeMax` is recursive over the
   whole trie; a long shared prefix chain could deepen the stack. Low risk, but
   worth confirming it doesn't `StackOverflow` on the real assets (it currently
   runs fine in unit tests, so likely OK — just verify on-device).

## Suspect C — MainActivity unresponsiveness

The settings app being un-tappable is most likely the **same** Compose cold-start
+ `material-icons-extended` cost (Suspect A) — MainActivity is fully Compose. The
`rememberKeyboardStatus()` reads go through `InputMethodManager` on the main
thread but are cheap and only fire on resume/focus, so they're not the cause.
Fixes A(1)+A(2) cover this too.

## How to verify the fix

1. **Reproduce on a constrained device**: create a low-RAM AVD (e.g. 1–2 GB,
   older API) or use `adb shell am` throttling; or profile a real budget phone.
2. **Capture the current ANR**: `adb shell dumpsys` / pull
   `/data/anr/traces.txt` to confirm the blocked main-thread stack (expect Compose
   inflation and/or GC).
3. Apply A + B, rebuild **release** (minified + baseline profile).
4. Measure: time-to-first-keyboard-frame (Macrobenchmark `StartupTimingMetric` or
   a manual log around `onCreateInputView` → first `onDraw`), and confirm no ANR,
   the keyboard shows promptly, and MainActivity is responsive.
5. Add `StrictMode` (debug only) to catch any accidental main-thread disk/IO.

### Files touched
`app/build.gradle.kts` + `gradle/libs.versions.toml` (profileinstaller, drop
icons-extended, maybe a `:baselineprofile` module), `suggestion/WordDictionary.kt`
(thread priority + streaming build + lazy load), `AddiyonKeyboardService.kt`
(sequence/lazy the loads), icon replacements where `material-icons-extended` was
used.

---

# Cross-cutting notes

- **Testing after each feature**: `./gradlew testDebugUnitTest` (pure-Kotlin logic
  has good coverage — transliteration, trie, case), `./gradlew assembleDebug`, and
  a manual on-device pass (enable the IME, type, try each feature). Voice + ANR
  fixes need a **real/constrained device** — they don't show up in JVM tests.
- **Permissions/privacy**: Feature 3 adds `RECORD_AUDIO`; update the Play "Data
  safety" form and privacy policy (the website plan that used to live in this file
  covered a privacy page — keep that in sync).
- **Sequencing reminder**: doing Feature 1 (rename) last means features 2–4 land
  against `Tana*` names and the rename sweeps them all at once — one clean commit,
  no rebasing symbols mid-flight.

## Sources (Feature 3 research)
- [Android `SpeechRecognizer` reference](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Google: voice typing for African languages (Amharic supported)](https://blog.google/intl/en-africa/products/connect-communicate/voice-typing-for-african-languages/)
- [Vosk models list (no Amharic)](https://alphacephei.com/vosk/models)
- [Whispering in Amharic: fine-tuning Whisper (Amharic is low-resource for Whisper)](https://arxiv.org/abs/2503.18485)
