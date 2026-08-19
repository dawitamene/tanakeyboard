# Addiyon design system v1

This document is the normative source of truth for Addiyon's interface. It is
written for people, LLMs, and coding agents. A change is not design-system
compliant because it looks close in one screenshot; it is compliant when it
uses the roles, tokens, components, behavior, and review gate documented here.

## Authority and order of precedence

Use these sources in this order:

1. Product safety, Android platform behavior, and accessibility requirements.
2. This document and the executable contract in
   `apps/textrevamp/src/test/java/com/addiyon/keyboard/ui/design/DesignSystemContractTest.kt`.
3. The implementation in `keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/`,
   `keyboard/ui/src/main/java/com/addiyon/keyboard/ui/theme/Theme.kt`, and the shared UI components.
4. Screen-specific product requirements and existing call-site behavior.
5. A visual preference or an individual request.

When two sources disagree, preserve user safety and platform behavior, then
update this document, the implementation, and the contract test together. Do
not silently add a local visual convention. Read this file before any UI work,
including work proposed by an LLM.

## Product principles

- Make typing and setup feel calm, direct, and dependable.
- Use one clear hierarchy: page title, supporting context, primary action,
  then secondary actions.
- Reduce visual noise. Surfaces group related information; color communicates
  meaning or interaction state, not decoration.
- Keep behavior predictable across app screens and the keyboard. A control
  must either work, be disabled with a reason, or not be rendered.
- Design Addiyon for Amharic, English, and Afaan Oromo; design TextRevamp for English.
- Preserve user choice in the IME. The app brand must not override a user's
  keyboard palette.

## Two distinct surfaces

Addiyon has two related but deliberately separate surfaces:

### Branded app UI

Settings, onboarding, home, manual, feedback, account, and other Activity
screens use `AddiyonBrandTheme`. This surface uses the fixed Addiyon brand
palette, paper background treatment, Public Sans typography for every text
including headers, and the Public Sans brand face where a logo treatment calls
for it. Brand teal is reserved for primary actions and meaningful emphasis.
Text uses the grayish-black `ink` roles (`#23272F` light / `#E5E7EB` dark) instead of pure black, and icons use the gray `icon` roles (`#5E6B78` light / `#9CA3AF` dark) unless an explicit semantic accent is documented.

### User-themed IME

The input method, suggestion strip, emoji panel, and keyboard-height preview use
`CustomKeyboardTheme`. `KeyboardPalette` remains user-selectable and owns the
keyboard tray, key, special-key, accent, and background effect colors. Do not
replace palette roles with the fixed app brand. AI tone controls are the narrow
exception: their semantic icon accents and shared neon gradient come from
`MaterialTheme.addiyonColors` so the AI actions keep a stable identity instead
of inheriting the keyboard accent. The IME is a fixed-height,
latency-sensitive surface: preserve its measured height and never introduce a
scrolling full-panel container to solve overflow.

## Brand and logo primitives

The current logo source is the vector `keyboard.svg` at the repository root;
`keyboard.png` is its transparent raster fallback. It is a teal Ethiopic mark
intended for a white icon background. Generated Android and web assets keep the
visible mark near 65% of the white tile and compensate
the adaptive foreground for Android's mask zoom; do not crop, stretch, recolor,
or redraw it with arbitrary text or emoji. The files in
`new_design/exports`, `new_design/exports-1`, and the root `logo_*.svg` set are
retained as legacy artwork, not as the current logo source. `PublicSansFamily` is the single product UI and brand-display face
(legacy `PlaypenSansBrand` and `PoppinsFamily` aliases remain for compatibility and delegate to Public Sans). The canonical brand primary is
`#009099`; it is declared once as `@color/addiyon_brand_primary`, loaded by
`rememberAddiyonBrand`, and used to derive the branded light and dark roles.
Brand color names describe intent, not permission to use a hex value in a
screen.

## Semantic color

Use `MaterialTheme.colorScheme` roles for all app and keyboard UI colors:

- `primary` / `onPrimary`: the main action and its content.
- `primaryContainer` / `onPrimaryContainer`: selected or emphasized regions.
- `secondary` and `tertiary`: supporting accents only.
- `surface` / `onSurface`: cards, grouped surfaces, and default content.
- `surfaceVariant` / `onSurfaceVariant`: muted controls, supporting text, and
  secondary containers.
- `outline` / `outlineVariant`: borders and dividers.
- `background` / `onBackground`: page or keyboard backdrop.

On branded app screens, content on a primary-colored background is always
white. The light app backdrop is a subtle neutral gray, while `surface` stays
white for cards and `surfaceVariant` provides the light neutral fill used by
muted controls.

Status meaning uses the extended `MaterialTheme.addiyonColors` roles:
`success`, `onSuccess`, `successContainer`, and `onSuccessContainer`, plus `icon` / `iconMuted` for the default gray icon tint. A
success color is not a general green accent. Error, warning, and info states
must use the corresponding Material roles or a future documented extension.
`brandPrimary` and `onBrandPrimary` expose the fixed Addiyon primary pair for
explicitly documented brand-identity moments such as AI empty-state icons.
`aiToneIcons` exposes distinct light/dark accent roles for the Humanize,
Professional, Casual, Formal, Friendly, Fix grammar, Shorten, and Summarize
icons. These accents identify tone categories and must not be substituted with
error, success, or other status roles.
`aiToneGlow` exposes the fixed magenta-to-blue gradient used by the selected AI
tone's outline and outward halo. Its two endpoints are sampled from the approved
neon reference and remain stable across light and dark keyboard appearances.
`aiCustomToneColors` exposes the predefined eight-color palette (teal, indigo,
orange, purple, green, rose, blue, amber) that user-created tones pick from for
their chip identity; each color id has a distinct light and dark value.
`resultSurface` and `onResultSurface` provide a flat white reading canvas and
fixed ink text; this pair intentionally remains light in
both appearances so generated versions have one quiet, shadow-free treatment.

Brand and semantic roles have light and dark values. Use the theme-provided
role; do not branch on `isSystemInDarkTheme()` in a child composable to select
a raw color. `AddiyonBrandTheme` provides the fixed brand scheme and semantic
colors. `CustomKeyboardTheme` provides the selected keyboard scheme while
keeping the same semantic contract.

The canonical brand hex belongs only in `res/values/colors.xml`; Compose loads
that resource and derives its roles in `ui/design/AddiyonDesignTokens.kt`.
Other raw hex values and named raw colors belong only in `Theme.kt` and the
design-token implementation, where they are named and tested. The
keyboard palette declarations are an intentional exception because they are
the user-facing palette data. A vector path placeholder tint (for example the
black path inside `ui/icons/ShiftIcon.kt`) is also allowed because `Icon()`
applies the actual theme tint. `Color.Transparent` or an unset color is
allowed when transparency is the behavior being requested, not as a way to
avoid choosing a semantic role.

## Typography

`AddiyonTypography` is the single Public-Sans-based `Typography` instance and is
installed by both `AddiyonBrandTheme` and `CustomKeyboardTheme`. Every text,
including headers, uses Public Sans via `PublicSansFamily` at the root; do not
introduce a second typeface. Use Material text styles (`titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`,
`labelLarge`, and so on) instead of inventing a one-off `sp` style. Legacy `PlaypenSansBrand` and `PoppinsFamily` remains as aliases to Public Sans for compatibility. Do not stretch, outline, or
fake bold text to compensate for a missing font weight.

User-facing TextRevamp text belongs in its English-only `ui/i18n/AppStrings.kt`.
Addiyon app-shell text belongs in Android string resources and must cover its
Amharic, English, and Afaan Oromo product scope. New or newly touched app
screens must not introduce hard-coded copy in a composable. Technical
identifiers, test tags, and keyboard key labels are not user-facing prose.

## Tokens

Use the public tokens in `ui/design/AddiyonDesignTokens.kt`:

| Token group | Values | Typical use |
| --- | --- | --- |
| `AddiyonSpacing` | 4, 8, 12, 16, 20, 24, 32 dp | gaps, insets, and page rhythm |
| `AddiyonRadii` | 8, 12, 16, 20, 28 dp, pill | controls, cards, groups, chips |
| `AddiyonSizes` | 10 loading dot; 40 compact, 44 keyboard action, 48 minimum touch, 56 form control, 64 app header; 16/24/32/44 icon sizes | controls, bars, icons, loading indicators |
| `AddiyonBorders` | 1 dp tone glow | neon AI tone outlines |
| `AddiyonElevation` | none, low, raised, overlay | surfaces and overlays |
| `AddiyonMotion` | 150, 250, 400, 500 ms | feedback, standard transitions, emphasis |

Prefer a token over a new literal. The current contract intentionally checks
raw colors first; dp/sp migration is incremental, so a new value must still be
justified and added to the token set when it will be reused.

## Layout and responsiveness

App pages use `AddiyonScreenColumn` for a 24 dp horizontal gutter and standard
vertical rhythm. Use `AddiyonContentSection` for every white, rounded grouping,
including settings groups, guide cards, language selection,
feedback destinations, and other section-like app content. Compatibility
aliases may remain while old call sites migrate, but they must delegate to this
single renderer. AI authentication and account screens intentionally use a flat
layout without content-section containers. Respect system bars, font scaling, landscape widths, and
content that grows when translated into Amharic. Prefer constraint-aware layout
(`BoxWithConstraints`, weights, and measured rows) to screen-width guesses. Do
not use fixed heights for app content unless the component contract explicitly
requires one.

Multi-product app navigation uses the shared renderers in `:features:app-shell`.
`KeyboardOnboardingScreen`, `KeyboardProductHeader`, and
`KeyboardSettingsMenuScreen` define the common onboarding and settings shell.
`KeyboardPageTopBar`, `KeyboardThemePickerScreen`, `KeyboardPreferencesScreen`,
`KeyboardTestScreen`, `KeyboardAboutScreen`, and `KeyboardTextGuideScreen`
define its reusable destinations. Products supply localized copy, preference
storage, functional destinations, and capability-specific rows without cloning
those layouts into application modules.

Both products also use the shared `Theme.KeyboardAppShell` content window and
`Theme.KeyboardAppShell.Splash` launch window from `:features:app-shell`.
Launcher and IME test-host activities keep `adjustResize`; product manifests
must not redefine these common window styles.

The signed-in profile icon is a direct navigation control: it opens
the AI account screen without an intermediate logout menu.

IME surfaces are the exception: keyboard rows, suggestion strips, and the emoji
panel have a measured-height contract. Keep keyboard rows and emoji-panel height
stable during state changes. TextRevamp AI never replaces or enlarges the
keyboard. Tone requests, loading, results, and errors remain within the normal
keyboard shell.

While any tone request loads, the persistent tone row remains at full opacity and
the selected tone's outline and halo animate. A fixed-height AI result surface
immediately replaces both the standard 40 dp suggestion row and every keyboard
key row while the tone row remains visible above it. The replacement occupies
exactly the same measured height as those removed regions, so the IME does not
resize. Its three equally weighted result cards render pulsing three-line
skeletons until the generated versions arrive. The active dismiss control is
available during loading. The completed versions remain equally weighted and
vertically stacked with no duplicate tone label or result header, and their text
wraps without an ellipsis; an individual card scrolls vertically when its full
text cannot fit within the fixed-height surface. Choosing another tone from an
existing result keeps this result surface and its active dismiss control mounted
while the skeletons return.
The dismiss control appears at the leading edge of the persistent tone row while
a result, result skeleton, or error is visible. It is layered above the full-width
tone scroller as a dense, high-opacity frosted white `resultSurface` liquid-glass
control with a vertical sheen and soft shadow but no border. An initial 8 dp gap separates
it from the first tone, but the scroll viewport
continues beneath the control: the selected tone's glow can draw into that gap, and
tones remain only faintly visible beneath the frosted control while scrolling. The result root does not scroll.
Tapping a version replaces the captured text using the cursor-relative editor
contract. Typing or moving the caret while a request is loading dismisses stale
AI state. The regular suggestion row and keys return after a replacement or
dismissal. The AI toolbar icon opens authentication when signed out and the
account dashboard when signed in.

Tone icons use distinct semantic theme accents to make the actions easier to
scan. Every tone keeps the standard chip background. Unselected tones are
borderless and have no halo. Only the selected tone uses the static
magenta-to-blue 1 dp gradient outline with a soft matching halo around the full
pill. The border runs from magenta on the left through violet to blue on the
right, matching the approved neon reference. While an AI request is loading,
the selected tone's border becomes a continuous color sweep and its outward
halo sweeps and pulses; the treatment returns to the static gradient when the
request finishes. The selected semantics and glow treatment identify selection
without changing the chip fill.

TextRevamp exposes the horizontally scrollable tone-action row immediately above
the standard suggestion strip. The row is 56 dp tall so its 40 dp tone chips
retain their full height inside 8 dp vertical and horizontal outer insets.
The chips use 8 dp horizontal content padding and 8 dp horizontal gaps. Tone
icons are 16 dp, and labels use the full semantic `labelSmall` size. The row uses
`CustomKeyboardTheme` semantic surface/content roles and reuses
the same pill controls and semantic tone icons throughout the AI flow. Every tone stays
visible but is disabled with reduced emphasis until the editor contains text;
private fields also keep the actions disabled. Fix grammar is first and Casual
is second. Choosing an enabled tone starts that transformation without hiding
the keys. During a request, the selected tone's animated magenta-to-blue halo
uses a restrained but still visible pulse so the loading state remains obvious
at keyboard scale. The row is never included in Addiyon.

The tone row ends with an always-enabled "Add custom" chip (standard chip fill,
primary icon, no selection halo). Tapping it opens the custom-instructions
destination on the AI account screen, where the user gives a tone a short
title (for example "Romantic") and a free-form instruction (for example "Make
it sound more romantic") and saves it. Saved custom tones render as standard
tone chips after the built-in tones and before the add chip, labelled by their
short title; selecting one behaves like any tone — the instruction is sent
verbatim as the rewrite prompt while the tone field stays on a valid built-in
value so the server contract is unchanged. The custom-instructions screen is a
flat branded AI screen (no content-section containers) using the shared input
fields and pill primary action. Its form labels the Title and Instruction
fields, offers a four-row icon grid (a curated thirty-two-icon set) shown in a
muted gray until an icon is selected; selecting an icon reveals a floating
color popup anchored to that icon (mirroring the keyboard's skin-tone chooser)
with the eight-color palette, and the chosen color tints the selected icon. A
new tone combines a short title, a free-form instruction, and a chosen icon
and color identity. Editing swaps the primary action row to Cancel and Save
changes as equal-width pill buttons with Cancel on the left. Saved tones are
listed as white cards with edit and remove controls (the remove control
matches the personal dictionary's delete button), and a new-tone form, so
users can add, change, and delete them. Saving navigates back so the full tone
list is visible again; custom tone changes apply to the keyboard immediately
through preference observation.

TextRevamp's settings menu exposes the AI workspace as "Usage" and "Instructions"
entries following the primary keyboard preferences block (Themes, Preferences,
Test keyboard, Personal dictionary). Neither entry carries a badge.

Phrase completion is currently suspended: TextRevamp does not create its
completion controller, render the phrase-completion bar, or expose its account
dashboard switch. The dormant completion implementation may remain in source
for future iteration, but it must not be reachable from the product runtime.

## Component patterns

Keep the shared layer small and composable:

- `AddiyonContentSection`: the single white, 28 dp rounded renderer for all
  section-like branded app content.
- `AddiyonInputField`: the single borderless, muted-gray, rounded input used by
  ordinary branded app email, password, search, and freeform text fields.
- `AddiyonDropdownMenu`: the white rounded popup used by branded app menus.
- `AddiyonScreenColumn`: app-page column with the standard 24 dp gutter.

Use Material 3 components for buttons, fields, dialogs, lists, and navigation.
Set content colors through `ButtonDefaults`, `TextFieldDefaults`, or theme
roles. A new component needs a repeated product pattern, a stable API, and a
test or contract entry; do not create a wrapper for one screen's cosmetic
preference.

Buttons and text fields presented together in an app form use
`AddiyonSizes.formControl` as their minimum height and a shared radius. The
minimum-height contract preserves alignment at normal font sizes while allowing
controls to grow when accessibility text scaling requires more room.
Authentication form buttons and single-line fields use the pill radius.
`AddiyonInputField` uses a medium-light `surfaceVariant` fill without an
outline; multiline fields use the shared section radius. The authentication
primary action has an additional 8 dp separation from the last input. The
AI account sign-out action remains an outlined button on the themed page
background and uses the small radius for a sharper silhouette. The
special six-cell OTP entry and fields inside the user-themed IME remain custom
because they have different behavior and palette ownership.

## States and interaction

Every data-driven screen must define loading, empty, error, and success/result
states. Authentication/private screens must clearly distinguish signed-out,
link-sent, signed-in, and signed-out/error outcomes without exposing private
data in a public or anonymous state. Loading indicators explain what is
happening; empty states explain what the user can do next; errors offer a
recovery action or a clear reason. Results show the selected/current result
before secondary alternatives.

Controls need a real callback, a meaningful disabled state, or a documented
platform handoff. Do not render a button that only changes appearance. Keep
copy and action order stable while asynchronous work is in flight.

## Accessibility

Use at least the 48 dp minimum touch target for interactive controls. The 40 dp
compact and 44 dp keyboard-action sizes are allowed only where the surrounding
IME control contract provides the effective target. Every meaningful icon has
an accessible label; decorative icons use a null description. Preserve visible
focus/pressed/selected states, sufficient contrast, and state announcements
for dynamic results. Do not rely on color alone to communicate status.

Respect system font scaling and avoid clipping or truncating Amharic glyphs.
Keep semantics on the actual clickable node, not only on a decorative parent.

## Localization and Amharic

Use `LocalAppStrings` and the language preference for app copy. Keep
placeholders identical between English and Amharic translations. Do not split
Ethiopic syllables, insert Latin-only assumptions into a shared label, or use a
text width that only fits English. Test long strings and mixed Latin/Ge'ez
examples. Keyboard key labels are data-driven by the layout and are separate
from app prose.

## Content and copy

Write concise, concrete labels with a verb for actions (for example, “Open
settings” or “Copy”). Titles state the user's task; supporting text explains
why or what happens next. Use sentence case, avoid unexplained jargon, and do
not promise a result before an operation succeeds. Never put secrets, tokens,
or private text into logs, screenshots, analytics, or empty/error copy.

## Forbidden patterns and narrow exceptions

The following are prohibited in newly touched production UI:

- raw `Color(0x...)` literals or direct `Color.White`, `Color.Black`,
  `Color.Red`, or `Color.Gray` in a screen;
- arbitrary one-off visual values when a token or Material role applies;
- hard-coded user-facing strings on newly touched app screens;
- nonfunctional controls or controls with no recovery/error behavior;
- root/full-panel scrolling in fixed-height IME surfaces;
- a second typography system, a second app-brand palette, or a private copy of
  a shared component.

Narrowly justified exceptions are allowed only in the implementation files:
theme/design token color declarations, user-selectable keyboard palette
declarations, transparent/unset colors whose behavior is intentional, and
vector path placeholder tints that are overridden by a themed icon. Document a
new exception in this file and in the contract test before merging.

## Implementation examples

```kotlin
AddiyonScreenColumn {
    AddiyonContentSection(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AddiyonSpacing.lg)
    ) {
        Text(text = strings.title, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onConfirm) {
            Text(text = strings.confirm)
        }
    }
}
```

```kotlin
Icon(
    imageVector = Icons.Default.CheckCircle,
    contentDescription = strings.completed,
    tint = MaterialTheme.addiyonColors.success
)
```

For keyboard UI, read `MaterialTheme.colorScheme.surface`,
`onSurface`, `primary`, and the selected palette's roles. Do not import the
fixed app teal into an IME key or suggestion chip.

## Change and governance process

Before changing a token, role, component, or theme behavior, identify every
consumer and decide whether the change is additive, a migration, or a breaking
visual contract. Update this document, implementation, and contract tests in
the same change. Keep compatibility aliases when a public token is renamed.
Reviewers may reject a screen-only workaround when a shared token/component is
the correct fix. A design-system maintainer owns the final decision, but
accessibility and platform requirements always win.

## Strict pre-merge checklist

- [ ] Read this file before touching UI code.
- [ ] Chose the correct surface: `AddiyonBrandTheme` or `CustomKeyboardTheme`.
- [ ] Used semantic Material roles and existing tokens/components.
- [ ] Added new user-facing copy to English and Amharic localization.
- [ ] Kept controls functional and defined loading/empty/error/result states.
- [ ] Verified 48 dp targets, labels, contrast, font scaling, and Amharic layout.
- [ ] Preserved fixed-height IME behavior and did not add root/full-panel scrolling.
- [ ] Updated this document and the contract test for any system change.
- [ ] Ran `/Users/dev/code/addiyon-keyboard/gradlew testDebugUnitTest --tests "com.addiyon.keyboard.ui.design.DesignSystemContractTest"`.
- [ ] Ran `/Users/dev/code/addiyon-keyboard/gradlew compileDebugKotlin`.
- [ ] Ran `git diff --check`.
