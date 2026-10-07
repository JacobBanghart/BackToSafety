#!/usr/bin/env bash
# Boots the parity emulator headless and waits for it, then prepares it.
# One-time setup (see PARITY_PLAN.md "Infrastructure"): Android SDK in ~/Android/Sdk
# with emulator, platform-tools and system-images;android-36;google_apis;x86_64, and
#   avdmanager create avd -n nijii-pixel7 -k "system-images;android-36;google_apis;x86_64" -d pixel_7
# with hw.ramSize=4096M and hw.cpu.ncore=8 in its config.ini (2 GB dropped adb under load).
# /dev/kvm must be usable; until a fresh login picks up the kvm group, this uses sudo -g kvm.
#
# Software GPU (swiftshader) keeps screenshots identical run to run; -no-snapshot
# cold-boots every time so no state leaks between runs.

set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMU=("$ANDROID_HOME/emulator/emulator" -avd nijii-pixel7 -no-window -no-audio -no-boot-anim
  -gpu swiftshader_indirect -no-snapshot -no-metrics)

if [ -w /dev/kvm ]; then
  nohup "${EMU[@]}" >/tmp/emulator.log 2>&1 &
else
  nohup sudo -n -u "$USER" -g kvm env ANDROID_HOME="$ANDROID_HOME" "${EMU[@]}" >/tmp/emulator.log 2>&1 &
fi

timeout 600 "$ADB" wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
"$(dirname "$0")/prepare-android.sh"
