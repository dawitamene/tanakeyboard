#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
aab="$project_dir/app/build/outputs/bundle/release/app-release.aab"
mapping="$project_dir/app/build/outputs/mapping/release/mapping.txt"
manifest="$project_dir/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
profiles="$project_dir/app/src/release/generated/baselineProfiles"
metadata="$project_dir/app/build/outputs/release-candidate.properties"
version_file="$project_dir/version.properties"
firebase_config="$project_dir/app/google-services.json"
firebase_release_config="$project_dir/app/src/release/google-services.json"
firebase_values="$project_dir/app/build/generated/res/google-services/release/values/values.xml"
crashlytics_mapping_id="$project_dir/app/build/crashlytics/release/mappingFileId.txt"

fail() {
    printf 'Release verification failed: %s\n' "$1" >&2
    exit 1
}

[[ -s "$aab" ]] || fail "release AAB is missing"
[[ -s "$mapping" ]] || fail "R8 mapping is missing"
[[ -s "$manifest" ]] || fail "merged release manifest is missing"
[[ -s "$profiles/baseline-prof.txt" ]] || fail "generated baseline profile is missing"
[[ -s "$profiles/startup-prof.txt" ]] || fail "generated startup profile is missing"
firebase_config_count=0
firebase_config_path=""
if [[ -s "$firebase_config" ]]; then
    firebase_config_count=$((firebase_config_count + 1))
    firebase_config_path="$firebase_config"
fi
if [[ -s "$firebase_release_config" ]]; then
    firebase_config_count=$((firebase_config_count + 1))
    firebase_config_path="$firebase_release_config"
fi
[[ "$firebase_config_count" -eq 1 ]] ||
    fail "provide exactly one production google-services.json"
[[ -s "$firebase_values" ]] || fail "generated production Firebase resources are missing"
[[ -s "$crashlytics_mapping_id" ]] || fail "Crashlytics mapping metadata is missing"
grep -Eq '"package_name"[[:space:]]*:[[:space:]]*"com\.addiyon\.keyboard"' \
    "$firebase_config_path" ||
    fail "production Firebase config has the wrong application ID"
expected_firebase_app_id="$(
    sed -n 's/^firebaseProductionAppId=//p' "$version_file"
)"
expected_firebase_project_id="$(
    sed -n 's/^firebaseProductionProjectId=//p' "$version_file"
)"
[[ -n "$expected_firebase_app_id" ]] ||
    fail "firebaseProductionAppId is not pinned in version.properties"
[[ -n "$expected_firebase_project_id" ]] ||
    fail "firebaseProductionProjectId is not pinned in version.properties"
actual_firebase_app_id="$(
    sed -n 's/.*"mobilesdk_app_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
        "$firebase_config_path" |
        head -1
)"
actual_firebase_project_id="$(
    sed -n 's/.*"project_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
        "$firebase_config_path" |
        head -1
)"
[[ "$actual_firebase_app_id" == "$expected_firebase_app_id" ]] ||
    fail "production Firebase app ID does not match version.properties"
[[ "$actual_firebase_project_id" == "$expected_firebase_project_id" ]] ||
    fail "production Firebase project ID does not match version.properties"
generated_firebase_app_id="$(
    sed -n 's/.*<string name="google_app_id"[^>]*>\([^<]*\)<\/string>.*/\1/p' \
        "$firebase_values" |
        head -1
)"
[[ "$generated_firebase_app_id" == "$expected_firebase_app_id" ]] ||
    fail "generated production Firebase app ID does not match version.properties"

entries="$(unzip -Z1 "$aab")"
for entry in \
    base/assets/amharic.db \
    base/assets/english.db \
    base/assets/dictionary_manifest.properties \
    base/assets/emoji.dat \
    BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map \
    BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof; do
    grep -Fxq "$entry" <<<"$entries" || fail "AAB entry $entry is missing"
done

if grep -Eq 'base/assets/.*_(words|ngrams)\.dat$' <<<"$entries"; then
    fail "raw dictionary inputs are packaged"
fi

expected_permissions="$(
    printf '%s\n' \
        android.permission.RECORD_AUDIO \
        android.permission.VIBRATE \
        android.permission.INTERNET \
        android.permission.ACCESS_NETWORK_STATE \
        android.permission.WAKE_LOCK \
        com.addiyon.keyboard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION |
        sort
)"
actual_permissions="$(
    sed -n 's/.*<uses-permission[^>]*android:name="\([^"]*\)".*/\1/p' "$manifest" |
        sort
)"
if [[ "$actual_permissions" != "$expected_permissions" ]]; then
    fail "release permissions differ from the allowlist: $actual_permissions"
fi
if grep -Eiq 'AD_ID|ADSERVICES|AD_SERVICES|ACCESS_ADSERVICES|TOPICS|ATTRIBUTION' \
    <<<"$actual_permissions"; then
    fail "advertising or AdServices permission is present: $actual_permissions"
fi
for metadata_name in \
    firebase_analytics_collection_enabled \
    firebase_crashlytics_collection_enabled \
    google_analytics_adid_collection_enabled \
    google_analytics_default_allow_ad_personalization_signals; do
    metadata_block="$(grep -A2 -F "android:name=\"$metadata_name\"" "$manifest" || true)"
    grep -Fq 'android:value="false"' <<<"$metadata_block" ||
        fail "$metadata_name is not false in the merged release manifest"
done
grep -Fq 'android:name="com.addiyon.keyboard.AddiyonKeyboardService"' "$manifest" ||
    fail "IME service is missing"
grep -Fq 'android:targetSdkVersion="36"' "$manifest" ||
    fail "target SDK is not 36"
if grep -Eq \
    'android:name="com\.addiyon\.keyboard\.(benchmarkhost|debug)\.' \
    "$manifest"; then
    fail "a debug or benchmark component is present in the release manifest"
fi

while IFS= read -r dex_entry; do
    if unzip -p "$aab" "$dex_entry" |
        strings |
        grep -E 'telemetry_fatal|telemetry_non_fatal|ImeTestCommandReceiver|controlled telemetry test crash' \
            >/dev/null; then
        fail "production dex contains a debug crash trigger"
    fi
done < <(grep -E '^base/dex/classes[^/]*\.dex$' <<<"$entries")

jarsigner -verify "$aab" >/dev/null 2>&1 ||
    fail "AAB signature verification failed"
expected_certificate="$(sed -n 's/^releaseCertificateSha256=//p' "$version_file" | tr '[:upper:]' '[:lower:]')"
actual_certificate="$(
    keytool -printcert -jarfile "$aab" 2>/dev/null |
        sed -n 's/.*SHA256: //p' |
        head -1 |
        tr -d ':' |
        tr '[:upper:]' '[:lower:]'
)"
[[ -n "$expected_certificate" ]] || fail "expected signing certificate is not configured"
[[ "$actual_certificate" == "$expected_certificate" ]] ||
    fail "AAB signing certificate does not match version.properties"

if rg -q 'Lcom/addiyon/keyboard/benchmarkhost/|Lcom/addiyon/keyboard/debug/' \
    "$profiles/baseline-prof.txt" "$profiles/startup-prof.txt"; then
    fail "generated profile references benchmark-only app classes"
fi

version_name="$(sed -n 's/^versionName=//p' "$version_file")"
version_code="$(sed -n 's/.*android:versionCode="\([0-9][0-9]*\)".*/\1/p' "$manifest" | head -1)"
[[ -n "$version_code" ]] || fail "release versionCode is missing"
if [[ -n "${PLAY_MAX_VERSION_CODE:-}" ]] &&
    (( version_code <= PLAY_MAX_VERSION_CODE )); then
    fail "versionCode $version_code is not higher than Play $PLAY_MAX_VERSION_CODE"
fi

source_status="clean"
if [[ -n "$(git -C "$project_dir" status --porcelain --untracked-files=no)" ]]; then
    source_status="dirty"
fi
if [[ "${REQUIRE_CLEAN_RELEASE:-0}" == "1" && "$source_status" != "clean" ]]; then
    fail "source tree is dirty"
fi

mkdir -p "$(dirname "$metadata")"
{
    printf 'commit=%s\n' "$(git -C "$project_dir" rev-parse HEAD)"
    printf 'sourceStatus=%s\n' "$source_status"
    printf 'versionName=%s\n' "$version_name"
    printf 'versionCode=%s\n' "$version_code"
    printf 'certificateSha256=%s\n' "$actual_certificate"
    printf 'aabSha256=%s\n' "$(shasum -a 256 "$aab" | awk '{print $1}')"
    printf 'mappingSha256=%s\n' "$(shasum -a 256 "$mapping" | awk '{print $1}')"
    printf 'aabBytes=%s\n' "$(stat -f '%z' "$aab")"
} >"$metadata"

printf 'Release artifact verified: %s\n' "$aab"
printf 'Candidate metadata: %s\n' "$metadata"
