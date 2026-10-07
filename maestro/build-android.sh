#!/usr/bin/env bash
# Builds the RN app as the parity harness runs it: a release build (bundled JS,
# no dev tools) with test seams on, signed with the debug keystore so later
# builds, including the Kotlin app for the L5 upgrade test, can install over it.
# x86_64 only, for the emulator. Prints the APK path.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export EXPO_PUBLIC_TEST_SEAMS=1
export ANDROID_KEYSTORE_FILE="$ROOT/android/app/debug.keystore"
export ANDROID_KEYSTORE_PASSWORD=android ANDROID_KEY_ALIAS=androiddebugkey ANDROID_KEY_PASSWORD=android

cd "$ROOT/android"
[ -f local.properties ] || printf 'sdk.dir=%s\n' "$ANDROID_HOME" >local.properties
./gradlew assembleRelease -PreactNativeArchitectures=x86_64 -q >&2
echo "$ROOT/android/app/build/outputs/apk/release/app-release.apk"
