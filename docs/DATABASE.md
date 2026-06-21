# Database Layer

All user data is stored locally on the device. The database layer abstracts the difference between native SQLite (`expo-sqlite`) and web AsyncStorage.

---

## 1. Platform Split

For each data area there are three files:

| File                        | Purpose                                                                              |
| --------------------------- | ------------------------------------------------------------------------------------ |
| `database/<area>.ts`        | Public re-export of the native implementation (fallback for non-web bundler targets) |
| `database/<area>.native.ts` | SQLite implementation                                                                |
| `database/<area>.web.ts`    | AsyncStorage implementation                                                          |

Callers import from `database/<area>` (without a platform suffix):

```ts
import { getProfile, saveProfile } from '@/database/profile';
```

The Metro/web bundler selects the correct implementation automatically.

---

## 2. Schema (`database/schema.ts`)

### Database configuration

```ts
export const DATABASE_NAME = 'nijii.db';
export const SCHEMA_VERSION = 1;
```

### Migration rules

- `MIGRATIONS` is a record keyed by target version.
- Migrations run sequentially inside a transaction, one version at a time.
- If any migration fails, the transaction is rolled back and app initialization fails fast.
- Never edit an already-released migration.
- See [Release Versioning](./release-versioning.md) for version-bumping rules.

### Tables

The version 1 migration creates the following tables:

| Table            | Purpose                              |
| ---------------- | ------------------------------------ |
| `schema_version` | Migration bookkeeping                |
| `profile`        | Single-row profile (id = 1)          |
| `contacts`       | Emergency contacts                   |
| `destinations`   | Likely/familiar destinations         |
| `incidents`      | Historical emergency incidents       |
| `safety_checks`  | Home and away safety checklist items |
| `settings`       | Key/value app settings               |
| `onboarding`     | Onboarding step completion state     |

### Default seed data

After migrations, these defaults are inserted:

#### Safety checks (`DEFAULT_SAFETY_CHECKS`)

- At Home
  - Visual supports on doors
  - Locks in high location
  - Door chimes
  - Geofence setup
  - Physical boundaries
- Away From Home
  - Alert caregivers/staff
  - Safety plan at common locations
  - Introduce to first responders
  - Evaluate locative technology
- Foundation
  - Social stories
  - Water safety classes
  - Safety responsibility

#### Onboarding steps (`ONBOARDING_STEPS`)

1. `welcome`
2. `profile_name`
3. `profile_photo`
4. `profile_appearance`
5. `emergency_contact`
6. `complete`

---

## 3. Storage Abstraction

### Native implementation (`database/storage.native.ts`)

Public API:

```ts
export const STORAGE_TYPE = 'sqlite';

export async function initializeDatabase(): Promise<void>;
export async function getDatabase(): Promise<SQLiteDatabase>;
export async function isOnboardingComplete(): Promise<boolean>;
export async function completeOnboardingStep(step: string, skipped?: boolean): Promise<void>;
export async function getCurrentOnboardingStep(): Promise<string | null>;
export async function resetOnboarding(): Promise<void>;
export async function getSetting(key: string): Promise<string | null>;
export async function saveSetting(key: string, value: string): Promise<void>;
export async function clearAllData(): Promise<void>;
export async function closeDatabase(): Promise<void>;
export async function getDatabaseSchemaVersion(): Promise<number>;
```

### Web implementation (`database/storage.web.ts`)

Same public API, but data is stored as JSON under AsyncStorage keys prefixed with `@nijii/`:

- `@nijii/schema_version`
- `@nijii/profile`
- `@nijii/contacts`
- `@nijii/destinations`
- `@nijii/incidents`
- `@nijii/safety_checks`
- `@nijii/onboarding`
- `@nijii/settings`

---

## 4. Profile (`database/profile.ts`)

### Type

```ts
export interface Profile {
  id?: number;

  // Personal Info
  name: string;
  nickname?: string;
  dateOfBirth?: string;
  photoUri?: string;
  height?: string;
  weight?: string;
  hairColor?: string;
  eyeColor?: string;
  identifyingMarks?: string;

  // Medical & Behavioral
  medicalConditions?: string;
  medications?: string;
  allergies?: string;
  cognitiveStatus?: string;
  dominantHand?: 'left' | 'right' | 'unknown';
  mobilityLevel?: string;

  // Communication & De-escalation
  communicationPreference?: string;
  escalationSigns?: string;
  deescalationTechniques?: string;
  approachGuidance?: string;
  likes?: string;
  dislikesTriggers?: string;
  safeWord?: string;

  // Devices & IDs
  locativeDeviceInfo?: string;
  idBracelets?: string;
  medicAlertId?: string;
  medicAlertHotline?: string;

  // Metadata
  createdAt?: string;
  updatedAt?: string;
}
```

### Functions

```ts
export async function getProfile(): Promise<Profile | null>;
export async function hasProfile(): Promise<boolean>;
export async function saveProfile(profile: Partial<Profile>): Promise<void>;
export async function deleteProfile(): Promise<void>;
```

Notes:

- Native profile is stored as a single row with `id = 1`.
- `saveProfile` merges the partial profile with the existing row.
- `hasProfile` returns true if a profile exists (web also verifies `profile.name` is truthy).

---

## 5. Contacts (`database/contacts.ts`)

### Type

```ts
export interface Contact {
  id?: number;
  name: string;
  phone: string;
  relationship?: string;
  role?: 'primary_caregiver' | 'caregiver' | 'neighbor' | 'family' | 'friend' | 'other';
  address?: string;
  notifyOnEmergency: boolean;
  shareMedicalInfo: boolean;
  notes?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}
```

### Functions

```ts
export async function getContacts(): Promise<Contact[]>;
export async function getEmergencyContacts(): Promise<Contact[]>;
export async function getContact(id: number): Promise<Contact | null>;
export async function createContact(
  contact: Omit<Contact, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<number>;
export async function updateContact(id: number, contact: Partial<Contact>): Promise<void>;
export async function deleteContact(id: number): Promise<void>;
export async function getContactCount(): Promise<number>;
```

Notes:

- Native defaults: `role ?? 'other'`, `notify_on_emergency DEFAULT 1`, `share_medical_info DEFAULT 0`, `sort_order DEFAULT 0`.
- `getEmergencyContacts` filters `notifyOnEmergency = true`, sorted by `sortOrder`.

---

## 6. Destinations (`database/destinations.ts`)

### Type

```ts
export interface Destination {
  id?: number;
  name: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  category?:
    | 'water'
    | 'former_workplace'
    | 'church'
    | 'store'
    | 'restaurant'
    | 'friend_family'
    | 'walking_route'
    | 'other';
  reason?: string;
  distanceFromHome?: string;
  riskLevel?: 'high' | 'medium' | 'low';
  notes?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}
```

### Functions

```ts
export async function getDestinations(): Promise<Destination[]>;
export async function getHighRiskDestinations(): Promise<Destination[]>;
export async function getDestination(id: number): Promise<Destination | null>;
export async function createDestination(
  dest: Omit<Destination, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<number>;
export async function updateDestination(id: number, dest: Partial<Destination>): Promise<void>;
export async function deleteDestination(id: number): Promise<void>;
export async function getDestinationCount(): Promise<number>;
```

Notes:

- Native defaults: `category ?? 'other'`, `riskLevel ?? 'medium'`.
- `getDestinations` and `getHighRiskDestinations` sort by `sortOrder`.

---

## 7. Incidents (`database/incidents.ts`)

### Type

```ts
export interface Incident {
  id?: number;
  startedAt: string;
  endedAt?: string;
  outcome?: 'found' | 'found_by_other' | '911_called' | 'returned_home' | 'ongoing';

  // Location data
  lastSeenLat?: number;
  lastSeenLon?: number;
  lastSeenAccuracy?: number;
  foundLat?: number;
  foundLon?: number;
  foundLocationName?: string;

  // Context
  weather?: string;
  timeOfDay?: string;
  triggerIdentified?: string;
  wearing?: string;

  // Search details
  areasChecked?: string[];
  peopleContacted?: string[];

  // Notes
  notes?: string;
  createdAt?: string;
}
```

### Functions

```ts
export async function getIncidents(): Promise<Incident[]>;
export async function getRecentIncidents(): Promise<Incident[]>;
export async function getIncident(id: number): Promise<Incident | null>;
export async function createIncident(incident: Partial<Incident>): Promise<number>;
export async function updateIncident(id: number, incident: Partial<Incident>): Promise<void>;
export async function resolveIncident(
  id: number,
  outcome: Incident['outcome'],
  foundLocation?: { lat: number; lon: number; name?: string },
): Promise<void>;
export async function getIncidentCount(): Promise<number>;
export async function getIncidentPatterns(): Promise<{
  totalIncidents: number;
  byTimeOfDay: Record<string, number>;
  byOutcome: Record<string, number>;
  commonTriggers: string[];
  averageDurationMinutes: number | null;
}>;
```

Notes:

- `createIncident` defaults `startedAt` to now and `outcome` to `'ongoing'`.
- `resolveIncident` sets `endedAt` to now and writes the outcome plus optional found location.
- `getIncidentPatterns` returns aggregate statistics for potential insights screens.

---

## 8. Settings

Settings are arbitrary key/value strings. The database storage modules implement:

```ts
export async function getSetting(key: string): Promise<string | null>;
export async function saveSetting(key: string, value: string): Promise<void>;
```

### Known settings keys

| Key                   | Value                           | Used by                         |
| --------------------- | ------------------------------- | ------------------------------- |
| `theme_preference`    | `system`, `light`, or `dark`    | `ThemeContext`                  |
| `language_preference` | `en` or `es`                    | `i18n/index.ts`                 |
| `device_id`           | UUID                            | `utils/device-id.ts`, analytics |
| `active_emergency`    | JSON string of `EmergencyState` | `app/emergency.tsx`             |

### Unused seeded structures

The `safety_checks` table is created and seeded with default home/away/foundation items, but no screen currently displays or edits them. They are reserved for a future safety-checklist feature.

---

## 9. Migrations

To add a schema change:

1. Increment `SCHEMA_VERSION` in `database/schema.ts`.
2. Add a new `MIGRATIONS[version]` entry containing the required `ALTER TABLE`/`CREATE TABLE` statements.
3. Ensure there is a migration for every version between the user's current schema and the new target.
4. Update [Release Versioning](./release-versioning.md) notes if needed.
5. Never edit an older migration that has already been released.
