#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
product_dir="$project_dir/apps/textrevamp"
aab="$product_dir/build/outputs/bundle/release/textrevamp-release.aab"
mapping="$product_dir/build/outputs/mapping/release/mapping.txt"
manifest="$product_dir/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
profiles="$product_dir/src/release/generated/baselineProfiles"
metadata="$product_dir/build/outputs/release-candidate.properties"
version_file="$product_dir/version.properties"

fail() {
    printf 'Release verification failed: %s\n' "$1" >&2
    exit 1
}

[[ -s "$aab" ]] || fail "release AAB is missing"
[[ -s "$mapping" ]] || fail "R8 mapping is missing"
[[ -s "$manifest" ]] || fail "merged release manifest is missing"
[[ -s "$profiles/baseline-prof.txt" ]] || fail "generated baseline profile is missing"
[[ -s "$profiles/startup-prof.txt" ]] || fail "generated startup profile is missing"

entries="$(unzip -Z1 "$aab")"
for entry in \
    base/assets/english.db \
    base/assets/english_dictionary_manifest.properties \
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
        com.textrevamp.keyboard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION |
        sort
)"
actual_permissions="$(
    sed -n 's/.*<uses-permission[^>]*android:name="\([^"]*\)".*/\1/p' "$manifest" |
        sort
)"
if [[ "$actual_permissions" != "$expected_permissions" ]]; then
    fail "release permissions differ from the allowlist: $actual_permissions"
fi
grep -Fq 'android:name="com.addiyon.keyboard.AddiyonKeyboardService"' "$manifest" ||
    fail "IME service is missing"
grep -Fq 'android:targetSdkVersion="36"' "$manifest" ||
    fail "target SDK is not 36"
if grep -Eq \
    'android:name="com\.addiyon\.keyboard\.(benchmarkhost|debug)\.' \
    "$manifest"; then
    fail "a debug or benchmark component is present in the release manifest"
fi

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
