#!/usr/bin/env bash
# capture-ios.sh sharded across the booted nijii-* simulators (start-simulator.sh N).
# Plain bash 3 (macOS's /bin/bash).
#   capture-parallel-ios.sh [out-dir] [state ...]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/captures/ios}"
shift || true
sims=($(xcrun simctl list devices | grep -E 'nijii-[0-9]+ .*Booted' | grep -oE '[0-9A-F-]{36}'))
[ ${#sims[@]} -gt 0 ] || { echo "no nijii simulators booted" >&2; exit 2; }
states=("$@")
if [ ${#states[@]} -eq 0 ]; then
  for f in "$HERE"/states/*.yaml; do states+=("$(basename "$f" .yaml)"); done
fi
mkdir -p "$OUT"
pids=()
for n in "${!sims[@]}"; do
  mine=()
  for i in "${!states[@]}"; do [ $((i % ${#sims[@]})) -eq "$n" ] && mine+=("${states[$i]}"); done
  [ ${#mine[@]} -gt 0 ] || continue
  SIM_UDID="${sims[$n]}" "$HERE/capture-ios.sh" "$OUT" "${mine[@]}" >"$OUT.${sims[$n]}.log" 2>&1 &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid" || true; done
cat "$OUT".*.log | grep -E "flow failed" || true
echo "captured $(find "$OUT" -name '*.png' ! -name '*.diff.png' | wc -l | tr -d ' ') screens on ${#sims[@]} simulators"
