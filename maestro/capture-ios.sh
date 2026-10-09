#!/usr/bin/env bash
# iOS counterpart of capture.sh: every screen state (states/*.yaml) in every mode, as a
# screenshot plus the testID layout. Output: <out>/<mode>/<state>.png and .layout.json.
#
#   SIM_UDID=<udid> maestro/capture-ios.sh [out-dir] [state ...]
# Modes: light, dark (system appearance), large-text (Dynamic Type extra-extra-extra-large,
# ~1.35x body text, iOS's nearest step to Android's 1.3). MODES limits a run.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$ROOT/captures/ios}"
shift || true
U="${SIM_UDID:?set SIM_UDID to the simulator to capture on}"

states=("$@")
if [ ${#states[@]} -eq 0 ]; then
  for f in "$ROOT"/states/*.yaml; do states+=("$(basename "$f" .yaml)"); done
fi

set_mode() {
  case "$1" in
    light) xcrun simctl ui "$U" appearance light; xcrun simctl ui "$U" content_size large ;;
    dark) xcrun simctl ui "$U" appearance dark; xcrun simctl ui "$U" content_size large ;;
    large-text) xcrun simctl ui "$U" appearance light; xcrun simctl ui "$U" content_size extra-extra-extra-large ;;
  esac
}

for mode in ${MODES:-light dark large-text}; do
  set_mode "$mode"
  mkdir -p "$OUT/$mode"
  for state in "${states[@]}"; do
    echo "== $mode / $state"
    maestro --device "$U" test --no-ansi "$ROOT/states/$state.yaml" >"$OUT/$mode/$state.log" 2>&1 || {
      echo "   flow failed (see $OUT/$mode/$state.log)"
      continue
    }
    # Overlapping `maestro hierarchy` calls on different simulators all answer with one
    # simulator's tree, so capture-parallel-ios.sh's shards take turns (a mkdir lock). A screen
    # can also still be settling (a Dynamic Type re-render, the keyboard going away): keep a
    # layout only when it holds the testID the state flow last asserted and reads the same
    # before and after the screenshot; otherwise ask again.
    anchor="$(grep -oE 'id: [a-z0-9_-]+' "$ROOT/states/$state.yaml" | tail -1 | cut -d' ' -f2)"
    layout() {
      until mkdir /tmp/maestro-hierarchy.lock 2>/dev/null; do sleep 0.2; done
      maestro --device "$U" hierarchy >"$OUT/$mode/$state.hierarchy.json" 2>/dev/null || true
      rmdir /tmp/maestro-hierarchy.lock
      python3 "$ROOT/extract_layout_ios.py" "$OUT/$mode/$state.hierarchy.json" 2>/dev/null || true
    }
    before="$(layout)"
    for attempt in 1 2 3 4 5 6; do
      sleep 1
      xcrun simctl io "$U" screenshot --type=png "$OUT/$mode/$state.png" >/dev/null 2>&1
      after="$(layout)"
      if [ "$before" = "$after" ] && grep -q "\"$anchor\"" <<<"$after"; then break; fi
      [ "$attempt" = 6 ] && echo "   layout never settled with $anchor (see $OUT/$mode/$state.layout.json)"
      before="$after"
    done
    printf '%s\n' "$after" >"$OUT/$mode/$state.layout.json"
    [ -n "${KEEP_XML:-}" ] || rm "$OUT/$mode/$state.hierarchy.json"
  done
done
set_mode light
