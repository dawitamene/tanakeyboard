# AI Rephrase & Grammar — Toolbar Overlay Feature

## Goal
Add an AI-powered rephrase / spelling & grammar panel to Addiyon Keyboard that works like Samsung Keyboard / TextRevamp extension: an **AI toolbar icon** opens a full-width **overlay over the keys** with tone tabs (Humanize, Professional, Casual, Formal, etc. + Fix Spelling & Grammar), calls the hosted TextRevamp backend (`https://api.textrevamp.com`, code in `~/Users/dev/code/textrevamp`, infra in `~/Users/dev/code/addiyon-gitops`), enforces **email registration + daily word quota (free tier)**, and offers **Copy / Replace** after the response. Keep an upgrade path stub without building billing now.

## Success Criteria
- User taps a new AI icon in the strip/toolbar and sees a panel that **covers the key rows** (not a separate Activity) with selectable tabs and an input preview.
- Selecting a tab rephrases the **selected text** if any, otherwise the **sentence / surrounding text before cursor** (bounded, cursor-relative). Results streaming or single-shot both acceptable, but UI must show loading, success, error, and quota-exceeded states without flashing the toolbar.
- **Fix Spelling and Grammar** corrects only — does not rephrase (matches `ToneOptions.FixSpellingAndGrammar` contract).
- **Copy** puts result on clipboard; **Replace** replaces the **exact same range that was sent** via cursor-relative `commitText`/`setComposingText` through `EditorGateway` — no absolute document offsets, no text loss in Compose/WebView fields. Undo is not required for v1 but the replace must be atomic.
- **Auth-gated**: unauthenticated user sees email registration prompt; after magic-link verification the JWT is stored and attached as `Authorization: Bearer`. Anonymous fallback (device UUID + `X-Anonymous-Id`) is acceptable for free tier only if we keep the current `UsageGuard` path — but spec says email registration is required, so free requests without email must be blocked.
- **Quota**: daily **words** limit (configurable, default ~ 500–1000 words/day for free) enforced client + server; when reached, tabs disable with a clear "Daily limit reached — try tomorrow" message and upgrade CTA placeholder.
- No regression to composing invariant, suggestion strip shape-holding, or low-RAM behaviour.
- `INTERNET` permission is re-enabled only for this feature and explained in Play listing (reverses the trust-cost decision in `AndroidManifest.xml:11-15`).

## Context And Current Facts

### Keyboard (app)
- Single module `:app`, Compose UI. `AddiyonKeyboardService` (`app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt:87ff`) owns `isAmharic`, `shiftState`, `suggestionUiState`, `TypingController`, `EditorGateway`. All `InputConnection` access goes through `EditorGateway` (`app/src/main/java/com/addiyon/keyboard/EditorGateway.kt:115ff`) which is cursor-relative and token-guarded (`isCurrent`/`isCurrentSession`, `revalidateSelection`, `replacementSnapshot`, `transitionAfterAcceptedReplacement`). New code **must not** store absolute offsets.
- `SuggestionUiState` (`app/src/main/java/com/addiyon/keyboard/ui/SuggestionUiState.kt:8ff`) is `Toolbar | LoadingLanguage | LoadingPredictions | LoadingCompletions | WordCompletions | NextWordPredictions | EmailSuggestions | Private | Voice`. The strip renders this in `SuggestionBar.SuggestionArea` (`app/src/main/java/com/addiyon/keyboard/ui/SuggestionBar.kt:84ff`) and keeps its height at `40.dp` while `KeyboardScreen` (`app/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt:57ff`) hosts the key rows below it. Row-shape holding (keep chips while predictions compute, `activeCompletionKey` guard) is how flicker was killed — AI panel must not reintroduce it.
- `onAiAction()` at `AddiyonKeyboardService.kt:1872` and `onClipboardAction()` at `:1879` are **stubs** (`TODO: hook up`). `SuggestionBar.Toolbar` shows `ToolbarActions` (settings/themes/guide/feedback/emoji) + mic; `Private` is same without mic. The AI entry is passed as `onAi = service::onAiAction` from `KeyboardScreen.KeyboardSuggestionArea:72` but no icon currently renders for it — `ToolbarActions` must gain the sparkle/AI button.
- `EmojiPanel` (`app/src/main/java/com/addiyon/keyboard/ui/emoji/EmojiPanel.kt:45`) is the precedent for "replace toolbar + key rows": it covers that exact area with its own composable. `KeyboardScreen` already has an `emojiSearching` gate that swaps rows; voice mode swaps `suggestionUiState` to `Voice`. AI mode should follow the same pattern (new `AiPanel` composable + a service-owned `aiUiState` or an extension of `SuggestionUiState`).
- `AndroidManifest.xml:11` has `INTERNET` **commented out** with a comment to re-enable when network is actually used. `build.gradle.kts:39ff` has `telemetryEnabled = false` which gates Firebase. Re-enabling network has Play-policy and user-trust implications — needs a justification string.
- `KeyboardPrefs` (`app/src/main/java/com/addiyon/keyboard/ui/settings/KeyboardPrefs.kt:16ff`) is the `SharedPreferences` facade. No AI/auth keys exist yet. It already caps opaque strings at `MAX_OPAQUE_PREF_LENGTH = 65536` and sanitizes on read.

### TextRevamp backend (~/Users/dev/code/textrevamp/server)
- NestJS 10, `server/src/text/types.ts:1ff`: `ToneOptions = ShowAll | Rephrase | Casual | Friendly | Formal | Confident | Professional | Polite | Humorous | Emojified | Fix Spelling and Grammar | Summarize | Shorten`, `Strength = subtle|balanced|strong` (`DEFAULT balanced`). Values are the **wire contract** — must stay identical on client (`server/src/text/dto/revamp-text-dto.ts:7ff` duplicates the enum, values are `chrome.contextMenus` IDs).
- `server/src/text/text.service.ts:27ff`: `POST /text` → `revamp()` / `POST /text/alternatives` → `alternatives()` (returns 3 alternatives) / `POST /text/detect` → tone detection. System prompts in `server/src/text/prompts.ts:1ff` include the rule "For Fix Spelling and Grammar: change ONLY what is wrong. Do not rephrase."
- `server/src/text/rewrite-preparation.ts:8ff`: `MAX_INPUT_LENGTH = 4000`, `prepareRewriteText` truncates, `stripRewriteWrapping` / `normalizeRewriteAlternatives`. Completion budgets: `REWRITE_MAX = 2048`, `ALTERNATIVES_MAX = 4096`.
- `server/src/usage/usage.service.ts:6ff` + `usage.repository.ts:9ff` + `usage.guard.ts:9ff`: `DAILY_LIMIT = 50` **requests/day**, keyed by `user_id` + `day`, `X-Anonymous-Id` header required, `incrementWithinLimit` does atomic `INSERT ... ON CONFLICT ... WHERE count < limit`. Current auth is **anonymous UUID**; `auth/` (magic link via `POST /auth/link` → email, `GET /auth/callback?token=&redirect_uri=` → JWT, `AuthGuard` checks `Bearer`) and `user/` exist but `UsageGuard` does **not** check JWT — it only uses `X-Anonymous-Id`. For email-gated words/day quota we need either (a) extend `UsageGuard` to prefer JWT `sub` when present, or (b) add a new `AiUsageGuard`.
- `server/src/grammar/` has `POST /text/check`, `/check/v2`, `/check/enhancements` via Luna `gpt-5.6-luna`. Not needed for rephrase but shares the quota guard.
- CORS in `server/src/configure-app.ts` / `main.ts` is restricted to `chrome-extension://` — Android (`capacitor://`, `https://`, or no Origin) will be blocked unless we add `capacitor`/app origins or — better — allow the Android app's package via `Origin: https://api.textrevamp.com`-style or no-Origin + API key approach. Currently any `fetch` from the keyboard without an allowed Origin will 403.
- Deployment via `~/Users/dev/code/addiyon-gitops/textrevamp/resources/deployment.yaml` (image `ghcr.io/dawitamene/textrevamp-server:<sha>`, `PORT=8080`). CI in `textrevamp/.github/workflows/ci.yml` rebuilds on `server/**` push. Any server contract change (new endpoint, new header) must be reflected here if it needs env.

### Assumptions needing confirmation
- Backend host is `https://api.textrevamp.com` (from `textrevamp/CLAUDE.md`). No staging host assumed.
- "Words per day" in spec maps to backend's current "requests per day" (`50`). Converting to true word-count quota requires server change (new column or word-length check). For v1 we can treat words ≈ `ceil(chars/5)`.
- User wants tabs like `Humanize | Professional | Spelling & Grammar | ...` — "Humanize" is not a current `ToneOptions` value. It will need to map to `Rephrase` with `instruction: "Humanize — remove AI-like phrasing, make it sound naturally human"` or a new `ToneOptions.Humanize` added server + extension simultaneously.

## Constraints And Non-goals

**Constraints**
- `kotlin.code.style=official`, no code comments in generated code (repo convention).
- No multi-character key labels in layout data; no absolute offsets in composing layer — AI replace must go through `EditorGateway` cursor-relative calls.
- `EditorGateway` token invalidation on failed writes — if `commitText` returns false or connection changes, invalidate and surface an error, do not retry with a stale token.
- Do not break `assembleProvider` hook in `app/build.gradle.kts` that copies APK to `/Users/dev/Sync`.
- Network on the main thread is forbidden — all HTTP off `Dispatchers.IO`.

**Non-goals (v1)**
- No upgrade/billing flow (only a placeholder CTA and a `hasPro` boolean that can be flipped via remote config later).
- No history persistence on device beyond daily quota + auth token; no cloud sync of rewrites.
- No inline grammar underlines / live proofreading (that's the Luna `check/enhancements` path — separate).
- No custom-tone creation UI (server has `custom-tones/` but keyboard v1 uses only built-ins).
- No per-app allow-list or enterprise policy.
- No change to `AmharicTable` / `Transliterator`.

## Key Decisions

| Decision | Options | Recommendation & Why |
|---|---|---|
| **Tone set exposed as tabs** | A: 1:1 map of all 12 `ToneOptions`. B: Curated 5–6 tabs (e.g. Humanize, Professional, Casual, Formal, Fix Grammar, Shorten) + "More". C: Dynamic from server `BUILT_IN_TONES`. | **B for v1**, with a 1:1 mapping underneath. 12 tabs don't fit a 360dp keyboard without horizontal scroll jank. Curated covers the spec examples (humanize/professional/spelling) and matches Samsung's 4–5. Keep a `ToneTab` enum that maps to `ToneOptions` so adding a tab is one line. Alternative A rejected for UX; C rejected for offline complexity — can graduate to C later. |
| **Humanize tone** | A: Reuse `Rephrase` + custom instruction. B: Add `ToneOptions.Humanize` server + client. | **A for v1**, **B if server owner agrees**. A requires no deploy; the instruction `"Humanize the text: remove AI-like phrasing, vary sentence structure, keep meaning"` has been validated against `REVAMP_SYSTEM_PROMPT` (it respects "do not add info"). If we ship A we must not break the shared contract — `tone` stays `Rephrase`, `instruction` carries the humanize direction. |
| **Input source** | A: Always surrounding text (sentence extraction). B: Selection if present else sentence. C: Explicit "select text first" only. | **B** — mirrors Samsung/Gboard. `EditorGateway.selectedText(optional=false)` first; if null/empty, `surroundingText(before=400, after=100)` → extract last sentence (`[^.!?]*[.!?]?` from cursor backward, bounded at 400 chars, trimmed to `MAX_INPUT_LENGTH 4000`). This guarantees something to send without forcing the user to select. C is too demanding; A loses the user's explicit intent when they did select. |
| **Replace mechanics** | A: `commitText` over the replaced range via `replacementSnapshot`. B: `setComposingText` + `finishComposingText`. C: Clipboard Lighthack. | **A** when selection existed (range = selection). When we sent surrounding-text sentence, use `replacementSnapshot(replacementStart, replacementEnd, ...)` where `replacementStart/End` are computed from `EditorSurroundingText.offset` + sentence bounds, then `EditorGateway.write { it.commitText(replacement, 1) }`. This is the only cursor-relative way to replace a mid-field sentence. B is for composing-region flows and would leave text underlined; C is fragile. |
| **Auth** | A: Email magic-link only (no password). B: Email+password. C: OAuth (Google). | **A** — reuses existing `AuthService.issueMagicLink` / `consumeMagicToken` / `JwtService`. Keyboard cannot host a full OAuth redirect cleanly; magic link via `CustomTabs` + deep link `addiyon://auth/callback?token=` is simplest. Keep `X-Anonymous-Id` as pre-auth device identifier for quota, but gate AI behind `JWT` — anonymous requests to `/text` get 401 with "Email registration required" body so the UI can prompt. |
| **Quota unit** | A: Requests/day (existing 50). B: Words/day (spec). C: Characters/day. | **B per spec**, implemented as **words = `trim().split(/\s+/).length`** on both client (preflight, to disable tabs early) and server (authoritative). Server needs a new `usage_counters.words` column or a new `ai_usage` table. For v1 we can land **A + client words display** (cheaper, no migration) and migrate to B in phase 3 — plan below does B properly because product asked for words. |
| **Network stack** | A: Ktor client. B: Retrofit + OkHttp + Moshi/kotlinx.serialization. C: `HttpURLConnection`. | **B** — Retrofit+OkHttp is the Android default, supports interceptors (auth header, logging), and `Moshi` keeps DTOs tiny. Ktor is larger and duplicates `kotlinx.coroutines` setup. Add `com.squareup.retrofit2:retrofit:2.9.0`, `converter-moshi`, `okhttp:4.12`, `moshi-kotlin`. Pin `INTERNET` permission to `usesPermission` with `android:usesCleartextTraffic="false"` and a `network_security_config` that only allows `api.textrevamp.com`. |
| **UI layering** | A: New `SuggestionUiState.Ai` variant. B: Separate `AiUiState` in service, `KeyboardScreen` switches. | **B** — `SuggestionUiState` is the strip's state; AI is a **full panel** that owns strip + keys. Mixing them makes `SuggestionArea`'s `when` explode and breaks `isSuggestionState` shape logic. Keep `AiPanelState { isVisible, selectedTab, inputPreview, result, isLoading, error, quota }` as a separate `mutableStateOf` in the service, and have `KeyboardScreen` render `AiPanel` instead of key rows when `isVisible`. The strip can show a minimal "AI — Rephrase" header or be hidden. |
| **State holder** | A: Service field + `mutableStateOf`. B: ViewModel. C: Separate `AiController`. | **A + C**: service owns the `AiController` (like `TypingController`/`VoiceInputController`) and exposes its `StateFlow`/`mutableStateOf` to Compose. `AiController` is pure Kotlin over `AiRepository` (network) + `EditorGateway` (text) so it is JVM-testable with a `FakeEditorGateway`. No Android ViewModel needed inside an `InputMethodService` (lifecycle is the service itself). |

## Recommended Approach

Keep the keyboard's hard invariants, reuse the backend as-is where possible, and layer AI as a **panel** not a strip:

1. **Overlay panel over keys**: When the user taps the new sparkle icon in `SuggestionBar.Toolbar`, `AddiyonKeyboardService.onAiAction()` toggles `aiController.isVisible`. `KeyboardScreen` reads `service.aiUiState` and, when visible, renders `ui/ai/AiPanel` in place of `keyboardRows` (same trick `EmojiPanel` uses). The panel covers the exact height of the rows + number row so no window inset shift is visible. Tabs are a `ScrollableTabRow` with 5–6 curated tones; content is: input preview (read-only, 2 lines, ellipsized), result card (shimmer while loading), and a footer row with **Copy** + **Replace**.
2. **Cursor-relative text sourcing**: On panel open, synchronously capture `EditorGateway.selectedText()` or `surroundingText().value` + sentence bounds. Store the captured `EditorReplacementSnapshot` (or `EditorToken` + offsets) as `pendingReplacement`. All later `Replace` writes use that snapshot's `transitionAfterAcceptedReplacement` check — if token is stale, button disables with "Text changed — reopen AI".
3. **Auth before quota**: First tap with no stored JWT shows an inline email field + "Send link" button (not a separate Activity). `POST /auth/link { email, redirectUri: "addiyon://auth/callback" }` via existing `AuthService`. User opens email, taps link, system handles `addiyon://` via an intent-filter on a lightweight `AiAuthActivity` (or `MainActivity` deep link) which calls `GET /auth/callback?token=&redirect_uri=` → extracts `jwt` query param → stores via `EncryptedSharedPreferences` (or `KeyboardPrefs` with `MAX_OPAQUE` guard, key `KEY_AI_JWT`). From then on every `/text` call carries `Authorization: Bearer <jwt>` **and** `X-Anonymous-Id: <uuid>` (the latter for backwards compat with `UsageGuard` until it is updated to read JWT).
4. **Quota as words**: Client computes `wordCount = input.trim().split(Regex("\\s+")).size` (empty → 0) and shows `~123 words • 420 left today`. Preflight disables action if `words > remaining`. Server enforces authoritatively: new `ai_usage` table (`user_id, day, words_used`) or extend `usage_counters` with `words` column; `POST /ai/revamp` guard checks `words_used + wordCount <= DAILY_WORD_LIMIT` before calling `AiService`. Returns `429 { code: "DAILY_LIMIT_REACHED", remaining: 0 }` which the panel renders as quota state. Keep `DAILY_WORD_LIMIT` in `KeyboardPrefs` synced from `GET /ai/quota` so offline preflight is accurate.
5. **No new IME window**: Panel is Compose inside the existing `InputMethodService` input view. No `Dialog`, no `PopupWindow`, no `SYSTEM_ALERT_WINDOW`. This preserves the service's `onFinishInput`/`onStartInput` lifecycle and keeps `EditorGateway.generation` valid.

## Work Plan

### Phase 0 — Discovery & Contract Freeze (0.5 day, no code)
- Confirm `api.textrevamp.com` host, CORS allow-list for Android, and whether `Humanize` is a new `ToneOptions` or an instruction on `Rephrase`. Freeze the tab→tone mapping.
- Decide DAILY_WORD_LIMIT value (suggest 800 words/day free, matches ~16 × 50-word messages) and whether paid tier is a feature-flag only (`hasPro` in prefs).
- Capture current strip/panel heights and decide AI panel height equals `keyboardRows` height (measure in `KeyboardMetrics`).

**Deliverable**: one-page contract doc checked into `docs/ai-contract.md`.

### Phase 1 — Service & Keyboard Foundation (3–4 days)
**Files**:
- `AndroidManifest.xml` — re-enable `<uses-permission android:name="android.permission.INTERNET" />` + `android:usesCleartextTraffic="false"` + `network_security_config.xml` (allow only `api.textrevamp.com`, `localhost` for debug).
- `app/build.gradle.kts` — add Retrofit/Moshi/OkHttp, `androidx.security:security-crypto`, `androidx.core:core-ktx` if needed; keep `assembleProvider` hook.
- `ui/settings/KeyboardPrefs.kt` — add `KEY_AI_JWT`, `KEY_AI_EMAIL`, `KEY_AI_ANON_ID`, `KEY_AI_WORDS_USED_TODAY`, `KEY_AI_QUOTA_DAY`, `KEY_AI_DAILY_LIMIT`; getters/setters with `MAX_OPAQUE_PREF_LENGTH` guard. `KEY_AI_ANON_ID` generated once as `UUID.randomUUID().toString()` and never rotated.
- `network/AiApi.kt` — `interface AiApi { @POST("text") suspend fun revamp(@Body dto: RevampTextDto): RevampResult; @POST("text/alternatives") ...; @POST("auth/link") ...; @GET("auth/callback") ...; @GET("ai/quota") ... }` with `MoshiConverterFactory`, `OkHttp` interceptor adding `X-Anonymous-Id` and `Authorization`.
- `network/AuthStore.kt` — `EncryptedSharedPreferences` wrapper (fallback to `KeyboardPrefs` if StrongBox unavailable), `jwtFlow: StateFlow<String?>`.
- `ai/AiRepository.kt` — thin wrapper over `AiApi`, maps HTTP 401/429 to typed `AiError` (`NeedsAuth`, `QuotaExceeded(remaining)`, `Network`, `Server`).
- `ai/AiController.kt` — pure, testable: `fun open(): AiInput` (captures `EditorGateway` snapshot + word count), `suspend fun revamp(tab: ToneTab): Result<AiResult>`, `fun copy(text)`, `suspend fun replace(snapshot, result): Boolean`. No Android imports except `EditorGateway`.
- `AddiyonKeyboardService.kt` — add `lateinit var aiController`, `var aiUiState by mutableStateOf(AiUiState.Hidden)`, wire `onAiAction()` to toggle, `onAiTabSelected(tab)` to call `aiController.revamp`, `onAiCopy()`, `onAiReplace()`, `onAiDismiss()`. Ensure `onStartInput`/`onFinishInput` reset `aiUiState` if the field changed (selection token stale).

**Tests**: `AiControllerTest` with `FakeEditorGateway` + `FakeAiApi` covering selection vs sentence extraction, word count, auth header.

### Phase 2 — Overlay UI (3–4 days)
**Files**:
- `ui/SuggestionBar.kt` — add sparkle AI icon to `ToolbarActions` (when `aiUiState` is hidden) and to `Toolbar` row (always visible even in `WordCompletions` — spec says "extra ai toolbar icon", so it must not disappear when suggestions show; place it at the strip's end alongside the mic/dismiss affordances).
- `ui/ai/AiPanel.kt` — new composable: `ScrollableTabRow` tabs (`Humanize`→`Rephrase+instruction`, `Professional`, `Friendly`, `Formal`, `Fix Grammar`→`FixSpellingAndGrammar`, `Shorten`), input preview card, result card (shimmer `PredictionLoadingStrip` reuse when `isLoading`), error banner, quota footer, `Copy`/`Replace` buttons (Material3 `FilledTonalButton` + `Button`). Copy uses `ClipboardManager`; Replace delegates to service. Panel height = `keyboardRows` height; it scrolls internally if content overflows so keys never shift.
- `ui/ai/AiAuthInline.kt` — inline email field inside `AiPanel` when `jwt == null`: `OutlinedTextField` + "Send link" + "Open email app" helper + dev-mode `devLink` display when `!isProduction`.
- `ui/KeyboardScreen.kt` — branch: `if (service.aiUiState.isVisible) AiPanel(...) else keyboardRows`. Ensure `emojiSearching` and `aiVisible` are mutually exclusive (opening one closes the other).
- `ui/theme` — add `AiPanel` colors that respect `KeyboardPalette` (reuse `MaterialTheme.colorScheme.surfaceContainer`).

**Tests**: Compose screenshot/preview for `AiPanel` (light/dark), `SuggestionBar` toolbar icon still renders in all `SuggestionUiState` variants.

### Phase 3 — Replace & Clipboard Wiring (1–2 days)
**Files**:
- `EditorGateway.kt` — no change expected, but add a helper `fun replaceWithSnapshot(snapshot: EditorReplacementSnapshot, text: String, token: EditorToken): Boolean` that does `replacementSnapshot` validation + `commitText` atomically if not already present via `write`.
- `composing/ResumableWord.kt` — reuse `emailWordEndingAtCursor` pattern for sentence bounds: new `fun sentenceEndingAtCursor(...): StringRange?` that walks `textBeforeCursor` backward to sentence start. Keep pure.
- `ai/AiController.kt` — implement `replace`: `editorGateway.replacementSnapshot(...) ?: return false`, `editorGateway.write(token) { commitText(result) }`, update `pendingPredictionBoundary` if needed so next-word predictions still work after AI replace.
- `AddiyonKeyboardService.kt` — `onAiReplace` calls `aiController.replace` and on success `hideAiPanel()` + `updateSuggestions()`.

**Tests**: `TypingControllerTest` addition: after `onAiReplace`, composing is clean and next tap types at the new caret.

### Phase 4 — Quota, Privacy, Resilience (2 days)
**Server** (`~/Users/dev/code/textrevamp/server`):
- New `ai/` or extend `usage/`: migration `ALTER TABLE usage_counters ADD COLUMN words_used INT DEFAULT 0` or new `ai_usage(user_id, day, words_used, requests)`. New guard `AiUsageGuard` that prefers `Authorization` `sub` over `X-Anonymous-Id`, counts words (`wordCount(dto.text)`), returns `{ remaining, limit, used }` in response header `X-Ai-Quota` and in `429` body.
- New `GET /ai/quota` returning `{ used, limit, remaining, resetAt: ISO8601 midnight UTC }`.
- CORS: add Android origin (`capacitor://*`, `https://*`, and allow no-Origin with `X-Anonymous-Id` — simplest is `origin: true` for `POST /text` when `Authorization` present, or add `appPackage` allow-list).
- Deploy via `~/Users/dev/code/addiyon-gitops/textrevamp/resources/deployment.yaml` (bump tag).

**Keyboard**:
- Preflight word count in `AiController.open()` disables "Rephrase" button when `wordCount == 0` or `wordCount > remaining`.
- Handle `isPrivateField` — AI panel still opens but shows "Not available in private fields" and disables tabs (mirrors voice's `isPrivateField` gate). Never send private-field text to the server; do not log it.
- Offline: `AiRepository` catches `IOException` → panel shows "Offline — check connection" with Retry. Do not queue.
- Low-RAM: `AiPanel` is not cached; if `isLowRam && isEmergencyMode` drop the panel's history (keep only latest result).

### Phase 5 — Release Hardening (1–2 days)
- Add `network_security_config.xml` tests, `INTERNET` permission rationale in Play Console "Data safety" (declare `Network` + `Email` collection, retention, encryption).
- Add `AiPanel` a11y labels, TalkBack traversal, keyboard height scale respected.
- Manual QA matrix: Gboard vs Samsung vs Chrome Custom Tab vs WebView, selection vs no-selection, Amharic vs English, private fields, rotation, multi-window, low-RAM device (`low-ram-stress.sh`, `phase3-ime-stress.sh` from `plans/`).
- Build + install: `/Users/dev/code/addiyon-keyboard/gradlew assembleDebug` (APK to `/Users/dev/Sync`) and `/Users/dev/code/addiyon-keyboard/gradlew installDebug` on emulator.
- Docs: update `docs/release-notes/2.0.5.md`, `AGENTS.md` architecture section (new AI layer), and `plans/next-word-flicker-fix.md` sibling.

**Dependencies**: Phase 1 → 2 → 3; Phase 4 server can parallelize with Phase 2; Phase 5 after all.

## Validation Plan

| Area | Command / Check | Expected Evidence |
|---|---|---|
| Contract | `cat server/src/text/types.ts` vs keyboard `ToneTab` mapping | Every tab maps to a valid `ToneOptions`; `Humanize` decision recorded in `docs/ai-contract.md` |
| Unit — AI controller | `/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest --tests "com.addiyon.keyboard.ai.AiControllerTest"` | Pass: selection preferred, sentence fallback, word count, quota preflight, auth error mapping, stale-token replace rejected |
| Unit — composing invariant | `/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest --tests "com.addiyon.keyboard.composing.TypingControllerTest"` | Pass: no absolute offsets introduced; replace after AI does not desync `Composition` |
| Unit — server (if changed) | `cd ~/Users/dev/code/textrevamp/server && npm test && npm run test:e2e` | Pass: new `AiUsageGuard` word-count tests, CORS allows Android, `GET /ai/quota` returns `remaining` |
| Compile | `/Users/dev/code/addiyon-keyboard/gradlew compileDebugKotlin` | No errors; `INTERNET` permission does not break debug signing |
| Install | `/Users/dev/code/addiyon-keyboard/gradlew installDebug` + manual: open any app, select a sentence, tap AI sparkle, pick `Fix Grammar` → result shows → Copy works → Replace swaps exactly the source range | Visual: input preview matches selection, shimmer while loading, Copy toast, Replace moves caret to end of replaced text, no lost chars |
| Quota | Set `DAILY_WORD_LIMIT=5` in debug prefs, send 3-word then 3-word request | Second request gets `429 DAILY_LIMIT_REACHED`, panel shows quota banner, tabs disabled, `GET /ai/quota` shows `remaining=0`, resets after midnight UTC |
| Privacy | Enable incognito/private field, open AI panel | Panel shows "Not available in private fields", no network call in logcat (`adb logcat | grep -i ai`) |
| Low-RAM | `./plans/low-ram-stress.sh` + `./plans/phase3-ime-stress.sh` | No OOM, panel dismisses cleanly on `onFinishInput`, service not killed |

Highest-risk validation: **Replace correctness** — a cursor-relative replace that is off by one or uses a stale token silently corrupts the field. The `AiControllerTest` with `FakeEditorGateway` + a manual emulator pass on a Compose `TextField` and a Chrome `contentEditable` are both required; neither alone is sufficient.

## Risks / Rollback

| Risk | Likelihood | Impact | Mitigation / Rollback |
|---|---|---|---|
| `INTERNET` permission drops Play trust / triggers new Data Safety review | Medium | High | Keep permission behind a `buildConfigField` `AI_ENABLED` flag so a flavor without it can ship if review fails. Rollback: comment permission back out, `aiUiState` stays `Hidden`, rest of keyboard unaffected. |
| CORS blocks Android (current allow-list is `chrome-extension://`) | High | High | Add Android to allow-list in the same PR as the client; test with a real device against staging before prod deploy. Rollback: deploy previous server tag via GitOps (`textrevamp/resources/deployment.yaml` image tag). |
| `EditorGateway` read returns null / stale (WebView, Compose) | Medium | High | All reads are `optional` where appropriate; panel degrades to "Select text to rephrase" empty state. Never crash on null. Stale `Replace` disables button. |
| Quota word-count drift (client vs server counting differently) | Medium | Medium | Authoritative is server; client preflight is advisory. Define word counting once (`trim().split("\\s+").filter { it.isNotEmpty() }.size`) and share via `docs/ai-contract.md`. Log `X-Ai-Quota` header for debugging. |
| Magic-link flow is awkward inside an IME (no browser) | Medium | Medium | Use `CustomTabsIntent` to open the email link; deep link `addiyon://auth/callback` is handled by `MainActivity` (already the settings host). Provide "Paste token" fallback for users whose email app doesn't fire the link. |
| Backend `DAILY_LIMIT 50 requests` vs spec `words/day` mismatch | Low | Medium | Phase 4 migrates to words; until then display "X requests left" and keep words preflight advisory so the UX doesn't lie. |
| Panel jank / height mismatch on small screens | Low | Low | Panel height is measured from `KeyboardMetrics`, not hardcoded; `ScrollableTabRow` handles overflow. Test at `KEYBOARD_HEIGHT_SCALE_MIN/MAX`. |
| User pastes sensitive text to server | Medium | High | Show explicit "Text is sent to TextRevamp to rephrase" disclosure on first open (required for Play). Respect `isPrivateField` and never send. Add a "Don't ask again" pref but keep disclosure in settings. |

Rollback is per-phase: each phase lands behind `AI_ENABLED` / `hasPro` flags so the feature can be disabled without reverting the APK — flip the flag, the sparkle icon hides and `onAiAction` no-ops.

## Open Questions

1. **Humanize tone**: should we add a new `ToneOptions.Humanize` on the server (`text/types.ts` + `prompts.ts` + `TODO` in `ToneOptions` contract) or ship v1 as `Rephrase` with `instruction: "Humanize..."`? A new tone is cleaner but requires a server deploy and extension sync.
2. **Daily words limit**: what is the free quota? Spec says "free credits i.e words per day limit" — propose 800 words/day free (fits `MAX_INPUT_LENGTH 4000` ≈ 800 words). Confirm whether the limit should reset at midnight UTC (current `usage.repository.ts: today()` uses UTC) or device local time.
3. **Auth UX**: magic link (existing) vs email+password vs Google Sign-In? Magic link reuses `auth/` but adds an email-app round-trip. If friction is a concern, should we allow **anonymous free tier** (device UUID only) and only require email when quota is exceeded, or gate from the first use as spec says ("To use it users have to register with email")?
4. **CORS / API key**: should the Android app use an `X-Api-Key` or `X-Package-Name` header in addition to JWT so the server can reject non-keyboard callers even when `Origin` is missing? Current `UsageGuard` trusts any `X-Anonymous-Id`.
5. **Replace granularity**: when no selection exists, should Replace replace the **entire field** or just the **sentence at cursor**? Current recommendation is sentence (less destructive, matches Samsung). Confirm product wants sentence vs whole-field.
6. **Amharic support**: should the panel be available in Amharic mode? `ToneOptions` prompts are English-centric. Options: (a) enable AI only when `isAmharic == false`, (b) enable but add disclosure "English only", (c) add Amharic-aware prompts later. Recommend (a) for v1.
7. **Upgrade path**: spec says "upgrade path for now not considered, but keep that in mind." Should we add a `GET /ai/entitlement` endpoint now (returns `{ plan: "free"|"pro", dailyLimit }`) so the client can show "Upgrade for unlimited words" without a second migration, or defer entirely and just leave a `hasPro` placeholder pref?
8. **Logging & rate limiting abuse**: should the client add a per-minute debounce (e.g. 1 request / 2s) and exponential backoff on 429/5xx to avoid burning quota accidentally via rapid tab switching? Server's `incrementWithinLimit` is atomic but does not dedupe rapid retries.
9. **Data retention**: server's `history/` module may store rewrites. Should AI rewrites from the keyboard be **excluded** from history (privacy) or included for the user's own history view? Current extension history is per-user.
10. **Play Data Safety**: adding `INTERNET` + email collection requires a new Data Safety declaration (why email is collected, where text is sent, retention). Who owns the Play Console update and privacy policy link?

---
*Plan saved to `plans/ai-rephrase-feature.md`. No code was changed. Next step is approval to proceed to Phase 1.*
