# Rust port (experiment)

A third implementation of Back to Safety, all Rust: `core/` (the rules and the store, a port
of `kmp/shared`) and `app/` (Dioxus; the UI is HTML/CSS in the system WebView). It lives on
the `rust-port` branch only. Nothing here touches `main` or ships.

## The oracle is the KMP app

The Kotlin app in `kmp/` is the oracle: what it does is correct, and the Rust app copies it.

- **Behavior:** read the KMP source first (`kmp/shared` for logic, `kmp/androidApp` for the
  screen), then the RN original in git history (`git show 2f4dc9d^:app/<screen>.tsx`). RN
  styles were Yoga flexbox, so they carry over to CSS almost 1:1. The Compose code shows
  which RN quirks matter (rnBorder, font padding and so on).
- **Running:** `mise run rust:oracle` installs the oracle APK (`com.backtosafety.app`, built
  once into `~/.cache/nijii-oracle`) next to the Rust app (`com.backtosafety.rust`) on your
  emulator. Drive it with Maestro or adb to see exactly what a screen does.
- **Gates:** `spec/` holds the contract both apps answer to:
  - `spec/vectors` is run by `core/tests/vectors.rs`.
  - `spec/testids.json`: every testID becomes an HTML `id`, which Android exposes as the
    resource-id Maestro selects by.
  - `spec/design-tokens.json` becomes CSS variables (`app/build.rs`).
  - `i18n/locales` is read by `nijii_core::Translations`.
  - `spec/db-schema.json` and `spec/storage.md` define the store.
- Where the oracle and the spec disagree, or the oracle is silent, don't guess. Write it in
  your lane's notes in `PORT_PLAN.md` and pick the KMP behavior.

## Commands (mise only)

Every tool comes from `mise.toml`, so never install with cargo, rustup or apt directly. Run
from the repo root.

| Task | What |
|---|---|
| `mise run rust:check` | core tests (vectors, store) + the app compiles for Android |
| `mise run rust:build` | debug APK for the x86_64 emulator; prints its path |
| `mise run rust:run` | build, install, launch on `ANDROID_SERIAL` |
| `mise run rust:oracle` | install the KMP oracle on `ANDROID_SERIAL` |
| `mise run rust:compare [state…]` | capture `maestro/states` from both apps, gate on layout vs the oracle |
| `mise run rust:flows [flow…]` | `maestro/flows` retargeted at the Rust app, on `ANDROID_SERIAL` only |

`CARGO_BUILD_JOBS` defaults to 16, because lanes build at the same time. Override it per
command if the box is idle: `CARGO_BUILD_JOBS=48 mise run rust:build`.

## Parallel work

The port is split into lanes (`PORT_PLAN.md`). Each lane is one agent in its own git
worktree on its own branch, cut from `rust-port`. The orchestrator reviews each lane and
merges it into `rust-port`.

- **Stay in your files.** Each lane owns its screen modules (`app/src/screens/<area>.rs`
  and `<area>.css`), plus whatever new files it adds. Shared files are `main.rs`,
  `screens/mod.rs`, `ui/`, `platform/mod.rs`, `app.css`, `core/src/lib.rs` and the
  Cargo.toml files. Touch them only with one-line additions (a `mod`, a dependency, a
  route). A shared component you need before the shell lane has it goes in your own file
  first; the orchestrator dedupes when merging.
- **Your emulator only.** Each lane has a port: `rust/scripts/emulator.sh up <port>` gives
  you `emulator-<port>`. Then `export ANDROID_SERIAL=emulator-<port>` for every adb, mise
  and maestro command. At most `EMULATOR_MAX` (4) run at once; `up` waits for a slot.
  - Run `emulator.sh down <port>` when you're done verifying. Never run
    `maestro/stop-emulators.sh`, `run-flows.sh` or `capture-parallel.sh`: they act on every
    emulator, including other lanes'.
- **Don't run the KMP Gradle build.** The oracle is cached. Only `REBUILD=1 mise run
  rust:oracle` rebuilds it, and only the orchestrator runs that.
- **Commit early and often** on your lane branch, with WIP commits included. Agents get cut
  off by usage limits, and a commit is how the next session picks up. Never push, never
  merge into `rust-port` yourself, and never rebase other branches.
- **Pick up merged work:** `git merge rust-port` in your worktree when the orchestrator
  says something you need landed (the store API, shared components).
- **Notes:** at the end of each work session, update your lane's row and notes in
  `PORT_PLAN.md` on your branch: what's done, what's next, questions for the owner.

## Done means

A screen is done when its `maestro/states` pass `rust:compare` (layout vs the oracle) in
light, dark and large-text (`MODES="light dark large-text"`), and the `maestro/flows` that
cover it pass under `rust:flows`. You also look at the Rust and oracle screenshots side by
side yourself. Logic that has a KMP test gets a Rust test for the same cases.

## Platform notes

- Android exposes each HTML `id` as a resource-id, and text as accessibility text, so
  Maestro's `id:` and text selectors work unchanged (checked 2026-10-10).
- dp = CSS px in the WebView. Every `div`/`button` is a flex column by default (`app.css`),
  as in RN.
- Android APIs (intents, AlarmManager, the photo picker) go through JNI from Rust, in
  `app/src/platform/`. There is no Kotlin or Java in the Rust app.
