#!/bin/zsh
set -euo pipefail

package_name="com.addiyon.keyboard"
service_name="$package_name/.AddiyonKeyboardService"
iterations="${1:-50}"

adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell ime enable "$service_name"
adb shell ime set "$service_name"
adb logcat -c

for ((index = 1; index <= iterations; index++)); do
    adb shell am start -a android.settings.SETTINGS >/dev/null
    adb shell input keyevent 84
    adb shell input tap 500 620
    adb shell input text "performance${index}"
    adb shell input keyevent 67
    adb shell input keyevent 4
    process_id="$(adb shell pidof "$package_name" || true)"
    if [[ -n "$process_id" ]]; then
        adb shell am send-trim-memory "$package_name" RUNNING_LOW >/dev/null 2>&1 || true
    fi
done

adb shell dumpsys meminfo "$package_name" || true
adb logcat -d -t 3000 AndroidRuntime:E SQLiteLog:E AddiyonKeyboard:E '*:S'
