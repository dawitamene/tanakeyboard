#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
ADB_SERIAL="${ADB_SERIAL:-}"
PACKAGE="${PACKAGE:-com.addiyon.keyboard.debug}"
SERVICE_CLASS="${SERVICE_CLASS:-com.addiyon.keyboard.AddiyonKeyboardService}"
HOST_CLASS="${HOST_CLASS:-com.addiyon.keyboard.ImeTestHostActivity}"
RECEIVER_CLASS="${RECEIVER_CLASS:-com.addiyon.keyboard.debug.ImeTestCommandReceiver}"
ITERATIONS="${ITERATIONS:-10}"
STRESS_MODE="${STRESS_MODE:-typing}"
CRAWL_EVENTS="${CRAWL_EVENTS:-100}"
CORRUPT_DICTIONARY="${CORRUPT_DICTIONARY:-0}"
STRICT_MODE_AS_FAILURE="${STRICT_MODE_AS_FAILURE:-0}"
ROTATION_CYCLES="${ROTATION_CYCLES:-4}"
OUTPUT_DIR="${OUTPUT_DIR:-plans/capture/phase3-$(date +%Y%m%d-%H%M%S)}"

adb_cmd() {
    if [[ -n "$ADB_SERIAL" ]]; then
        "$ADB_BIN" -s "$ADB_SERIAL" "$@"
    else
        "$ADB_BIN" "$@"
    fi
}

service_id="$PACKAGE/$SERVICE_CLASS"
host_component="$PACKAGE/$HOST_CLASS"
receiver_component="$PACKAGE/$RECEIVER_CLASS"
mkdir -p "$OUTPUT_DIR"

adb_cmd get-state >/dev/null
adb_cmd shell pm path "$PACKAGE" >/dev/null
package_uid="$(
    adb_cmd shell dumpsys package "$PACKAGE" |
        tr -d '\r' |
        awk -F= '/userId=/{ print $2; exit }'
)"
{
    adb_cmd shell getprop ro.product.manufacturer
    adb_cmd shell getprop ro.product.model
    adb_cmd shell getprop ro.build.version.sdk
    adb_cmd shell getprop ro.build.version.release
    adb_cmd shell getprop ro.config.low_ram
    adb_cmd shell cat /proc/meminfo
} >"$OUTPUT_DIR/device-profile.txt"

original_ime="$(adb_cmd shell settings get secure default_input_method | tr -d '\r')"
original_hardware_setting="$(
    adb_cmd shell settings get secure show_ime_with_hard_keyboard | tr -d '\r'
)"
original_auto_rotation="$(
    adb_cmd shell settings get system accelerometer_rotation | tr -d '\r'
)"
original_user_rotation="$(
    adb_cmd shell settings get system user_rotation | tr -d '\r'
)"
ime_was_enabled="$(
    adb_cmd shell ime list -s | tr -d '\r' | awk -v target="$service_id" '$0 == target { print "yes" }'
)"

restore_device() {
    if [[ -n "$original_ime" && "$original_ime" != "null" ]]; then
        adb_cmd shell ime set "$original_ime" >/dev/null 2>&1 || true
    fi
    if [[ "$ime_was_enabled" != "yes" && "$original_ime" != "$service_id" ]]; then
        adb_cmd shell ime disable "$service_id" >/dev/null 2>&1 || true
    fi
    if [[ -n "$original_hardware_setting" && "$original_hardware_setting" != "null" ]]; then
        adb_cmd shell settings put secure show_ime_with_hard_keyboard \
            "$original_hardware_setting" >/dev/null 2>&1 || true
    else
        adb_cmd shell settings delete secure show_ime_with_hard_keyboard \
            >/dev/null 2>&1 || true
    fi
    if [[ -n "$original_auto_rotation" && "$original_auto_rotation" != "null" ]]; then
        adb_cmd shell settings put system accelerometer_rotation \
            "$original_auto_rotation" >/dev/null 2>&1 || true
    else
        adb_cmd shell settings delete system accelerometer_rotation \
            >/dev/null 2>&1 || true
    fi
    if [[ -n "$original_user_rotation" && "$original_user_rotation" != "null" ]]; then
        adb_cmd shell settings put system user_rotation \
            "$original_user_rotation" >/dev/null 2>&1 || true
    else
        adb_cmd shell settings delete system user_rotation \
            >/dev/null 2>&1 || true
    fi
}
trap restore_device EXIT

select_test_ime() {
    adb_cmd shell settings put secure show_ime_with_hard_keyboard 1
    adb_cmd shell ime enable "$service_id" >/dev/null
    adb_cmd shell ime set "$service_id" >/dev/null
}

start_field() {
    local field="$1"
    local fault="${2:-none}"
    adb_cmd shell am start -W -n "$host_component" \
        --es field "$field" \
        --es fault "$fault" >/dev/null
    sleep 0.25
}

command() {
    local action="$1"
    local value="${2:-}"
    if [[ -n "$value" ]]; then
        adb_cmd shell am broadcast \
            -n "$receiver_component" \
            -a com.addiyon.keyboard.TEST_COMMAND \
            --es command "$action" \
            --es value "$value" >/dev/null
    else
        adb_cmd shell am broadcast \
            -n "$receiver_component" \
            -a com.addiyon.keyboard.TEST_COMMAND \
            --es command "$action" >/dev/null
    fi
}

type_word() {
    local word="$1"
    local index
    for ((index = 0; index < ${#word}; index++)); do
        command character "${word:index:1}"
    done
}

select_test_ime
adb_cmd logcat -c

if (( ROTATION_CYCLES > 0 )); then
    adb_cmd shell settings put system accelerometer_rotation 0
    start_field normal
    for ((rotation_cycle = 0; rotation_cycle < ROTATION_CYCLES; rotation_cycle++)); do
        adb_cmd shell settings put system user_rotation "$((rotation_cycle % 4))"
        sleep 0.5
        command character r
    done
fi

for ((iteration = 1; iteration <= ITERATIONS; iteration++)); do
    start_field normal
    type_word selam
    command space
    command language
    type_word hello
    command space
    command language
    type_word rapid
    command space
    command enter

    command shift
    command character a
    command shift
    command shift
    command character b
    command shift

    command number
    command character 1
    command symbols
    command character "+"
    command keypad
    command character 2
    command number

    command emoji_open
    command emoji "🙂"
    command emoji_close

    command delete_start
    command delete
    command delete
    command delete_end

    start_field email
    command text "tester${iteration}@example.com"

    start_field fault reject_mutations
    command character x
    command character y

    start_field fault null_reads
    command delete

    start_field fault throw_all
    command character z
    command space
    command enter

    start_field password
    command text "secret${iteration}"

    adb_cmd shell am send-trim-memory "$PACKAGE" RUNNING_LOW >/dev/null 2>&1 || true
    adb_cmd shell am send-trim-memory "$PACKAGE" BACKGROUND >/dev/null 2>&1 || true

    if [[ "$STRESS_MODE" == "lowram" ]]; then
        adb_cmd shell am send-trim-memory "$PACKAGE" RUNNING_CRITICAL \
            >/dev/null 2>&1 || true
        if (( iteration % 5 == 0 )); then
            adb_cmd shell am force-stop "$PACKAGE"
            select_test_ime
            start_field normal
            command text "after-process-death-${iteration}"
        fi
    fi
done

if [[ "$CORRUPT_DICTIONARY" == "1" ]]; then
    adb_cmd shell am force-stop "$PACKAGE"
    dictionary_name="$(
        adb_cmd shell run-as "$PACKAGE" ls no_backup/dictionaries 2>/dev/null |
            tr -d '\r' |
            grep '\.db$' |
            sed -n '1p' || true
    )"
    if [[ -n "$dictionary_name" ]]; then
        dictionary_file="no_backup/dictionaries/$dictionary_name"
        echo "$dictionary_file" >"$OUTPUT_DIR/corrupted-dictionary.txt"
        adb_cmd shell run-as "$PACKAGE" \
            dd if=/dev/zero "of=$dictionary_file" bs=64 count=1 \
            >/dev/null 2>&1
        select_test_ime
        start_field normal
        command text after-database-recovery
        sleep 1
    else
        echo "No installed dictionary found for corruption recovery." >&2
        exit 1
    fi
fi

if (( CRAWL_EVENTS > 0 )); then
    adb_cmd shell monkey \
        -p "$PACKAGE" \
        --pct-syskeys 0 \
        --pct-appswitch 0 \
        --throttle 25 \
        "$CRAWL_EVENTS" >"$OUTPUT_DIR/monkey.txt" 2>&1
fi

adb_cmd shell dumpsys meminfo "$PACKAGE" >"$OUTPUT_DIR/meminfo.txt" 2>&1 || true
adb_cmd shell dumpsys input_method >"$OUTPUT_DIR/input-method.txt" 2>&1 || true
adb_cmd logcat -d >"$OUTPUT_DIR/logcat-full.txt"
if [[ -n "$package_uid" ]]; then
    adb_cmd logcat -d "--uid=$package_uid" >"$OUTPUT_DIR/logcat.txt"
else
    cp "$OUTPUT_DIR/logcat-full.txt" "$OUTPUT_DIR/logcat.txt"
fi

app_failure_pattern="FATAL EXCEPTION|E AndroidRuntime|Process: $PACKAGE|SQLiteDatabaseCorruptException|WindowLeaked|has leaked IntentReceiver|BadTokenException|Unable to add window"
system_failure_pattern="ANR in $PACKAGE|Input dispatching timed out.*$PACKAGE|$PACKAGE.*Input dispatching timed out"
: >"$OUTPUT_DIR/failures.txt"
has_failure=0
if grep -E "$app_failure_pattern" "$OUTPUT_DIR/logcat.txt" >>"$OUTPUT_DIR/failures.txt"; then
    has_failure=1
fi
if grep -E "$system_failure_pattern" "$OUTPUT_DIR/logcat-full.txt" >>"$OUTPUT_DIR/failures.txt"; then
    has_failure=1
fi
if (( has_failure != 0 )); then
    echo "Phase 3 stress failed. See $OUTPUT_DIR/failures.txt" >&2
    exit 1
fi

finding_pattern="StrictMode policy violation|Skipped [0-9]+ frames|Slow dispatch|Slow delivery|SQLiteLanguageStore load"
grep -E "$finding_pattern" "$OUTPUT_DIR/logcat.txt" >"$OUTPUT_DIR/findings.txt" || true
if [[ "$STRICT_MODE_AS_FAILURE" == "1" ]] &&
    grep -q "StrictMode policy violation" "$OUTPUT_DIR/findings.txt"; then
    echo "Phase 3 stress failed its StrictMode gate. See $OUTPUT_DIR/findings.txt" >&2
    exit 1
fi

finding_count="$(wc -l <"$OUTPUT_DIR/findings.txt" | tr -d ' ')"
echo "Phase 3 stress passed with $finding_count diagnostic findings. Results: $OUTPUT_DIR"
