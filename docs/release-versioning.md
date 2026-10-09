# Release Versioning Guide

The apps track three versions:

- App version (`version` in `version.json`): the public version users see in the stores.
- Build numbers (`android.versionCode`, `ios.buildNumber` in `version.json`): monotonically
  increasing identifiers the stores require.
- Database schema version (Room, `kmp/shared/.../db/AppDatabase.kt`): the local data model.

## Rules

1. Releases are cut by pushing a `v*` tag (for example `v1.4.0`). The release workflows stamp
   the tag's version and their run number (the build number) into `version.json` before
   building, so `version.json` in the repo only needs to be right for local builds.
2. If the database structure changes:
   - Bump the Room database version and add a migration (`Migrations.kt`).
   - Update `spec/db-schema.json` and keep the upgrade fixture test passing
     (`DatabaseTest`, `maestro/upgrade/`).
   - Never edit a migration after it has shipped.

## Quick Release Checklist

- CI green on `main`.
- Tag and push: `git tag v1.4.0 && git push origin v1.4.0`.
- Verify in Settings > About: version label and platform.
