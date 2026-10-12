# Persisted data contract (L5)

The apps ship under the same package and bundle ID (`com.backtosafety.app`) as the React Native
app they replaced (removed 2026-10-09) and installed **over** it: they opened its data as-is,
with no export step and no re-onboarding. This file is the contract for that data.
`spec/db-schema.json` pins the table shapes (generated from the RN app's schema; Room's
migration and `DatabaseTest` hold the shared store to it and to the committed fixture).

## Database

|                         |                                                                                                                                               |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| File                    | `nijii.db`, opened by Room (bundled SQLite)                                                                                                   |
| Android path            | `<filesDir>/SQLite/nijii.db` (`/data/data/com.backtosafety.app/files/SQLite/nijii.db`)                                                        |
| iOS path                | `<Documents>/SQLite/nijii.db`                                                                                                                 |
| Schema version          | `PRAGMA user_version` (Room); currently `2` (`DATABASE_VERSION`). The RN app (version 1) kept it in a `schema_version` table instead and left `user_version` 0 |
| Migrations              | Room migrations, run in order (`MIGRATION_1_2` rebuilds the RN tables in Room's shape). Never edit a shipped migration; add the next number.  |
| Seeding on every launch | 12 `safety_checks` rows and 6 `onboarding` rows, all `INSERT OR IGNORE`                                                                       |

Conventions: booleans are `INTEGER` 0/1. `created_at`/`updated_at` default to SQLite's
`CURRENT_TIMESTAMP` (`YYYY-MM-DD HH:MM:SS`, UTC). Enum columns are enforced with `CHECK`
constraints (see `db-schema.json`), and the UI options must stay inside them
(`spec/check.py` checks this).

## Rows with special meaning

- **`profile`**: a single row, `id = 1` (enforced by `CHECK`). Columns are snake_case versions
  of the `Profile` fields (`dateOfBirth` → `date_of_birth`).
  - `date_of_birth`: text, `MM/DD/YYYY` (see `spec/vectors/age.json`).
  - `mobility_level`: comma-separated English option labels (`Uses cane, Has vehicle`). A
    custom "Other" value is stored as its free text. Labels stay English in storage;
    `describeMobility` (`spec/vectors/mobility.json`) translates them for display.
  - `photo_uri`: an **absolute** `file://` URI to `<Documents>/profile_photo_<epochMs>.jpg`.
    On iOS the container path changes across app updates, so readers resolve it
    by file name inside the current documents directory (`photoFileName`, `spec/vectors/photo.json`),
    falling back to the stored path. Both apps do this on read (F-21).
    Only the file the profile points at is kept: saving a different photo, and Delete Account,
    remove the other `profile_photo_*` files (and, on Android, the camera's and cropper's temp
    files in the cache).
- **`onboarding`**: steps `welcome`, `profile_name`, `profile_photo`, `profile_appearance`,
  `emergency_contact`, `complete`. The user counts as onboarded when no row has
  `completed = 0 AND skipped = 0`.
- **`contacts`** / **`destinations`**: display order is `sort_order`, then `created_at`.
  `contacts.notify_on_emergency = 1` marks the SMS alert recipients.
- **`incidents`**: one row per emergency. Created as `ongoing` when it starts; Call 911 sets
  `outcome = '911_called'`; Found sets `outcome = 'found'` and `ended_at`; ending without an
  outcome sets only `ended_at`. Each update records `areas_checked` (JSON array of checked
  step IDs) and `wearing`. Emergencies started before this existed get their row on first update.

## `settings` keys

| Key                   | Value                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `active_emergency`    | JSON `{"startedAt": ISO-8601, "wearing": string, "checkedSteps": [stepId…], "isActive": true, "incidentId"?: number}`. `incidentId` was added after release, so older records lack it. Set to `''` (empty string, not deleted) when the emergency ends. Read it with `parseActiveEmergency` (`spec/vectors/emergency.json`): invalid or inactive records count as no emergency. An active emergency resumes with `secondsRemaining(startedAt, now)`. |
| `emergency_away_from` | Epoch ms when the time away from the emergency screen began (its last countdown tick), or `''`. Set on leaving the screen with an emergency running; read and cleared on returning, to catch up on an alert that came due (`EmergencyAway`, `spec/vectors/alerts.json#catchUpAlert`). After a phone restart mid-emergency (Android), read to post the alert that came due, and set to the emergency's start if empty (the screen was showing; when it stopped is unknown). Cleared when the emergency ends. |
| `notifications_prompted` | `1` once the notification permission prompt has been shown. |
| `device_id`           | UUID v4, created on first launch. Analytics identity; must survive the upgrade unchanged.                                                                                                                                                                                                                                                                                                                                                            |
| `theme_preference`    | `system` \| `light` \| `dark`. Missing means `system`.                                                                                                                                                                                                                                                                                                                                                                                               |
| `language_preference` | `en` \| `es`. Missing means the device locale if shipped, else `en`.                                                                                                                                                                                                                                                                                                                                                                                 |

## Restored at launch

- `lastSeen` (the time; the app collects no location) is rebuilt from `active_emergency` at launch, so a restart
  mid-emergency keeps the readout's last-seen time.
