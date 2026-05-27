# Back to Safety

A React Native / Expo app for caregivers of people with dementia or other wandering risks. Profiles, emergency contacts, and familiar destinations are stored locally on device.

- Privacy policy: https://backtosafety.app/privacy
- GitHub Pages: https://jacobbanghart.github.io/BackToSafety/

---

## Features

- Emergency protocol: 11-step guided checklist with a 15-minute countdown timer, haptic alerts, one-tap 911 calling, and SMS alerts to emergency contacts
- Profile: name, photo, medical conditions, medications, cognitive status, and de-escalation techniques
- Emergency contacts: import from the device address book or add manually
- Familiar destinations: saved places surfaced during the emergency checklist
- Readout: formatted profile summary for first responders, one tap away during an emergency
- Onboarding flow for first-time setup
- i18n via `i18next` / `react-i18next`
- Automatic light/dark theme

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Expo SDK 54 (React Native 0.81) |
| Navigation | Expo Router (file-based) |
| Local storage | SQLite via `expo-sqlite`, secure values via `expo-secure-store` |
| Analytics | PostHog (session replay + events) |
| Testing | Vitest (unit), Playwright (e2e web) |
| CI / Releases | EAS Build + EAS Submit |

---

## Getting Started

```bash
npm install
npx expo start
```

Run on a specific target:

```bash
npm run android   # Android emulator
npm run ios       # iOS simulator
npm run web       # Browser
```

---

## Scripts

| Script | Description |
|--------|-------------|
| `npm start` | Start Expo dev server |
| `npm run android` | Run on Android emulator |
| `npm run ios` | Run on iOS simulator |
| `npm run web` | Run in browser |
| `npm test` | Run unit tests (Vitest) |
| `npm run test:coverage` | Unit tests with coverage report |
| `npm run e2e` | Run Playwright end-to-end tests |
| `npm run lint` | Lint with ESLint |
| `npm run typecheck` | TypeScript type check |
| `npm run format` | Format with Prettier |
| `npm run prebuild` | Generate native Android/iOS projects |
| `npm run build:apk` | Build release APK locally |
| `npm run build:aab` | Build release AAB locally |

---

## Project Structure

```
app/               # Expo Router screens (file-based routing)
  (tabs)/          # Tab navigator screens
  onboarding/      # Onboarding flow
  emergency.tsx    # Emergency protocol screen
  contacts.tsx     # Emergency contacts
  destinations.tsx # Familiar places
  profile.tsx      # Profile editor
  readout.tsx      # First-responder info sheet
  settings.tsx     # App settings
components/        # Shared UI components
constants/         # Colors, typography, spacing, shadows
context/           # React contexts (Profile, Theme, Onboarding)
database/          # SQLite data layer (profile, contacts, destinations, incidents)
i18n/              # Localization strings
utils/             # Analytics, navigation helpers, phone utilities
docs/              # Release guides, signing docs, store listing templates
```

---

## Releases

Builds use EAS with auto-incrementing build numbers. See [`docs/release-step-by-step.md`](docs/release-step-by-step.md) for the release process and [`docs/android-signing-and-release.md`](docs/android-signing-and-release.md) for Android signing setup.

Current version: 1.3.3 (build 13)

- Android package: `com.backtosafety.app`
- iOS bundle ID: `com.backtosafety.app`
