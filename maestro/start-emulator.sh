#!/usr/bin/env bash
# Boots N headless emulators (default 1) on ports 5554, 5556, ... and prepares each.
# With an APK argument, installs it on every one (read-only instances lose installs on exit).
#
#   start-emulator.sh [count] [apk]          GPU=guest|swiftshader_indirect (default guest)
#
# Uses $ANDROID_HOME (mise's android-sdk under `mise run`) and the nijii-pixel7 AVD, which it
# creates the first time (API 36 Google APIs, pixel_7, hw.ramSize=4096M, hw.cpu.ncore=8).
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

# The AVD, the first time: the API 36 Google APIs image (adb root works on it), 4 GB, 8 cores.
if [ ! -d "$HOME/.android/avd/nijii-pixel7.avd" ]; then
  case "$(uname -m)" in aarch64 | arm64) abi=arm64-v8a ;; *) abi=x86_64 ;; esac
  image="system-images;android-36;google_apis;$abi"
  bin="$(ls -d "$ANDROID_HOME"/cmdline-tools/*/bin | head -1)"
  [ -d "$ANDROID_HOME/system-images/android-36/google_apis/$abi" ] || { yes | "$bin/sdkmanager" --licenses >/dev/null 2>&1 || true; "$bin/sdkmanager" emulator "$image"; }
  echo no | "$bin/avdmanager" create avd -n nijii-pixel7 -k "$image" -d pixel_7 >/dev/null
  printf 'hw.ramSize=4096M\nhw.cpu.ncore=8\n' >>"$HOME/.android/avd/nijii-pixel7.avd/config.ini"
fi

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
  ANDROID_SERIAL="$serial" "$HERE/seed-device.sh" >/dev/null
  # A fresh install: a build signed with another key can't update over the old one.
  if [ -n "$APK" ]; then
    "$ADB" -s "$serial" uninstall com.backtosafety.app >/dev/null 2>&1 || true
    "$ADB" -s "$serial" install "$APK" >/dev/null
  fi
  echo "$serial ready"
done
