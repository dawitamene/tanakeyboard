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
palette, paper background treatment, Poppins typography, and the Playpen Sans
brand face where a logo treatment calls for it. Brand teal is reserved
for primary actions and meaningful emphasis.

### User-themed IME

The input method, suggestion strip, emoji panel, and keyboard-height preview use
`CustomKeyboardTheme`. `KeyboardPalette` remains user-selectable and owns the
keyboard tray, key, special-key, accent, and background effect colors. Do not
replace palette roles with the fixed app brand. The selected AI tone is the
narrow exception: it uses `MaterialTheme.addiyonColors.brandPrimary` so the AI
action has one stable Addiyon identity instead of inheriting a blue keyboard
accent. The IME is a fixed-height,
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
retained as legacy artwork, not as the current logo source. `PlaypenSansBrand`
is the brand-display face;
`PoppinsFamily` is the product UI face. The canonical brand primary is
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
`success`, `onSuccess`, `successContainer`, and `onSuccessContainer`. A
success color is not a general green accent. Error, warning, and info states
must use the corresponding Material roles or a future documented extension.
`brandPrimary` and `onBrandPrimary` expose the fixed Addiyon primary pair for
the selected AI tone and other explicitly documented brand-identity moments.
`aiToneIcons` exposes distinct light/dark accent roles for the Humanize,
Professional, Casual, Formal, Friendly, Fix grammar, Shorten, and Summarize
icons. These accents identify tone categories and must not be substituted with
error, success, or other status roles.
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

`AddiyonTypography` is the single Poppins-based `Typography` instance and is
installed by both `AddiyonBrandTheme` and `CustomKeyboardTheme`. Use Material
text styles (`titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`,
`labelLarge`, and so on) instead of inventing a one-off `sp` style. Keep
`PlaypenSansBrand` for logo/display moments only. Do not stretch, outline, or
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
| `AddiyonSizes` | 40 compact, 44 keyboard action, 48 minimum touch, 56 form control, 64 app header; 16/24/32/44 icon sizes | controls, bars, icons |
| `AddiyonBorders` | 3 dp selected tone | emphasized selection outlines |
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

IME surfaces are the exception: keyboard rows, suggestion strips, and AI/emoji
panels have a measured-height contract. Keep keyboard rows and emoji-panel
height stable during state changes. The TextRevamp AI panel is intentionally
40% taller than the configured keyboard height while it is open. The root panel
must not scroll. A bounded inner content region may scroll when persistent
header and primary actions remain visible.

The canonical AI assistant panel keeps a 48 dp, truly circular version of the
suggestion bar's chevron-left control with a small arrow and fixed white surface.
It does not show a toolbar title or usage control. Compact pill-shaped
tones with leading semantic icons sit directly beside the back action in the
same persistent row. The toolbar uses compact 8 dp top and 12 dp bottom insets,
gives the state and result region the bounded scroll, omits a duplicate input
preview, and stacks the three generated versions vertically. It does not show
the AI mark, a close icon, a separate “Tone” label, strength selectors, or pinned
global actions. Generated versions use rounded `resultSurface` surfaces without
card elevation and provide compact per-result Copy and Replace controls. Loading
uses three rounded result-shaped placeholders with an animated directional shimmer
rather than a spinner or status sentence.
Tone icons use distinct semantic theme accents to make the actions easier to
scan. Selection does not change the chip background. Instead, the selected tone
uses a 3 dp border derived entirely from its icon color. The border is static
while idle and becomes an animated sweep shine only while the AI request is
loading; varying alpha creates the moving shine while the text label keeps
selection independent of color alone.

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
