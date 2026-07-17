# Gboard key-position parity

## Problem

Users coming from Gboard (i.e. everyone) miss keys on Addiyon Keyboard. The
requirement is that every key sit at *exactly* Gboard's position so muscle
memory transfers 1:1 — verified by measurement, not by eye.

## Research method (no guessing)

Both keyboards were measured on the same device (emulator `sdk_gphone64_arm64`,
1080×2400 px, 420 dpi → 1 dp = 2.625 px), in the same app (Messages compose
field), from raw screenshots. A Python script (`measure_keys.py`, scratchpad)
segments key faces from the canvas color and reports L/T/R/B/center/size for
every key. Gboard's IME window frame was confirmed via
`dumpsys window` (`type=ime frame=[0,1517][1080,2400]`).

### Measured Gboard geometry (portrait, default height, English)

Horizontal:
- 10 equal **columns of pitch 107 px** spanning the width minus a **5 px outer
  margin** per side (2 dp): `1080 = 5 + 10×107 + 5`.
- Visible key face is **93 px wide** → inset ~7 px (2.67 dp) per side inside
  its column cell. Gaps between faces are **not dead**: Gboard's touch cells
  tile the keyboard with no gaps.
- Home row (9 keys) is offset **half a cell**: A's face starts at
  `5 + 53.5 + 7 = 65.5 px`. Z–M sit exactly under S–K's columns.
- Shift, Backspace, ?123, Enter are **1.5 cells**; comma / emoji-slot /
  period are **1 cell**; **space is 4 cells** (columns 3.5–7.5).
  Bottom row = `1.5 | 1 | 1 | 4 | 1 | 1.5` = 10 cells exactly.

Vertical (window top at y=1517):
- Suggestion/toolbar strip: **122 px ≈ 46 dp**.
- 4 key rows at **row pitch 155 px = 59 dp** (tops measured 1654 / 1808 /
  1963 / 2118). Visible face height ~123.5 px → **6 dp inset** top+bottom.
- Below the last row: **~30 dp of keyboard surface** (Gboard's bottom strip)
  **plus the gesture-nav inset (63 px = 24 dp)** to the screen edge:
  `2400 − (30 dp + 24 dp) ≈ 2257 px` = bottom of the last row's cell.

Key centers (px) for reference: Q=58 W=165 E=272 R=379 T=486 Y=593 U=700
I=807 O=914 P=1021; A=111.5 … L=967 (half-cell offset); shift face
[12,157], backspace [921,1067]; space face [386,799].

### Measured Addiyon Keyboard (before)

- IME window top 1735 (**218 px shorter** than Gboard).
- Row pitch **132 px** (46 dp key + 6 dp spacer) vs Gboard 155 px.
- Rows sit **132–202 px (50–77 dp) lower** than Gboard's (Q-row center 1916
  vs 1715) — the dominant cause of missed keys.
- Bottom row widths wrong: `Modifier.weight` keys only split the *leftover*
  after fixed-width keys, yielding ?123/Enter ≈1.30 cells (should be 1.5),
  language toggle ≈1.04 (slot is 1), space ≈4.35 (should be 4) — so comma,
  period and space-bar edges are 15–34 px off Gboard columns.
- **Dead zones**: `KeyButton` applies `.padding(horizontal 3dp)` *before* the
  clickable, so the 6 dp gutters between faces and the 6 dp spacers between
  rows accept no touches at all. Gboard has zero dead area inside the grid.
- Horizontal letter centers were already close (±2–8 px) since the
  cell = width/10 model matches; outer margin 4 dp vs Gboard 2 dp.

## Design

Adopt Gboard's model literally: every key owns a full **cell** (touch target)
that tiles the keyboard; the visible **face** is a smaller Card inset inside
the cell. All cell sizes derive from two numbers: column pitch
`(availableWidth)/10` and row pitch `59 dp`.

Constants (all Gboard-measured):
- `KeyHorizontalInset = 8/3 dp` (7 px), `KeyVerticalInset = 6 dp` (15.5 px)
- Row pitch 59 dp portrait. Landscape (unmeasured on Gboard) keeps the old
  width-derived heuristic expressed as a cell: `(w×1.1).coerceIn(36,46)+6dp`.
- Outer keyboard margin 4 dp → **2 dp**; drop the rows' 6 dp vertical padding.
- Suggestion strip 40 dp → **46 dp**.
- New **30 dp bottom strip** below the last row + `navigationBarsPadding()`
  on the keyboard root (Gboard's bottom clearance; also stops rendering
  under the gesture bar).
- Bottom-row widths become fixed cell multiples `1.5|1|1|4|1|1.5`
  (`KeyWeights`: SPACE 5→4, LANGUAGE_TOGGLE 1.2→1; nothing flexes anymore).

## Affected files

| File | Change |
|---|---|
| `ui/KeyboardMetrics.kt` | `keyHeight` → `rowHeight` (59 dp portrait / legacy landscape); add `isPortrait` param; add `KeyHorizontalInset`/`KeyVerticalInset` constants; docs |
| `ui/KeyButton.kt` | Outer `Box` = full cell carrying testTag + all input modifiers; inner `Card` face inset by the two constants; press-preview balloon sized to face height |
| `ui/keys/KeyWeights.kt` | SPACE 4f, LANGUAGE_TOGGLE 1f; doc: Gboard ratios, all fixed |
| `ui/keys/KeyComposables.kt` | Space/Enter/NumberToggle/LanguageToggle take `width: Dp` and use `Modifier.width(width × weight)` instead of `Modifier.weight(...)` |
| `ui/KeyRow.kt` | Pass `metrics.keyWidth` to those four keys; `keyHeight`→`rowHeight`; row-wrapper doc |
| `ui/KeyboardScreen.kt` | Root `navigationBarsPadding()`; rows box `padding(horizontal 2dp)` only; remove per-row 6 dp `Spacer`; add 30 dp bottom spacer; `isPortrait` from `LocalConfiguration`; emoji-panel height mirror updated (`46dp + rowHeight×n + 30dp + 168dp`, width −4dp) |
| `ui/SuggestionBar.kt` | 3× `.height(40.dp)` → `46.dp` |
| `test .../ui/KeyboardMetricsTest.kt` | Rewrite for `rowHeight` semantics + new test: every bottom row sums to exactly 10 cells at Gboard ratios |

## Steps

1. Rewrite `KeyboardMetrics.kt` (cells + constants + `isPortrait`).
2. Restructure `KeyButton.kt` (cell Box outside, face Card inside; input on
   the cell so gutters are live; balloon anchors/sizes to the face).
3. Update `KeyWeights.kt`, then the four composables in `KeyComposables.kt`
   to fixed widths, then `KeyRow.kt` call sites.
4. Update `KeyboardScreen.kt` (margins, bottom strip, nav insets, emoji
   mirror) and `SuggestionBar.kt` heights.
5. Update `KeyboardMetricsTest.kt`; run
   `./gradlew testDebugUnitTest --tests "...KeyboardMetricsTest" --tests "...KeyboardLayoutInvariantTest"`, then the full `testDebugUnitTest`.
6. `./gradlew installDebug`; re-measure with `measure_keys.py` against the
   recorded Gboard numbers. Acceptance: window frame `[0,1517][1080,2400]`
   on the reference emulator; every face's L/T/R/B within ±2 px of Gboard
   (±2 px = our 1 dp Card elevation shadow in the segmentation).
7. Tap-test the former dead zones via `adb shell input tap` (gutter between
   two faces, gutter between two rows) and confirm the adjacent cell's
   letter is typed (uiautomator dump of the field text).
8. `./gradlew assembleDebug` for the timestamped APK in `/Users/dev/Shared`.

## Result of a prior dry run

This exact change set was already applied and measured once (then reverted to
be re-done through this plan): the rebuilt keyboard's IME window came out
**identical** (`[0,1517][1080,2400]`) and all four row tops within 0–1 px
(1654/1809/1964/2119 vs 1654/1808/1963/2118), all key centers within
≤1.5 px of Gboard — i.e. the design above is confirmed to hit the target.

## Risks

- **Landscape** row pitch is a heuristic (Gboard landscape wasn't measured);
  portrait is the complaint and is exact. Follow-up if landscape matters.
- **Taller keyboard** (+83 px window): apps get less viewport; this *is*
  Gboard's size, so it matches user expectation.
- The 30 dp bottom strip is currently empty surface; Gboard uses that band
  for its own bottom bar. Cosmetic only — could later host controls.
- `navigationBarsPadding()` behavior on 3-button-nav devices: system already
  keeps IME windows above the opaque bar, and the inset then reports 0 there,
  so no double padding — but worth a glance on a 3-button device at some point.
- Emoji panel height mirror is arithmetic duplication; if the key branch
  changes again the mirror must follow (kept adjacent comments in sync).

## Open questions (non-blocking)

- Should the empty 30 dp bottom strip eventually host Gboard-like controls
  (hide-keyboard chevron, etc.)?
- Amharic-mode bottom row keeps the language toggle in Gboard's emoji slot
  (1 cell) — accepted as the product's intended mapping.
