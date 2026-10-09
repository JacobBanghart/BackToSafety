#!/usr/bin/env bash
# Drag-to-reorder for contacts and places. Maestro can't hold-then-drag, so between a
# setup flow and a verify flow this uses `adb shell input draganddrop` (long press, then
# drag), aimed by testID bounds. Android only; reorder-ios.sh is the iOS counterpart.
#
#   reorder.sh            runs contacts and destinations

set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
MAESTRO=(maestro)
[ -n "${ANDROID_SERIAL:-}" ] && MAESTRO+=(--device "$ANDROID_SERIAL")

center() { # testID -> "x y" from a UI dump
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
  "$ADB" shell cat /sdcard/ui.xml | grep -oE "resource-id=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" |
    grep -oE '[0-9]+' | tail -4 | paste -sd' ' | awk '{printf "%d %d", ($1+$3)/2, ($2+$4)/2}'
}

for screen in contacts destinations; do
  "${MAESTRO[@]}" test --no-ansi "$HERE/$screen-setup.yaml" >"/tmp/reorder-$screen.log" ||
    { echo "$screen: setup failed (/tmp/reorder-$screen.log)"; exit 1; }
  read -r x1 y1 <<<"$(center "$screen-item-1-name")"
  read -r _ y0 <<<"$(center "$screen-item-0-name")"
  "$ADB" shell input draganddrop "$x1" "$y1" "$x1" "$((y0 - 30))" 1500
  "${MAESTRO[@]}" test --no-ansi "$HERE/$screen-verify.yaml" >>"/tmp/reorder-$screen.log" ||
    { echo "$screen: reorder not saved (/tmp/reorder-$screen.log)"; exit 1; }
  echo "$screen: reorder ok"
done
