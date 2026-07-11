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

| Layer         | Technology                                                                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Framework     | Expo SDK 57 (React Native 0.86)                                                                                                              |
| Navigation    | Expo Router (file-based)                                                                                                                     |
| Local storage | SQLite via `expo-sqlite`, anonymous device ID via `expo-secure-store` (native)                                                               |
| Analytics     | PostHog (session replay + events)                                                                                                            |
| Testing       | Vitest (unit), Playwright (e2e web)                                                                                                          |
| Lint / Format | oxlint / oxfmt (via `vite-plus`)                                                                                                             |
| CI / Releases | Self-hosted GitHub Actions workflows; stamp `app.json` versions from git tags — not EAS Build (`eas.json` is present but unused by releases) |

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

| Script                  | Description                          |
| ----------------------- | ------------------------------------ |
| `npm start`             | Start Expo dev server                |
| `npm run android`       | Run on Android emulator              |
| `npm run ios`           | Run on iOS simulator                 |
| `npm run web`           | Run in browser                       |
| `npm test`              | Run unit tests (Vitest)              |
| `npm run test:coverage` | Unit tests with coverage report      |
| `npm run e2e`           | Run Playwright end-to-end tests      |
| `npm run lint`          | Lint with oxlint                     |
| `npm run typecheck`     | TypeScript type check                |
| `npm run format`        | Format with oxfmt                    |
| `npm run format:check`  | Check formatting with oxfmt          |
| `npm run prebuild`      | Generate native Android/iOS projects |
| `npm run build:apk`     | Build release APK locally            |
| `npm run build:aab`     | Build release AAB locally            |

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

## Data & Privacy

All app data is local-only — there is no cloud sync or backend server.

- Profile data (name, photo, medical conditions, medications, emergency contacts, destinations) is stored in local SQLite via `expo-sqlite`. This data is **not encrypted at rest**.
- An anonymous device ID is stored in `expo-secure-store` on native platforms, used only to identify a device to analytics without any personally identifying information.
- Nothing is sent off-device except anonymous analytics events (PostHog).

See the [privacy policy](https://backtosafety.app/privacy) for the full picture.

---

## CI

`.github/workflows/ci.yml` runs on every pull request and on push to `main`:

- `checks` job: lint (oxlint), typecheck, format check (oxfmt), and unit tests (Vitest)
- `e2e` job: Playwright end-to-end tests against the web build

---

## Releases

Releases are built by self-hosted GitHub Actions workflows (`.github/workflows/android-release.yml`, `ios-release.yml`), triggered by pushing a `v*` tag. These workflows stamp the real shipped version (and build/version code) into `app.json` from the git tag at build time — they do **not** use EAS Build. `eas.json` exists in the repo but is not used by the release process.

See [`docs/release-step-by-step.md`](docs/release-step-by-step.md) for the release process and [`docs/android-signing-and-release.md`](docs/android-signing-and-release.md) for Android signing setup.

The `version` field in `package.json` (and `package-lock.json`) is informational only — it is not what ships. The actual shipped version comes from the git tag stamped into `app.json` at release time.

Current version: 1.3.3 (build 13)

- Android package: `com.backtosafety.app`
- iOS bundle ID: `com.backtosafety.app`
