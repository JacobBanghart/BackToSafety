#!/usr/bin/env bash
# Builds the RN app as the parity harness runs it on iOS: a Release build (bundled JS, no dev
# tools) with test seams on, for the arm64 simulator, unsigned. Prints the .app path.
# Runs on a Mac with Xcode (the self-hosted runner, or any Mac with the repo's mise tools).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export EXPO_PUBLIC_TEST_SEAMS=1

cd "$ROOT"
[ -d node_modules ] || npm ci >&2
cd ios
# A stale local spec index misses new pod versions; refresh it only when needed.
pod install >&2 || pod install --repo-update >&2
xcodebuild -workspace BacktoSafety.xcworkspace -scheme BacktoSafety -configuration Release \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$ROOT/ios/build" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO \
  -quiet >&2
echo "$ROOT/ios/build/Build/Products/Release-iphonesimulator/BacktoSafety.app"
