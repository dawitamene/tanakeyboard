# Voice Input Reliability Improvement Plan

## Goal

Significantly improve Addiyon Keyboard voice input so it feels stable, fast, and predictable:

- Dictated text must not disappear or be overwritten when the user speaks the next phrase.
- The mic should not frequently surface generic "Voice input error" messages.
- The recognizer should not get stuck in a non-listening state that requires closing/reopening the keyboard.
- Voice mode UI should match the requested layout: the mic icon remains in the toolbar, the other toolbar icons disappear while listening, voice status text appears in the suggestion area, and the settings position becomes a back/exit button that leaves voice mode and restores the toolbar.
- The implementation should be testable, observable, and structured enough to support newer Android speech APIs where available.

This is a plan only. No product code should be changed until this plan is approved.

## Current Implementation Snapshot

Relevant files:

- `app/src/main/java/com/addiyon/keyboard/voice/VoiceInputController.kt`
- `app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt`
- `app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt`
- `app/src/main/java/com/addiyon/keyboard/VoicePermissionActivity.kt`
- `app/src/main/AndroidManifest.xml`

Current flow:

1. `SuggestionArea` shows a toolbar when suggestions are empty, including the mic icon.
2. Tapping mic calls `AddiyonKeyboardService.onVoiceInput()`.
3. The service checks `RECORD_AUDIO`; if missing, it launches `VoicePermissionActivity`.
4. If granted, the active word composer is committed, then `VoiceInputController.start("am-ET" or "en-US")` starts `SpeechRecognizer`.
5. Partial results call `InputConnection.setComposingText(...)`.
6. Final results call `InputConnection.commitText(...)`.
7. The controller restarts after final results and after some transient errors.

## Likely Root Causes

### 1. New speech removes previous speech

The current partial-result strategy writes every interim hypothesis into the target app's composing region with `setComposingText`. That is good for a single utterance, but risky for continuous dictation because Android composing regions are replaceable by design. When sessions restart or callbacks arrive late, a new partial can replace an old still-composing span instead of appending after committed text.

The controller already uses a generation guard, but the service still has no explicit "voice session text buffer" separate from the target field. The target field is being used as both preview and committed output.

Plan direction: make voice dictation transactional. Partial text should be preview-only in keyboard UI. Only final or segment results should be committed to the target app, and committed ranges should never be reopened as composing text.

### 2. Frequent voice input errors

Current error handling treats `NO_MATCH`, `SPEECH_TIMEOUT`, `RECOGNIZER_BUSY`, and `CLIENT` as restartable, but several other common errors still become a generic toast. Also, repeated restart attempts can cause `ERROR_TOO_MANY_REQUESTS`, `ERROR_SERVER_DISCONNECTED`, or additional busy/client errors if the recognizer service has not fully released.

Plan direction: replace the simple restart loop with a state machine, debounced restart policy, backoff, and error classification. Generic errors should be reserved for truly unknown cases.

### 3. Sometimes does not listen until keyboard is closed/reopened

Possible reasons:

- `isListening` only reflects callbacks from the recognizer. A start request can be pending while no callback arrives.
- `onReadyForSpeech` may not arrive after a failed restart, leaving UI and controller state out of sync.
- `SpeechRecognizer.startListening` can be rejected if called before the previous `onResults` or `onError` has fully completed.
- `cancel()` plus `destroy()` plus immediate recreation may leave the platform recognizer service in a busy state on some devices.
- Permission return flow requires the user to tap mic again; that can read as "not listening" after permission is granted.

Plan direction: model explicit states like `Idle`, `Starting`, `SpeakNow`, `Listening`, `Finalizing`, `Restarting`, `Recovering`, `Unavailable`, and drive UI from those states rather than a boolean plus a small enum.

### 4. UI does not match desired voice mode

Current `SuggestionArea` replaces the row with only text plus a bubble mic when `voiceState != IDLE`. There is no back/exit button in the settings position, and the mic's toolbar position is not preserved as a stable toolbar affordance.

Plan direction: split the row into stable zones:

- Left toolbar slot: settings normally, back/exit in voice mode.
- Center suggestion/status area: suggestions normally, "Speak now" / "Listening" / transient errors in voice mode.
- Right toolbar slot: mic icon always present; animated while active.
- Other toolbar icons hidden only during voice mode.

## Recommended Main Approach

Keep Android `SpeechRecognizer` as the default engine, but rewrite the voice feature around a stronger controller/service contract:

- Controller owns platform recognizer lifecycle and emits structured events.
- Service owns text insertion policy and spacing.
- UI owns voice-mode presentation and never writes to `InputConnection`.
- Partial hypotheses stay in keyboard UI, not in the target app's composing region.
- Final results, API 33 segment results, or flushed last stable partials commit with `commitText`.

This keeps the app free of paid backends and preserves Amharic support through the Android/Google recognizer, while removing the main replacement race.

## Proposed Architecture

```text
SuggestionArea
  -> service.enterVoiceMode() / service.exitVoiceMode()
  -> service.toggleVoiceListening()

AddiyonKeyboardService
  owns VoiceUiState
  owns VoiceTextCommitter
  owns spacing/capitalization decisions
  never lets voice partial text touch target composing region

VoiceInputController
  owns SpeechRecognizer
  exposes events:
    Starting
    ReadyForSpeech
    BeginningOfSpeech
    Partial(text)
    Segment(text)       API 33+ when available
    Final(text)
    RecoverableError(kind)
    FatalError(kind)
    Ended

VoiceSessionStateMachine
  prevents illegal starts
  backoff/retries recoverable errors
  times out stuck starts
  destroys/recreates recognizer only at safe boundaries
```

## Detailed Implementation Plan

### Phase 1 - Add Diagnostics Before Changing Behavior

Affected files:

- `VoiceInputController.kt`
- `AddiyonKeyboardService.kt`

Steps:

1. Add debug-only structured logging around every recognizer event: session id, state, language, elapsed time, error code, restart count, and whether a partial/final was committed.
2. Log `onStartInputView`, `onFinishInputView`, `onDestroy`, permission grant path, and voice button taps.
3. Add a small internal `VoiceErrorKind` mapping for every Android `SpeechRecognizer` error code, including:
   - `ERROR_AUDIO`
   - `ERROR_CLIENT`
   - `ERROR_INSUFFICIENT_PERMISSIONS`
   - `ERROR_LANGUAGE_NOT_SUPPORTED`
   - `ERROR_LANGUAGE_UNAVAILABLE`
   - `ERROR_NETWORK`
   - `ERROR_NETWORK_TIMEOUT`
   - `ERROR_NO_MATCH`
   - `ERROR_RECOGNIZER_BUSY`
   - `ERROR_SERVER`
   - `ERROR_SERVER_DISCONNECTED`
   - `ERROR_SPEECH_TIMEOUT`
   - `ERROR_TOO_MANY_REQUESTS`
   - API 33/34 support and download errors where available
4. Keep this logging behind `ApplicationInfo.FLAG_DEBUGGABLE` so release builds stay quiet.

Success criteria:

- Logcat can explain whether a failure was no speech, network, unsupported language, busy service, too many requests, or lifecycle cancellation.
- No more "Voice input error" without a specific underlying code in debug logs.

### Phase 2 - Replace Booleans With a Voice State Machine

Affected files:

- `VoiceInputController.kt`
- `AddiyonKeyboardService.kt`
- possibly new `voice/VoiceModels.kt`

Steps:

1. Replace `VoiceInputState { IDLE, SPEAK_NOW, LISTENING }` with a richer sealed model:
   - `Idle`
   - `Starting`
   - `SpeakNow`
   - `Listening(level: Float?)`
   - `Processing`
   - `Restarting`
   - `Recovering(message: String?)`
   - `PermissionRequired`
   - `Unavailable(message: String)`
2. Keep a derived `isVoiceMode` separate from `isRecognizerActive`.
3. Keep a derived `isRecording` for the animated mic.
4. Add a session id/generation token and require every callback to carry the token already captured in the listener.
5. Add a watchdog when starting:
   - If `onReadyForSpeech` does not arrive within a short window, cancel and recover once.
   - If recovery fails, show an actionable status instead of staying stuck.
6. Make `stop`, `cancel`, `destroy`, and `restart` idempotent and state-aware.

Success criteria:

- UI cannot claim "Listening" when the recognizer is actually idle or stuck.
- Tapping the back/exit button always returns to `Idle` and releases the recognizer.
- Repeated mic taps cannot create overlapping recognizer sessions.

### Phase 3 - Make Text Insertion Transactional

Affected files:

- `AddiyonKeyboardService.kt`
- `VoiceInputController.kt`
- possibly new `voice/VoiceTextCommitter.kt`

Steps:

1. Stop calling `InputConnection.setComposingText` from `onVoicePartialResult`.
2. Store partial text in service state, for display in the voice status/suggestion area only.
3. Commit only these stable events:
   - `onResults` final text.
   - `onSegmentResults` on API 33+ when segmented session mode is supported.
   - Last partial only when the user explicitly exits voice mode and the partial is non-blank and newer than the last committed final.
4. Add de-duplication:
   - Normalize whitespace.
   - Track `lastCommittedVoiceText`.
   - Ignore final text if it equals the last committed segment/final.
   - If a final begins with the previous committed segment, commit only the suffix.
5. Add spacing rules:
   - Inspect `getTextBeforeCursor(1, 0)` once per commit, not once per partial.
   - Insert a leading space only when the cursor is after a non-whitespace char.
   - Add a trailing space for ordinary dictation phrases, but avoid double spaces.
   - Avoid extra spaces before punctuation if the recognizer returns punctuation.
6. Wrap multi-step commits in `beginBatchEdit` / `endBatchEdit`.
7. On voice start, call `activeComposer.commit()` once to finish typed composition before dictation begins.
8. While voice mode is active, decide whether normal key taps should:
   - exit voice mode first, then type; recommended
   - or coexist with voice mode; higher risk and not needed for the requested UX.

Success criteria:

- Speaking "hello" then "world" results in `hello world `, not `world ` replacing `hello`.
- Late partial callbacks cannot rewrite committed text.
- Exiting voice mode does not lose the last visible phrase.

### Phase 4 - Use Newer Android Speech APIs When Available

Affected files:

- `VoiceInputController.kt`
- `AndroidManifest.xml` if any support queries need adjustment

Baseline remains `SpeechRecognizer.createSpeechRecognizer(context)` for compatibility.

Enhancements:

1. API 33+: call `checkRecognitionSupport(intent, executor, callback)` before first start for the active language. Cache support results per language tag.
2. API 33+: if language support is unavailable but downloadable, offer or trigger model download with `triggerModelDownload(intent)`.
3. API 33+: experiment with `RecognizerIntent.EXTRA_SEGMENTED_SESSION` to receive `onSegmentResults`. This is a better fit for dictation because segments can be committed incrementally without constantly restarting the whole recognizer session.
4. API 31+: detect `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` and evaluate `createOnDeviceSpeechRecognizer(context)` as an optional engine for English/offline use.
5. API 34+: optionally enable `EXTRA_ENABLE_LANGUAGE_DETECTION` with allowed languages `["am-ET", "en-US"]` for diagnostics or future mixed-language dictation.
6. API 34+: do not enable automatic language switching by default until tested; model availability and recognizer implementation support vary by device.

Recommended compatibility policy:

- Default path: online/system recognizer with explicit language.
- API 33+ path: use support checks and segmented sessions when supported.
- API 31+ optional path: on-device recognizer only when supported and only after checking quality by language.
- Never assume any optional extra works; recognizer implementations may ignore them.

### Phase 5 - Improve Restart and Recovery Policy

Affected files:

- `VoiceInputController.kt`

Steps:

1. Replace constant `RESTART_DELAY_MILLIS = 300` with controlled backoff:
   - first recoverable miss: 300ms
   - second: 700ms
   - third: 1500ms
   - then pause and show "Tap mic to try again"
2. Treat these as recoverable while voice mode remains active:
   - no match
   - speech timeout
   - recognizer busy
   - server disconnected
   - client error immediately after cancellation/restart
3. Treat these as fatal until user action or environment change:
   - insufficient permissions
   - recognizer unavailable
   - language not supported
   - repeated too-many-requests
4. Treat network errors as non-fatal UI states:
   - show "Voice needs internet" for Amharic/online mode
   - leave mic available for retry
5. Wait for `onResults` or `onError` before starting again, per Android docs.
6. Add a cooldown after user stop so callbacks from the old recognizer cannot restart voice mode.

Success criteria:

- No busy/error loops after a pause.
- Stuck sessions self-recover or fail into a clear state.
- Closing/reopening the keyboard should no longer be required to listen again.

### Phase 6 - Redesign Voice Mode UI

Affected files:

- `SuggestionBar.kt`
- `KeyboardScreen.kt`
- possibly `ui/icons` for a back arrow if not using Material icon

Requested behavior:

- The mic icon stays in the toolbar and never gets rewritten into a different location.
- When voice mode is active, the mic shows animation.
- Other toolbar icons disappear while listening.
- "Speak now" and "Listening" appear where suggestions are normally shown.
- In the settings icon position, show a back button. Tapping it exits voice mode and restores all toolbar icons.

Proposed row layout:

```text
Normal empty suggestion row:
[settings] [guide] [feedback] [themes]                         [mic]

Normal suggestions row:
[suggestions or completions fill center]                         [mic optional]

Voice mode:
[back]   [Speak now... / Listening... / transcript / status]     [animated mic]
```

Implementation details:

1. Change `SuggestionArea` API to accept a single `voiceUiState` object instead of separate `isListening` and `voiceState`.
2. Reserve fixed slots for the left action and right mic, so layout does not jump when voice mode changes.
3. In voice mode:
   - left action uses back arrow and `service.exitVoiceMode()`.
   - center displays state label or the latest partial transcript.
   - right action is the mic; tapping it can pause/resume recording inside voice mode or exit, depending on chosen interaction.
4. Recommended interaction:
   - Back button exits voice mode.
   - Mic toggles recording pause/resume but stays in voice mode.
   - This matches the request that the back button exits voice mode.
5. Add short, localized strings through existing app string infrastructure if user-visible text is already centralized.
6. Keep row height at 40.dp to avoid keyboard height jumps.
7. Preserve accessibility descriptions:
   - "Start voice input"
   - "Pause voice input"
   - "Resume voice input"
   - "Exit voice input"

Success criteria:

- Voice mode has a stable visual structure.
- The mic is always in the same toolbar-side position.
- Suggestions are replaced by status text only while in voice mode.
- Exiting voice mode restores the toolbar immediately.

### Phase 7 - Permission Flow Improvements

Affected files:

- `VoicePermissionActivity.kt`
- `AddiyonKeyboardService.kt`

Steps:

1. After permission is granted, auto-start voice on the next `onStartInputView` or via a small shared preference/flag consumed by the service.
2. If permission is denied, set `VoiceUiState.PermissionRequired` with a short status and keep the keyboard usable.
3. If permission is permanently denied, open the app settings screen from a clear action in the main settings app, not from the IME row.
4. Make sure requesting permission does not leave `isVoiceMode` stuck true.

Success criteria:

- First-time permission grant leads naturally into listening without requiring the user to guess they must tap mic again.
- Denial leaves the UI in a clean, non-stuck state.

### Phase 8 - Tests

Affected files:

- new JVM tests under `app/src/test/java/com/addiyon/keyboard/voice/`
- possibly fake `InputConnection`

Unit tests:

1. State machine:
   - start -> ready -> listening -> final -> restarting
   - start timeout -> recovering -> retry
   - user exit prevents restart
   - stale callback ignored by generation
2. Error policy:
   - no speech restarts with backoff
   - busy restarts with backoff
   - too many requests stops after threshold
   - network shows network state
   - permission error shows permission state
3. Text committer:
   - first phrase at empty cursor
   - phrase after existing word inserts leading space
   - no double spaces
   - punctuation does not get a space before it
   - duplicate final after segment does not duplicate text
   - suffix-only commit when final includes already committed segment
4. UI state derivation:
   - voice mode hides normal toolbar icons
   - back button appears in left slot
   - mic remains available in right slot

Manual emulator/device tests:

1. Fresh install, permission not granted, tap mic, grant permission, confirm voice starts.
2. Speak two phrases separated by a pause: previous phrase remains.
3. Speak, pause, speak again for at least five cycles.
4. Turn network off in Amharic mode and confirm useful message.
5. English mode dictation works with same UI.
6. Switch input fields while listening; recognizer stops and UI resets.
7. Press back/exit while listening; toolbar returns.
8. Rapidly tap mic/back/mic; no stuck "Listening" state.
9. Try a device without Google Speech Services if available; mic should fail gracefully.

Verification commands after implementation:

- `./gradlew testDebugUnitTest`
- `./gradlew installDebug`
- `./gradlew assembleDebug`

## Alternative Improvement Options

### Option A - Stronger SpeechRecognizer Implementation

This is the recommended path.

Pros:

- Lowest product and privacy risk.
- Best chance of Amharic quality without building a backend.
- Uses platform APIs and current manifest/permission structure.
- Can support newer API 33/34 improvements incrementally.

Cons:

- Android explicitly warns that the speech recognizer may stream audio remotely and is not intended for unlimited continuous recognition.
- Behavior varies by device recognizer implementation.
- Amharic offline support may be unavailable.

### Option B - API 33+ Segmented Speech Path

Use `EXTRA_SEGMENTED_SESSION` where supported, with fallback to restart-after-final on older or unsupported devices.

Pros:

- Better match for dictation than manual stop/restart.
- Lets the app commit stable segments without overwriting previous text.
- Reduces recognizer busy errors from rapid recreation.

Cons:

- API 33+ only.
- Recognizer implementations may ignore the extra.
- Needs careful fallback and device testing.

### Option C - On-Device Recognizer Path

Use `SpeechRecognizer.createOnDeviceSpeechRecognizer` when `isOnDeviceRecognitionAvailable` is true.

Pros:

- Better privacy and offline potential.
- Lower network error rate for supported languages/models.

Cons:

- API 31+ only.
- Language/model availability varies.
- Amharic quality/support must be proven on real devices before making it default.

Recommended use:

- Optional English offline engine first.
- Keep Amharic on the default online recognizer unless real-device testing proves on-device Amharic is available and good.

### Option D - Cloud Speech Backend

Use Google Cloud Speech-to-Text, Azure Speech, or another paid backend controlled by Addiyon.

Pros:

- More control over streaming, retries, telemetry, and model selection.
- Potentially better server-side quality and punctuation.

Cons:

- Requires backend, billing, API keys, abuse protection, privacy policy updates, and network reliability work.
- Increases Play Data Safety obligations.
- Bigger engineering and maintenance surface.

Recommended only if platform recognizer quality is unacceptable after the rewrite.

### Option E - Bundled Offline Model

Use a local model such as Vosk or Whisper-derived native integration.

Pros:

- Fully offline and private.
- No dependency on Google Speech Services.

Cons:

- App size, CPU, memory, and battery costs are high for a keyboard.
- Amharic model quality/availability is uncertain.
- Native integration and low-end-device performance risk are substantial.

Recommended only as a future research prototype, not as the main fix.

## Risks

- Speech recognition behavior differs across devices and Android versions.
- Optional API extras may be ignored by the recognizer implementation.
- Continuous dictation can consume battery and bandwidth; keep sessions user-visible and easy to exit.
- Committing only final/segment results means the target field will not show live partial text; this is intentional, but the keyboard UI must make partial feedback feel immediate.
- Permission auto-start after grant must be carefully scoped so it does not unexpectedly start recording later.

## Open Questions

1. Should tapping the animated mic during voice mode pause/resume recording, or should it also exit voice mode? Recommended: pause/resume on mic, exit on back.
2. Should voice dictation commit a trailing space after every final phrase? Recommended: yes for normal phrases, with punctuation cleanup.
3. Should the mic remain visible even when suggestions are showing outside voice mode? The request says the icon should stay in the toolbar; recommended: yes, keep a right mic slot even when suggestions fill the center.
4. Should Amharic voice output be accepted exactly as recognizer returns it, or should it pass through any normalization? Recommended: accept recognizer Fidel text directly, then add only whitespace/punctuation cleanup.
5. Should voice mode block language/layout toggles? Recommended: exiting voice mode before any non-voice key keeps state simpler and safer.

## References Checked

- Android `SpeechRecognizer` reference: main-thread requirement, `destroy()`, `RECORD_AUDIO`, remote streaming warning, `createOnDeviceSpeechRecognizer`, `checkRecognitionSupport`, `triggerModelDownload`, and restart constraints.
- Android `RecognizerIntent` reference: `EXTRA_SEGMENTED_SESSION`, `EXTRA_ENABLE_LANGUAGE_DETECTION`, `EXTRA_ENABLE_LANGUAGE_SWITCH`, and language extras.
- Android `RecognitionSupport` reference: installed on-device languages and online languages.
