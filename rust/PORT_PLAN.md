# Rust core plan

The durable state of the work: every new session and every agent starts here. The rules are
in `AGENTS.md`.

**Goal (owner, 2026-10-10):** the native Compose and SwiftUI apps unchanged, on the Rust core
via UniFFI. `kmp/shared` retires. The existing gates pass without re-blessing anything.

## Phases

| # | Phase | Who | Status |
|---|---|---|---|
| 0 | Rust core ported from kmp/shared, 50 tests (vectors, store, migrations, EmergencyAway) | done | ✅ |
| 1 | UniFFI on `nijii-core`: exported API, generated Kotlin; Android libs via cargo-ndk; Gradle wiring | agent **ffi-android** | API ✅; Android libs and Gradle wiring in progress |
| 2 | androidApp on the Rust core, then remove kmp/shared from Android; all Android gates green, DB carried over from a KMP install | agent **ffi-android** | in progress |
| 3 | iOS: XCFramework + Swift bindings, built on the Mac; the Xcode project links it | agent **ffi-ios** | ✅ |
| 4 | iosApp on the Rust core; iOS gates green | agent **ffi-ios** | ✅ (14/14 flows, upgrade from KMP keeps data, 50/51 fidelity) |
| 5 | Delete kmp/shared, Room and KMP plugins; docs, CI workflows | orchestrator | last |

At most 2 agents run at once (load limits). Worktrees: `~/github/nijii-ffi-android` (branch
`rust/ffi-android`) and `~/github/nijii-ffi-ios` (branch `rust/ffi-ios`), both cut from
`rust-port`.

**API contract between the two agents:**

- ffi-android owns the UniFFI annotations in `rust/core`.
- ffi-ios consumes them and asks, through notes here, for anything Swift needs.
- The orchestrator merges ffi-android's API commits (prefix `FFI API:`) into `rust-port`
  quickly, so ffi-ios can merge them.

## Agents

| Agent | Branch | ID | Status |
|---|---|---|---|
| ffi-android | `rust/ffi-android` | `a8059c0ac75567d8e` | running |
| ffi-ios | `rust/ffi-ios` | `af214a0b87546f6d3` | done (phase 4); idle until phase 5 |

## Merge log

- 2026-10-10: core ported (from the first attempt's data lane): rules + vectors, the store
  with the Room v2 DDL and identity hash, MIGRATION_1_2, adoption of RN databases,
  EmergencyAway, an analytics sink generated from spec, startup analytics properties.
- 2026-10-10: the Dioxus/WebView UI was shelved by the owner; the native UIs stay. Removed
  `rust/app`, its tasks, and the app-scanning analytics test (the apps' own parity test
  covers it).

- 2026-10-10: merged `rust/ffi-android` M1 (671d536): UniFFI 0.32.2 skeleton,
  `uniffi-bindgen`, `rust:bindings`.
- 2026-10-10: merged `rust/ffi-ios` phase 3 (6ca0f14): `ios:rust` builds the staticlibs, Swift
  bindings (module `NijiiCore`) and an XCFramework; wired into project.yml. A simulator test
  calls `coreVersion()`. Dropped the Dioxus CLI from mise.
- 2026-10-10: merged `rust/ffi-android` M2 (80af3b4): full FFI API in package
  `com.backtosafety.core.ffi`, AppClock in core, the posthog client removed (apps keep their
  SDKs). 49 core tests pass.

- 2026-10-10: merged `rust/ffi-ios` phase 4 (2579d7a): iosApp on the Rust core, with no `Shared`
  imports and no Gradle phase. AppStore matches the oracle: a failed open throws on every call
  (no in-memory fallback). Evidence from the Mac: 14/14 flows and the gestures, the upgrade
  from the KMP app keeps its data, 50/51 fidelity. 6 snapshot diffs are environment-only:
  pixel-identical to the KMP build on the same simulator, which runs a newer OS than CI.

## Incident: 2026-10-10 reboot

From about 13:50, free memory sat near 0.8 GB for over three hours, according to the Gradle
daemon logs. Reclaim and swap on the loop-device rootfs pushed I/O pressure to ~90%. journald
stopped at 17:12, ssh died, and the box had to be rebooted.

Causes, all from the first attempt's 7 parallel lanes:

- 4 emulators at 5.3 to 8.8 GB each (the image falls back to host lavapipe rendering)
- 7 builds at 16 jobs each, with separate target dirs (~52 GB)
- lingering Gradle daemons
- no memory checks

Guardrails since then (owner-approved; see AGENTS.md "Load limits"):

- a memory check before every build and emulator boot
- one queued build with a shared target dir
- at most 2 emulators, at low priority
- the `nijii-watchdog` service

## Owner questions

1. **iOS fidelity mark:** `large-text/contacts-list` measures 0.87%, under its 1.02% mark. The
   pure KMP build prints the same line on the same Mac, so it isn't caused by this change.
   Lower the mark (`fidelity.py --update`) or leave it?

## Pending (orchestrator)

- Forward to ffi-android: export `report_open_failure`, so iOS can send the data_migrated
  failure when the open itself fails.
- The CI workflow edits (ci.yml, ios-release.yml install Rust and run `ios:rust`) haven't run;
  they need a GitHub run, which needs a push. Ask the owner first.

Settled:

- The DB path follows the KMP app, so updates keep their data.
- Native dialogs and photo cropping come for free, because the UIs stay native.

## Notes

Each agent appends its notes here.

### ffi-android notes

- 2026-10-10 M1 done (commit `FFI API: UniFFI skeleton`): UniFFI 0.32.2 proc-macro mode in
  `nijii-core` (crate types lib+cdylib+staticlib), `rust/uniffi-bindgen` workspace binary,
  `core/uniffi.toml` (Kotlin package `com.backtosafety.core.ffi`), `mise run rust:bindings`
  (Kotlin into `$BINDINGS_OUT`, default `$CARGO_TARGET_DIR/bindings/kotlin`). The only export
  so far is `core_version()`. Next: M2, the full API.

### ffi-ios notes

**2026-10-10, phase 3 (pipeline) done and checked on the Mac.**

- `mise run ios:rust` (Mac only; `mise run ios:remote ios:rust` from here) builds `nijii-core`
  as a staticlib for `aarch64-apple-ios` and `aarch64-apple-ios-sim` (`cargo rustc --crate-type
  staticlib`, so Android's cdylib setting is untouched), runs the workspace `uniffi-bindgen`
  in library mode for Swift, and packages `kmp/iosApp/RustCore/` (gitignored):
  `NijiiCore.xcframework`, `Generated/NijiiCore.swift`, `include/` (header + `module.modulemap`,
  module `NijiiCoreFFI`). Output is replaced only when it changed, so Xcode doesn't relink for
  nothing. Cold build 1 to 2 minutes on the Mac, incremental seconds.
- Swift module name is set in `rust/core/uniffi.toml` (`[bindings.swift] module_name =
  "NijiiCore"`): the generated Swift is compiled into the app target, the C module is
  `NijiiCoreFFI`; nothing clashes with `Shared`.
- `kmp/iosApp/project.yml`: a "Build the Rust core" pre-build phase before the Gradle one
  (declares its output file), `RustCore/Generated` as sources, `-lnijii_core`, and
  `LIBRARY_SEARCH_PATHS` per SDK into the xcframework's slices (Xcode resolves a referenced
  `.xcframework` before any build phase runs, so a fresh checkout would fail; reading the
  slices in place avoids that). `SWIFT_INCLUDE_PATHS` for the app and the test target.
  `maestro/build-ios.sh` runs `ios:rust` before `xcodegen`, because the project lists the
  generated file.
- Evidence: `xcodebuild test -only-testing:BackToSafetyTests/RustCoreTests` on an iPhone
  simulator passes (`coreVersion()` called through the UniFFI bindings from the running app);
  a generic iOS (device) build compiles and links. `BackToSafetyTests/RustCoreTests.swift` is
  the smoke test.
- `ios:remote` now protects `kmp/iosApp/RustCore/` from rsync `--delete` (the ignore filter
  didn't), and `build-tests/` is gitignored.
- `mise.toml`: rust targets now include the two iOS ones (and `cargo:dioxus-cli` is still
  installed although the WebView app is shelved; the orchestrator may drop it).
- `kmp/iosApp/generate-design-tokens.py` writes `Generated/DesignTokens.swift` from
  `spec/design-tokens.json` with the same shape the Kotlin framework exported
  (`DesignTokens.Light.shared.text`, `Type_`, ...), so `Theme.swift` and `RNText.swift` don't
  change. Not in `project.yml` yet: it would clash with `Shared.DesignTokens` until the switch.

**Phase 4 plan (starts when the full API lands).** Adapters in `iosApp/BackToSafety/Core/`:
`Analytics.shared.track(event:properties:)` (47 call sites) and `.screen`, `AppClock.shared`
(`nowMs`, `freeze`, `advance`, `testSeamsEnabled`), `Translate`/translations lookup, the
`EmergencyAway` + `AlertNotificationCenter` bridge, `Store` calls. Then drop `import Shared`
from the 22 files and delete the Gradle phase. Shared symbols Swift uses today: Store (about
40 methods), `AppClock`, `Analytics`, `AnalyticsEvent`, `EmergencyAway`, `DatabaseBuilder_iosKt`
(`openStore`, `openStoreAt`), and the top-level functions in `PhoneKt`, `EmergencyKt`,
`ReadoutKt`, `MobilityKt`, `FormattersKt`, `AgeKt`, `EmergencyAlertsKt`, `AppDatabaseKt`.

**ffi-ios requests (for ffi-android's API):**

1. Export `Store` methods as `async` where the Kotlin ones are `suspend` (Swift awaits them
   today; UniFFI async maps to `async throws`). Errors as a `#[uniffi::Error]` enum, not panics.
2. Analytics sink as a foreign callback interface taking the wire name plus properties. PostHog
   wants `[String: Any]`, so a small `PropertyValue` enum (string, int, double, bool, null; lists
   and maps if the Kotlin properties use them) is easiest; I convert it in the adapter. The
   event enum `AnalyticsEvent` should be exported (the Swift call sites pass `.appReady`).
3. `AppClock` (`now_ms`, `freeze`, `advance`, `test_seams_enabled`) exported as functions or a
   global object, shared by Store and EmergencyAway inside the core, so a frozen clock in the
   UI freezes the core's too.
4. `open_store(path)` taking an explicit path; iOS computes `<Documents>/SQLite/nijii.db`
   itself (same as `DatabaseBuilder.ios.kt`) and creates the directory.
5. The constants Swift reads as globals (`SEARCH_WINDOW_SECONDS`, `MOBILITY_OPTIONS`,
   `MOBILITY_OPTION_KEYS`, `Analytics.SCREEN`) as exported functions or consts.
6. Optional numbers and the `KotlinInt`-style boxing: please export `Option<i32>` and friends
   plainly; Swift then sees `Int32?`.

- 2026-10-10 M2 done (commit `FFI API: full surface`). The surface is `rust/core/src/ffi.rs`
  plus derives in place (Record/Enum/Object). Conventions the wrappers can rely on:
  - `i32` where Kotlin's core had `Int`, `i64` for `Long`; no unsigned types.
  - Property bags are `{String: PropValue}` (`Null | Bool | Integer | Decimal | Text`, each with
    `value`). Analytics sink: foreign trait `AnalyticsSink.on_event(name, properties)`, installed
    with `analytics_set_sink(Some(sink))`; screens arrive as `$screen` with `$screen_name`.
  - Translations: `Translations.from_json({locale: {namespace: json}})`, `.translator(locale, ns)`
    gives a `Translator` object with `t(key)` / `tv(key, vars)` / `locale()`. Functions that
    took a `Translate` take the `Translator`.
  - `Store` is an object: `Store.open(path)` / `Store.open_in_memory()`, synchronous methods,
    errors as `StoreFailure` (Sqlite, Io, UnknownProfileField, NewerDatabase). No closures:
    `save_profile_entity(entity)` + `profile_entity()` replace `saveProfile { }`;
    `update_profile({field: value?})` is the partial save. Setting keys come from `setting_keys()`.
  - `EmergencyAway.new(store, scheduler)`, `AlertScheduler` is a foreign trait
    (`schedule(alerts: [ScheduledAlert])`, `cancel_all()`).
  - Dates are `Date {year, month, day}`; `ReadoutInput.today` is a `Date`.
  - Enum `key`s (`countdown_alert_key` ...) and consts (`search_window_seconds()`) are functions.
  - AppClock is `app_clock_*` free functions (`app_clock_handle_link` applies the
    `backtosafety://debug/clock` seam link); `AppReady.report(startup_ms, store, device)` and
    `seed_and_report_migration(store)` do app_ready / data_migrated.
  - Removed: `posthog.rs` (the apps keep their PostHog SDK) and `ureq`, and the Dioxus CLI
    from mise.toml. `lifecycle_events`, `report_open_failure` stay in core (tested) but aren't
    exported: the SDK sends lifecycle events itself.

**2026-10-10, phase 4 (iosApp on the Rust core): done on this branch; gates run on the Mac
(Xcode 26.4, iOS 26.4 simulators).**

- The 22 files no longer `import Shared`; `kmp/iosApp` has no Gradle phase and no Shared
  framework (`FRAMEWORK_SEARCH_PATHS`, `-framework Shared` gone). Adapters in
  `BackToSafety/Core/`: `Analytics` (same `track`/`screen`/`sink` shape over `analyticsTrack` +
  the `AnalyticsSink` foreign trait), `AppClock` (core clock, `handleLink` for the
  debug/clock link), `Translate` (wraps `Translator`), `StoreAsync` (async overloads of every
  `Store` method on a serial queue, so `try await store.x()` call sites stay and calls apply in
  order off the main thread), `PropValues`, `Keys` (`.key` properties, `MOBILITY_OPTIONS`),
  `AppStore` (`<Documents>/SQLite/nijii.db`, the KMP app's path), `LaunchReport` (was
  `AppReady`; the core exports a class of that name, so the app's enum was renamed).
  `CountdownNotifications` is still the `AlertScheduler`; PostHog stays the sink.
- Swift renames in `rust/core/uniffi.toml` (`[bindings.swift.rename]`): the core's `Date` is
  `CalendarDate` in Swift, because the bindings compile into the app module and a `Date` there
  would hide Foundation's. Call sites changed: the free functions lost their `PhoneKt.`-style
  prefixes (they're module globals now), `Kotlinx_datetimeLocalDate` is `CalendarDate`
  (months are 1-based now), `t.lookup` is `t.translator`, `updateProfile` takes
  `[String: String?]`.
- DesignTokens: `kmp/iosApp/generate-design-tokens.py` is run by `ios:rust` into
  `RustCore/Generated/DesignTokens.swift`.
- CI (`ci.yml` kotlin-ios job, `ios-release.yml`) installs Rust instead of Java/Android SDK and
  runs `mise run ios:rust` before xcodegen. Untested here (needs a GitHub run).
- **Evidence (all on the Mac, nothing re-blessed, `__Snapshots__`, `spec/` and goldens
  untouched):**
  - `ios:flows`: all 14 Maestro flows ok, plus both reorder gestures.
  - Upgrade test (`maestro/upgrade/upgrade-ios.sh install-over`): KMP-built app (Room
    database) filled through the UI, then this build installed over it: "data and settings
    survived", verify.yaml passes. This is the DB-carried-over check for iOS.
  - `ios:fidelity`: 50 of 51 states ok. The one report, `large-text/contacts-list: 0.87%,
    better than mark 1.02%: lower it`, is a ratchet note, not a regression: the pure KMP
    build (commit before the pipeline) gives the identical line on this Mac. Left unlowered
    (marks are not mine to bless; the owner may run `fidelity.py --update`).
  - Snapshot tests (`BackToSafetyTests`, iPhone 16 Pro simulator on iOS 26.4): 6 of 8 screens
    match the CI references (24 of 30 images); Contacts and Settings differ in the
    green/red button colors, but the 6 images are byte-identical to the ones the KMP build
    renders on the same Mac (pixel diff: none), so it is the simulator OS (CI is macOS 15), not
    the change. A real check needs the CI runner: push and look at the `ios-snapshots` job.
  - Unit tests: `CountdownNotificationsTests` (4, through the Rust `EmergencyAway`) and
    `RustCoreTests` pass.
- Decisions to look at:
  - If `Store.open` throws (database from a newer app, not a database, unreadable), the app
    starts but `AppStore` is *failed*: every call throws the open error, so each save shows the
    screens' save-failed error and `save_failed`, as the KMP app did when Room failed on every
    query (no in-memory fallback). `FailedStoreTests` covers a garbage file and a
    newer-version (user_version 999) database. EmergencyAway gets a scratch in-memory store
    only in that state, so no user data goes there.
  - `report_open_failure` (data_migrated as failed when open itself fails) isn't exported, so
    that event is lost on iOS when open fails mid-migration. Request: export it.
  - The Mac has no iPhone 16 Pro simulator by default; I created `nijii-ffi-16pro`.
- Not done by me (orchestrator, phase 5): deleting `kmp/shared` and the Kotlin docs; the
  Android side. `docs/ARCHITECTURE.md` still describes the KMP core.

- 2026-10-10 M3 (androidApp on the Rust core), state so far:
  - Gradle (kmp/androidApp/build.gradle.kts + buildSrc/RustCore.kt): `buildRustHost` (cargo build
    for this machine: the JNA lib the JVM tests load, and uniffi-bindgen), `generateCoreBindings`
    (Kotlin bindings into build/generated/uniffi), `buildRustAndroid` (cargo-ndk, release,
    arm64-v8a + x86_64 into jniLibs), `generateDesignTokens` into `com.backtosafety.app.core`.
    JNA: aar for the app, jar for the JVM tests (`jna.library.path` = the host build). cargo-ndk
    comes from mise (`cargo:cargo-ndk`, a prebuilt binary). `heavy.sh` is now re-entrant
    (NIJII_HEAVY) and doesn't pass its lock fd to the command, so Gradle (and its daemon) can run
    cargo under the queue without deadlocking or holding the queue after the build.
  - Adapters in `com.backtosafety.app.core`: `Translate` (a class over the core `Translator`,
    so the translator-taking core functions get the real object), `Translations`, `Analytics`
    (sink = lambda over the foreign trait), `AppClock`, `Store` (suspend over the sync core on
    `Store.dispatcher`, which snapshot tests set to Unconfined), `EmergencyAway`, `ReadoutInput`
    and the translator functions, `parseDob`/`formatDob` over kotlinx LocalDate, the `key`
    extensions, `Throwable.analyticsTypeName()` for save_failed/data_migrated (StoreFailure ->
    the core's type names). Screens changed by imports only, plus: RnModifiers' fully qualified
    names, ProfileScreen's dob imports, App.kt's launch effect (`store.seed()` now also reports
    data_migrated, in the core), MainActivity (path, translations loading, the clock link via
    `AppClock.handleLink`).
  - Tests changed: ScreenSnapshotTest builds `Store.inMemory()` instead of Room;
    AnalyticsParityTest (kmp/shared) also counts the events rust/core/src sends.
  - Evidence: 24 snapshot tests + 9 others pass in verify mode against the committed PNGs
    (no RECORD); `mise run check` and `mise run lint` pass.
  - DB carry-over: maestro/upgrade fill.yaml on the cached KMP oracle APK, then
    `adb install -r` of the Rust build, then verify.yaml: passes (profile, contact, place,
    theme, running emergency with 2 steps and clothing, readout). Note: on this emulator the
    oracle's own fill loses the date of birth (the KMP app, unchanged; the cause isn't diagnosed,
    possibly typing before the profile form finishes loading), so `upgrade.sh install-over`
    fails at the DOB assertion even oracle-to-oracle. I set date_of_birth with sqlite3 between
    fill and install, and the DB dumps before/after the upgrade are identical except
    emergency_away_from (EmergencyAway, as in KMP) and sqlite_sequence for safety_checks
    (INSERT OR IGNORE re-seeding on launch). `upgrade.sh restore` of the committed KMP-written
    fixture (spec/fixtures/upgrade/android) also reads back on the Rust build.
  - Gates on `rust/ffi-android` (no RECORD, no golden/snapshot/vector edits): `mise run check`,
    `mise run lint`, `mise run snapshots` ("snapshots match"), `DEVICES=1 mise run android:flows`
    (14 flows + contacts/destinations reorder ok, 1148 s), `DEVICES=1 mise run android:fidelity`
    (all 39 state/theme captures ok, 2478 s; needs a background timeout over 30 min). Fixed the
    fidelity task to create /tmp/nijii-captures (it was missing after the reboot).
  - Android no longer depends on kmp/shared (`project(":shared")` removed; shared stays for iOS).
    Not done by me: Room/KMP plugin removal, docs and CI workflow updates (phase 5).
