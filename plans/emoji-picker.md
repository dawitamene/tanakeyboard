# Emoji Picker (Gboard-parity) for Addiyon Keyboard

## Context

The keyboard has no emoji support. The goal is a Gboard-parity emoji picker: full Unicode emoji coverage across all categories, a Recents category, skin-tone variant long-press popups, and emoji search — opened from a new emoji icon in the existing toolbar row. It must be jank-free on slow devices (minSdk 24) and must never show tofu for emoji the device font can't render.

User-confirmed decisions: **custom Compose picker** (not androidx `emoji2-emojipicker`) so it follows all 37 `KeyboardPalette`s and dark mode; scope includes **recents + skin tones + search** in this first version.

## Architecture overview

Mirror the dictionary pipeline exactly:

- **Data asset**: `tools/build_emoji_data.py` (like `tools/build_english_dict.py`) generates gzip `app/src/main/assets/emoji.dat` from Unicode `emoji-test.txt` (v16.0: codepoints, Gboard-matching group order, skin-tone variant sequences) + CLDR `en.xml`/`annotationsDerived/en.xml` (names, search keywords). One asset (~120 KB gzipped) — keywords included.
- **Pure Kotlin layer** (JVM-testable, like `WordTrie`): `emoji/EmojiData.kt` — models + parser + search matcher; `emoji/RecentEmojiStore.kt`; `emoji/SkinToneStore.kt`.
- **Android wrapper** (like `WordDictionary`): `emoji/EmojiRepository.kt` — asset + gzip + `Paint.hasGlyph` filtering on a background Thread, main-Looper Handler callback.
- **UI**: new `ui/emoji/` composables replacing the SuggestionArea + key-rows area at exactly equal height (no IME window resize jump).

### emoji.dat record format (UTF-8, tab-separated lines)

```
V<TAB>1<TAB>16.0                                   # format version, emoji version (first line)
G<TAB>Smileys & Emotion                            # group header, opens a category
E<TAB>😀<TAB>grinning face<TAB>face,grin,happy<TAB>            # base emoji, empty variants field
E<TAB>👋<TAB>waving hand<TAB>hand,wave<TAB>👋🏻 👋🏼 👋🏽 👋🏾 👋🏿    # variants space-separated, CLDR order
```

Generator rules: fully-qualified rows only; skip the `Component` group; fold skin-tone variant rows (name = `<base>: <tone>…`, incl. multi-person combos) into the base's variants field; groups in emoji-test.txt order (Smileys & Emotion, People & Body, Animals & Nature, Food & Drink, Travel & Places, Activities, Objects, Symbols, Flags — kept as separate tabs, like Gboard). Missing CLDR annotation → fall back to emoji-test.txt short name. Cache downloads under `tools/.cache/`.

### Renderability filtering (no tofu)

During background load, run `Paint().hasGlyph(s)` on every base and variant (~6,400 cheap native calls ≈ 10–40 ms on slow hardware, off the main thread). Drop unrenderable bases; drop only the unrenderable variants of a renderable base (popup shrinks; if all variants drop, no popup). Same pass precomputes each emoji's lowercase search haystack (`name + '' + keywords`) so queries never lowercase per keystroke. No persistent hasGlyph cache in v1 (parse dominates; a `Build.FINGERPRINT`-keyed cache is a follow-up if profiling demands).

### Loading

`EmojiRepository.loadAsync(onReady)` — idempotent, `THREAD_PRIORITY_BACKGROUND`, exactly the `WordDictionary` shape. Kick off in service `onCreate` chained after the existing dictionary chain (`AddiyonKeyboardService.kt:1052-1054`): dictionaries gate typing, emoji don't; sequencing avoids parallel allocation. Also call it from `openEmojiPanel()` (no-op if started); panel shows a lightweight loading state until `isReady`. Parsed data (including the flat grid list with interleaved headers, built once) lives in the repository instance on the service — never in composition.

## Service changes (`AddiyonKeyboardService.kt`)

New observable state + methods, following the existing `mutableStateOf` + service-method pattern:

- `var showEmojiPanel by mutableStateOf(false); private set`
- `var emojiSearchQuery by mutableStateOf<String?>(null); private set` (null = browsing; "" = search open, empty)
- `fun openEmojiPanel()`: `leaveVoiceModeForKeyboardInput(); activeComposer.commit()` (an emoji must not land inside a composing region — Gboard commits too), `updateSuggestions()`, start repo load, set flag. Note: committing the composer empties `amharicBufferLatin`, so `BufferPreviewStrip` never coexists with the panel — height parity holds.
- `fun closeEmojiPanel()`: clear both states.
- `fun commitEmoji(emoji: String)`: `currentInputConnection?.commitText(emoji, 1)` + `recentEmojiStore.recordUse(emoji)`. (Not the existing `commitText()` — composer already flushed, and recents must record.)
- **Search key routing**: `KeyRow` already routes all keys through service methods, so reusing the real English key rows for search input needs only guards at the top of the four input methods:
  - `onCharacter` (line 766): if `emojiSearchQuery != null`, append shift-resolved char to the query, consume one-shot shift, return.
  - `onDelete` (line 874): if query non-null, `dropLast(1)` (no-op when empty), return.
  - `onSpace` (line 914): append `" "`, return.
  - `onEnter` (line 929): commit first search result if any, return.
- Panel reset: in `onStartInputView` (line 1067) alongside the composer resets, set `showEmojiPanel = false; emojiSearchQuery = null`. Guard `toggleLanguage`/`toggleNumberMode` to close the panel too (unreachable from panel UI, but cheap insurance).

## UI

### Toolbar entry (`ui/SuggestionBar.kt`)

Add `ToolbarIcon(Icons.Outlined.EmojiEmotions, "Emoji", onEmoji)` as the first icon in the empty-suggestions branch (line ~122); new `onEmoji: () -> Unit` param wired from `KeyboardScreen` to `service.openEmojiPanel()`. Not shown while suggestions are populated (matches current toolbar behavior). `material-icons-extended` is already a dependency.

### KeyboardScreen (`ui/KeyboardScreen.kt`)

Hoist the `BoxWithConstraints`/`computeKeyboardMetrics` computation so both branches share it, then branch:

```
if (service.showEmojiPanel) EmojiPanel(service, panelHeight)
else { SuggestionArea(...); BoxWithConstraints { KeyRows } }
```

`panelHeight` = 40.dp (SuggestionArea) + key-area height computed from the same metrics the key branch uses: `rows.size × (keyHeight + 6.dp) + 12.dp` (per-row 6.dp Spacer + BoxWithConstraints 6.dp vertical padding ×2). Computed, not hardcoded, so it tracks the number-row setting and narrow screens — zero window-height jump when toggling the panel.

### New composables — `app/src/main/java/com/addiyon/keyboard/ui/emoji/`

- **`EmojiPanel.kt`** — owns the fixed height and mode switch (loading / browse / search). Browse layout:
  - Top row 40.dp: "Search emoji" pill → search mode.
  - `LazyVerticalGrid` (weight 1f): **single grid with span-full category headers** (`GridItemSpan(maxLineSpan)`), not a pager — one lazy composition, continuous Gboard-style scroll, no per-page state churn. `GridCells.Adaptive(minSize = 40.dp)` (~9 columns).
  - Bottom row 40.dp: ABC key (→ `closeEmojiPanel()`) | category tabs (Recents clock + 9 group icons) | backspace (→ `onDelete()`).
- **`EmojiGrid.kt`** — `items(count, key = {…}, contentType = { header | emoji })` over the repository's prebuilt flat list; each cell a 40×40.dp `Box` + `Text(emoji, fontSize = 24.sp)` with `clip(CircleShape).combinedClickable` (tap = commit, long-press = skin-tone popup); stable hoisted callbacks (no per-item closures over changing state).
- **`EmojiCategoryTabs.kt`** — tab tap → `gridState.scrollToItem(headerIndex)` (jump, like Gboard); active-tab highlight via `derivedStateOf` over `firstVisibleItemIndex` against a precomputed header-index IntArray, so scrolling recomposes only the tab row.
- **`SkinTonePopup.kt`** — Compose `Popup` anchored above the cell: base + renderable variants in a `surface`-colored rounded row (small grid for 25-variant multi-person combos). Picking a tone commits it and remembers it per base emoji, so the grid cell shows the chosen tone thereafter (Gboard behavior).

Search mode layout (inside `EmojiPanel`): 40.dp query row (back arrow → browse, query rendered as plain `Text` with a cursor bar — **not** a `TextField`, the IME can't serve itself, clear button) + results row/short grid + the real **English** `KeyRow`s below, reused unchanged (keys route through the guarded service methods). Search: synchronous main-thread linear `contains` over precomputed haystacks (<1 ms for ~3.8k entries), name-prefix matches ranked first, capped at 60 results.

All colors from `MaterialTheme.colorScheme` (background=tray, surface=key, primary=accent) — palette + dark mode support for free.

### Performance guarantees

- No main-thread parsing: asset load + hasGlyph on the background thread (dictionary idiom).
- One lazy grid, keyed items, contentType split, immutable flat list built at load time.
- Recents displayed from a snapshot frozen per panel-open (`remember(showEmojiPanel)`), so commits neither shift the grid under the finger nor recompose it.
- Search stays on the main thread by design (measured trivial), avoiding hop latency.

## Persistence (`ui/settings/KeyboardPrefs.kt`)

- `KEY_RECENT_EMOJIS = "recent_emojis"` — `RecentEmojiStore`: LRU, cap 32, most-recent-first, stores the committed string (toned commit shows toned in recents), ``-joined; persisted with `apply()` on each commit. Takes load/save lambdas so tests need no Android.
- `KEY_EMOJI_SKIN_TONES = "emoji_skin_tones"` — `SkinToneStore`: `base:variant` pairs, ``-joined codec; loaded into a `mutableStateMapOf` on first panel open so only affected cells recompose.

## Implementation phases (each independently buildable)

1. **Data pipeline** — `tools/build_emoji_data.py`; generate `assets/emoji.dat`; `emoji/EmojiData.kt` (models, parser, search); parser + search unit tests. `./gradlew testDebugUnitTest`.
2. **Repository + service wiring** — `emoji/EmojiRepository.kt`; instantiate in `onCreate`, chain after `inactiveDictionary.loadAsync`. Verify via logcat timing.
3. **Panel shell (browse MVP)** — service state/methods (`commitEmoji` without recents yet); toolbar icon; `KeyboardScreen` branch + shared metrics; `EmojiPanel`/`EmojiGrid`/`EmojiCategoryTabs` with loading state, ABC, backspace.
4. **Recents** — `RecentEmojiStore` + tests; prefs key; record in `commitEmoji`; Recents tab with frozen snapshot (empty-state text when empty).
5. **Skin tones** — `SkinTonePopup` + `SkinToneStore` + tests; long-press wiring; remembered tone rendered in cell.
6. **Search** — `emojiSearchQuery` + the four service-method guards; search UI with reused English `KeyRow`s; enter commits first result.
7. **Polish** — re-verify `onStartInputView` reset; StrictMode clean; dark mode + several palettes; scroll-jank pass on a low-end emulator profile.

## New / modified files

**New**: `tools/build_emoji_data.py`, `app/src/main/assets/emoji.dat` (generated), `emoji/EmojiData.kt`, `emoji/EmojiRepository.kt`, `emoji/RecentEmojiStore.kt`, `emoji/SkinToneStore.kt`, `ui/emoji/EmojiPanel.kt`, `ui/emoji/EmojiGrid.kt`, `ui/emoji/EmojiCategoryTabs.kt`, `ui/emoji/SkinTonePopup.kt`, tests under `app/src/test/java/com/addiyon/keyboard/emoji/` (`EmojiDataTest`, `EmojiSearchTest`, `RecentEmojiStoreTest`, `SkinToneStoreTest`).

**Modified**: `AddiyonKeyboardService.kt` (state, open/close/commitEmoji, 4 method guards, onCreate chain, onStartInputView reset), `ui/KeyboardScreen.kt` (metrics hoist + panel branch), `ui/SuggestionBar.kt` (emoji ToolbarIcon + param), `ui/settings/KeyboardPrefs.kt` (2 keys).

## Verification

- Unit: `./gradlew testDebugUnitTest` (parser, search ranking, recents LRU/codec/corrupt-pref tolerance, skin-tone codec).
- Per CLAUDE.md after each phase: `./gradlew installDebug` then `./gradlew assembleDebug`.
- Manual on emulator: open panel from toolbar while mid-word (word commits first, no stranded composing text); scroll every category on an **API 24 emulator** (no tofu anywhere) and a recent API; tab jumps + active-tab tracking; height doesn't jump when opening/closing the panel (with and without number row enabled); long-press tone popup, chosen tone persists across sessions; recents populate and survive keyboard restart; search: type via keys, results update, enter commits first result, back returns to browse; rotate + dark-mode toggle mid-panel; switch fields (panel resets via onStartInputView).

## Risks

- CLDR annotation gaps → generator falls back to emoji-test.txt short names.
- `hasGlyph` false positives on some OEM fonts (glyph reported but renders generic) — rare, accepted; Gboard has the same exposure.
- Height-parity math: if the 6.dp padding arithmetic drifts, fall back to measuring the key branch once via `onSizeChanged` and reusing.
- First scroll through Flags may rasterize glyphs (one-time cost, matches Gboard).
