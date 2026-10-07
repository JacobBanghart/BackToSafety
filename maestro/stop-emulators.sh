#!/usr/bin/env bash
# Stops every running emulator. Capture runs start fresh ones and stop them afterwards:
# the emulator's host memory grows with every screenshot.
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
for d in $("$ADB" devices | awk '/^emulator-[0-9]+\t/ {print $1}'); do
  "$ADB" -s "$d" emu kill >/dev/null 2>&1 || true
done
timeout 120 bash -c 'while pgrep -x qemu-system-x86 >/dev/null; do sleep 1; done'
