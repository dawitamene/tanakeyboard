#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/path/to/app.apk" >&2
  exit 2
fi

apk_path="$1"
kdeconnect_cli="${KDECONNECT_CLI:-/Applications/KDE Connect.app/Contents/MacOS/kdeconnect-cli}"

if [[ ! -f "$apk_path" ]]; then
  echo "APK not found: $apk_path" >&2
  exit 1
fi

if [[ ! -x "$kdeconnect_cli" ]]; then
  echo "KDE Connect CLI not found or not executable: $kdeconnect_cli" >&2
  exit 1
fi

if [[ -z "${DBUS_SESSION_BUS_ADDRESS:-}" ]]; then
  dbus_socket="$(launchctl getenv DBUS_LAUNCHD_SESSION_BUS_SOCKET)"
  if [[ -z "$dbus_socket" ]]; then
    echo "KDE Connect D-Bus session is unavailable. Open KDE Connect and try again." >&2
    exit 1
  fi
  export DBUS_SESSION_BUS_ADDRESS="unix:path=$dbus_socket"
fi

device_id="${KDECONNECT_DEVICE_ID:-a35a4d1606804b909f75827cb1160327}"

"$kdeconnect_cli" --device "$device_id" --share "$apk_path"
