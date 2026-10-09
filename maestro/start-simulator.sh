#!/usr/bin/env bash
# iOS counterpart of start-emulator.sh: boots N "nijii-<i>" simulators (iPhone 17 Pro), installs
# the app, adds the address-book contact contacts-import.yaml picks (Rosa Diaz), and pins the
# status bar so screenshots don't vary. Prints one UDID per line.
#
#   maestro/start-simulator.sh [N] [App.app]

set -euo pipefail
N="${1:-1}"
APP="${2:-}"
DEVICE_TYPE="iPhone 17 Pro"
RUNTIME="$(xcrun simctl list runtimes -j | python3 -c 'import json,sys; r=[x for x in json.load(sys.stdin)["runtimes"] if x["platform"]=="iOS" and x["isAvailable"]]; print(r[-1]["identifier"])')"

vcf="$(mktemp -t rosa).vcf"
printf 'BEGIN:VCARD\nVERSION:3.0\nN:Diaz;Rosa;;;\nFN:Rosa Diaz\nTEL;TYPE=CELL:+1 555 987 6543\nEND:VCARD\n' >"$vcf"

for i in $(seq 1 "$N"); do
  name="nijii-$i"
  udid="$(xcrun simctl list devices -j | python3 -c "import json,sys; d=[x['udid'] for v in json.load(sys.stdin)['devices'].values() for x in v if x['name']=='$name' and x['isAvailable']]; print(d[0] if d else '')")"
  [ -n "$udid" ] || udid="$(xcrun simctl create "$name" "$DEVICE_TYPE" "$RUNTIME")"
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b >/dev/null
  xcrun simctl status_bar "$udid" override --time 9:41 --batteryState charged --batteryLevel 100 \
    --cellularMode active --cellularBars 4 --wifiBars 3 --dataNetwork wifi
  xcrun simctl addmedia "$udid" "$vcf" 2>/dev/null || true
  if [ -n "$APP" ]; then
    xcrun simctl uninstall "$udid" com.backtosafety.app 2>/dev/null || true
    xcrun simctl install "$udid" "$APP"
  fi
  echo "$udid"
done
rm -f "$vcf"
