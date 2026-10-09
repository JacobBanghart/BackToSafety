#!/usr/bin/env bash
# L5 upgrade test on the iOS simulator: the SwiftUI app installs over the RN app (same
# bundle ID, so the data container stays) and must read its data.
#
#   upgrade-ios.sh install-over OLD.app NEW.app   fill data with OLD, install NEW over it, verify
#
# Runs on the Mac. SIM_UDID picks the simulator (default: the first booted nijii-* one).
# The RN app keeps its database at Documents/SQLite/nijii.db (expo-sqlite), which is where
# the SwiftUI app opens it. Plain bash 3 (macOS's /bin/bash).

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=com.backtosafety.app
U="${SIM_UDID:-$(xcrun simctl list devices | grep -E 'nijii-[0-9]+ .*Booted' | grep -oE '[0-9A-F-]{36}' | head -1)}"
[ -n "$U" ] || { echo "no booted simulator" >&2; exit 2; }

settings() {
  local db
  db="$(xcrun simctl get_app_container "$U" "$PKG" data)/Documents/SQLite/nijii.db"
  sqlite3 "$db" "select key, value from settings where key in ('device_id','theme_preference','active_emergency') order by key;"
}

case "${1:-}" in
  install-over)
    xcrun simctl uninstall "$U" "$PKG" >/dev/null 2>&1 || true
    xcrun simctl install "$U" "$2"
    maestro --device "$U" test --no-ansi "$HERE/fill.yaml" >/tmp/upgrade-fill.log 2>&1 ||
      { echo "fill failed: /tmp/upgrade-fill.log"; exit 1; }
    sleep 2 # RN saves the last tap asynchronously; let it land before the app is killed
    xcrun simctl terminate "$U" "$PKG" >/dev/null 2>&1 || true
    before="$(settings)"
    xcrun simctl install "$U" "$3"
    maestro --device "$U" test --no-ansi "$HERE/verify.yaml" >/tmp/upgrade-verify.log 2>&1 ||
      { echo "verify failed: /tmp/upgrade-verify.log"; exit 1; }
    after="$(settings)"
    if [ "$before" != "$after" ]; then
      echo "settings changed across the upgrade:"; diff <(echo "$before") <(echo "$after") || true; exit 1
    fi
    echo "upgrade ok: data and settings (device_id, theme, active emergency) survived"
    ;;
  *)
    sed -n '2,9p' "$0"; exit 2 ;;
esac
