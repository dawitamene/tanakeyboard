#!/usr/bin/env bash
# Deterministic typing script for memory verification.
# Run on an emulator with the TanaKeyboard IME enabled and selected in the active field.
# Captures memory after a 2s idle tail.

set -euo pipefail

ADB="${ADB:-adb}"
PKG="${PKG:-com.addiyon.keyboard.debug}"
OUT_DIR="${OUT_DIR:-plans/capture}"
mkdir -p "$OUT_DIR"

# Step 1: focus a text field. We assume the TestKeyboardScreen EditText is already focused.
# If not, tap into it first:
#   adb shell input tap 540 1800     # adjust per device

# Tap EditText and clear it
"$ADB" shell input tap 540 500
sleep 1
"$ADB" shell input keyevent KEYCODE_MOVE_END
for i in $(seq 1 80); do "$ADB" shell input keyevent KEYCODE_DEL; done
"$ADB" logcat -c
sleep 1

# Helper: type a string (spaces encoded as %s)
type_str() {
  local s="$1"
  # adb shell input text requires escaping spaces as %s
  local escaped="${s// /%s}"
  "$ADB" shell input text "$escaped"
}

press_space() { "$ADB" shell input keyevent 62; }
press_back()  { "$ADB" shell input keyevent 67; }
press_enter() { "$ADB" shell input keyevent 66; }
toggle_lang() { "$ADB" shell am broadcast -n com.addiyon.keyboard.debug/com.addiyon.keyboard.debug.LanguageToggleReceiver -a com.addiyon.keyboard.TOGGLE_LANGUAGE >/dev/null; }
sleep_ms()    { sleep "$(awk -v ms="$1" 'BEGIN{printf "%.3f", ms/1000}')"; }

dump_meminfo() {
  local n="$1"
  "$ADB" shell dumpsys meminfo "$PKG" > "$OUT_DIR/meminfo-${n}.txt"
}

# ---- 200-char mixed script with 3 language toggle cycles ----

# 1. Amharic (default)
type_str "selam habesha"
press_space
type_str "betam amiligno"
press_space
type_str "metseh neger"
press_space
toggle_lang
sleep_ms 500

# 2. English
type_str "hello"
press_space
type_str "world"
press_space
type_str "thursday"
press_space
type_str "morning"
press_space
toggle_lang
sleep_ms 500

# 3. Amharic again
type_str "konjo"
press_space
type_str "tiru"
press_space
type_str "yefikir"
press_space
toggle_lang
sleep_ms 500

# 4. English again
type_str "please"
press_space
type_str "thanks"
press_space
type_str "tomorrow"
press_space
toggle_lang
sleep_ms 500

# 5. Amharic tail
type_str "amharic"
press_space
type_str "meskel"
press_space

# 6. Backspace gesture
for i in 1 2 3 4 5 6; do press_back; done

# 7. Final idle tail for GC quiescence
sleep 2

# ---- Memory capture (3 samples) ----
dump_meminfo 1
sleep_ms 500
dump_meminfo 2
sleep_ms 500
dump_meminfo 3

# Capture logcat for MemoryProbe + GC
"$ADB" logcat -d -s MemoryProbe:V dalvikvm:V > "$OUT_DIR/logcat.txt"

echo "Done. Results in $OUT_DIR/"
