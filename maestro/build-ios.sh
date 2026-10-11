#!/usr/bin/env bash
# Builds the iOS app (ios) as the parity harness runs it: Release, with test
# seams on, for the arm64 simulator, unsigned. Prints the .app path. Needs a Mac with Xcode,
# XcodeGen (brew install xcodegen) and the repo's mise tools (the Rust toolchain with the iOS
# targets): mise run ios:core builds the Rust core and its Swift bindings, and the project's
# first build phase refreshes them.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# The Rust core's library and Swift bindings come first: the project lists the generated file.
(cd "$ROOT" && mise run ios:core) >&2
cd "$ROOT/ios"
xcodegen generate --quiet >&2
xcodebuild -project BackToSafety.xcodeproj -scheme BackToSafety -configuration Release \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO \
  SWIFT_ACTIVE_COMPILATION_CONDITIONS='$(inherited) TEST_SEAMS' -quiet >&2
echo "$ROOT/ios/build/Build/Products/Release-iphonesimulator/BackToSafety.app"
