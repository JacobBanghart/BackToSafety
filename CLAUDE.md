# nijii-app (Back to Safety)

Two native UIs on one Rust core: `android/` (Jetpack Compose) and `ios/` (SwiftUI), both on
`core/` through UniFFI. See `docs/ARCHITECTURE.md`, and `docs/decisions/rust-core.md` for why the
core is Rust.

## Change both apps together

A user-visible change lands in the Android and the iOS app in the same commit, and logic goes
in `core/` rather than in either UI. What the apps must agree on lives in `spec/`:

- New or renamed testIDs: `spec/testids.json`, then `python3 spec/check.py`.
- Analytics events: `spec/analytics-events.json` (`AnalyticsEvent` is generated from it).
- Colors, type and spacing: `spec/design-tokens.json` (`DesignTokens` is generated from it, in each app).
- Strings: `i18n/locales/` (both apps read the same JSON).
- Logic with fixed inputs and outputs: `spec/vectors/`, run by `core/tests/vectors.rs`.

## Before pushing

`mise run check` (contracts, the core's tests and analytics parity), `mise run lint` (ktlint,
SwiftFormat; `FIX=1` fixes) and `mise run snapshots` (Android screens; `RECORD=1` after a
deliberate visual change). CI runs these, the iOS snapshot tests, and builds both apps. A
visual change re-records both platforms' snapshots in the same commit (`docs/ARCHITECTURE.md`).

## The Rust core

- The core stays platform-free. What only a platform can do comes in through foreign traits the
  apps implement: `AlertScheduler` (AlarmManager / UNUserNotificationCenter) and the analytics
  sink (each app's PostHog SDK).
- The apps reach it through thin adapters (`android/app/.../app/core/`, `ios/BackToSafety/Core/`)
  over the generated bindings. A core change that the UI uses goes through the adapter, not
  into the screens.
- Run cargo from `core/` (the mise tasks, Gradle and `ios:core` do): `core/.cargo/config.toml`
  trims the bundled SQLite, and a test fails if it isn't applied.
- The database file, path, schema and Room identity hash must not change without a migration
  (`core/src/store/migrations.rs`, `docs/release-versioning.md`); installs from the KMP and RN
  apps are upgrade-tested (`maestro/upgrade/`).

## Load limits on the devbox

This box has 48 GB. Unchecked parallel builds and emulators once exhausted it and it had to
be rebooted (2026-10-10). So:

- Cargo and Gradle builds go through `scripts/heavy.sh` (the mise tasks do): one at a time,
  only with 8 GB+ available, at low priority. Every worktree shares one `CARGO_TARGET_DIR`.
- At most 2 emulators: `scripts/emulator.sh up <port>` / `down <port>` (it waits for a slot and
  for 12 GB+ available; each emulator takes 5 to 9 GB). The `nijii-watchdog` user service stops
  the newest emulator when memory runs low, and any emulator over 12 GB resident.
- `android:flows` / `android:fidelity` boot 4 emulators by default: run them with `DEVICES=1`.
- Never `adb kill-server` or a global `./gradlew --stop`: they cut off other sessions' runs.
- At most 2 agents at once.
- iOS work runs on the Mac (`ssh jacob@10.1.0.17`, `mise run ios:remote <task>`). It's a
  laptop that may be asleep, so fail fast.

## Writing code here

This app is used in an emergency, so a failure path matters as much as the happy path. Each
rule below comes from a defect this codebase shipped or nearly shipped (review of 2026-10-11).

- **Lifetimes.** Before you `launch` or start a `Task`, decide who owns it and who waits for
  it. Work that must outlive a screen runs in the app's scope (`AppServices.scope` on
  Android), and any write the user is leaving after is awaited before navigating. A write
  launched in `rememberCoroutineScope` and then navigated away from is cancelled halfway.
- **Errors aren't optional by default.**
  - On Android, wrap suspend calls in `runCatchingCancellable` (`ui/Failures.kt`), never
    `runCatching`, which swallows cancellation.
  - On iOS, use `try?` on a store call only when failing really doesn't matter. Otherwise
    catch it, report `save_failed` (`reportSaveFailed`) and show it.
  - Keep "it failed" and "there's nothing there" apart. A failed read must never be treated
    as empty and then overwritten (an emergency was nearly restarted this way).
- **Critical before optional.** On a screen's load path, nothing optional (profile,
  places) sits before or blocks the critical read (the active emergency).
- **Shared core.** Every fallible core function the apps call returns a `Result` with the
  UniFFI error type, so Kotlin and Swift see a thrown error, never a panic across the FFI. A
  write of more than one statement goes in one transaction.
- **Effects.**
  - A `LaunchedEffect` or `.onChange` is keyed on everything it reads.
  - An effect that persists state must not run again after that state was cleared. When an
    emergency ends, `EmergencyRecorder` stops saving it.
  - Nothing that does disk work or decodes images runs on the main thread or during
    composition.
- **Types, not strings, across a boundary.**
  - Build URIs with `Uri.fromParts` or `URLComponents`, never by concatenating strings
    (`dialString` for phone numbers).
  - Settings keys, outcomes, profile fields (`ProfileField`) and step ids are shared
    constants or enums, used by both apps.
  - Parse a value once, where it comes in (`ActiveEmergency.startedAtMs`); don't re-parse it
    at each use.
  - No `!!` where a type would do.
- **Parity on failure.** Both apps behave the same when something fails, not just when it
  works: the same error shown, the same event reported.
- **Hostile conditions.** Consider what happens on reboot, process death, rotation, a
  font-size change (the Android activity is recreated) and a full disk. State that must
  survive goes in `rememberSaveable` or the store.
- **Deletion.** Anything the app creates (files, photos, notifications, analytics identity)
  is removed by Delete Account (`Store.clearAllData` and `photoCleaner`).
- **Tests prove the fix.** A fix comes with a test that fails without it, including the
  failure path. Make the store throw, cancel the scope, deny the permission. Prefer behaviour
  tests that drive the real click path over snapshots.
- **Strings and access.** Every user-facing string comes from `i18n/locales/`, including
  "OK". Controls have an accessibility role, state and label.
- **Comments say the rule.** State why in a sentence. A finding id (F-n) or "Port of …" can
  follow it, but can't replace it.

## Keyboard avoidance

Every screen with a text field keeps its focused field and its bottom button above the
keyboard. Android: the screen takes the IME inset (`imePadding()` on the scroll container, or
`windowInsetsPadding(WindowInsets.safeDrawing)`, which includes it, as `OnboardingScaffold`
does). iOS: fields sit in a
`ScrollView` with `.scrollDismissesKeyboard(.interactively)` (number and phone keypads have no
Return key, so dragging the form is the way to close them). Screens shipped without this twice
in the past.

## Visual fidelity

The apps are held to `spec/goldens/` (light, dark, large text) by `maestro/fidelity.py`;
`maestro/README.md` has the commands. A deliberate visual change re-blesses the goldens and
lowers or raises the marks in its own commit.
