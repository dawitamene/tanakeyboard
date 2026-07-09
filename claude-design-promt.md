# Design Brief: Tana Keyboard Landing Website

> **How to use this file:** Paste this entire document to Claude as a build request. Alongside it,
> I will provide four assets: `screenshot1`, `screenshot2`, `screenshot3` (app screenshots) and
> `logo.xml` (the app's Android vector logo). Build a complete, production-ready site from this brief.

---

## 1. Your role & goal

You are a senior product designer and front-end engineer. **Design and build a modern, polished,
animated marketing landing website for "Tana Keyboard."**

**Requirements for the build:**
- **Framework:** Next.js (App Router) + TypeScript + Tailwind CSS.
- **Animation library:** Framer Motion.
- **Quality bar:** responsive and mobile-first, accessible (semantic HTML, alt text, keyboard-navigable,
  good color contrast), fast (great Lighthouse scores), and deployable to Vercel with zero extra config.
- **Feel:** clean, confident, "Apple-meets-Google-Play" product marketing — lots of whitespace, smooth
  motion, crisp typography. Not busy, not templated. It should feel like a real shipped product page.

---

## 2. About the app (context)

**Tana Keyboard** is an Android keyboard (IME) for typing **Amharic** in the **Ge'ez / Fidel script**.
You type in Latin letters and it transliterates them live into Fidel (e.g. `selam` → `ሰላም`), and it also
includes a standard English layout. It's built natively for Android in Jetpack Compose.

The app is named after **Lake Tana** — the largest lake in Ethiopia and the source of the Blue Nile.
Lean into a calm **water / lake / blue-gradient** motif throughout the design.

- App name: **Tana Keyboard**
- Android package: `com.addiyon.tanakeyboard`
- Audience: Amharic speakers and the Ethiopian diaspora who want fast, accurate Amharic typing on any app.

---

## 3. Features to showcase

Give these three top billing (the user specifically wants them highlighted):

1. **Complete Amharic support** — every Fidel letter and all Amharic special characters. Nothing missing;
   the full Ge'ez syllabary plus punctuation (፣ ። etc.) and labialized forms.
2. **All Ge'ez numbers** — full support for Ethiopic numerals (፩ ፪ ፫ … ፲ ፳ … ፻ ፼).
3. **Robust autocomplete** — frequency-ranked word suggestions from large built-in dictionaries, and it's
   smart about **ambiguous cases**: it reconciles capitalization intelligently (e.g. `england` → **England**,
   `us` → the pronoun vs. **US**, `march` vs. **March**), keeping proper nouns capitalized while respecting
   what you actually typed.

Supporting features worth weaving in as secondary highlights:

- **Live Latin → Fidel transliteration** — type the sound, get the script; the whole word is re-read on
  every keystroke so it stays correct as you go (underlined "composing" preview until you commit).
- **Huge offline dictionaries** — ~182,000 Amharic words and ~250,000 English words, all on-device.
- **English layout included** — switch between Amharic and a standard QWERTY English keyboard.
- **Beautiful themes** — multiple keyboard color themes, with automatic light/dark support.
- **Works everywhere** — it's a system keyboard, so it works in every app on the phone.
- **Private by design** — your typing is processed on your device (see the Privacy section).

---

## 4. Brand system

**Color palette** (use these exact values — they match the app's real theme and icon):

| Role | Name | Hex |
|------|------|-----|
| Primary | Deep Tana | `#0E3A63` |
| Secondary | Lake Blue | `#1B6FB3` |
| Accent | Shore Aqua | `#38A9D4` |
| Highlight | Sand | `#E9B872` |
| Light surface | Mist | `#EEF3F7` |
| Text / dark surface | Ink | `#0B1722` |

- **Signature gradient:** a diagonal Deep Tana → Lake Blue → Shore Aqua (top-left to bottom-right). This is
  the app icon's gradient — reuse it for the hero background, buttons, and accents so the site and app feel
  like one product. Use **Sand** sparingly as a warm highlight/CTA accent against the blues.
- **Support both light and dark modes.** Light = Mist surfaces on white; dark = Ink surfaces.

**Typography:**
- A clean, modern geometric/neo-grotesque sans for all UI/body text (e.g. Inter, or Google's Product Sans-like
  feel). Big, tight hero headings; generous body line-height.
- **Ge'ez sample text must render correctly:** load an Ethiopic-capable webfont (e.g. **Noto Sans Ethiopic**
  / **Noto Serif Ethiopic**) for any Amharic/Fidel characters shown on the page. Show real Amharic words in the
  hero and feature sections — e.g. ሰላም, አማርኛ, ኢትዮጵያ, and Ge'ez numerals — to make the script tangible.

---

## 5. Site structure

Build these routes:

### Home (`/`)
- **Sticky navbar:** logo + wordmark on the left; links (Features, Privacy) and a small "Get it on Google
  Play" button on the right. Transparent over the hero, solid on scroll.
- **Hero:** bold headline (e.g. *"Type Amharic the way you speak it."*), a supporting subhead, and the primary
  **"Get it on Google Play"** CTA (see §8 — marked *Coming soon*). Include the animated typing/transliteration
  motif (see §7) and a floating phone showing a screenshot.
- **Feature sections:** the three headline features (§3) as alternating left/right blocks pairing copy with a
  screenshot or a small live demo/animation, then a grid of the secondary features.
- **Screenshot showcase:** creative presentation of the three screenshots (see §6).
- **"How it works" / transliteration explainer:** a short, delightful visual of `selam → ሰላም`.
- **Final CTA band** + **footer** (logo, links to Features/Privacy, copyright, contact email).

### Features (`/features`)
- A deeper dive into everything in §3 & the secondary list, each with an icon, short explanation, and where
  helpful a small animation or Fidel example. (You may instead make this a rich anchored section on Home if the
  content is thin — but a dedicated route is preferred.)

### Privacy Policy (`/privacy`) — **required**
This page exists to satisfy **Google Play's privacy-policy-URL and Data Safety requirements**, so it must be a
real, readable policy at a stable public URL. Write it to accurately reflect the app's behavior:

- **On-device processing:** transliteration, autocomplete, and dictionary lookups all run locally on the
  device. The words and text you type are **not transmitted, sold, or shared**, and the app does not send your
  keystrokes or typed content to any server.
- **Analytics:** the app collects **basic anonymous analytics** (e.g. crash reports and aggregate usage stats)
  solely to fix bugs and improve the product. This data is not tied to your identity and is never used to
  reconstruct what you typed.
- **No selling of data**, no ad targeting.
- **Permissions:** explain (in plain language) why a keyboard requests the permissions it does.
- **Children / data retention / your rights:** brief standard sections.
- **Contact:** `dawitnewsletter@gmail.com`.
- **Effective date** at the top.
- Add a small, clearly-marked note (an HTML comment or a subtle callout) reminding the developer to review and
  adjust specifics before publishing, since exact data practices are the developer's responsibility.

Keep the privacy page clean and easy to read — simple typographic layout, table of contents/anchor links.

---

## 6. Screenshots — integrate them creatively (explicit requirement)

I will add three app screenshots to the project named **`screenshot1`**, **`screenshot2`**, and
**`screenshot3`**. Place them under `public/` and reference them by those names (add the correct image
extension when you wire them up).

**Do not just drop them in a flat grid.** Present them with craft, for example:
- Inside realistic **floating Android phone frames/mockups** with soft shadows over the blue gradient.
- With **scroll-triggered reveals and gentle parallax** as the user scrolls the showcase.
- A subtle **3D tilt on hover** (mouse-following perspective).
- Optionally a **carousel/stepper** where each screenshot is tied to one of the headline features, so scrolling
  or clicking advances both the caption and the image together.

Make the screenshots the visual centerpiece — they're the proof the product is real and beautiful.

---

## 7. Animations (explicit requirement)

The site should feel alive but tasteful. Use Framer Motion and CSS. Include:

- **Hero entrance:** headline, subhead, and CTA staggering in on load.
- **Live transliteration animation:** a looping hero centerpiece where Latin letters are "typed" and morph into
  their Fidel equivalents (e.g. `s-e-l-a-m` → `ሰላም`, `a-m-a-r-i-g-n-a` → `አማርኛ`), mimicking the keyboard's live
  composing behavior. Show a suggestion "chip" appearing, nodding to the autocomplete feature.
- **Scroll-triggered reveals:** sections fade/slide in as they enter the viewport.
- **Animated gradient background:** a slow, subtle moving Lake Tana water gradient (Deep Tana → Lake Blue →
  Shore Aqua) behind the hero and CTA band.
- **Micro-interactions:** button hover/press states, phone-mockup parallax/tilt (§6), animated feature icons.
- **Respect `prefers-reduced-motion`:** disable or greatly reduce non-essential motion for users who ask for it.

Keep everything performant (transform/opacity-based, no janky layout thrash).

---

## 8. CTA & links

- **Primary CTA everywhere: "Get it on Google Play."** The app is **not published yet**, so present the button
  in a **"Coming soon"** state (e.g. badge with a "Coming soon" tag, and/or a tooltip). Point the link at the
  expected listing URL so it just works once the app is live:
  `https://play.google.com/store/apps/details?id=com.addiyon.tanakeyboard`
  Use the official Google Play badge styling/shape.
- Secondary links: Features and Privacy in the nav and footer.

---

## 9. Logo

I will provide **`logo.xml`** — the app's logo as an **Android vector drawable** (a white Ge'ez glyph sitting on
the Deep Tana → Lake Blue → Shore Aqua diagonal gradient). Use it in the navbar and footer, and as the site's
favicon / OG image mark.

- Android `<vector>` XML isn't directly usable on the web — **convert the path(s) to an SVG** for use in the
  site (extract the `pathData` into `<svg><path d="…"/></svg>` and apply the gradient with an SVG
  `<linearGradient>`).
- **Fallback if conversion is impractical:** recreate the mark as a clean SVG yourself — a white Fidel glyph
  centered on the signature diagonal blue gradient in a rounded-square tile — matching the palette in §4.

---

## 10. Technical & deliverable constraints

- **Next.js App Router**, **TypeScript**, **Tailwind CSS**, **Framer Motion**.
- Mobile-first and fully responsive down to small phones; no horizontal overflow.
- **SEO & sharing:** proper `<title>`, meta description, Open Graph + Twitter card tags, and an OG image using
  the logo/gradient. Sensible per-route metadata.
- **Accessibility:** semantic landmarks, alt text on every image (including the screenshots), focus states,
  AA contrast.
- **Assets layout:** `public/screenshot1.*`, `public/screenshot2.*`, `public/screenshot3.*`, and the logo
  under `public/`.
- Provide the full project so it runs with `npm install && npm run dev` and builds cleanly with `npm run build`.

---

## 11. Self-check before you finish

Confirm the finished site includes all of the following:

- [ ] Next.js App Router site, responsive, light/dark aware, deployable to Vercel.
- [ ] Home, Features, and a dedicated `/privacy` page.
- [ ] The three headline features: **all Amharic letters & special characters**, **all Ge'ez numbers**, and
      **robust autocomplete that handles ambiguous cases**.
- [ ] Secondary features (live transliteration, big offline dictionaries, English layout, themes, works
      everywhere).
- [ ] Brand palette and signature diagonal gradient applied; Ge'ez text rendered with an Ethiopic webfont.
- [ ] The three screenshots (`screenshot1/2/3`) integrated creatively (phone mockups + parallax/tilt/carousel).
- [ ] The logo (`logo.xml` → SVG) in navbar, footer, and favicon/OG image.
- [ ] Meaningful animations, including the live Latin→Fidel typing motif; `prefers-reduced-motion` respected.
- [ ] "Get it on Google Play" CTA marked **Coming soon**, linking to the package's placeholder URL.
- [ ] Privacy page reflects **on-device processing + basic anonymous analytics**, with the contact email and a
      developer-review note.
