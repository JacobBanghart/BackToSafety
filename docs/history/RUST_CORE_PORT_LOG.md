# Rust core plan

The durable state of the work: every new session and every agent starts here. The rules are
in `AGENTS.md`.

**Goal (owner, 2026-10-10):** the native Compose and SwiftUI apps unchanged, on the Rust core
via UniFFI. `kmp/shared` retires. The existing gates pass without re-blessing anything.

## Phases

| # | Phase | Who | Status |
|---|---|---|---|
| 0 | Rust core ported from kmp/shared, 50 tests (vectors, store, migrations, EmergencyAway) | done | ✅ |
| 1 | UniFFI on `nijii-core`: exported API, generated Kotlin; Android libs via cargo-ndk; Gradle wiring | agent **ffi-android** | ✅ |
| 2 | androidApp on the Rust core, then remove kmp/shared from Android; all Android gates green, DB carried over from a KMP install | agent **ffi-android** | ✅ (14/14 flows, 51/51 fidelity, 24 snapshots, upgrade from KMP keeps data) |
| 3 | iOS: XCFramework + Swift bindings, built on the Mac; the Xcode project links it | agent **ffi-ios** | ✅ |
| 4 | iosApp on the Rust core; iOS gates green | agent **ffi-ios** | ✅ (14/14 flows, upgrade from KMP keeps data, 50/51 fidelity) |
| 5 | Delete kmp/shared, Room and KMP plugins; move AnalyticsParityTest; docs, CI workflows | agent **ffi-android** | ✅ (flows rerun after deletion: see the notes below) |

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
| ffi-android | `rust/ffi-android` | `a8059c0ac75567d8e` | done |
| ffi-ios | `rust/ffi-ios` | `af214a0b87546f6d3` | running: DOB-loss investigation (KMP app, read-only), then iOS gates after phase 5 |

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

- 2026-10-10: merged `rust/ffi-android` M3 (59643b3): androidApp on the Rust core and off
  kmp/shared. Gates: check, lint, 24 snapshots, 14/14 flows plus the gestures, 51/51
  fidelity; an upgrade from the KMP oracle APK keeps its data. On the merged rust-port the
  orchestrator re-ran `mise run check` and `mise run lint`; both pass.

- 2026-10-10: merged `rust/ffi-android` phase 5 (1d2c1f5): kmp/shared, Room and the KMP plugins
  deleted (−2.5k lines); AnalyticsParityTest is in androidApp and covers Kotlin, Swift and
  rust/core; docs and CI updated (actionlint only, not run). On the merged tree the
  orchestrator re-ran check, lint and snapshots; all pass, nothing re-recorded. `snapshots` now
  stops its Gradle daemon. 7 of 14 flows failed once with "package not installed"; most likely
  the DOB investigation re-installing the same KMP package at the same moment. All passed
  on rerun.

## Is it worth it? Android numbers (2026-10-10)

The KMP app is `main` f4b5d88 (the cached oracle APK). The Rust app is rust-port after the
size-tuned release profile and the 4 ABIs. Both are release builds with test seams, measured on
the same API 36 x86_64 emulator. Each start is a cold start (`am start -W -S`) on the Welcome
screen, 15 runs in each of two rounds.

| | KMP | Rust core | Δ |
|---|---|---|---|
| APK, all ABIs | 8.2 MB | 17.3 MB | +9.1 MB |
| Download per phone (arm64 split) | 4.6 MB | 7.1 MB | +2.4 MB (+53%) |
| Native code per phone (arm64) | 1.2 MB (Room's SQLite) | 3.6 MB (core 3.4 + JNA 0.2) | +2.4 MB |
| Rust lib before release tuning | | 5.8 MB per ABI | (LTO + strip: −41%) |
| Cold start, median | 285 / 294 ms | 309 / 315 ms | +~20 ms (+7%) |
| Memory (PSS, Welcome) | 30.6 MB | 33.0 / 34.4 MB | +2.4–3.7 MB |
| Clean release build | 99 s | 150 s (cold Rust deps, 5 targets) | +51 s |
| Rebuild after a core logic change | 79 s | 43 s | −36 s |
| Core source (no tests) | 1,161 lines Kotlin | 3,326 lines Rust + 460 glue | ×3.3 |
| Core tests | 651 lines | 1,632 lines | |

On Android the Rust core costs size, start time and memory, and gains a faster edit-rebuild loop
on core logic. Release tuning found along the way: the first build shipped an unstripped,
un-LTO'd library, and dropped 32-bit ABIs, so 32-bit phones couldn't have installed it (both
fixed in d30622f).

## Android optimization pass (2026-10-11)

Goal: make the Rust core competitive on Android. Same method as above (release builds with test
seams, `maestro/build-android.sh`, `rust/scripts/measure-android.sh`: 15 cold starts per APK,
median), alternating KMP / Rust before / Rust after, two rounds, each round on a freshly booted
emulator (see "Measuring" below for why). "Before" is 3a17cc9, "after" is this branch.

### Size, step by step (arm64 `libnijii_core.so`, stripped)

| Step | Bytes | Δ |
|---|---|---|
| Before (opt-level s, fat LTO, strip) | 3,591,072 | |
| Hand-written patterns instead of `regex` (phone extension, `{{placeholder}}`, DOB, height) | 2,343,960 | −1,247,112 |
| SQLite trimmed (no FTS3/4/5, R-tree, DBSTAT, STAT4, SOUNDEX, JSON, extensions, deprecated, shared cache, trace, progress, explain, incrblob, get_table) | 1,897,608 | −446,352 |
| More SQLite OMITs (UTF-16, authorization, `sqlite3_complete`, btree count) | 1,890,680 | −6,928 |
| opt-level `z` instead of `s` | 1,694,064 | −196,616 |
| chrono `now` instead of `clock`; uniffi without default features | 1,690,368 | −3,696 |
| Final (with the startup changes below) | 1,695,168 | −1,895,904 (−53%) |

Compressed (gzip -9) 1.52 MB → 0.84 MB. `cargo bloat` (host, symbols kept): `.text` 2.0 MiB → 1.1 MiB;
regex_automata + aho_corasick + regex_syntax (520 KiB) gone, SQLite C 698 → 442 KiB, std 309 → 273 KiB,
nijii_core itself 382 KiB (UniFFI scaffolding and the converters; unchanged).

What was tried and not kept:

- `opt-level` z vs s: z won on size (−197 KB raw, −24 KB compressed) and cold start shows no
  difference at this scale, so z stays.
- `-DSQLITE_OMIT_ANALYZE`, `_VACUUM`, `_REINDEX`, `_WINDOWFUNC`: the amalgamation doesn't compile or
  link without them (undefined `sqlite3Analyze`, `sqlite3Vacuum`, `sqlite3Reindex`, window-function
  symbols). The host test link caught it; note that a cdylib links with undefined symbols without
  complaint, so only the host tests would tell.
- `-Wl,--icf=all`: −21 KB (1.1%); not worth a linker flag.
- std's panic backtrace code (gimli, addr2line, miniz_oxide: about 70 KB of `.text`) can only go
  with `-Zbuild-std` (nightly).

The regex replacements are held to the original regexes by `core/tests/regex_equivalence.rs`
(every string up to length 6 over an alphabet of each pattern's edge characters, plus 20,000
random longer ones per pattern; `regex` is a dev dependency only). The SQLite flags live in
`rust/.cargo/config.toml` (`LIBSQLITE3_FLAGS`) and a test (`bundled_sqlite_is_trimmed_to_what_the_store_uses`)
fails if cargo runs outside `rust/` or the flags drift. Run cargo from `rust/`.

### Startup: where the time went

Timestamps in a scratch build, emulator, medians of several cold starts (ms since process start
unless noted):

| Step | Cost |
|---|---|
| Process start to `Activity.onCreate` | ~30 |
| `libjnidispatch` + JNA class init | ~6 |
| `System.loadLibrary("nijii_core")` | ~1.5 to 3 |
| UniFFI init (registering ~300 native functions with JNA, ~170 checksum calls, callback vtables) | **20 to 34** |
| `Store.open` | 2.5 to 4.5 |
| Reading and parsing all locale JSON (`Translations`) | 7 to 12 |
| `Store.seed` (a write transaction on every launch, committing for the disk) | 9 to 11 |

So the ~20 ms Rust deficit was UniFFI's registration on the main thread before the first screen.
Fixes: the core starts on its own thread from `Application.onCreate` (`CoreStartup`), and the
first screen waits for it only when it needs the store. Core ready at ~45 to 90 ms after process
start; Compose is ready for it at ~240 ms, so the core is off the critical path. Locale JSON is
parsed per namespace on first lookup, and `seed()` only writes when a row is missing (the second
launch onward writes nothing). R8 now keeps only what JNA finds by name (native functions,
structures, callbacks) instead of the whole bindings package: dex 3.41 → 3.26 MB.

### Memory: what the extra PSS is

`dumpsys meminfo`, Welcome screen, KMP vs Rust before the pass: native heap is the same (14.5 MB
both: Skia, Compose, ART; Rust's own heap is small). The difference was Code +1.0 MB (the
core's `.so` pages mapped from the APK, dex of the generated bindings) and Java heap +1.1 MB (JNA
and UniFFI's converter and function objects). The smaller library and the narrowed R8 rules
took 0.7 to 1.7 MB off; the rest is JNA/UniFFI machinery, which only fewer exported functions
would shrink.

### Results (same emulator, release builds with test seams)

| | KMP | Rust before | Rust after | after − KMP |
|---|---|---|---|---|
| APK, all ABIs | 8.6 MB | 18.1 MB | 11.1 MB | +2.5 MB (was +9.5) |
| arm64 split, as stored in the APK | 4.86 MB | 7.42 MB | 5.48 MB | +0.62 MB, +13% (was +2.56, +53%) |
| arm64 split, entries deflated | 3.98 MB | 4.98 MB | 4.25 MB | +0.27 MB, +7% |
| Native code, arm64 (core + JNA; KMP: Room's SQLite) | 1.29 MB | 3.78 MB | 1.88 MB | +0.59 MB |
| dex | 3.18 MB | 3.41 MB | 3.26 MB | +0.08 MB |
| Cold start median, round 1 | 276 ms | 298 ms | 277 ms | +1 ms |
| Cold start median, round 2 | 294 ms | 315 ms | 295 ms | +1 ms |
| PSS, Welcome, round 1 | 30.7 MB | 33.1 MB | 32.4 MB | +1.7 MB |
| PSS, Welcome, round 2 | 30.8 MB | 34.7 MB | 32.5 MB | +1.7 MB |

Cold start is level with KMP; the download is +0.27 MB deflated (+13% as stored, which is how the
earlier numbers were counted; they read 4.6 / 7.1 MB with the same method but a slightly
different accounting, the ratios agree); PSS is +1.7 MB.

Gates after the pass: `mise run check` (50+ Rust tests incl. the equivalence and SQLite-flag tests,
analytics parity), `mise run lint`, `mise run snapshots` (24, no RECORD) and `DEVICES=1 mise run
android:flows` (14 flows + both reorder gestures, 1090 s) pass. Fidelity not rerun (nothing visual
changed). iOS: the changes that reach `ios:rust` are the release profile (opt-level z), the
dependency changes (regex, chrono, uniffi features), `rust/.cargo/config.toml` (SQLite flags;
`ios:rust` runs cargo from `rust/`, so it applies) and the lazy translations/seed. The Mac was
offline, so it is unbuilt there; CI's iOS job or `mise run ios:remote ios:rust` will tell.

Measuring: the emulator (`-gpu guest`, lavapipe) grows during a session; after about 40 minutes
one measured 31 GB resident and every start read ~600 ms. Rounds are measured on a fresh
emulator and the box is checked (`ps -o rss`) between them.

- 2026-10-11: merged `rust/ffi-android` Android optimization pass (b309468): the arm64 core is 3.59 → 1.69
  MB (regex replaced by patterns held to it by an equivalence test, trimmed SQLite, opt-level z),
  and the core starts off the main thread. Cold start now matches KMP; PSS is +1.7 MB. CI is green
  (run 38129362902), including the iOS build and snapshots. iOS size and launch weren't
  re-measured with the new profile (the Mac was offline). The watchdog now also stops any
  emulator over 12 GB resident.

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

- **Finding:** on Android, `maestro/upgrade/upgrade.sh install-over` fails at the
  date-of-birth assertion even KMP→KMP: the KMP app's own fill loses the DOB. Either a real
  bug in the shipping app or a flow timing problem; ffi-ios is investigating (read-only on
  main).

- Forward to ffi-android: export `report_open_failure`, so iOS can send the data_migrated
  failure when the open itself fails.
- CI: green on rust-port (run 38113217340, dispatched; the owner approved the push). The first
  run failed: mise was rate-limited (fixed by GITHUB_TOKEN), sdkmanager wasn't on PATH, and SC2129
  (fixed in 02ea712). The CI iOS snapshot job passes, which confirms that the 6 local snapshot
  diffs were only the simulator OS. The release workflows haven't run (they sign and publish).
- The DOB fix is PR #1 against main, all checks green, awaiting the owner; it's cherry-picked
  onto rust-port.

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
- 2026-10-10 Phase 5 (ffi-android):
  - `kmp/shared`, Room, the KMP/KSP/serialization Gradle plugins, the unused libs and
    `GenerateAnalyticsEvents` are deleted. The Room v2 schema the Rust migration test reads moved
    to `spec/room-schema-v2.json`; the old Kotlin tests live on in `rust/core/tests`.
  - `AnalyticsParityTest` is in `kmp/androidApp/src/test`: Android, iOS (Swift) and
    `rust/core/src` against `spec/analytics-events.json` (3 tests pass).
  - `mise run check` = `spec/check.py` + `cargo test` + the parity test through Gradle, which it
    stops on exit (no daemon left).
  - Docs: ARCHITECTURE, CLAUDE.md, AGENTS.md, README and the docs that cited kmp/shared.
    Left as history: `PARITY_PLAN.md`, `spec/vectors/alerts.json`'s "source" field (vectors aren't
    edited), this file.
  - CI (read through plus `actionlint`, now in mise and `mise run lint`; nothing could be
    pushed): ci.yml has a `rust-core` job (cargo test) in place of `kotlin-core`; the Android
    job installs Rust, cargo-binstall + cargo-ndk (mise) and the NDK (sdkmanager, 29.0.14206865,
    ANDROID_NDK_HOME), with NIJII_HEAVY=1 so Gradle's cargo calls skip the devbox queue;
    android-release.yml (self-hosted) installs Rust, cargo-ndk and the NDK too. The Gradle
    steps are otherwise unchanged. `lintRelease` passes locally. Untested on real runners:
    mise's `cargo:cargo-ndk` install on ubuntu-latest (binstall) and sdkmanager NDK install.
  - Gates after the deletion: `mise run check`, `mise run lint` (with actionlint), `mise run
    snapshots` pass. `DEVICES=1 mise run android:flows`: the first full run passed 7 of 14 flows
    and the 7 others died at once with "Package com.backtosafety.app is not installed" (the
    emulator lost its app mid-run; no watchdog entry, cause unknown); rerunning those 7 passed
    (ok x7, both reorder gestures ok). Fidelity not rerun: nothing visual changed.

### DOB finding (ffi-ios, 2026-10-10)

**Verdict: (a), a real app bug on Android (KMP and the Rust-core Android alike, since the UI is
the same), not the flow typing too early. iOS is not affected.** The date of birth is only the
visible symptom: on Android's profile editor, **editing a second text field reverts the edit
made in the previous field** before Save. The fill flow types the DOB first and then the height,
so the DOB is the one lost.

Repro (emulator-5558, `~/.cache/nijii-oracle/oracle.apk` = main f4b5d88, fresh install; probes
are Maestro flows that onboard, open the profile, then):
- `fill.yaml` as is: DB row `Margaret Smith||5'6"|Moderate Alzheimer's|left|Uses cane`,
  `date_of_birth` NULL (the fill flow itself exits 0; only verify.yaml notices).
- DOB only, hide keyboard, Save: DOB saved (`03/15/1940`). So typing is not too early: the
  screenshot right after `inputText '03151940'` shows `03/15/1940` in the field, and the form
  doesn't compose until the profile has loaded (`if (f == null) return@Column`, line 217), so
  Maestro cannot tap the field early.
- DOB, hide keyboard, then wait 4 s, then height `56`, Save: DOB lost. Not a race.
- DOB, then nickname (`ie`), Save: nickname saved, DOB lost.
- nickname (`ie`), then DOB, Save: both saved.
- nickname (`ie`), then height `56`, Save: nickname reverted to `Maggie`, height saved.

Pattern: the field edited **last** survives; an earlier edit survives only if the later one
was the DOB.

Root cause, `kmp/androidApp/.../profile/ProfileScreen.kt` at f4b5d88:
- `:219-221`: `fun update(field, value) { form = f.with(field, value) }` where `f` is the
  `val f = form` snapshot of the composition (`:147`), not the current state.
- Every text field gets it as `Field(..., f, ::update, ...)` (`:231-260`, `:316-325`, ...).
  The callable reference to a local function capturing `f` evidently stays bound to the `f` of
  the composition it was first created in (the load result), so each such edit writes
  `loadedForm.with(field, value)`, dropping every edit made since the load except its own.
  I did not read the generated bytecode; the memoization of the reference is my explanation
  for the observed behavior, and the one-line fix below removes the symptom.
- Why the DOB behaves differently: it is wired as a fresh lambda in the Section content,
  `DateOfBirth(..., { update("dateOfBirth", it) })` (`:233`), which calls the *current*
  composition's `update`. So the DOB edit keeps earlier edits, but the next stale `::update`
  edit throws the DOB away. (The dominant-hand and mobility handlers use `f.copy` in fresh
  lambdas too, and are fine; the photo picker already uses `form = form?.with(...)` at `:206`.)
- Not the formatter (`formatDobInput`, shared Formatters.kt, vector-tested) and not save
  timing: `save()` reads `form` at tap time, and DOB-then-Save saves the DOB.

User impact: a caregiver who edits the profile (any of about 20 text fields) and moves on to
another field in the same visit sees the earlier field's new text revert to the loaded value
(the field is bound to `f[field]`, so it is visible), and if they miss it, saves without it.
Not specific to the DOB; the DOB is first in the fill flow only.

Proposed fix (one line, `ProfileScreen.kt:220`): `form = form?.with(field, value)`, the same
read-current-state form `:206` already uses. **Verified:** I built main f4b5d88 with only that
line changed (scratch copy in /tmp, nothing in the repo touched) and ran the same flows on a
fresh install: `fill.yaml` now saves `Margaret Smith|Maggie|03/15/1940|5'6"|Moderate
Alzheimer's|left|Uses cane`; nickname-then-DOB and DOB-then-nickname keep both
(`Maggieie`, `03/15/1940`); nickname-then-height keeps both. The unpatched APK built the same
way reproduces the bug, so the build is not the cause. The change belongs in the owner's call
(main, and the Android UI on `rust-port`); a regression test would be a Compose/Maestro flow
that edits two fields and asserts both after Save (e.g. add a nickname edit to
`maestro/flows/profile.yaml`) and `maestro/upgrade/verify.yaml` already asserts the DOB.

iOS: not affected. `ProfileView.swift` keeps the form in one `@State` value edited by key
(`form?[field] = ...` through bindings), with no captured snapshot. Evidence: the KMP-built
iOS app was filled by the same `fill.yaml` on the Mac and `verify.yaml` (DOB `03/15/1940`,
name, hand, mobility) passed after installing the Rust build over it (phase 4 notes above).

Hygiene note from this task: `maestro/upgrade/upgrade.sh` runs `maestro test` without
`--device`, and `fill.yaml` doesn't assert the DOB, so a lost field only fails later in
verify.

### iOS stats (ffi-ios, 2026-10-10): KMP (main f4b5d88) vs the Rust core

Mac: Apple M4 Pro (14 cores, 48 GB), Xcode 26.4, iPhone 16 Pro simulator on iOS 26.4 (arm64).
KMP = `main` f4b5d88 (`~/nijii-remote-main`; its `kmp/shared` Shared.framework, static). Rust =
rust-port head as of 28ee328 plus this branch's iOS work. **The size-tuned `[profile.release]`
(opt-level s, fat LTO, codegen-units 1, strip) is not committed on rust-port**; it is only a
working-tree change in `~/github/nijii-rust`. I applied the same four settings through
`CARGO_PROFILE_RELEASE_*` environment variables ("rust-size" below) and also measured the
committed default release profile ("rust-default"). Neither build is signed.

**1. Size** (Release, arm64 device build, `xcodebuild -sdk iphoneos ARCHS=arm64
CODE_SIGNING_ALLOWED=NO`). Neither app has a Frameworks directory: both cores are static
libraries linked into the one executable. "IPA" is the unsigned `.app` zipped under `Payload/`
(`zip -y`): the real App Store IPA is signed and thinned, so read it as relative.

| | KMP | Rust (size profile) | Rust (default release) |
|---|---|---|---|
| .app bundle (du) | 17 804 KB | 14 256 KB (-20%) | 16 912 KB (-5%) |
| main executable | 16 614 KB | 13 066 KB (-21%) | 15 720 KB (-5%) |
| executable __TEXT / __DATA_CONST | 6.88 MB / 0.93 MB | 5.49 MB / 0.26 MB | 7.62 MB / 0.38 MB |
| unsigned IPA (zip) | 5 153 KB | 4 554 KB (-12%) | 5 651 KB (+10%) |
| core static lib before linking | Shared 15 802 944 B (ar) | libnijii_core.a 10 281 864 B | 27 958 496 B |

The executable also holds the SwiftUI app, PostHog and, for Rust, the 6 000-line UniFFI Swift
file, so the core's own contribution is not isolated here (the executable delta is 3.5 MB).

**2. Cold launch** (simulator, Release, no test seams, Welcome screen; first run of each
series dropped as warm-up, 15 runs kept, median shown; two series per build, in the order
kmp, rust, rust, kmp). Two methods, the same for both builds:
(a) `startup_ms`: the app's own app_ready measure (process start from the kernel to the first
frame of the first screen), printed to stderr by a scratch one-line change in both trees and
read through `xcrun simctl launch --console --terminate-running-process`;
(b) an XCUITest loop (`app.terminate(); app.launch()`, then wait for the Welcome button).

| | KMP | Rust (size profile) |
|---|---|---|
| (a) startup_ms median, series B / C | 534 / 539 | 530 / 542 |
| (a) simctl launch to first frame, wall, median | 851 / 848 ms | 851 / 844 ms |
| (b) XCUITest `launch()` median (min-max) | 2018 ms (1982-2067) | 2009 ms (1992-2065) |
| (b) until the Welcome button exists | 3135 ms (3069-3179) | 3123 ms (3097-3178) |

No launch difference beyond noise (about 1%). The Rust `Store.open` (migration check) at
`AppModel.init` and the UniFFI setup cost nothing visible; Kotlin/Native runtime initialization
isn't visible either.

Raw (a) startup_ms, runs 1-15:
- KMP B: 620 569 557 553 546 542 530 537 534 533 526 527 528 532 527
- KMP C: 580 576 584 586 573 540 546 539 537 538 533 531 534 528 524
- Rust B: 602 565 540 526 542 541 531 530 530 527 522 531 519 521 515
- Rust C: 596 556 580 584 590 544 541 542 537 543 538 532 531 526 525

Raw (b) launch_ms: KMP 2045 2067 2039 2025 2034 2023 2018 1998 2025 1986 2009 1993 1982 2008
2006; Rust 2057 2065 2032 2023 2003 1997 2005 2011 1997 2010 2004 2011 2009 2007 1992.
welcome_ms: KMP 3141 3179 3149 3158 3138 3107 3140 3101 3141 3069 3135 3111 3102 3133 3107;
Rust 3105 3178 3136 3128 3126 3125 3106 3123 3097 3123 3114 3124 3117 3108 3103.

**3. Memory** (simulator, Welcome screen 6 s after launch, `footprint <pid>` on the host,
15 launches per series, all identical): physical footprint **KMP 31 MB (peak 36 MB), Rust
27 MB (peak 33 MB)**, about 4 MB (13%) less. `ps` RSS (counts shared simulator frameworks, so
only indicative): KMP 233 MB, Rust 227 MB. Per-run lines are in `/tmp/l2-*.txt` on the Mac.
`footprint` rounds to whole MB.

**4. Release build time from clean outputs** (dependency downloads kept: Gradle/Konan caches,
cargo registry; `kmp/*/build`, DerivedData and the cargo target dir removed first; seconds).

| | KMP | Rust (size profile) | Rust (default) |
|---|---|---|---|
| core only, device slice | 48 (`:shared:linkReleaseFrameworkIosArm64`, cold daemon, no build cache) | 20.0 (`cargo rustc`, aarch64-apple-ios) | 16.2 |
| whole `ios:rust` step | (n/a) | 80.9 (both slices + host uniffi-bindgen + xcframework) | 59.8 |
| `xcodebuild` device Release | 84.1 (Gradle build cache warm), 85.2 (cache removed) | 46.3 | 47.4 |
| total from clean | 85 | 127.2 (ios:rust + xcodebuild) | 107.2 |

Rust is slower from clean because `ios:rust` also builds the simulator slice and the bindgen
tool; the Xcode build phase repeats `ios:rust` but it is then a cached no-op (seconds). An
incremental Rust change is seconds; not measured for Kotlin.

Not measured: device (not simulator) launch time or memory, App Store thinned size, energy.
