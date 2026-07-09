Addiyon Keyboard — Android adaptive launcher icon
==================================================

Design: 6a "Stencil Solid" — vermillion tile, አ knocked out in warm white,
segmented by two stencil cuts. All glyphs are real vector <path> data (no
<text>), so every file imports and compiles in Android Studio.

FILES
-----
ic_addiyon_background.xml   Adaptive BACKGROUND — solid vermillion fill.
ic_addiyon_foreground.xml   Adaptive FOREGROUND — the አ glyph + stencil cuts.
ic_launcher.xml             Adaptive descriptor tying background + foreground.
ic_addiyon_icon.xml         Single pre-rounded icon (non-adaptive use / in-app).

INSTALL (adaptive launcher icon, Android 8.0+)
----------------------------------------------
1. Copy into your module:
     res/drawable/ic_addiyon_background.xml
     res/drawable/ic_addiyon_foreground.xml
     res/mipmap-anydpi-v26/ic_launcher.xml   (rename from ic_launcher.xml)
2. In AndroidManifest.xml:  android:icon="@mipmap/ic_launcher"

HOW THE STENCIL WORKS
---------------------
The background is a flat vermillion. The foreground paints the white glyph, then
lays two vermillion bars across it at the same color as the background — so the
cuts read as gaps. Foreground and background parallax together, so the cuts stay
aligned with the glyph on tilt.

SINGLE DRAWABLE
---------------
For an in-app logo or a non-adaptive icon, use ic_addiyon_icon.xml
(@drawable/ic_addiyon_icon) — it includes the rounded tile.

FROM SVG INSTEAD
----------------
../Addiyon-icon.svg is the same composition as pure paths — import via
File > New > Vector Asset > Local file (SVG) if you prefer.

COLORS
------
Vermillion #EE4D2D · Warm white #FFF6F0 · Ink #17150F · Paper #F4F1EA
