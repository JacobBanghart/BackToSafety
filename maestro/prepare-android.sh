#!/usr/bin/env bash
# Puts the connected Android emulator into a fixed, screenshot-stable state:
# no animations, a frozen status bar (demo mode), fixed timezone and locale.
# Run once after boot, before any flow (PARITY_PLAN rule 4: determinism first).

set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" shell settings put global "$setting" 0
done

"$ADB" shell settings put global sysui_demo_allowed 1
demo() { "$ADB" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0900
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile hide
demo notifications -e visible false

"$ADB" shell cmd alarm set-timezone America/Los_Angeles
"$ADB" shell settings put system time_12_24 12

# Keep the soft keyboard's suggestion strip and autofill out of screenshots.
"$ADB" shell settings put secure autofill_service null
"$ADB" shell settings put secure spell_checker_enabled 0

echo "device prepared"
