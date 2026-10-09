#!/usr/bin/env bash
# Builds the Android app (kmp/androidApp) as the parity harness runs it: release, with test
# seams on, signed with the debug key. Prints the APK path.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/kmp"
./gradlew :androidApp:assembleRelease -PtestSeams=true --console=plain -q >&2
echo "$ROOT/kmp/androidApp/build/outputs/apk/release/androidApp-release.apk"
