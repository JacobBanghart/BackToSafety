#!/usr/bin/env bash
# Builds the Android app (kmp/androidApp) as the parity harness runs it: release, with test
# seams on, signed with the debug key. Prints the APK path.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/kmp"
# Queued like every heavy build (rust/scripts/heavy.sh); it also builds the Rust core.
"$ROOT/rust/scripts/heavy.sh" ./gradlew --no-daemon :androidApp:assembleRelease -PtestSeams=true --console=plain -q >&2
echo "$ROOT/kmp/androidApp/build/outputs/apk/release/androidApp-release.apk"
