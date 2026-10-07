# Persisted data contract (L5)

The Kotlin app ships under the same package and bundle ID (`com.backtosafety.app`) and installs
**over** the RN app. On first launch it must open the RN app's data as-is: no export step and no
re-onboarding. This file is the contract. `spec/db-schema.json` pins the table shapes, generated
from `database/schema.ts` by `spec/db-schema.test.ts`.

Web builds keep data in AsyncStorage instead. That path is out of scope for the Kotlin apps.

## Database

|                         |                                                                                                                                               |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| File                    | `nijii.db`, opened by `expo-sqlite`                                                                                                           |
| Android path            | `<filesDir>/SQLite/nijii.db` (`/data/data/com.backtosafety.app/files/SQLite/nijii.db`)                                                        |
| iOS path                | `<Documents>/SQLite/nijii.db`                                                                                                                 |
| Schema version          | `MAX(version)` in `schema_version`; currently `1`                                                                                             |
| Migrations              | Run in order, each in its own transaction, then `INSERT OR REPLACE INTO schema_version`. Never edit a shipped migration; add the next number. |
| Seeding on every launch | 12 `safety_checks` rows and 6 `onboarding` rows, all `INSERT OR IGNORE`                                                                       |

Conventions: booleans are `INTEGER` 0/1. `created_at`/`updated_at` default to SQLite's
`CURRENT_TIMESTAMP` (`YYYY-MM-DD HH:MM:SS`, UTC). Enum columns are enforced with `CHECK`
constraints (see `db-schema.json`), and the UI options must stay inside them
(`spec/testids.test.ts` checks this).

## Rows with special meaning

- **`profile`**: a single row, `id = 1` (enforced by `CHECK`). Columns are snake_case versions
  of the `Profile` fields (`dateOfBirth` → `date_of_birth`).
  - `date_of_birth`: text, `MM/DD/YYYY` (see `spec/vectors/age.json`).
  - `mobility_level`: comma-separated English option labels (`Uses cane, Has vehicle`). A
    custom "Other" value is stored as its free text. Labels stay English in storage;
    `describeMobility` (`spec/vectors/mobility.json`) translates them for display.
  - `photo_uri`: an **absolute** `file://` URI to `<Documents>/profile_photo_<epochMs>.jpg`.
    On iOS the container path changes across app updates (F-21), so readers should resolve
    by **basename** inside the documents directory, not by the stored path.
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

| Key                   | Value                                                                                                                                                                                                                                                                     |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `active_emergency` | JSON `{"startedAt": ISO-8601, "wearing": string, "checkedSteps": [stepId…], "isActive": true, "incidentId"?: number, "lastSeenCoords"?: {"lat", "lon", "accuracy"?}}`. The two optional fields were added after release, so older records lack them. Set to `''` (empty string, not deleted) when the emergency ends. Read it with `parseActiveEmergency` (`spec/vectors/emergency.json`): invalid or inactive records count as no emergency. An active emergency resumes with `secondsRemaining(startedAt, now)`. |
| `device_id`           | UUID v4, created on first launch. Analytics identity; must survive the upgrade unchanged.                                                                                                                                                                                 |
| `theme_preference`    | `system` \| `light` \| `dark`. Missing means `system`.                                                                                                                                                                                                                    |
| `language_preference` | `en` \| `es`. Missing means the device locale if shipped, else `en`.                                                                                                                                                                                                      |

## Restored at launch

- `lastSeen` (time and coordinates) is rebuilt from `active_emergency` at launch, so a restart
  mid-emergency keeps the readout's last-seen time.
