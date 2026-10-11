# Rust port plan

The durable state of the port: the resume cron and every new session start here. The rules
are in `AGENTS.md`.

**Goal:** the whole app in Rust (core + Dioxus UI), held to the KMP oracle on Android.
iOS comes after Android, on the Mac.

**Status (2026-10-10):**

- Done:
  - `core/` has every pure rule, and all 232 `spec/vectors` cases pass.
  - The Welcome screen is ported.
  - The Maestro harness drives the Rust app: HTML ids reach Maestro as resource-ids.
  - `rust:*` mise tasks, and the oracle APK is cached.
- Next: wave 1 below.

## ⚠️ PAUSED (2026-10-10 17:25): box rebooted after memory exhaustion

Do **not** resume the lanes until the owner approves the guardrails below. From about 13:50,
free memory sat at roughly 0.8 GB for about 3h20m (Gradle daemon logs). Reclaim and swap on
the loop-device rootfs pushed I/O pressure to ~90%. journald stopped at 17:12 and ssh died.
Causes, all from this port's parallel setup:

- up to 4 emulators at 5.3 to 8.8 GB each: the image falls back from `-gpu guest` to host
  lavapipe rendering and grows, plus one duplicate emulator from the emulator.sh race
  (fixed in dc649d8)
- 7 worktrees building at once with CARGO_BUILD_JOBS=16 each, with ~52 GB of separate
  target dirs
- dx's Gradle bundling: 2 GB-heap daemons, some per worktree, that lingered after use
- nothing measured memory, and agents that died at the session limit left their emulators and
  daemons running

## Lanes (wave 1)

Each lane has its worktree at `~/github/nijii-rust-<lane>` on branch `rust/<lane>`, cut
from `rust-port`.

| Lane | Port | Owns | Agent | Status |
|---|---|---|---|---|
| data | 5570 | `core/src/store/` (rusqlite; Room v2 schema), `EmergencyAway`, analytics sink, app-level store context | `aba6906b305d83d7a` | running: trial integration (`rust/integration`, `INTEGRATION.md`): conflicts and duplicates across lanes |
| shell | 5556 | `main.rs` routing/startup, `ui/`, `platform/` (JNI base, edge-to-edge, dark mode, font scale, back, deeplinks + debug clock seam, keyboard) | `a6bc77f81b965afdc` | running |
| onboarding | 5558 | `screens/onboarding.*` (name, photo, appearance, contact, complete), `platform/photo.rs` | `aa9228cf7917db373` | running |
| home | 5560 | `screens/home.*`, `screens/settings.*` | `af899f27403815313` | running |
| emergency | 5562 | `screens/emergency.*`, `platform/alerts.rs` (AlarmManager), SMS and dial intents | `a399373f46570d27a` | running |
| readout | 5564 | `screens/readout.*`, `screens/profile.*` (clipboard, share) | `a409ae7037f99154b` | running |
| contacts | 5566 | `screens/contacts.*`, `screens/destinations.*` (contact picker, reorder gestures) | `afa819db5fc29fc9f` | running |

The orchestrator uses port 5554.

**Order inside the wave:**

1. *data* lands the store API first: entity types and `Store` method signatures named after
   `Store.kt` in snake_case. The orchestrator merges it into `rust-port` right away, and
   screen lanes `git merge rust-port`.
2. Until then, screen lanes build layout and CSS against the oracle.

## Orchestration

- The orchestrator session runs a recurring cron (every 30 min) to resume work. Each tick it:
  - re-reads this file
  - checks each lane (`git -C ~/github/nijii-rust-<lane> log rust-port..`)
  - resumes any agent that stopped on a usage or session limit, via SendMessage to its agent ID
  - reviews and merges finished work into `rust-port` and runs `mise run rust:check`
  - tells lanes to `git merge rust-port` when something they need has landed
- If the orchestrator session itself is gone, a new session resumes from this file, the lane
  branches and their notes. Agent IDs only work in the session that started them; a new
  session starts fresh agents on the same worktrees ("continue from your lane notes and
  commits").

## Merge log

- 2026-10-10: `rust-port` base: core + vectors, Welcome, skeleton (every route stubbed),
  harness tasks.
- 2026-10-10: merged `rust/data` STORE API (5fd8eb4): rusqlite store with the Room v2 DDL and
  identity hash (checked against kmp/shared/schemas), MIGRATION_1_2, adoption of RN
  databases, EmergencyAway, an analytics sink generated from spec. 15 store tests pass. The
  `put_setting` updated_at is NULL, as in Kotlin (RN stamped it; nothing reads it).

- 2026-10-10: merged `rust/data` milestone 2 (4cc925f). `use_store()` and
  `use_emergency_away()` are in context, and the DatabaseTest, EmergencyAwayTest and
  AnalyticsParity ports pass (2 ignored until the screens land). The Android DB path hasn't
  run on a device yet; the shell lane checks it.

- 2026-10-10: merged `rust/data` startup analytics (c880bdb): app_ready, data_migrated and
  data_migration_failed, and a PostHog sink (ureq + rustls; the key comes from the environment
  at build time, none in the repo). 18 new tests. Not yet run on a device; all 4 emulator
  slots were busy with screen lanes.

- 2026-10-10: merged `rust/data` `rust:parity` (97447cc): states × modes, flows and gestures in
  one run, with a pass/fail summary and oracle captures cached per oracle commit. The trial
  run on device showed the store opens and seeds; the rows fail until the screens merge.

## Owner questions

1. **DB path (data):** the Rust app keeps `nijii.db` in `databases/`; the KMP app keeps it in
   `<filesDir>/SQLite/`. That's fine while this stays an experiment. If the Rust app ever
   replaced the KMP one, it would need the KMP path (or a copy on first launch). Not blocking.
2. **DB open failure (data):** the app panics instead of opening an empty database that
   would hide the caregiver's data. Do you want a visible error screen instead? Not
   blocking.

## Lane notes

Each lane appends here on its own branch: what's done, what's next, any surprises.

### data lane

Done:

- `core/src/store/`: entities, `Store` (Kotlin names in snake_case, synchronous, `Mutex<Connection>`),
  Room v2 DDL plus identity hash, `MIGRATION_1_2`, RN database adoption (`adopt_rn_database`).
  `Store::open` migrates in one transaction and refuses a newer schema rather than downgrading.
- `core/src/emergency_away.rs` (`AlertScheduler` trait, `EmergencyAway`) and `core/src/analytics.rs`
  (global sink; `AnalyticsEvent` generated by `core/build.rs` from `spec/analytics-events.json`).
- Tests (`core/tests/`): `store.rs` (StoreTest, 15), `database.rs` (DatabaseTest on the RN fixture,
  plus the on-disk schema checked against `2.json` for fresh and migrated files, 6),
  `emergency_away.rs` (10), `analytics_parity.rs`.
- `app/src/store.rs`: `store::provide()` (called at the top of `App`) opens and seeds the store once
  and puts `Arc<Store>` and `Arc<EmergencyAway>` in context. Screens use `use_store()` and
  `use_emergency_away()`; `store::store()` is the same store for code outside components.

For other lanes:

- **Emergency lane:** `EmergencyAway` delivers through `store::alert_scheduler()`, which is a no-op
  today. Replace its body with the AlarmManager scheduler (one place). `EmergencyAway` methods that
  touch the store return `store::Result`.
- **Shell lane:** `Store::migrated_from()` / `migration_duration_ms()` give data_migrated's inputs
  (Kotlin's `DatabaseOpen.migratingFrom`); `data_migrated_properties(from, ms)`,
  `readiness_properties()` and `device_id()` are ready for startup. A migration failure surfaces as
  `StoreError` from `Store::open`, which today panics at startup (see questions); the shell lane
  decides how to report it (`data_migrated` with ok=false:
  `store::data_migration_failed_properties`).
- Analytics: `Analytics::set_sink(...)` is process-wide. Tests that read it must serialize (see
  `core/tests/emergency_away.rs`). The two `analytics_parity` tests that need every screen are
  `#[ignore]`d; the orchestrator runs them with `--ignored` once all lanes have merged.
- Dev runs off Android keep the database at `$XDG_DATA_HOME/nijii/nijii.db`; `NIJII_DB` overrides.

Decisions from the oracle: `put_setting` writes `updated_at = NULL` (Kotlin; RN stamped it, nothing
reads it). `save_failed`'s error type is `StoreError::type_name()`.

Open questions for the owner:

1. The Rust app's DB is at `/data/data/com.backtosafety.rust/databases/nijii.db` (as briefed), while
   the KMP app uses `<filesDir>/SQLite/nijii.db` so it can adopt the RN app's file. Fine for an
   experiment (separate package, no RN data), but a shipping Rust app replacing KMP would need the
   KMP path (or a copy on first launch).
2. If the database can't be opened at startup the app panics rather than opening an empty database
   that would hide the caregiver's data. Is a visible error screen wanted instead?

`data_migration_failed_properties` is ported (`store::`); the shell lane calls it at startup.

#### data lane: startup analytics (2026-10-10)

- `core/src/posthog.rs`: PostHog HTTP `/batch/` client on a background thread (ureq + rustls, no
  OpenSSL). Same settings as the KMP SDK config: flush at 20 events or 10 s, 100 per request, 1000
  queued (oldest dropped), failed batches retried at the next flush, people profiled only after
  `identify` (`$process_person_profile: false` before). `Analytics` null properties are dropped,
  as the KMP sink does.
- `core/src/startup.rs`: app_ready properties and once-per-process guard, `report_data_migration`
  (ok or failed after the first query), `report_open_failure` (a migration that can't open is
  reported as data_migrated with ok=false), process uptime from /proc (Android's
  `uptimeMillis - getStartUptimeMillis`), and the SDK lifecycle events ("Application
  Installed/Updated/Opened", last version kept in `posthog_app_version` next to the database).
- `app/src/platform/posthog.rs`: key and host from `POSTHOG_KEY` / `POSTHOG_HOST` at build time
  (`app/build.rs`; no key, no sink, nothing sent), version and build from `version.json`
  (`NIJII_VERSION`, android.versionCode). `app/src/startup.rs`: `open_store` (called by
  `store::services()`) does what App.kt's launch effect does; `use_app_ready` (a hook inside
  `store::provide()`) reports app_ready after the first render.
- Tests: `core/tests/posthog.rs` (9, against a local fake server), `core/tests/startup.rs` (9).
  `analytics_parity` has a new test for the events the core and startup send; the two
  all-events/screens tests stay `#[ignore]`d because the remaining events (contacts, destinations,
  emergency, onboarding, profile, readout, settings, save_failed, dial_failed, screen_viewed) are
  the screen lanes'. Run them with `-- --ignored` after the merges to get the list that is left.
- `rust:build` links with ureq/rustls (ring) and bundled SQLite; not run on an emulator (no free
  slot after two 7-minute waits).

For other lanes:

- **Emergency lane:** app_ready also carries `notifications_enabled` and `exact_alarms` in KMP
  (CountdownNotifications). Call `startup::set_device_readiness(|| props)` once the alarms exist;
  until then they're omitted.
- **Shell lane:** screens are already reported by your layout. The SDK also sends "Application
  Backgrounded" when the app goes to the background; that needs a lifecycle hook from the platform
  and isn't ported. Not ported either: session replay, error autocapture, feature flags
  (`captureScreenViews` is off in KMP too).
- The anonymous ID before `identify` is per process (the SDK persists it); events before identify
  are not profiled, so only the link event differs.

#### data lane: parity runner

`mise run rust:parity -- [options]` (`--help` lists them) is the final-verification command:

- Runs every `maestro/states` in light, dark and large-text through `rust:compare`'s logic, every
  `maestro/flows` through `rust:flows`, and `maestro/gestures/reorder.sh` retargeted at the Rust
  app, on one device (`ANDROID_SERIAL`) or sharded across several (`--serials a,b` or
  `PARITY_SERIALS`; `--up 5570,5572` boots them with `emulator.sh` and shuts them down after).
  The Rust APK is built once and installed per device; the shards run in parallel.
- Writes `/tmp/nijii-parity/summary.md` and `results.json` (`OUT=` to move them): a failing list
  first, then one row per state and mode (pass/fail, layout deltas from `compare_screens.py`, the
  Rust and oracle screenshot paths), per flow (with the log), and per reorder gesture. Anything
  with no result in the logs is a failure (`not run`, `Rust capture failed`, `oracle capture
  missing`). Exit status 1 on any failure.
- Filters: `--states a,b|none`, `--flows a,b|none`, `--no-gestures`, `--modes "light dark"`.
  A kind with no filter runs in full.
- Oracle cache: `~/.cache/nijii-oracle/captures/<oracle commit>/<mode>/<state>.{png,layout.json}`.
  A plain run recaptures the oracle for the selected states and refreshes the cache; `--fast`
  captures only what the cache lacks, so the oracle is captured once per oracle commit.
- `rust:compare` and `rust:flows` keep their CLI. New optional env: `APK`, `SKIP_INSTALL`, and for
  compare `ORACLE_CAPTURES` / `ORACLE_REFRESH` (as above). `rust/scripts/parity_summary.py` builds
  the summary; `python3 rust/scripts/test_parity_summary.py` tests its parsing off-device.

Tried on slot 5570 (`--up 5570 --states onboarding-welcome,settings --flows contacts --no-gestures
--modes "light dark"`, against rust-port before the screens/shell merged): the oracle captures for
both states in both modes landed in `~/.cache/nijii-oracle/captures/f4b5d88/` (so `--fast` skips
them from now on), and the Rust app's launch worked (store opened and seeded, the stub Home
showed), but the Rust captures and the flow failed because the stubs have no `onboarding-*` ids and
startup routing isn't merged. The summary reported those as five failures with the log paths, as
designed. A first attempt lost its emulator during the 11-minute APK bundling (the box was
loaded); the runner now boots emulators after the build and reports a device that is gone as the
reason rather than "not run". Re-run it once the lanes have merged.
