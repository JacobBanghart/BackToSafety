#!/usr/bin/env bash
# reorder.sh for iOS: between the same setup and verify flows, an XCUITest bundle
# (gestures/ios) long-presses the second item and drags it to the top. Run on the Mac with
# a booted simulator; SIM_UDID picks one (default: the first booted nijii-* simulator).
# Plain bash 3 (macOS's /bin/bash).
#
#   reorder-ios.sh            runs contacts and destinations

set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
U="${SIM_UDID:-$(xcrun simctl list devices | grep -E 'nijii-[0-9]+ .*Booted' | grep -oE '[0-9A-F-]{36}' | head -1)}"
[ -n "$U" ] || { echo "no booted simulator" >&2; exit 2; }
(cd "$HERE/ios" && xcodegen generate --quiet)
xcodebuild build-for-testing -project "$HERE/ios/Gestures.xcodeproj" -scheme GestureTests \
  -destination "id=$U" -derivedDataPath "$HERE/ios/build" -quiet >/tmp/reorder-ios-build.log 2>&1 ||
  { echo "gesture driver build failed (/tmp/reorder-ios-build.log)"; exit 1; }

for screen in contacts destinations; do
  maestro --device "$U" test --no-ansi "$HERE/$screen-setup.yaml" >"/tmp/reorder-$screen.log" 2>&1 ||
    { echo "$screen: setup failed (/tmp/reorder-$screen.log)"; exit 1; }
  TEST_RUNNER_SCREEN="$screen" xcodebuild test-without-building -project "$HERE/ios/Gestures.xcodeproj" \
    -scheme GestureTests -destination "id=$U" -derivedDataPath "$HERE/ios/build" -quiet >>"/tmp/reorder-$screen.log" 2>&1 ||
    { echo "$screen: drag failed (/tmp/reorder-$screen.log)"; exit 1; }
  maestro --device "$U" test --no-ansi "$HERE/$screen-verify.yaml" >>"/tmp/reorder-$screen.log" 2>&1 ||
    { echo "$screen: reorder not saved (/tmp/reorder-$screen.log)"; exit 1; }
  echo "$screen: reorder ok"
done
