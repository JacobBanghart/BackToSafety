# Architecture

Back to Safety is two native apps on one shared Kotlin core:

| Part | Where | What |
| --- | --- | --- |
| Shared core | `kmp/shared` | Kotlin Multiplatform: models, the SQLite store (Room, bundled SQLite), migrations, the emergency protocol, readout text, formatters, i18n, analytics events, design tokens. Built for Android, the JVM (tests) and iOS (a static XCFramework, `Shared`). |
| Android app | `kmp/androidApp` | Jetpack Compose UI on the shared core. |
| iOS app | `kmp/iosApp` | SwiftUI UI on the shared core. `project.yml` generates the Xcode project (`xcodegen generate`). |
| Contracts | `spec/` | What both apps must agree on: design tokens, analytics events, testIDs, the DB schema and seed rows, logic vectors, the feature inventory, screenshot goldens and fidelity marks. |
| Device harness | `maestro/` | Maestro flows, screen captures and comparisons, reorder gestures and upgrade tests, run against both apps. |
| Website | `site/` | The public site and privacy policy (GitHub Pages). |

Both apps replaced a React Native app (removed 2026-10-09) and keep its identifiers
(`com.backtosafety.app`), its database location and schema, and its data, so they install over
it as an update. `PARITY_PLAN.md` records how parity was established and held. Code comments
that cite an RN file ("Port of app/contacts.tsx") point into that history, before the removal
commit.

## Shared resources

- `i18n/locales/<lang>/<namespace>.json`: every string, read by both apps (`Translations` in the
  shared core implements the i18next subset the strings use).
- `assets/images/logo-full.png`: the welcome logo.
- `version.json`: the version and build numbers; the release workflows stamp them from the tag.

## Generated code

- `spec/design-tokens.json` → `DesignTokens` and `spec/analytics-events.json` →
  `AnalyticsEvent` (Gradle tasks in `kmp/buildSrc`).

## Checks

- `python3 spec/check.py`: testIDs present in both apps, feature coverage references resolve.
- `./gradlew :shared:jvmTest` (in `kmp/`): logic vectors, the store and migrations (against the
  committed upgrade fixture), analytics parity between the apps.
- CI builds both apps on every push; the Maestro harness runs on the devbox (Android) and the
  Mac (iOS), see `maestro/README.md`.

## Releases

Pushing a `v*` tag runs `android-release.yml` (signed AAB, attached to the GitHub release) and
`ios-release.yml` (archive on the self-hosted Mac runner, uploaded to App Store Connect). See
`docs/release-step-by-step.md`.
