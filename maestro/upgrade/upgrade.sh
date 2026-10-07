#!/usr/bin/env bash
# L5 upgrade test. The new app installs over the old one and must read its data.
#
#   upgrade.sh install-over OLD.apk NEW.apk   fill data with OLD, install NEW over it, verify
#   upgrade.sh capture OLD.apk                fill data with OLD, save it as the committed fixture
#   upgrade.sh restore NEW.apk                load the committed fixture into a fresh NEW, verify
#
# Both APKs must share the package name and signing key (build-android.sh uses the
# debug key). Needs `adb root` (Google APIs emulator images allow it).
# Fixture: spec/fixtures/upgrade/android/ = the app's files/ directory, kept to the SQLite DB and
# profile photos, plus nijii.sql (a readable dump for review; restore ignores it).

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
PKG=com.backtosafety.app
FILES="/data/data/$PKG/files"
FIXTURE="$ROOT/spec/fixtures/upgrade/android"

"$ADB" root >/dev/null && "$ADB" wait-for-device

settings() {
  "$ADB" shell "sqlite3 $FILES/SQLite/nijii.db \"select key, value from settings where key in ('device_id','theme_preference','active_emergency') order by key;\""
}

fresh_install() {
  "$ADB" uninstall "$PKG" >/dev/null 2>&1 || true
  "$ADB" install "$1" >/dev/null
}

fill() {
  maestro test --no-ansi "$HERE/fill.yaml" >/tmp/upgrade-fill.log || { echo "fill failed: /tmp/upgrade-fill.log"; exit 1; }
  "$ADB" shell am force-stop "$PKG"
}

verify() {
  maestro test --no-ansi "$HERE/verify.yaml" >/tmp/upgrade-verify.log || { echo "verify failed: /tmp/upgrade-verify.log"; exit 1; }
}

case "${1:-}" in
  install-over)
    fresh_install "$2"
    fill
    before="$(settings)"
    "$ADB" install -r "$3" >/dev/null
    verify
    after="$(settings)"
    if [ "$before" != "$after" ]; then
      echo "settings changed across the upgrade:"; diff <(echo "$before") <(echo "$after") || true; exit 1
    fi
    echo "upgrade ok: data and settings (device_id, theme, active emergency) survived"
    ;;
  capture)
    fresh_install "$2"
    fill
    rm -rf "$FIXTURE" && mkdir -p "$FIXTURE"
    # Fold the write-ahead log into the database so the fixture is one self-contained file.
    "$ADB" shell "sqlite3 $FILES/SQLite/nijii.db 'PRAGMA wal_checkpoint(TRUNCATE);'" >/dev/null
    "$ADB" pull "$FILES/." "$FIXTURE" >/dev/null
    # Keep only app data: the database and profile photos (not analytics or ART state).
    find "$FIXTURE" -mindepth 1 -maxdepth 1 ! -name SQLite ! -name 'profile_photo_*' -exec rm -rf {} +
    # A readable copy, so a fixture change shows up as a reviewable diff.
    python3 -c 'import sqlite3, sys; print("\n".join(sqlite3.connect(sys.argv[1]).iterdump()))' "$FIXTURE/SQLite/nijii.db" >"$FIXTURE/nijii.sql"
    echo "fixture saved to ${FIXTURE#$ROOT/}"
    ;;
  restore)
    fresh_install "$2"
    # Let the app create its own data directories: root-created ones get SELinux labels
    # the app can't use. Then swap in only the database file (and any photos).
    "$ADB" shell am start -W -n "$PKG/.MainActivity" >/dev/null
    for _ in $(seq 1 30); do
      "$ADB" shell test -f "$FILES/SQLite/nijii.db" && break
      sleep 1
    done
    "$ADB" shell am force-stop "$PKG"
    owner="$("$ADB" shell stat -c %u:%g "$FILES/SQLite")"
    "$ADB" shell rm -f "$FILES/SQLite/nijii.db" "$FILES/SQLite/nijii.db-wal" "$FILES/SQLite/nijii.db-shm"
    "$ADB" push "$FIXTURE/SQLite/nijii.db" "$FILES/SQLite/nijii.db" >/dev/null
    for photo in "$FIXTURE"/profile_photo_*; do
      [ -e "$photo" ] && "$ADB" push "$photo" "$FILES/" >/dev/null
    done
    "$ADB" shell "chown $owner $FILES/SQLite/nijii.db $FILES/profile_photo_* 2>/dev/null; restorecon $FILES/SQLite/nijii.db $FILES/profile_photo_* 2>/dev/null; true"
    verify
    echo "restore ok: the committed fixture reads back"
    ;;
  *)
    sed -n '2,10p' "$0"; exit 2 ;;
esac
