#!/usr/bin/env bash
# Device-side data some flows import from: an address-book contact (contacts.import).
# The photo flow adds its own image with Maestro's addMedia. Run once per fresh emulator;
# start-emulator.sh does it.
#
# The contact is created through the Contacts app's own insert screen. A contact written
# straight into the contacts provider shows in the picker, but the picker then hands the
# app nothing.

set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
MAESTRO=(maestro)
[ -n "${ANDROID_SERIAL:-}" ] && MAESTRO+=(--device "$ANDROID_SERIAL")

if ! "$ADB" shell content query --uri content://com.android.contacts/data --projection data1 2>/dev/null | grep -q "Rosa Diaz"; then
  "$ADB" shell am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact \
    -e name "Rosa\ Diaz" -e phone "+15559876543" >/dev/null
  "${MAESTRO[@]}" test --no-ansi "$HERE/subflows/save-contact.yaml" >/dev/null
fi
echo "device seeded"
