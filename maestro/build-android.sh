#!/usr/bin/env bash
# Builds the Android app (android/app) as the parity harness runs it: release, with test
# seams on, signed with the debug key. Prints the APK path.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/android"
# Queued like every heavy build (scripts/heavy.sh); it also builds the Rust core.
"$ROOT/scripts/heavy.sh" ./gradlew --no-daemon :app:assembleRelease -PtestSeams=true --console=plain -q >&2
echo "$ROOT/android/app/build/outputs/apk/release/app-release.apk"
