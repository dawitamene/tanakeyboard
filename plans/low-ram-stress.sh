#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ITERATIONS="${ITERATIONS:-${1:-50}}" \
STRESS_MODE="lowram" \
CORRUPT_DICTIONARY="${CORRUPT_DICTIONARY:-1}" \
"$script_dir/phase3-ime-stress.sh"
