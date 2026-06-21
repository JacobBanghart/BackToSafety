# Architecture Overview

This document describes the high-level architecture of **Back to Safety** (repo: `nijii-app`): a privacy-first, local-only React Native/Expo application for caregivers of people with dementia or other wandering risks.

---

## 1. Design Principles

| Principle                       | How it manifests                                                                                                            |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| **Local-first / privacy-first** | All profile, contacts, destinations, and incidents are stored on-device only.                                               |
| **Offline capable**             | The emergency protocol and readout work without a network connection.                                                       |
| **Cross-platform**              | Code targets iOS, Android, and web, using platform split files where native APIs differ.                                    |
| **Fast emergency access**       | The 15-minute search protocol, 911 call, SMS alerts, and readout are reachable within one or two taps from the home screen. |
| **Internationalizable**         | Strings are externalized via `i18next`; English and Spanish resources are available.                                        |
| **Theme aware**                 | Automatic light/dark mode with a manual override in settings.                                                               |

---

## 2. Tech Stack

| Layer          | Technology                                                                                       |
| -------------- | ------------------------------------------------------------------------------------------------ |
| Framework      | Expo SDK 54 / React Native 0.81                                                                  |
| Navigation     | Expo Router (file-based)                                                                         |
| State (UI)     | React Context                                                                                    |
| Local storage  | SQLite via `expo-sqlite` (native), AsyncStorage (web)                                            |
| Secure storage | `expo-secure-store` (declared but not yet used; data is currently stored in SQLite/AsyncStorage) |
| Styling        | React Native StyleSheet + design token system                                                    |
| Animation      | `react-native-reanimated`                                                                        |
| Gestures       | `react-native-gesture-handler`                                                                   |
| Images         | `expo-image`                                                                                     |
| Camera/photos  | `expo-image-picker`                                                                              |
| Contacts       | `expo-contacts`                                                                                  |
| SMS            | `expo-sms`                                                                                       |
| Analytics      | PostHog (`posthog-react-native` + session replay)                                                |
| Localization   | `i18next` / `react-i18next`                                                                      |
| Unit tests     | Vitest                                                                                           |
| E2E tests      | Playwright                                                                                       |
| CI / builds    | EAS Build + EAS Submit                                                                           |

---

## 3. Provider Hierarchy

Providers are nested in `app/_layout.tsx` from outermost to innermost:

```
I18nextProvider
└── ThemeProvider
    └── OnboardingProvider
        └── ProfileProvider
            └── PostHogProvider
                └── RootLayoutNav
                    └── SafeAreaProvider
                        └── GestureHandlerRootView
                            └── NavigationThemeProvider
                                └── Stack
```

- `OnboardingProvider` initializes the database and refreshes onboarding state.
- `ProfileProvider` loads profile, contacts, and incidents once onboarding is ready.
- `PostHogProvider` is always present but disabled when `EXPO_PUBLIC_POSTHOG_KEY` is missing.

---

## 4. File Structure

```
app/                  # Expo Router screens (file-based routing)
  (tabs)/             # Tab navigator (tab bar hidden)
  onboarding/         # Onboarding flow
  _layout.tsx         # Root layout + providers
  index.tsx           # Home dashboard
  emergency.tsx       # 15-minute emergency search protocol
  contacts.tsx        # Emergency contacts
  destinations.tsx    # Familiar places
  profile.tsx         # Profile editor
  readout.tsx         # 911 / first-responder info sheet
  settings.tsx        # App settings
components/           # Shared UI components
constants/            # Design tokens: Colors, Typography, Spacing, Shadows
context/              # React contexts (Profile, Theme, Onboarding)
database/             # Data layer (schema, storage, entity modules)
  *.native.ts         # SQLite implementation
  *.web.ts            # AsyncStorage implementation
  *.ts                # Public re-export or platform-agnostic types
hooks/                # Shared hooks
i18n/                 # Localization resources
utils/                # Analytics, PostHog init, phone utilities, navigation
docs/                 # Release guides and documentation
site/                 # GitHub Pages site (privacy policy)
```

---

## 5. Routing Map

Expo Router creates routes from files automatically.

| Route                    | File                            | Purpose                    |
| ------------------------ | ------------------------------- | -------------------------- |
| `/`                      | `app/index.tsx`                 | Home dashboard             |
| `/(tabs)`                | `app/(tabs)/_layout.tsx`        | Hidden tab shell           |
| `/(tabs)/index`          | `app/(tabs)/index.tsx`          | Redirects to `/`           |
| `/onboarding`            | `app/onboarding/_layout.tsx`    | Onboarding stack           |
| `/onboarding/index`      | `app/onboarding/index.tsx`      | Welcome / theme / language |
| `/onboarding/name`       | `app/onboarding/name.tsx`       | Name + nickname            |
| `/onboarding/photo`      | `app/onboarding/photo.tsx`      | Photo capture/selection    |
| `/onboarding/appearance` | `app/onboarding/appearance.tsx` | Physical description       |
| `/onboarding/contact`    | `app/onboarding/contact.tsx`    | First emergency contact    |
| `/onboarding/complete`   | `app/onboarding/complete.tsx`   | Completion screen          |
| `/emergency`             | `app/emergency.tsx`             | Emergency protocol         |
| `/readout`               | `app/readout.tsx`               | 911 readout                |
| `/profile`               | `app/profile.tsx`               | Profile editor             |
| `/contacts`              | `app/contacts.tsx`              | Contacts manager           |
| `/destinations`          | `app/destinations.tsx`          | Destinations manager       |
| `/settings`              | `app/settings.tsx`              | Settings                   |
| `/+not-found`            | `app/+not-found.tsx`            | 404 screen                 |

`app/_layout.tsx` guards entry: non-onboarded users are replaced into `/onboarding`, and onboarded users cannot stay inside `/onboarding`.

---

## 6. Data Flow

1. **App launch**
   - `OnboardingProvider` calls `initializeDatabase()`.
   - Migrations run and seed default data.
   - Onboarding state is loaded.

2. **Home screen focus**
   - `ProfileContext` loads profile, contacts, and incidents.
   - An active emergency state is read from settings and surfaced if still valid.

3. **User edits data**
   - UI updates local form state.
   - On save, the relevant context or database module writes changes.
   - Context refreshes state and the UI re-renders.

4. **Emergency protocol**
   - State is persisted to `settings.active_emergency` as JSON.
   - Checked steps, timer start, and clothing notes survive app restarts and process death.

## Notes on currently unused structures

- `expo-secure-store` is declared as a dependency but is not imported or used. Data is stored in SQLite (native) or AsyncStorage (web).
- The `safety_checks` table is seeded but not yet surfaced in any screen.
- The incidents module exposes `getIncidentPatterns()`, but there is no incident-history or insights screen yet.

---

## 7. Platform Abstractions

Native and web implementations live side by side. The bare `.ts` file re-exports the native implementation for bundler default behavior, while the web override is selected by `.web.ts`.

| Concern         | Native (`*.native.ts`)                | Web (`*.web.ts`)                |
| --------------- | ------------------------------------- | ------------------------------- |
| Storage backend | `expo-sqlite`                         | `AsyncStorage`                  |
| Storage type    | `STORAGE_TYPE = 'sqlite'`             | `STORAGE_TYPE = 'asyncstorage'` |
| Photo storage   | `expo-file-system` document directory | Same image URI persisted        |
| Contacts import | `expo-contacts`                       | Not supported                   |
| SMS             | `expo-sms`                            | `sms:` URL fallback             |
| Color scheme    | `useColorScheme` from RN              | Hydration-safe static `'light'` |

All modules expose the same public TypeScript interfaces, so callers do not branch on platform.

---

## 8. Analytics

PostHog is configured in `utils/posthog.ts` and wrapped at the root in `app/_layout.tsx`:

- Enabled only when `EXPO_PUBLIC_POSTHOG_KEY` is present.
- Session replay is enabled with text and image masking.
- A stable device ID is generated with `expo-crypto` and used to `.identify()`.
- `utils/analytics.ts` exposes a typed `track(event, properties)` helper covering onboarding, emergency, contacts/destinations, profile, readout, and settings events.

See [Analytics Events Reference](./ANALYTICS_EVENTS.md) for the full event catalog.

---

## 9. Theming

- `ThemeContext` stores `themePreference` (`system`, `light`, `dark`).
- `colorScheme` is the effective resolved scheme.
- `Colors.ts` exposes `Colors.light` and `Colors.dark` tokens; `useThemeColor` resolves a token name to the current scheme's value.
- The status bar and navigation theme react to `colorScheme`.

---

## 10. Build & Release Pipelines

See also:

- [Release Step by Step](./release-step-by-step.md)
- [Release Versioning](./release-versioning.md)
- [Android Signing and Release](./android-signing-and-release.md)
- [iOS CI Setup Guide](./ios-ci-setup-guide.md)

Builds are driven by `eas.json`:

- `preview` → internal distribution APK.
- `production` → auto-incrementing build numbers, submitted to stores.

Local Android release builds:

```bash
npm run prebuild:android
npm run build:apk   # build/BackToSafety.apk
npm run build:aab   # build/BackToSafety.aab
```
