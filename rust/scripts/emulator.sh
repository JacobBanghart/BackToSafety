#!/usr/bin/env bash
# One emulator per lane, on the lane's own port, sharing the box fairly.
#
#   emulator.sh up <port>     boot emulator-<port> (waits for a free slot), prepare and seed it
#   emulator.sh down <port>   stop emulator-<port> (only that one; never stop-emulators.sh)
#   emulator.sh list          running emulators
#   emulator.sh stop-all      stop every emulator (cleanup after agents; not during lanes' runs)
#
# EMULATOR_MAX (default 2) caps how many run at once across every lane, and a boot also waits
# until MemAvailable is at least EMULATOR_MIN_AVAIL_GB (default 12). Each emulator measured
# 5.3 to 8.8 GB: this image falls back from `-gpu guest` to host lavapipe rendering, which
# grows. Four of them plus builds exhausted the 48 GB and forced a reboot on 2026-10-10.
# Emulators run at low CPU and I/O priority so ssh stays responsive; rust/scripts/watchdog.sh
# stops them when memory runs low. Same AVD and settings as maestro/start-emulator.sh.
set -euo pipefail
ADB="$ANDROID_HOME/platform-tools/adb"
MAESTRO_DIR="$(cd "$(dirname "$0")/../../maestro" && pwd)"
MAX="${EMULATOR_MAX:-2}"
MIN_GB="${EMULATOR_MIN_AVAIL_GB:-12}"
avail_gb() { awk '/^MemAvailable:/ {printf "%d", $2 / 1048576}' /proc/meminfo; }
cmd="${1:-list}"
port="${2:-}"
serial="emulator-$port"

# Ports of running emulators, by process name (not command-line text, which also matches any
# shell whose command mentions qemu).
running() {
  local pid
  for pid in $(pgrep qemu-system); do
    tr '\0' ' ' </proc/"$pid"/cmdline 2>/dev/null | grep -oE -- '-port [0-9]+' | awk '{print $2}'
  done
}

case "$cmd" in
  list) running ;;
  stop-all)
    # Every emulator any lane left running (agents cut off by a usage limit leave theirs up).
    for p in $(running); do "$ADB" -s "emulator-$p" emu kill >/dev/null 2>&1 || true; done
    timeout 60 bash -c 'while pgrep qemu-system >/dev/null; do sleep 1; done' || pkill qemu-system || true
    ;;
  down)
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
    timeout 60 bash -c "while pgrep -f 'qemu-system.*-port $port( |\$)' >/dev/null; do sleep 1; done" || true
    ;;
  up)
    [ -n "$port" ] || { echo "usage: emulator.sh up <port>" >&2; exit 2; }
    # The AVD exists once maestro/start-emulator.sh has run; make it the same way if not.
    [ -d "$HOME/.android/avd/nijii-pixel7.avd" ] || { "$MAESTRO_DIR/start-emulator.sh" 1 >/dev/null; "$MAESTRO_DIR/stop-emulators.sh"; }
    # Check and boot under one lock: two `up` calls for the same port at once used to both see
    # it free and boot two emulators, the second unreachable but holding ~5 GB (2026-10-10).
    exec 9>/tmp/nijii-emulator.lock
    flock 9
    if ! running | grep -qx "$port"; then
      until [ "$(running | wc -l)" -lt "$MAX" ] && [ "$(avail_gb)" -ge "$MIN_GB" ]; do
        flock -u 9; echo "waiting for an emulator slot ($(running | wc -l)/$MAX in use, $(avail_gb) GB available, need $MIN_GB)" >&2; sleep 30; flock 9
        running | grep -qx "$port" && break
      done
      if ! running | grep -qx "$port"; then
        emu=(nice -n 10 ionice -c2 -n7 "$ANDROID_HOME/emulator/emulator" -avd nijii-pixel7 -port "$port" -read-only -no-window
          -no-audio -no-boot-anim -gpu guest -no-snapshot -no-metrics)
        if [ -w /dev/kvm ]; then nohup "${emu[@]}" >"/tmp/emulator-$port.log" 2>&1 &
        else nohup sudo -n -u "$USER" -g kvm env ANDROID_HOME="$ANDROID_HOME" "${emu[@]}" >"/tmp/emulator-$port.log" 2>&1 &
        fi
        # Until qemu shows up, another `up` would think the port is free.
        export -f running
        timeout 60 bash -c 'until running | grep -qx "$1"; do sleep 1; done' _ "$port" || true
      fi
    fi
    flock -u 9
    timeout 600 "$ADB" -s "$serial" wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
    ANDROID_SERIAL="$serial" "$MAESTRO_DIR/prepare-android.sh" >/dev/null
    ANDROID_SERIAL="$serial" "$MAESTRO_DIR/seed-device.sh" >/dev/null
    echo "$serial"
    ;;
  *) echo "usage: emulator.sh up|down <port> | list | stop-all" >&2; exit 2 ;;
esac
