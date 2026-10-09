#!/usr/bin/env bash
# Builds the iOS app (kmp/iosApp) as the parity harness runs it: Release, with test
# seams on, for the arm64 simulator, unsigned. Prints the .app path. Needs a Mac with Xcode,
# XcodeGen (brew install xcodegen) and the repo's mise tools (Java, android-sdk for Gradle):
# the project's first build phase builds the shared Kotlin core.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/kmp/iosApp"
xcodegen generate --quiet >&2
xcodebuild -project BackToSafety.xcodeproj -scheme BackToSafety -configuration Release \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO \
  SWIFT_ACTIVE_COMPILATION_CONDITIONS='$(inherited) TEST_SEAMS' -quiet >&2
echo "$ROOT/kmp/iosApp/build/Build/Products/Release-iphonesimulator/BackToSafety.app"
