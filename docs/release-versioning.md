# Release Versioning Guide

This app tracks three versions:

- App version (`expo.version`) - public semantic version users see in stores.
- Platform build version (`ios.buildNumber`, `android.versionCode`) - monotonically increasing build identifiers required by App Store and Play Store.
- Database schema version (`SCHEMA_VERSION`) - local data model version for migrations.

## Rules

1. Every store upload must increment at least one platform build version:
   - iOS: increment `expo.ios.buildNumber`.
   - Android: increment `expo.android.versionCode`.
2. Bump `expo.version` for every release train (for example `1.0.1`, `1.1.0`).
3. If local database structure changes:
   - Increment `SCHEMA_VERSION` in `database/schema.ts`.
   - Add a new entry in `MIGRATIONS` using the same version number.
   - Never edit old migration entries after release.

## Migration Safety

Native SQLite migrations run one version at a time, inside a transaction.

- A migration must exist for every version between current and target.
- On any migration failure, the transaction is rolled back and app init fails fast.

## Quick Release Checklist

- Update `app.json`:
  - `expo.version`
  - `expo.ios.buildNumber`
  - `expo.android.versionCode`
- If schema changed, update `database/schema.ts` migration version.
- Verify in app Settings > About:
  - Version label
  - Platform
  - DB Schema (developer mode)
