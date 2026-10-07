#!/usr/bin/env bash
# capture.sh sharded across every running emulator (start them with start-emulator.sh N APK).
# States are dealt round-robin; each device runs its share in all modes into the same
# output directory, so the result is identical to a serial capture.sh run.
#
#   capture-parallel.sh [out-dir] [state ...]

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/captures/android}"
shift || true
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

mapfile -t devices < <("$ADB" devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1}')
[ ${#devices[@]} -gt 0 ] || { echo "no emulators running" >&2; exit 2; }

states=("$@")
if [ ${#states[@]} -eq 0 ]; then
  for f in "$HERE"/states/*.yaml; do states+=("$(basename "$f" .yaml)"); done
fi

declare -A shard
for i in "${!states[@]}"; do
  d="${devices[$((i % ${#devices[@]}))]}"
  shard[$d]="${shard[$d]:-} ${states[$i]}"
done

pids=()
for d in "${devices[@]}"; do
  [ -n "${shard[$d]:-}" ] || continue
  # shellcheck disable=SC2086
  ANDROID_SERIAL="$d" "$HERE/capture.sh" "$OUT" ${shard[$d]} >"$OUT.$d.log" 2>&1 &
  pids+=($!)
done

status=0
for pid in "${pids[@]}"; do wait "$pid" || status=1; done
cat "$OUT".emulator-*.log | grep -E "flow failed" || true
echo "captured $(find "$OUT" -name '*.png' ! -name '*.diff.png' | wc -l) screens on ${#devices[@]} emulators"
exit $status
