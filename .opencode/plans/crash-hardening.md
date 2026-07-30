# Crash Hardening Plan for Tana Keyboard

## Goal

Stop the random crashes in the field by:

1. Adding a **global uncaught-exception net** so any throwable that escapes its call site is logged and either swallowed or re-raised into a stable state instead of taking the IME process down.
2. Wrapping **every block of code that can plausibly throw** with a localized `try/catch` so the failure surfaces in `Logcat` as a single tagged line instead of a force-close.
3. Adding a **service-level safety net** around the public methods the UI calls into, so even a missed exception from a deep subsystem (transliterator, composer, voice controller, dictionary, emoji) degrades to a no-op rather than killing the foreground IME.
4. Adding a **defensive `Application` initialization** (`onCreate`) that installs the uncaught-exception handler before anything else, and wraps each `Activity.onCreate` and the `AddiyonKeyboardService.onCreate` in a guard.

Nothing in this plan changes the keyboard's user behaviour. Every wrapped call degrades to a no-op (drop the key, ignore the callback, reset state, etc.) and the user can keep typing.

## Files to touch

Order of work:

- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt` (main blast radius: 1938 LOC, every keypath)
- `app/src/main/java/com/addiyon/keyboard/composing/WordComposer.kt` (InputConnection calls)
- `app/src/main/java/com/addiyon/keyboard/voice/VoiceInputController.kt` (SpeechRecognizer + Handler callbacks)
- `app/src/main/java/com/addiyon/keyboard/emoji/EmojiRepository.kt` (background asset load)
- `app/src/main/java/com/addiyon/keyboard/suggestion/WordDictionary.kt` (background asset load)
- `app/src/main/java/com/addiyon/keyboard/suggestion/NgramDictionary.kt` (background asset load)
- `app/src/main/java/com/addiyon/keyboard/MainActivity.kt` (Play Review, Compose content)
- `app/src/main/java/com/addiyon/keyboard/ThemesActivity.kt` (Compose)
- `app/src/main/java/com/addiyon/keyboard/ManualActivity.kt` (Compose)
- `app/src/main/java/com/addiyon/keyboard/FeedbackActivity.kt` (Compose)
- `app/src/main/java/com/addiyon/keyboard/VoicePermissionActivity.kt` (activity result)
- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardView.kt` (Compose entry)
- `app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt` (Compose: heavy layout math)
- `app/src/main/java/com/addiyon/keyboard/ui/emoji/EmojiPanel.kt` (uses `EmojiData`, lazy grid)
- `app/src/main/java/com/addiyon/keyboard/ui/feedback/FeedbackContent.kt` (intents)
- `app/src/main/java/com/addiyon/keyboard/ui/onboarding/OnboardingScreen.kt` (intents)
- `app/src/main/java/com/addiyon/keyboard/ui/settings/SettingsScreen.kt` (intents, Play store)
- `app/src/main/java/com/addiyon/keyboard/ui/home/HomeScreen.kt` (intents)
- `app/src/main/java/com/addiyon/keyboard/ui/i18n/Localization.kt` (locale lookup)
- `app/src/main/AndroidManifest.xml` (register the new `Application` subclass)

New files to add:

- `app/src/main/java/com/addiyon/keyboard/AddiyonApp.kt` — `Application` subclass that installs the uncaught handler.
- `app/src/main/java/com/addiyon/keyboard/SafeLogging.kt` — single-tag `Log.e` wrapper.
- `app/src/main/java/com/addiyon/keyboard/SafeOps.kt` — `safeRun`, `safeApply`, `safeOnMain` helpers.

## Architecture

### Crash surface (where things can throw)

1. **`InputConnection` calls** — `commitText`, `setComposingText`, `setComposingRegion`, `finishComposingText`, `deleteSurroundingText`, `getTextBeforeCursor`, `getTextAfterCursor`, `getSelectedText`, `getExtractedText`, `beginBatchEdit`/`endBatchEdit`, `performEditorAction`, `sendKeyEvent`. Can throw `RemoteException`, `SecurityException`, or `IllegalStateException` when the editor process is gone, locked, or transitioning sessions. Every InputConnection touch goes through `safeIc { ... }`.

2. **Voice / `SpeechRecognizer`** — `createSpeechRecognizer`, `startListening`, `cancel`, `destroy` can throw `RuntimeException` on bad states. Handler-delivered callbacks run on the main looper; an uncaught throw there crashes the service. Wrap every recognizer callback.

3. **Background asset / dictionary loaders** — `EmojiRepository.load()`, `WordDictionary.load()`, `NgramDictionary.load()` open an `AssetManager` stream, `GZIPInputStream` decode it, and call a parser. Any of those can throw `IOException`, `OutOfMemoryError`, or a parser exception. The worker thread is uncaught; a throw there (especially `OutOfMemoryError`) kills the service. Wrap the load body and the `mainHandler.post { ... }` callback.

4. **Intent / activity launch** — `startActivity(...)` can throw `ActivityNotFoundException`, `SecurityException` on Android 11+ package visibility, `TransactionTooLargeException`. Already partially wrapped (`runCatching`); make it consistent.

5. **Play Review** — `ReviewManagerFactory.create(this)` can throw when Play Services is missing/old. The `requestReviewFlow().addOnSuccessListener` chain is async and can throw a `RuntimeException` from the main thread executor. Wrap tightly.

6. **Compose content** — composables can throw `IllegalStateException` from bad state (e.g. a `LazyList` state that has been disposed, a `mutableStateOf` read after disposal). Wrap the activity-level `setContent { ... }` in a crash-safe wrapper that catches and renders a fallback.

7. **Window / `decorView`** — `window?.window?.decorView` is null-safe, but `WindowInsetsControllerCompat` can throw on weird OEMs. The `updateSystemNavigationBar` path is a common crash.

8. **Dictionary / suggestion lookups** — `amharicSuggestions`, `englishSuggestions`, `nextWordPredictions`, `topAmharicCandidate` walk tries and indexes. State-holding maps (`composingNgramBoost`, `amharicSuggestionCache`) can throw `ConcurrentModificationException` if mutated from two threads.

### The toolkit

```kotlin
// SafeLogging.kt
internal object SafeLog {
    private const val TAG = "AddiyonKb"
    fun e(t: Throwable, msg: String) {
        android.util.Log.e(TAG, msg, t)
    }
    fun w(msg: String) {
        android.util.Log.w(TAG, msg)
    }
}
```

```kotlin
// SafeOps.kt
internal inline fun <T> safeRun(default: T, block: () -> T): T = try {
    block()
} catch (oom: OutOfMemoryError) {
    SafeLog.e(oom, "safeRun OOM")
    default
} catch (t: Throwable) {
    SafeLog.e(t, "safeRun")
    default
}

internal inline fun safeApply(block: () -> Unit) {
    try {
        block()
    } catch (oom: OutOfMemoryError) {
        SafeLog.e(oom, "safeApply OOM")
    } catch (t: Throwable) {
        SafeLog.e(t, "safeApply")
    }
}

internal inline fun safeOnMain(handler: android.os.Handler, block: () -> Unit) {
    if (!handler.post { safeApply(block) }) {
        safeApply(block)
    }
}
```

```kotlin
// AddiyonApp.kt
class AddiyonApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("AddiyonKb", "Uncaught on ${thread.name}", throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
```

`AndroidManifest.xml` change: `<application android:name=".AddiyonApp" ...>`.

```kotlin
// InputConnection helper (in SafeOps.kt)
internal inline fun AddiyonKeyboardService.safeIc(block: (android.view.inputmethod.InputConnection) -> Unit) {
    try {
        val ic = currentInputConnection ?: return
        block(ic)
    } catch (oom: OutOfMemoryError) {
        SafeLog.e(oom, "InputConnection OOM")
    } catch (t: Throwable) {
        SafeLog.e(t, "InputConnection")
    }
}
```

### The pattern: every potentially-throwing call → `safeApply { ... }` / `safeIc { ... }`

| Surface                                            | Wrap with        | Default behaviour on catch                                |
|----------------------------------------------------|------------------|-----------------------------------------------------------|
| `InputConnection` * 13 methods                     | `safeIc { ... }` | no-op; field stays as-is                                  |
| `SpeechRecognizer` lifecycle                       | wrapped `safeApply { ... }` | releases recognizer, sets state to `Unavailable` |
| Recognizer listener callbacks (7 of them)          | `safeApply { ... }` | callback dropped; generation guard still works         |
| `Handler.postDelayed` runnables                    | `safeApply { ... }` | timer dropped; watchdog self-cancels                    |
| `AssetManager.open` / `GZIPInputStream` / `parse`  | `safeApply { ... }` | data stays null; UI shows loading state forever         |
| `startActivity` (every variant)                    | `safeApply { ... }` | already wrapped; just standardize                        |
| `MainActivity.maybeRequestReview()`                | `safeApply { ... }` | one-liner log; not critical                              |
| `setContent { ... }` (every Activity)              | activity-level `try/catch` around `super.onCreate` + `setContent` | fallback to plain `Text`                |
| `WordComposer` InputConnection calls (7)           | `safeIc { ... }` | already null-safe via `?.`; replace with throw-safe      |
| `AddiyonKeyboardService.onCreate` / lifecycle      | `safeApply { ... }` per public method | resets that subsystem; the keyboard keeps working |
| `Recomposer` / `LazyGrid`                          | rely on activity-level fallback | Compose tree is unreachable-crash-proof            |

## Step-by-Step Plan

### Phase 1 — Wire up the Application-level safety net

Files: `AddiyonApp.kt` (new), `SafeLogging.kt` (new), `SafeOps.kt` (new), `AndroidManifest.xml`.

Steps:

1. Create `app/src/main/java/com/addiyon/keyboard/AddiyonApp.kt` with `installCrashHandler`.
2. Create `SafeLogging.kt` and `SafeOps.kt` (including `safeIc`).
3. Edit `AndroidManifest.xml`: add `android:name=".AddiyonApp"` to `<application>`.
4. Verification: `./gradlew compileDebugKotlin`.

Success criteria: app launches; a developer probe that throws in a thread logs the tag in `Logcat` instead of crashing silently.

### Phase 2 — Service-level try/catch for every public method

Files: `AddiyonKeyboardService.kt`.

Every public method the UI calls into gets a top-level `try/catch (Throwable)` that logs via `SafeLog.e(...)` with the method name and either resets that subsystem to a safe default or returns.

Exhaustive list of public methods (`KeyRow.kt`, `KeyboardScreen.kt`, `EmojiPanel.kt`, and internal voice flow all call into these):

- `onCharacter(latin)` — wrap. On catch: drop the key, clear shift.
- `onDelete()` — wrap. On catch: clear active composer's buffer.
- `onSpace()` — wrap. On catch: clear active composer's buffer.
- `onEnter()` — wrap. On catch: clear active composer's buffer.
- `onSuggestionTapped(word)` — wrap. On catch: clear active composer.
- `toggleShift()`, `consumeShiftAfterCharacter()`, `resetShift()` — wrap each. On catch: `shiftState = OFF`.
- `toggleLanguage()` — wrap. On catch: leave `isAmharic` alone, reset both composers.
- `toggleNumberMode()`, `toggleSymbolsPage()`, `openKeypad()` — wrap each. On catch: `numbersMode = OFF`.
- `openEmojiPanel()`, `closeEmojiPanel()`, `openEmojiSearch()`, `closeEmojiSearch()`, `clearEmojiSearchQuery()`, `updateEmojiSearchField(value)` — wrap each. On catch: force `showEmojiPanel = false`, `emojiSearchField = null`.
- `commitEmoji(emoji)` — wrap. On catch: do nothing.
- `setSkinTone(base, variant)` — wrap. On catch: do nothing.
- `exitVoiceMode()` — wrap. On catch: force `voiceUiState = Idle`, reset voice composer.
- `onVoiceInput()` — wrap. On catch: force `voiceUiState = Unavailable("Voice input error.")`.
- `openAppScreen(screen)` — already has `runCatching` on inner call; lift outer `startActivity` into the same `try/catch`.
- `openFeedbackScreen()` — convert `runCatching` to `safeApply { ... }` for consistency.
- `onDeleteGestureStart()`, `onDeleteGestureEnd()` — wrap each. On catch: reset the gate.
- `commitText(text)` — wrap. On catch: clear the active composer.
- `recentEmojiSnapshot()` — wrap. On catch: return `emptyList()`.

Pattern: rename each method to a private `onCharacterImpl(...)` and wrap a public `onCharacter` around it:

```kotlin
fun onCharacter(latin: String) {
    try {
        onCharacterImpl(latin)
    } catch (oom: OutOfMemoryError) {
        SafeLog.e(oom, "onCharacter OOM")
    } catch (t: Throwable) {
        SafeLog.e(t, "onCharacter")
    }
}

private fun onCharacterImpl(latin: String) { /* existing body verbatim */ }
```

Same pattern for every entry above.

Success criteria:

- Every entry on the list has the wrapper.
- `./gradlew compileDebugKotlin` is clean.
- Unit tests still pass.

### Phase 3 — `InputConnection` hardening

Files: `AddiyonKeyboardService.kt`, `composing/WordComposer.kt`.

The current code uses `currentInputConnection?.method(...)` which is null-safe but **not throw-safe**. Replace every `currentInputConnection?.X(...)` (and the `it.X(...)` inside the relevant `let` blocks) with `safeIc { ... }`.

Explicit replacements in `AddiyonKeyboardService.kt`:

- `onVoicePartialResult` — `getTextBeforeCursor`, `setComposingText` → wrapped.
- `onVoiceFinalResult` — `getTextBeforeCursor`, `beginBatchEdit`, `setComposingText`, `deleteSurroundingText`, `commitText`, `endBatchEdit` → wrapped.
- `finalizeVoiceComposing` — `finishComposingText` → wrapped.
- `commitEmoji` — `commitText` → wrapped.
- `onCharacter` — `commitText` (two call sites) → wrapped.
- `onDelete` — `getSelectedText`, `commitText`, `getTextBeforeCursor`, `deleteSurroundingText` → wrapped.
- `commitText(...)` (public) — `commitText` → wrapped.
- `onSpace` — `getTextBeforeCursor`, `commitText(" ", 1)` → wrapped.
- `onEnter` — `getTextBeforeCursor`, `sendKeyEvent` (DOWN + UP), `performEditorAction` → wrapped.
- `maybeResumeWordAtCursor` — `getTextAfterCursor`, `getTextBeforeCursor`, `setComposingRegion` → wrapped.
- `maybeResumeWordAfterDeleteGesture` — `getExtractedText` → wrapped.
- `maybeAutoCapitalize` — `getTextBeforeCursor` → wrapped.
- `nextWordPredictions` — `getTextBeforeCursor` → wrapped.

In `WordComposer.kt`:

- `onBackspace` — `setComposingText("")`, `finishComposingText()` → wrapped.
- `commit` — `commitText` → wrapped.
- `finish` — `setComposingText("")`, `finishComposingText()` (two sites), `commitText` → wrapped.
- `commitSuggestion` — `commitText("$word ", 1)` → wrapped.
- `abandon` — `setComposingText("")`, `finishComposingText()` (two sites) → wrapped.
- `pushComposing` — `setComposingText(raw, 1)` → wrapped.

`pushComposing` is the hot path (called on every keystroke). The `safeIc` wrapper is a single try/catch with no `Log` call on the success path; only the catch path allocates the stack trace. Safe for the keystroke hot path.

Success criteria:

- Every `currentInputConnection?.X(...)` and `inputConnection()?.X(...)` site is reached via `safeIc { ... }`.
- `grep` no longer matches `currentInputConnection?.commitText` or `inputConnection()?.commitText` outside the helper file.

### Phase 4 — Voice recognizer hardening

Files: `voice/VoiceInputController.kt`.

Two distinct crash surfaces:

1. **Lifecycle methods** — `createSpeechRecognizer`, `startListening`, `cancel`, `destroy` can throw `RuntimeException` on bad states.
2. **Listener callbacks** — `onReadyForSpeech`, `onBeginningOfSpeech`, `onRmsChanged`, `onBufferReceived`, `onEndOfSpeech`, `onError`, `onResults`, `onPartialResults`, `onEvent` are invoked on the main thread; an uncaught throwable crashes the service.

Wrapping:

- `startSession(...)` — wrap. On catch: `onFatalError(UNKNOWN)`, return.
- `releaseRecognizer(cancel)` — wrap. On catch: null out `recognizer` regardless.
- `start(languageTag)` — wrap. On catch: `onFatalError(UNKNOWN)`.
- `stop()` — wrap. On catch: force `generation++`, cancel timers, null out recognizer.
- `restartSession()` — wrap. On catch: `onFatalError(UNKNOWN)`.
- `recognizerIntent(...)` — wrap. On catch: return bare `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)`.
- `createListener` — every `override fun onX(...)` wraps its body in `safeApply { ... }`. The `isCurrent()` guard stays.
- The 5 `Handler.postDelayed` `Runnable`s (watchdog, speech-end, restart, two timers) — wrap their bodies in `safeApply { ... }`.
- `flushLastPartial`, `onFinal`, `onPartial` callbacks — already covered by listener-level wrapping; explicitly `safeApply` the body of each for nested safety.

In `AddiyonKeyboardService.kt`:

- `onVoiceFatalError(...)` — wrap. On catch: force `voiceUiState = VoiceUiState.Idle`.
- `voiceController()` — wrap the `.also { voiceInputController = it }` block.

Success criteria:

- Creating a `SpeechRecognizer` after `onDestroy` is handled (no force-close).
- All `Handler` callbacks are safe.
- Force-stopping the recognizer from outside the app does not crash.

### Phase 5 — Background asset / dictionary loader hardening

Files: `emoji/EmojiRepository.kt`, `suggestion/WordDictionary.kt`, `suggestion/NgramDictionary.kt`.

These three classes share the pattern `Thread { Process.setThreadPriority(...); val loaded = load(); mainHandler.post { data = loaded; onReady() } }.start()`. The thread is uncaught — a `Throwable` or `OutOfMemoryError` in `load()` or in the `mainHandler.post` callback can crash the service.

Wrapping:

- `load()` — wrap with `safeApply { ... }` inside `try { ... } catch (Throwable)`. On catch: return `null` (or empty `WordTrie` / `NgramModel` for the dictionary loaders).
- The `Thread { ... }` body — wrap in `try { ... } catch (Throwable) { SafeLog.e(it, "loader thread") }`.
- The `mainHandler.post { ... }` callback — wrap in `safeApply { ... }`.

For `EmojiRepository.load()` — on catch: leave `data` as `null` (UI already handles null with "Loading emoji…").

For `WordDictionary.load()` — on catch: return an empty `WordTrie` (`WordTrie.build(emptyList(), keyChar)`).

For `NgramDictionary.load()` — on catch: return an empty `NgramModel` (`NgramModel(emptyMap(), emptyMap(), emptyList())`).

The `loadStarted` flag is set TRUE before the thread starts. If the catch path leaves `data` null, the thread won't restart (the `if (loadStarted) return` at the top of `loadAsync`). The keyboard still works, just without predictions.

Success criteria:

- Permanently corrupting one of the bundled `.dat` files (e.g. truncating it) does not crash the keyboard.
- `OutOfMemoryError` in the worker thread is logged and the keyboard stays alive.

### Phase 6 — Activity-level Compose safety net

Files: `MainActivity.kt`, `ThemesActivity.kt`, `ManualActivity.kt`, `FeedbackActivity.kt`, `VoicePermissionActivity.kt`.

Each `Activity.onCreate` currently does:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setTheme(R.style.Theme_Foo)
    enableEdgeToEdge()
    setContent { /* Compose tree */ }
}
```

Wrap each one at the activity level:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    try {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Foo)
        enableEdgeToEdge()
        setContent { /* Compose tree */ }
    } catch (oom: OutOfMemoryError) {
        SafeLog.e(oom, "Activity onCreate OOM")
        renderFallback()
    } catch (t: Throwable) {
        SafeLog.e(t, "Activity onCreate")
        renderFallback()
    }
}

private fun renderFallback() {
    try {
        setContent { Text("Addiyon Keyboard encountered a problem. Please reopen the app.") }
    } catch (_: Throwable) {
        finish()
    }
}
```

The fallback is a plain `Text` (no theme, no Material, no service lookups).

For `MainActivity`, `maybeRequestReview()` is wrapped in `safeApply { ... }` since `ReviewManagerFactory.create(this)` can throw on devices without Play Services.

Success criteria:

- Throwing inside the Compose tree yields the fallback `Text`, not a force-close.
- `VoicePermissionActivity` still finishes; `requestPermission.launch(...)` is covered by the same wrapping.

### Phase 7 — Suggestion / dictionary code path hardening

Files: `AddiyonKeyboardService.kt` (the lookup helpers).

`updateSuggestions`, `topAmharicCandidate`, `amharicSuggestions`, `englishSuggestions`, `nextWordPredictions`, `ngramPredictions`, `pinPreferredAlternate` are called from the keystroke hot path. Most are pure Kotlin, but they touch `LinkedHashMap` objects that could throw `ConcurrentModificationException` if a background thread sneaks in, and call `currentInputConnection?.getTextBeforeCursor(...)` (covered by Phase 3).

Wrap each:

- `updateSuggestions` — wrap. On catch: `publishSuggestions(emptyList(), arePredictions = false)`.
- `topAmharicCandidate` — wrap. On catch: return `Transliterator.transliterate(raw)`.
- `amharicSuggestions` — wrap. On catch: return empty list.
- `englishSuggestions` — wrap. On catch: return empty list.
- `nextWordPredictions` — wrap. On catch: return `emptyList()`.
- `pinPreferredAlternate` — wrap. On catch: return `ranked`.

Success criteria:

- A malformed transliteration input never throws to the keystroke handler.
- Suggestions are simply empty when anything goes wrong.

### Phase 8 — UI composable safety net

Files: `AddiyonKeyboardView.kt`, `ui/KeyboardScreen.kt`, `ui/emoji/EmojiPanel.kt`, `ui/feedback/FeedbackContent.kt`, `ui/onboarding/OnboardingScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/home/HomeScreen.kt`, `ui/i18n/Localization.kt`.

Compose does not propagate exceptions well through the recomposer — a thrown exception in a recomposition can crash the entire process. Strategy:

1. **Activity-level wrapping** (Phase 6) catches any throwable that escapes the Compose tree.
2. **`EmojiGrid` and `LazyGrid` callers** — wrap the `EmojiPanel(... height ...)` call site in `KeyboardScreen.kt` with `safeApply { ... }` so a `LazyGridState` ordered after disposal falls through to the suggestion strip.
3. **`EmojiPanel` body** — wrap the `@Composable` body in `safeApply { ... }` so the fallback is "Loading emoji…" forever.
4. **`KeyboardScreen` body** — wrap. On catch: render a `Text("Keyboard")` placeholder (a single bottom-anchored text strip) so the user can still dismiss the keyboard.
5. **`AddiyonKeyboardView.Content`** — wrap. On catch: render a single `Text` view directly via `setContent { Text("…") }`.

For `FeedbackContent.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`, `HomeScreen.kt`:

- `openFeedbackTelegram`, `sendFeedbackEmail`, `Intent(...)` startActivity calls are already wrapped in `runCatching { ... }`. Convert to `safeApply { ... }` for consistency.
- `Localization.kt` — wrap the `stringsOf(locale)` function in `safeApply { ... }` and return a fallback `AppStrings` constant on failure.

Success criteria:

- A misbehaving `LazyGrid` doesn't crash the keyboard service.
- The Compose tree is unreachable-crash-proof.

### Phase 9 — Window / system bar hardening

Files: `AddiyonKeyboardService.kt`.

`updateSystemNavigationBar()` is called from `onCreateInputView` and from `refreshTheme()`. The path:

```kotlin
window?.window?.let { imeWindow ->
    imeWindow.navigationBarColor = color.toArgb()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        imeWindow.isNavigationBarContrastEnforced = false
    }
    WindowInsetsControllerCompat(imeWindow, imeWindow.decorView)
        .isAppearanceLightNavigationBars = !isDarkTheme
}
```

is null-safe with `?.let`, but `Color.toArgb()` can throw on weird OEM themes, and `WindowInsetsControllerCompat(...)` can throw on devices with broken insets controllers. Wrap the entire method body in `safeApply { ... }`.

`refreshTheme` — wrap similarly. `KeyboardPrefs.palette(this)` calls `KeyboardPalette.fromId(...)` which could throw on a corrupt preferences file.

`onConfigurationChanged` — wrap. On catch: do nothing.

`onEvaluateInputViewShown`, `onEvaluateFullscreenMode` — wrap. On catch: return `super.onEvaluateInputViewShown()`.

`onCreateInputView` — wrap. On catch: return a minimal `View(this)` (raw Android view, not Compose) so a broken Compose tree doesn't crash the IME.

`ensureLifecycleStarted`, `ensureLifecycleResumed`, `pauseLifecycleIfResumed` — wrap. On catch: do nothing.

Success criteria:

- A broken `WindowInsetsControllerCompat` doesn't crash the IME.
- The keyboard is always usable even with a broken theme.

### Phase 10 — `onCreate` / `onStartInputView` / `onFinishInputView` / `onUpdateSelection` / `onDestroy` hardening

Files: `AddiyonKeyboardService.kt`.

These are framework callbacks — they can be called from any state and any thread. Wrap each one in `safeApply { ... }`. On catch:

- `onCreate` — keep the service alive but skip the failed step.
- `onStartInputView` — log, leave state alone.
- `onFinishInputView` — log, leave state alone.
- `onUpdateSelection` — log, leave state alone.
- `onDestroy` — log, do nothing.

Success criteria:

- A thrown exception in `onUpdateSelection` (a common IME crash site) does not crash the service.
- The service lifecycle is robust to any single bad callback.

### Phase 11 — `proc.setThreadPriority` and worker threads

Files: all three loaders + `VoiceInputController`.

The current code calls `Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)` on every worker thread. This call itself can throw on weird devices. Wrap each call in `safeApply { ... }`. On catch: skip the priority change.

For `VoiceInputController.scheduleRestart`, `scheduleStartWatchdog`, `scheduleSpeechEndFallback`, the `Handler.postDelayed` already cancels individual tokens; the wrapping in Phase 4 covers the runnable bodies.

### Phase 12 — Tests

The project has JVM unit tests under `app/src/test/java/com/addiyon/keyboard/`. Add tests for:

1. `SafeOpsTest.kt` — `safeRun` returns default on throw, executes block on success. Simulated `OutOfMemoryError` is also handled.
2. `WordComposerCrashTest.kt` — replaces the `inputConnection` lambda with one that throws `RemoteException` / `IllegalStateException`, asserts that `commit`, `finish`, `abandon`, `onBackspace`, `commitSuggestion` do not propagate the throw.
3. `VoiceInputControllerCrashTest.kt` — replaces the `RecognitionListener` with one that throws on every callback, asserts the controller's `onFatalError` is still called once and the controller reaches `Idle` state.
4. `WordDictionaryCrashTest.kt` — feeds a `ByteArrayInputStream` that throws `IOException` in `read(...)`, asserts that `load()` returns an empty `WordTrie`.
5. `EmojiRepositoryCrashTest.kt` — same shape.

Manual emulator tests:

1. Type a long string in the Amharic letter layout; force-stop the foreground app mid-string; confirm the keyboard reopens cleanly.
2. Tap the mic button, deny the permission; confirm no crash.
3. Tap the mic button, grant the permission, speak; confirm the user's text is in the field.
4. Open the emoji panel, leave the keyboard, open a different app, return; confirm the panel still works.
5. Tap each toolbar icon (settings, themes, guide, feedback); confirm each opens the right Activity.
6. Send the app to the background, foreground, and back; confirm the keyboard state is preserved.
7. While in a numeric field, tap the language toggle; confirm the keyboard switches.
8. Type a long word, tap a suggestion chip; confirm the word is replaced.
9. Type a long word, hold backspace; confirm the word is deleted.
10. Open the IME in a password field; confirm caps lock is off.

### Phase 13 — Verification

After each phase:

- `./gradlew compileDebugKotlin` — fast read-only check.
- `./gradlew testDebugUnitTest` — run the relevant unit tests.

After all phases:

- `./gradlew testDebugUnitTest` — full unit test suite.
- `./gradlew installDebug` — install on emulator.
- Manual smoke test on the emulator (the user's scenarios above).
- `./gradlew assembleDebug` — final APK in `/Users/dev/Shared`.

## Risks

- **Behavioural changes** — every wrapped call should be a no-op on catch, but a few (e.g. `commit` puts the word into the field) are not no-ops. The `safeIc` wrap only catches `Throwable` from the `InputConnection` API itself; the rest of the method body still runs. The keystroke path is wrapped in `safeApply` only at the outer level, so if `InputConnection.commitText` throws, the rest of `onCharacter` (shift reset, suggestions update) still runs.
- **Performance** — adding a try/catch to the hot path is normally free in the success case (JIT-compiled). `setComposingText` is called once per keystroke; the wrap is one extra stack frame per call. The wrapper is a single `try` block with no allocations on success.
- **Hidden bugs** — wrapping that swallows too much is a smell. The `SafeLog.e` line is the diagnostic, so every wrap is searchable in `Logcat`. The convention is "log + degrade to a no-op" rather than "log + crash later".
- **Threading** — `safeIc` is main-thread only (the wrapped block reads `currentInputConnection`). No thread-safety risk.
- **Play Review** — `MainActivity` already has a `ReviewManagerFactory.create(this)` call; the new wrap is tighter but doesn't change behaviour on success.

## Open Questions

1. Should the `Application`-class crash handler re-throw the throwable (current plan does — `previous?.uncaughtException`) so the OS still sees it? Recommended: yes — the handler is a diagnostic, not a saver.
2. Should the activity-level fallback `Text` view be styled to match the app theme? Recommended: no — keeping it plain avoids the same theme lookup that might have crashed.
3. Should we add a `BuildConfig.DEBUG` guard around the `SafeLog.e` calls? Recommended: no — `Log.e` is cheap and the production user can never see it.
4. Should `WordComposer.onCharacter` (the keystroke hot path) be wrapped in `safeApply` or just `safeIc`? Recommended: `safeIc` only — the rest of the method (buffer append, raw cache reset) is pure Kotlin and known safe.
5. Should the inner `setComposingText("")` calls in `WordComposer.finish` / `WordComposer.abandon` / `WordComposer.onBackspace` be wrapped in `safeIc` or `safeApply`? Recommended: `safeIc` — they're the only ones that touch the `InputConnection`.

## References

- Android `InputConnection` — can throw; the documented contract says "the connection may be invalidated at any time." Source: AOSP `InputConnection` interface.
- Android `SpeechRecognizer` — `createSpeechRecognizer` can throw `RuntimeException` on devices without a recognizer service installed. Source: AOSP `SpeechRecognizer` class.
- Android `AssetManager.open` — can throw `IOException` for missing/corrupt assets. Source: AOSP `AssetManager`.
- Android `Handler.postDelayed` — runnables run on the main thread; an uncaught throwable crashes the process. Source: AOSP `Handler`.
- Android `WindowInsetsControllerCompat` — wrap-state-dependent APIs can throw on some OEM builds. Source: AndroidX `core` library.
- `AGENTS.md` formatting rules: `kotlin.code.style=official`, no comments in code.
