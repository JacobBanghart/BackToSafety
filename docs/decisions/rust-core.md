# Decision: a Rust core under the native apps

**Status:** experiment on the `rust-port` branch (2026-10-10/11). `main` still runs on the
Kotlin Multiplatform core.

## Context

Back to Safety is two native UIs, Jetpack Compose and SwiftUI, on one shared core: the rules,
translations, the store and the emergency logic. On `main` that core is Kotlin Multiplatform
(`kmp/shared`), which compiles to the JVM for Android and to Kotlin/Native for iOS. This
branch replaces it with Rust.

## Decision

- The core is the Rust crate in `core/`.
- The apps reach it through [UniFFI](https://mozilla.github.io/uniffi-rs/), which generates the
  bindings: Kotlin (over JNA) for `android/` and Swift for `ios/`.
- Each app keeps its UI code unchanged. Thin adapters (`android/app/.../app/core/`,
  `ios/BackToSafety/Core/`) give the screens the same call sites they had on the Kotlin core.
- The database keeps its file, path, schema and Room identity hash. An install updated from
  the KMP app, or from the React Native app before it, keeps its data, and upgrade tests
  check that on both platforms.

## How it was held to the old behaviour

None of the existing gates was re-blessed. That covers the spec vectors, the screenshot
tests, the 14 Maestro flows plus the reorder gestures, fidelity against the goldens, and
upgrades from the KMP app. Any difference counted as a bug in the Rust path.

## Measurements

The Rust app was compared with the KMP app (`main` f4b5d88): release builds, the same device,
and 15 cold starts per round.

**Android** (API 36 emulator, arm64 split, after the optimization pass):

| | KMP | Rust core |
|---|---|---|
| Download per phone (deflated) | 3.98 MB | 4.25 MB (+7%) |
| Native library | 1.2 MB (Room's SQLite) | 1.7 MB (`libnijii_core.so`) + JNA 0.2 MB |
| Cold start, median of two rounds | 276 / 294 ms | 277 / 295 ms |
| Memory (PSS, Welcome screen) | 30.7 MB | 32.4 MB (+1.7 MB) |
| Clean release build | 99 s | 150 s (the Rust dependencies built cold, for 5 targets) |
| Rebuild after a core change | 79 s | 43 s |

**iOS** (iPhone 16 Pro simulator; arm64 device build; `main` a8dea5e against this branch; same
Mac, same session; after the optimization pass):

| | KMP | Rust core |
|---|---|---|
| App bundle (installed, `.app`) | 17.8 MB | 13.2 MB (−26%) |
| Download (zipped IPA, unsigned) | 5.15 MB | 4.17 MB (−19%) |
| Core library before linking | 15.8 MB (`Shared`) | 8.1 MB (`libnijii_core.a`) |
| Cold launch (startup_ms, median of two rounds) | 537 / 531 ms | 523 / 525 ms |
| Memory (footprint) | 31 MB | 27 MB (−13%) |
| Clean build | 81 s | 104 s (with the simulator slice and bindgen) |

On Android the Kotlin core costs almost nothing, because the runtime is already there. On
iOS, Kotlin/Native brings its own runtime and garbage collector, which a Rust static library
doesn't need.

Getting Android to parity took four changes:

- the 4 regex patterns hand-written instead of the regex crate (−1.25 MB)
- SQLite compiled without unused features (−0.45 MB)
- `opt-level = "z"`, fat LTO and stripping
- the core started off the main thread: UniFFI's ~300 function registrations had taken
  20–34 ms before the first frame

## Trade-offs

- **For users:** about even. Android is +7% download and +1.7 MB memory, with the same start
  time. iOS is −19% download, −26% installed and −13% memory.
- **For development:**
  - Core changes rebuild faster.
  - The core is about three times the lines of the Kotlin it replaced (3,300 against 1,200),
    plus about 460 lines of adapters and build glue.
  - The team works in a second language with its own toolchain (cargo, cargo-ndk, UniFFI).
- **The real case for Rust:** reusing the same core beyond these two apps, on web, desktop or
  a server.

## What was tried and dropped

The first attempt also wrote the UI in Rust (Dioxus in the system WebView). Every native
feature (photo picker and crop, dialogs, notifications and alarms) needed workarounds, and
the UI could never match the native apps. It was dropped in favour of this design.

## History

- [`docs/history/RUST_CORE_PORT_LOG.md`](../history/RUST_CORE_PORT_LOG.md): the full log of
  the port, with the phases, merges, findings and every measurement step.
- [`docs/history/PARITY_PLAN.md`](../history/PARITY_PLAN.md): how the native apps were held
  to the React Native app they replaced.
- One real bug the port turned up: on `main`, editing a second field on the Android profile
  screen reverted the first edit. The fix is in PR #1.
