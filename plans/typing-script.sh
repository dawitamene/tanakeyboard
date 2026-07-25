#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ITERATIONS="${ITERATIONS:-${1:-10}}" \
STRESS_MODE="typing" \
"$script_dir/phase3-ime-stress.sh"
