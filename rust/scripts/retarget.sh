#!/usr/bin/env bash
# Copies the Maestro tree (flows, states, subflows, gestures) into OUT with every appId pointed
# at APP_ID, so the harness written for the KMP app drives the Rust app unchanged.
#
#   retarget.sh <out-dir> [app-id]      (default com.backtosafety.rust)
set -euo pipefail
OUT="$1"
APP_ID="${2:-com.backtosafety.rust}"
SRC="$(cd "$(dirname "$0")/../../maestro" && pwd)"
rm -rf "$OUT" && mkdir -p "$OUT"
cp -r "$SRC"/. "$OUT"/
find "$OUT" -type f \( -name '*.yaml' -o -name '*.sh' \) -exec sed -i "s/com\.backtosafety\.app/$APP_ID/g" {} +
echo "$OUT"
