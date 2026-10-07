#!/usr/bin/env bash
# Boots N headless emulators (default 1) on ports 5554, 5556, ... and prepares each.
# With an APK argument, installs it on every one (read-only instances lose installs on exit).
#
#   start-emulator.sh [count] [apk]          GPU=guest|swiftshader_indirect (default guest)
#
# One-time setup (maestro/README.md): Android SDK in ~/Android/Sdk and the nijii-pixel7 AVD
# (API 36 Google APIs x86_64, pixel_7, hw.ramSize=4096M, hw.cpu.ncore=8).
# /dev/kvm must be usable; until a fresh login picks up the kvm group, this uses sudo -g kvm.
#
# -read-only lets several instances share the one AVD, and every boot starts from the
# same clean image. GPU rendering is software either way, so screenshots are identical
# run to run. `guest` renders inside Android; `swiftshader_indirect` renders on the host
# and leaked ~15 GB/hour of host memory under captures (2026-10-07).

set -euo pipefail

COUNT="${1:-1}"
APK="${2:-}"
GPU="${GPU:-guest}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
HERE="$(cd "$(dirname "$0")" && pwd)"

for i in $(seq 0 $((COUNT - 1))); do
  port=$((5554 + 2 * i))
  emu=("$ANDROID_HOME/emulator/emulator" -avd nijii-pixel7 -port "$port" -read-only -no-window
    -no-audio -no-boot-anim -gpu "$GPU" -no-snapshot -no-metrics)
  if [ -w /dev/kvm ]; then
    nohup "${emu[@]}" >"/tmp/emulator-$port.log" 2>&1 &
  else
    nohup sudo -n -u "$USER" -g kvm env ANDROID_HOME="$ANDROID_HOME" "${emu[@]}" >"/tmp/emulator-$port.log" 2>&1 &
  fi
done

for i in $(seq 0 $((COUNT - 1))); do
  serial="emulator-$((5554 + 2 * i))"
  timeout 600 "$ADB" -s "$serial" wait-for-device shell \
    'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
  ANDROID_SERIAL="$serial" "$HERE/prepare-android.sh" >/dev/null
  [ -n "$APK" ] && "$ADB" -s "$serial" install -r "$APK" >/dev/null
  echo "$serial ready"
done
