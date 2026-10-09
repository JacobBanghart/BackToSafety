#!/usr/bin/env bash
# Runs Maestro flows spread over every running device of one platform (round-robin), and
# prints one line per flow plus the first lines of each failure. Exits 1 if any flow failed.
# Plain bash 3 (macOS's /bin/bash).
#
#   maestro/run-flows.sh android|ios [flow.yaml ...]   (default: maestro/flows/*.yaml)

set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
platform="${1:?android or ios}"
shift
flows=("$@")
[ ${#flows[@]} -gt 0 ] || flows=("$HERE"/flows/*.yaml)

case "$platform" in
  android) devices=($("${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb" devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1}')) ;;
  ios) devices=($(xcrun simctl list devices | grep -E 'nijii-[0-9]+ .*Booted' | grep -oE '[0-9A-F-]{36}')) ;;
  *) echo "platform: android or ios" >&2; exit 2 ;;
esac
[ ${#devices[@]} -gt 0 ] || { echo "no $platform devices running" >&2; exit 2; }

OUT="${FLOWS_OUT:-/tmp/nijii-flows}"
rm -rf "$OUT" && mkdir -p "$OUT"
for n in "${!devices[@]}"; do
  (
    for i in "${!flows[@]}"; do
      [ $((i % ${#devices[@]})) -eq "$n" ] || continue
      name="$(basename "${flows[$i]}" .yaml)"
      if maestro --device "${devices[$n]}" test --no-ansi "${flows[$i]}" --test-output-dir "$OUT/$name" >"$OUT/$name.log" 2>&1; then
        echo "ok   $name" >>"$OUT/summary"
      else
        echo "FAIL $name ($OUT/$name.log)" >>"$OUT/summary"
      fi
    done
  ) &
done
wait
sort "$OUT/summary"
failed=0
for log in "$OUT"/*.log; do
  if grep -q FAILED "$log"; then
    failed=1
    echo "== $(basename "$log" .log)"
    grep -E "FAILED|not found|Assertion is" "$log" | head -3
  fi
done
exit $failed
