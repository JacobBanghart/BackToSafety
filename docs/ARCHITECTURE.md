# Architecture

Back to Safety is two native UIs on one Rust core:

| Part | Where | What |
| --- | --- | --- |
| Rust core | `core/` | Models, the SQLite store (rusqlite, the Room v2 schema), migrations, the emergency protocol, readout text, formatters, i18n, analytics events, EmergencyAway, AppClock. No UI and no platform code: the apps implement its foreign traits (`AlertScheduler`, `AnalyticsSink`). `core/uniffi-bindgen` generates the bindings. Why Rust: `docs/decisions/rust-core.md`. |
| Android app | `android/` | Jetpack Compose UI (the `app` module). Gradle builds the core with cargo-ndk (arm64-v8a, armeabi-v7a, x86_64, x86), generates the Kotlin bindings (`com.backtosafety.core.ffi`) and reaches it through JNA; thin adapters in `app/core` keep the UI call sites (`Translate`, `Store`, `Analytics`, `AppClock`). |
| iOS app | `ios/` | SwiftUI UI. `mise run ios:core` builds the core into an XCFramework with the Swift bindings; adapters in `Core/`. `project.yml` generates the Xcode project (`xcodegen generate`). |
| Contracts | `spec/` | What both apps must agree on: design tokens, analytics events, testIDs, the DB schema and seed rows, logic vectors, the feature inventory, screenshot goldens and fidelity marks. |
| Device harness | `maestro/` | Maestro flows, screen captures and comparisons, reorder gestures and upgrade tests, run against both apps. |
| Website | `site/` | The public site and privacy policy (GitHub Pages). |

Both apps replaced a React Native app (removed 2026-10-09) and keep its identifiers
(`com.backtosafety.app`), its database location and schema, and its data, so they install over
it as an update. `docs/history/PARITY_PLAN.md` records how parity was established and held, and
`docs/history/RUST_CORE_PORT_LOG.md` how the shared core moved from Kotlin Multiplatform to Rust. Code comments
that cite an RN file ("Port of app/contacts.tsx") point into that history, before the removal
commit.

## Shared resources

- `i18n/locales/<lang>/<namespace>.json`: every string, read by both apps (`Translations` in the
  Rust core implements the i18next subset the strings use).
- `assets/images/logo-full.png`: the welcome logo.
- `version.json`: the version and build numbers; the release workflows stamp them from the tag.

## Generated code

- `spec/analytics-events.json` → `AnalyticsEvent` (`core/build.rs`, then through UniFFI).
- `spec/design-tokens.json` → `DesignTokens`, per app because it is UI (`generateDesignTokens` in
  `android/app`, `generate-design-tokens.py` for iOS).

## Checks

Fast, no device (`mise run check`, `mise run lint`, `mise run snapshots`; all in CI). Cargo and
Gradle builds run through `scripts/heavy.sh`, which queues them (`CLAUDE.md`, "Load limits on the devbox"):

- `spec/check.py`: testIDs in both apps, feature coverage, locale parity, option lists vs the
  schema.
- `cargo test -p nijii-core` (`core/tests`): spec/vectors, the store and migrations (against
  the committed upgrade fixture and `spec/room-schema-v2.json`), EmergencyAway, the FFI surface.
- `AnalyticsParityTest` (`android/app/src/test`): both apps and the core send exactly the events
  in `spec/analytics-events.json`.
- ktlint and SwiftFormat (`.editorconfig`, `.swiftformat`).
- Snapshot tests of every main screen in light, dark and large text. Android
  (`android/app/src/test`, Robolectric + Roborazzi) runs anywhere; iOS
  (`ios/BackToSafetyTests`, swift-snapshot-testing) runs on CI's macOS runner, which is its
  reference environment. To re-record iOS after a deliberate change, delete the affected
  images under `__Snapshots__` and push: CI records them, fails, and uploads them as the
  `ios-snapshots` artifact; `mise run ios:snapshots` copies them in to commit.

On devices (the devbox for Android, the Mac for iOS; `maestro/README.md`):

- `mise run android:flows` / `ios:flows`: every Maestro flow and the reorder gestures.
- `mise run android:fidelity` / `ios:fidelity`: every screen against the goldens.
- From the devbox, `mise run ios:remote <task>` runs any of these on the Mac.

## Releases

Pushing a `v*` tag runs `android-release.yml` (signed AAB, attached to the GitHub release) and
`ios-release.yml` (archive on the self-hosted Mac runner, uploaded to App Store Connect). See
`docs/release-step-by-step.md`.
