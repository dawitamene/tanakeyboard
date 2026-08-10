#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/local.properties" | tail -n 1)"
ADB="$SDK_DIR/platform-tools/adb"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
IME_COMPONENT="com.addiyon.keyboard.debug/com.addiyon.keyboard.AddiyonKeyboardService"

if [[ ! -x "$ADB" ]]; then
    echo "Android Debug Bridge not found at $ADB" >&2
    exit 1
fi

if [[ -n "${1:-}" ]]; then
    ADB_COMMAND=("$ADB" -s "$1")
else
    ADB_COMMAND=("$ADB")
fi

"$PROJECT_DIR/gradlew" assembleDebug
"${ADB_COMMAND[@]}" wait-for-device
"${ADB_COMMAND[@]}" install -r -t "$APK"
"${ADB_COMMAND[@]}" shell settings put secure show_ime_with_hard_keyboard 1
"${ADB_COMMAND[@]}" shell ime enable "$IME_COMPONENT"
"${ADB_COMMAND[@]}" shell ime set "$IME_COMPONENT"

echo "Installed Addiyon as an in-place update with app data preserved."
