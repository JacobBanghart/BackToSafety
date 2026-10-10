#!/usr/bin/env bash
# One emulator per lane, on the lane's own port, sharing the box fairly.
#
#   emulator.sh up <port>     boot emulator-<port> (waits for a free slot), prepare and seed it
#   emulator.sh down <port>   stop emulator-<port> (only that one; never stop-emulators.sh)
#   emulator.sh list          running emulators
#
# EMULATOR_MAX (default 4) caps how many run at once across every lane: each takes ~4 GB and
# the host has 48 GB shared with builds. Same AVD and settings as maestro/start-emulator.sh.
set -euo pipefail
ADB="$ANDROID_HOME/platform-tools/adb"
MAESTRO_DIR="$(cd "$(dirname "$0")/../../maestro" && pwd)"
MAX="${EMULATOR_MAX:-4}"
cmd="${1:-list}"
port="${2:-}"
serial="emulator-$port"

running() { pgrep -f 'qemu-system.*-port [0-9]+' -a | grep -oE -- '-port [0-9]+' | awk '{print $2}'; }

case "$cmd" in
  list) running ;;
  down)
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
    timeout 60 bash -c "while pgrep -f 'qemu-system.*-port $port( |\$)' >/dev/null; do sleep 1; done" || true
    ;;
  up)
    [ -n "$port" ] || { echo "usage: emulator.sh up <port>" >&2; exit 2; }
    if ! running | grep -qx "$port"; then
      # The AVD exists once maestro/start-emulator.sh has run; make it the same way if not.
      [ -d "$HOME/.android/avd/nijii-pixel7.avd" ] || { "$MAESTRO_DIR/start-emulator.sh" 1 >/dev/null; "$MAESTRO_DIR/stop-emulators.sh"; }
      exec 9>/tmp/nijii-emulator.lock
      until flock 9 && [ "$(running | wc -l)" -lt "$MAX" ]; do
        flock -u 9; echo "waiting for an emulator slot ($MAX in use)" >&2; sleep 30
      done
      emu=("$ANDROID_HOME/emulator/emulator" -avd nijii-pixel7 -port "$port" -read-only -no-window
        -no-audio -no-boot-anim -gpu guest -no-snapshot -no-metrics)
      if [ -w /dev/kvm ]; then nohup "${emu[@]}" >"/tmp/emulator-$port.log" 2>&1 &
      else nohup sudo -n -u "$USER" -g kvm env ANDROID_HOME="$ANDROID_HOME" "${emu[@]}" >"/tmp/emulator-$port.log" 2>&1 &
      fi
      sleep 5
      flock -u 9
    fi
    timeout 600 "$ADB" -s "$serial" wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
    ANDROID_SERIAL="$serial" "$MAESTRO_DIR/prepare-android.sh" >/dev/null
    ANDROID_SERIAL="$serial" "$MAESTRO_DIR/seed-device.sh" >/dev/null
    echo "$serial"
    ;;
  *) echo "usage: emulator.sh up|down <port> | list" >&2; exit 2 ;;
esac
