# State Management

This document describes the React contexts that manage global UI state. Local data persistence is handled by the database layer documented in [DATABASE.md](./DATABASE.md).

---

## 1. Context Map

| Context             | File                            | Responsibility                                              |
| ------------------- | ------------------------------- | ----------------------------------------------------------- |
| `ThemeContext`      | `context/ThemeContext.tsx`      | Theme preference and resolved color scheme                  |
| `OnboardingContext` | `context/OnboardingContext.tsx` | Onboarding completion, current step, DB init                |
| `ProfileContext`    | `context/ProfileContext.tsx`    | Profile, contacts, emergency contacts, last seen, incidents |

All contexts are mounted in `app/_layout.tsx`.

---

## 2. ThemeContext

### State

```ts
type ThemeState = {
  themePreference: 'system' | 'light' | 'dark';
  colorScheme: 'light' | 'dark';
  setThemePreference: (preference: 'system' | 'light' | 'dark') => Promise<void>;
};
```

### Behavior

- On mount, loads `theme_preference` from settings.
- `colorScheme` is the effective resolved scheme (`system` resolves to the device value).
- `setThemePreference` persists the choice and updates state.
- Used by `useThemeColor` and `ThemedText`/`ThemedView`.

---

## 3. OnboardingContext

### State

```ts
type OnboardingState = {
  isLoading: boolean;
  isOnboarded: boolean;
  currentStep: string;
  completeStep: (step: string) => Promise<void>;
  refreshOnboardingState: () => Promise<void>;
};
```

### Behavior

- `useEffect` on mount calls `initializeDatabase()` then `refreshOnboardingState()`.
- `refreshOnboardingState` reads the onboarding progress and profiles state to determine `isOnboarded` and `currentStep`.
- `completeStep` marks a step complete and refreshes state.
- `skipped` is handled by the database layer; the React context API does not expose that parameter.
- `isOnboarded` is used by `app/_layout.tsx` to guard routes.

### Onboarding steps

Database step keys mirror the onboarding route order:

1. `welcome` — app purpose and theme selection
2. `profile_name` — required name + nickname
3. `profile_photo` — optional photo
4. `profile_appearance` — optional physical description
5. `emergency_contact` — optional first contact
6. `complete` — onboarding finished

---

## 4. ProfileContext

### State

```ts
export type LastSeen = {
  time?: string; // ISO timestamp
  coords?: { lat: number; lon: number; accuracy?: number };
};

export type Incident = {
  at: string; // ISO timestamp
  outcome: 'found' | 'not_found' | '911_called';
  location?: { lat: number; lon: number; accuracy?: number };
  notes?: string;
  checked?: string[]; // checklist step IDs
};

type ProfileState = {
  isLoading: boolean;
  profile: Profile | null;
  contacts: Contact[];
  emergencyContacts: Contact[];
  lastSeen: LastSeen;
  incidents: Incident[];

  refreshProfile: () => Promise<void>;
  refreshContacts: () => Promise<void>;
  saveProfile: (p: Partial<Profile>) => Promise<void>;
  setLastSeen: (ls: LastSeen) => void;
  addIncident: (i: Incident) => void;
};
```

### Behavior

- Loads after onboarding initialization finishes.
- `refreshProfile` reads from `database/profile.ts`.
- `refreshContacts` reads from `database/contacts.ts` and filters `notifyOnEmergency` into `emergencyContacts`.
- `saveProfile` delegates to `saveProfile(partial)` in the database, then refreshes.
- `setLastSeen` and `addIncident` are in-memory state setters.

### Last-seen data

`lastSeen` is primarily populated during the emergency protocol and consumed by `/readout`. It is transient: saving it is the caller's responsibility if persistence is required.

### Incidents

`incidents` stored in context are lightweight. Full incident records (with searchable patterns) are persisted in the `incidents` table.

---

## 5. When to Use Context vs. Database

| Use case                                       | Recommendation                                             |
| ---------------------------------------------- | ---------------------------------------------------------- |
| Theme preference                               | `ThemeContext`                                             |
| Onboarding progress                            | `OnboardingContext`                                        |
| Current profile snapshot (rendering)           | `ProfileContext`                                           |
| Storing profile edits permanently              | Database module (`database/profile.ts`)                    |
| Querying contacts/destinations in a screen     | Database module or `ProfileContext`                        |
| Cross-screen ephemeral state (e.g., last seen) | `ProfileContext` with consumer persistence if needed       |
| Active emergency state                         | Persisted to database settings, read/written by the screen |
