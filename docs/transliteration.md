# Amharic Transliteration — Architecture & Code Walkthrough

## Overview

Tana Keyboard transliterates Latin (SERA-style) keystrokes into Ethiopic fidel as the user types. The pipeline is pure Kotlin with zero Android/Compose imports, making it unit-testable on a JVM without an emulator.

```
User taps keys
    ↓
TanaKeyboardService.onCharacter(latin)
    ↓ (Amharic mode)
AmharicComposer  ──owns──▶ StringBuilder (Latin buffer)
    │                           │
    │  onCharacter(char)        │ append(char)
    │  onBackspace()            │ truncate via Transliterator.lastUnitStart
    │  commit()                 │ clear
    │                           ▼
    └─────────▶ Transliterator.transliterate(buffer)
                    │
                    ▼
                Ethiopic fidel String
                    │
                    ▼
                InputConnection.setComposingText(fidel)
```

Four files, bottom up:

| File | Role | Android deps |
|------|------|-------------|
| `AmharicTable.kt` | Pure data — consonant families, vowel tables | None |
| `Transliterator.kt` | Stateless algorithm — Latin → fidel | None |
| `AmharicComposer.kt` | Stateful buffer owner — connects to Android | `InputConnection` only |
| `TanaKeyboardService.kt` | IME service — owns shift state, routes keys | Full Android |

---

## 1. AmharicTable.kt — The Data

**File:** `transliteration/AmharicTable.kt:33`  
**Type:** `object` (singleton, no state)

### Family data structure

Every consonant in the scheme has exactly **7 syllabic forms** in a fixed vowel order, plus an optional **labialized "ua"** form:

```kotlin
data class Family(
    val forms: String,   // exactly 7 chars: e u i a ie (bare) o
    val ua: Char? = null // optional labialized form, e.g. 'ሏ' for "l+ua"
)
```

```kotlin
// Line 36: index of the bare (no-vowel) form inside forms
const val BARE_FORM_INDEX = 5

// Line 42: sentinel meaning "use the ua field instead of forms[index]"
const val UA_INDEX = -1
```

The 7 forms are stored as **literal Ethiopic strings** copied verbatim from a reference table. They are NOT computed by adding a vowel offset to a base codepoint. The Ethiopic block mostly works that way, but the "ua" forms break the arithmetic pattern inconsistently:

| Family | Base | + ua | Offset used |
|--------|------|------|-------------|
| l | ለ (U+1208) | ሏ (U+120F) | base + 7 |
| q | ቀ (U+1240) | ቋ (U+124B) | base + 11 |
| g | ገ (U+1308) | ጓ (U+1313) | base + 11 |

Explicit literals are trivially auditable and immune to off-by-one bugs.

### The consonant table — `families`

```kotlin
// Line 109-164
val families: Map<String, Family> = mapOf(
    "h"   to Family("ሀሁሂሃሄህሆ"),          // ሀ series
    "H"   to Family("ሐሑሒሓሔሕሖ"),          // ሐ series (distinct!)
    "l"   to Family("ለሉሊላሌልሎ", ua = 'ሏ'),
    "sh"  to Family("ሸሹሺሻሼሽሾ", ua = 'ሿ'),
    "'"   to Family("አኡኢኣኤእኦ"),          // glottal — used for bare vowels
    "C"   to Family("ጨጩጪጫጬጭጮ", ua = 'ጯ'), // distinct from "ch"!
    // ... 24 families total
)
```

Keys are **case-sensitive** because three pairs genuinely map to different consonant series:

| Lowercase | Series | Uppercase | Series |
|-----------|--------|-----------|--------|
| `h` | ሀ (ha) | `H` | ሐ (ha, pharyngeal) |
| `t` | ተ (te) | `T` | ጠ (te, ejective) |
| `ch` | ቸ (che) | `C` | ጨ (che, ejective) |

The remaining ~20 consonants have no distinct uppercase family, so shift is a no-op for them at the transliteration level.

```kotlin
// Line 174-175: pre-sorted longest-first for greedy matching
val consonantsByLength: List<String> =
    families.keys.sortedByDescending { it.length }
```

This produces `["sh", "gn", "zh", "kh", "ch", "ph", "ts", "h", "H", "l", ...]` — ensuring `"sh"` is tried before `"s"`+`"h"`, `"gn"` before `"g"`+`"n"`, etc.

### The two vowel tables — critical distinction

**Table 1: `vowels`** — used when a vowel follows a consonant:

```kotlin
// Line 70-78
val vowels: List<Pair<String, Int>> = listOf(
    "ie" to 4,
    "ua" to UA_INDEX,   // sentinel: use Family.ua
    "e"  to 0,
    "u"  to 1,
    "i"  to 2,
    "a"  to 3,
    "o"  to 6
)
```

`"a"` → index 3 means: for `"la"`, take the `l` family's `forms[3]` = ላ (the "a" vowel form).

**Table 2: `bareVowels`** — used when a vowel appears with no preceding consonant:

```kotlin
// Line 95-102
val bareVowels: List<Pair<String, Int>> = listOf(
    "ie" to 4,
    "a"  to 0,    // different index!
    "i"  to 2,
    "u"  to 1,
    "o"  to 6,
    "e"  to BARE_FORM_INDEX  // index 5, different!
)
```

`"a"` → index 0 means: resolve against the glottal (`"'"`) family's `forms[0]` = አ, not `forms[3]`.

**Why are they different?** In the SERA convention, vowel-order labeling is relative to the consonant:

| Vowel order | Meaning after consonant | Example | Meaning standalone | Example |
|-------------|------------------------|---------|-------------------|---------|
| 0 (e) | le → ለ | | "e" alone → እ (bare glottal) |
| 3 (a) | la → ላ | | "a" alone → አ (order 1 of glottal) |

The glottal family `"'"` has አ (a-sound) at index 0 and እ (bare) at index 5. But in `vowels`, index 3 means "a-vowel" for regular consonants. So a bare `"a"` needs index 0 against the glottal family, not index 3.

### The helper — `bareFormOf`

```kotlin
// Line 206-218
fun bareFormOf(latin: String): Char?
```

Used by the UI to show a key's corner preview glyph. Resolution order:
1. Exact match in `families` → bare form (preserves h/H, t/T, ch/C)
2. Lowercased match in `families` → bare form (handles "Q" → "q")
3. Match in `bareVowels` → resolve against glottal family (handles "a" → አ)
4. `keyboardToFamilyKey` mapping ("x" → "sh", "c" → "ch") → bare form
5. `null` — not a valid spelling

---

## 2. Transliterator.kt — The Algorithm

**File:** `transliteration/Transliterator.kt:75`  
**Type:** `object` (stateless, no mutable state)

### Core design: whole-buffer retransliteration

```kotlin
// Line 77-81
fun transliterate(latin: String): String {
    val out = StringBuilder(latin.length)
    forEachUnit(latin) { _, text -> out.append(text) }
    return out.toString()
}
```

Every keystroke re-processes the **entire** Latin buffer from position 0. This is deliberate: incremental patching ("we had ስ, now 'h' came in, swap to ሽ...") creates desync bugs with backspace, cursor jumps, and suggestion replacements. At typical word lengths (<1μs per call), the cost is negligible, and the Latin buffer remains the single source of truth.

### The matching loop — `forEachUnit`

```kotlin
// Line 103-174
private inline fun forEachUnit(latin: String, emit: (start: Int, text: String) -> Unit)
```

Walks the string left-to-right. At each position `i`:

#### Step 1: Consonant match (lines 113-117)

```kotlin
val consonant = AmharicTable.consonantsByLength
    .firstOrNull { latin.startsWith(it, i) }                  // case-sensitive
    ?: AmharicTable.consonantsByLength
        .firstOrNull { latin.startsWith(it, i, ignoreCase = true) }  // fallback
```

Two-pass lookup:
1. **Case-sensitive** scan: `"H"` will match before `"h"`, `"C"` before `"ch"`. This protects the three pairs with distinct uppercase families.
2. **Case-insensitive** fallback: if nothing matched exactly, try again ignoring case. This means `"Q"` (no distinct family) falls through to `"q"` instead of being passed through as Latin.

#### Step 2: Vowel match (lines 147-148)

```kotlin
val vowel = AmharicTable.vowels
    .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i, ignoreCase = true) }
```

Only tried if a consonant matched. Single case-insensitive pass (no vowel has a distinct uppercase family).

Three outcomes:

```kotlin
// Line 150-153: No vowel → bare (6th order) form
if (vowel == null) {
    emit(start, family.bare.toString())
    continue
}
```

```kotlin
// Line 159-172: Labialized "ua" 
if (index == AmharicTable.UA_INDEX) {
    val ua = family.ua
    val text = if (ua != null) {
        ua.toString()                            // e.g. "s"+"ua" → ሷ
    } else {
        family.bare.toString() + transliterate("ua")  // fallback: ህ + ኡ
    }
    emit(start, text)
}
```

If the matched vowel is `"ua"` → check if the family defines a dedicated `ua` character. If yes, emit it. If no (like `"h"` or `"H"` which have no ua form), emit bare form + recursively transliterated `"ua"` as a fallback.

```kotlin
// Line 170-171: Regular vowel → emit forms[index]
emit(start, family.forms[index].toString())
```

#### Step 3: Bare vowel match (lines 123-133)

```kotlin
val bareVowel = AmharicTable.bareVowels
    .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i) }
    ?: AmharicTable.bareVowels
        .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i, ignoreCase = true) }

if (bareVowel != null) {
    val (spelling, index) = bareVowel
    emit(start, AmharicTable.families.getValue("'").forms[index].toString())
    i += spelling.length
    continue
}
```

When no consonant matched, try bare vowels against the glottal (`"'"`) family. Same case-sensitive-then-insensitive order.

#### Step 4: Passthrough (lines 135-137)

```kotlin
emit(start, latin[i].toString())
i++
continue
```

If nothing matched, the character passes through unchanged. Covers digits, punctuation, fidel characters already in the buffer, and Latin letters outside the scheme.

### Backspace support — `lastUnitStart`

```kotlin
// Line 89-93
fun lastUnitStart(latin: String): Int {
    var lastStart = 0
    forEachUnit(latin) { start, _ -> lastStart = start }
    return lastStart
}
```

Reuses the same `forEachUnit` walk to find the Latin index where the **last fidel unit** began. This ensures backspace deletes a whole syllable: `"she"` (index 0-2) → `lastUnitStart` returns 0 → truncate to empty.

### Example walkthrough: "selam"

```
Input:  "selam"  (በለሰላም)
Position 0:
  - "s" matches (case-sensitive, AmharicTable has "s")
  - advance i by 1
  - "e" matches in vowels → index 0
  - advance i by 1
  - emit l's forms[0] = ሰ

Position 2:
  - "l" matches
  - advance i by 1
  - "a" matches in vowels → index 3
  - advance i by 1
  - emit l's forms[3] = ላ

Position 4:
  - "m" matches
  - advance i by 1
  - no vowel → bare form index 5
  - m's forms[5] = ም

Output: "ሰላም"
```

---

## 3. AmharicComposer.kt — The Buffer Owner

**File:** `transliteration/AmharicComposer.kt:58`  
**Type:** `internal class`

### Why a composer?

In Amharic mode, a single keypress doesn't map to a single committed character. The syllable is ambiguous until the vowel arrives:
- `"s"` might become `"sh"` (ሽ) or `"se"` (ሰ) or stay bare `"s"` (ስ).
- If we committed each keystroke with `commitText`, the field would flicker as we kept deleting and re-committing.

Android's `setComposingText` solves this: text in the composing region is rendered **underlined** and gets atomically replaced by the next `setComposingText`/`commitText` call — exactly the pattern autocomplete uses.

### State

```kotlin
// Line 62
private val latin = StringBuilder()
```

The romanized Latin buffer is the single source of truth. Every mutation appends to it, re-transliterates the whole thing, and pushes the result to the composing region.

### Constructor — InputConnection lambda

```kotlin
// Line 58-60
internal class AmharicComposer(
    private val inputConnection: () -> InputConnection?
)
```

The composer receives a **lambda** that returns `InputConnection?`, not a captured reference. The system creates new `InputConnection` instances across input sessions (each text field the user taps into gets a fresh one). Capturing `currentInputConnection` once in the constructor would use a stale reference on the next field — exactly the bug the `KeyboardScreen` comment warns about.

### Key methods

#### `onCharacter(char)` — line 80

```kotlin
fun onCharacter(char: String) {
    latin.append(char)
    pushComposing()
}
```

Appends the case-resolved letter to the Latin buffer, then re-transliterates and pushes. The caller (`TanaKeyboardService.onCharacter`) has already applied shift state, so `char` might be `"H"` or `"h"` depending on whether shift was active.

#### `pushComposing()` — line 147

```kotlin
private fun pushComposing() {
    val fidel = Transliterator.transliterate(latin.toString())
    inputConnection()?.setComposingText(fidel, 1)
}
```

`newCursorPosition=1` means: place the cursor at the end of the composed text. This is the natural "keep typing" position.

#### `onBackspace()` — line 93

```kotlin
fun onBackspace(): Boolean {
    if (latin.isEmpty()) return false
    latin.setLength(Transliterator.lastUnitStart(latin.toString()))
    if (latin.isEmpty()) {
        inputConnection()?.finishComposingText()
    } else {
        pushComposing()
    }
    return true
}
```

Three cases:
1. Buffer empty → `false` (let the caller handle it via `deleteSurroundingText`)
2. After truncation, buffer empty → `finishComposingText()` to clear the composing region cleanly
3. Buffer still has content → push updated composing text

This deletes **one full fidel unit**, not one Latin character:

```
"she" → ሸ, backspace → empty     (one press, entire syllable gone)
"selam" → ሰላም, backspace → ሰላ   (last unit "ም" removed, which was Latin "m")
```

#### `commit()` — line 112

```kotlin
fun commit() {
    if (latin.isEmpty()) return
    inputConnection()?.finishComposingText()
    latin.clear()
}
```

Locks the composing text into the field as normal committed text and clears the buffer. Called at word boundaries: space, enter, punctuation, language toggle.

#### `reset()` — line 125

```kotlin
fun reset() {
    latin.clear()
}
```

Drops the buffer WITHOUT committing. Called on `onStartInputView` — the old composing region belongs to a different field's `InputConnection`, so we can't meaningfully finish that composition.

#### `abandon()` — line 141

```kotlin
fun abandon() {
    if (latin.isEmpty()) return
    inputConnection()?.finishComposingText()
    latin.clear()
}
```

Freezes the composing text in place and drops the buffer. Called when the cursor moves outside the composing region (detected in `onUpdateSelection`). The user has walked away from the word, so we commit it as-is.

### Example lifecycle: typing "selam"

```
User action    latin buffer    fidel pushed        composing text
───────────    ────────────    ─────────────        ──────────────
s              "s"             ስ                    �ስ
e              "se"            ሰ                    ሰ
l              "sel"           ሰል                   ሰል
a              "sela"          ሰላ                   ሰላ
m              "selam"         ሰላም                  ሰላም
space          (clear)         finishComposingText  ሰላም (committed)
```

---

## 4. TanaKeyboardService.kt — The IME Service

**File:** `TanaKeyboardService.kt:27`  
**Type:** `InputMethodService`

### State owned by the service

```kotlin
// Line 47
var isAmharic by mutableStateOf(false)

// Line 50
var numbersMode by mutableStateOf(NumbersMode.OFF)

// Line 59
var shiftState by mutableStateOf(ShiftState.OFF)

// Line 71
var isDarkTheme by mutableStateOf(false)

// Line 83
private val composer = AmharicComposer { currentInputConnection }
```

### Shift state machine

```kotlin
// Line 166-172
fun toggleShift() {
    shiftState = when (shiftState) {
        ShiftState.OFF       → ShiftState.SHIFT
        ShiftState.SHIFT     → ShiftState.CAPS_LOCK
        ShiftState.CAPS_LOCK → ShiftState.OFF
    }
}
```

Cycle: **OFF → SHIFT (one-shot) → CAPS_LOCK (sticky) → OFF**.

```kotlin
// Line 179-183
fun consumeShiftAfterCharacter() {
    if (shiftState == ShiftState.SHIFT) {
        shiftState = ShiftState.OFF
    }
}
```

One-shot shift consumes itself after the next character; CAPS_LOCK stays on until the user taps shift again.

### Case resolution (line 212-226)

```kotlin
fun onCharacter(latin: String) {
    val output = when {
        isAmharic && !isNumberMode -> if (isShiftEnabled) latin.uppercase() else latin.lowercase()
        isShiftEnabled             -> latin.uppercase()
        else                       -> latin.lowercase()
    }

    if (isAmharic && !isNumberMode) {
        composer.onCharacter(output)           // → append, retransliterate, setComposingText
    } else {
        currentInputConnection?.commitText(output, 1)  // → immediate commit
    }

    consumeShiftAfterCharacter()
}
```

In Amharic mode: shift is applied to the **Latin letter** (`"s"`→`"S"` or `"h"`→`"H"`) before it enters the composer. This is how the transliterator distinguishes ሀ vs ሐ — it sees `"H"` vs `"h"`.

### Other key handlers

#### `onDelete` — line 234

```kotlin
fun onDelete() {
    if (isAmharic && composer.onBackspace()) return
    currentInputConnection?.deleteSurroundingText(1, 0)
}
```

Tries the composer first (which deletes a whole fidel unit). If the composer had nothing to delete, falls through to regular character deletion.

#### `onSpace` — line 244

```kotlin
fun onSpace() {
    composer.commit()
    currentInputConnection?.commitText(" ", 1)
}
```

Commits the in-flight word first, then inserts the space character. Order matters: if we inserted the space first, the composer's `setComposingText` would overwrite the preceding text.

#### `onEnter` — line 254

```kotlin
fun onEnter() {
    composer.commit()
    currentInputConnection?.sendKeyEvent(
        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
    )
}
```

#### `onUpdateSelection` — line 327

```kotlin
override fun onUpdateSelection(
    oldSelStart: Int, oldSelEnd: Int,
    newSelStart: Int, newSelEnd: Int,
    candidatesStart: Int, candidatesEnd: Int
) {
    // ...
    if (!composer.isComposing) return

    val cursorInsideComposing = newSelStart == newSelEnd &&
            candidatesStart >= 0 &&
            newSelStart in candidatesStart..candidatesEnd

    if (!cursorInsideComposing) {
        composer.abandon()
    }
}
```

Detects when the user taps elsewhere in the text, walking away from the composing region. The `candidatesStart..candidatesEnd` range is the framework's view of the current composing region (both `-1` when nothing is composed). If the cursor lands outside, it calls `abandon()` which calls `finishComposingText()` to commit the word as-is and clears the Latin buffer.

---

## Summary: data flow for one keystroke

```
Key tap (UI)
    │
    ▼
TanaKeyboardService.onCharacter("s")
    │
    ├── Resolve case: isShiftEnabled? "S" : "s"
    │
    ├── Amharic mode?
    │   YES ──▶ AmharicComposer.onCharacter("s")
    │                │
    │                ├── latin.append("s")      →  "s"
    │                ├── Transliterator.transliterate("s")
    │                │       │
    │                │       ├── pos 0: "s" matches consonant
    │                │       ├── pos 1: no vowel → bare form
    │                │       └── return "ስ"
    │                │
    │                └── inputConnection?.setComposingText("ስ", 1)
    │
    NO ──▶ currentInputConnection?.commitText("s", 1)
    │
    └── consumeShiftAfterCharacter()  (OFF if SHIFT, unchanged if CAPS_LOCK)
```
