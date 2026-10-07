#!/usr/bin/env bash
# Captures every screen state (maestro/states/*.yaml) in every device mode:
# a screenshot plus the testID layout. Output: <out>/<mode>/<state>.png and .layout.json.
#
# Usage: maestro/capture.sh [out-dir] [state ...]
# Modes: light, dark (system dark mode; the app follows it), large-text (font scale 1.3).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$ROOT/captures/android}"
shift || true
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
DPI="$("$ADB" shell wm density | grep -oE '[0-9]+' | tail -1)"

states=("$@")
if [ ${#states[@]} -eq 0 ]; then
  for f in "$ROOT"/states/*.yaml; do states+=("$(basename "$f" .yaml)"); done
fi

set_mode() {
  case "$1" in
    light) "$ADB" shell cmd uimode night no; "$ADB" shell settings put system font_scale 1.0 ;;
    dark) "$ADB" shell cmd uimode night yes; "$ADB" shell settings put system font_scale 1.0 ;;
    large-text) "$ADB" shell cmd uimode night no; "$ADB" shell settings put system font_scale 1.3 ;;
  esac
}

"$ROOT/prepare-android.sh" >/dev/null
for mode in light dark large-text; do
  set_mode "$mode"
  mkdir -p "$OUT/$mode"
  for state in "${states[@]}"; do
    echo "== $mode / $state"
    maestro test --no-ansi "$ROOT/states/$state.yaml" >"$OUT/$mode/$state.log" 2>&1 || {
      echo "   flow failed (see $OUT/$mode/$state.log)"
      continue
    }
    "$ADB" exec-out screencap -p >"$OUT/$mode/$state.png"
    "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
    "$ADB" pull /sdcard/ui.xml "$OUT/$mode/$state.xml" >/dev/null
    python3 "$ROOT/extract_layout.py" "$OUT/$mode/$state.xml" "$DPI" >"$OUT/$mode/$state.layout.json"
    rm "$OUT/$mode/$state.xml"
  done
done
set_mode light
