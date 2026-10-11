# Rust core plan

The durable state of the work: every new session and every agent starts here. The rules are
in `AGENTS.md`.

**Goal (owner, 2026-10-10):** the native Compose and SwiftUI apps unchanged, on the Rust core
via UniFFI. `kmp/shared` retires. The existing gates pass without re-blessing anything.

## Phases

| # | Phase | Who | Status |
|---|---|---|---|
| 0 | Rust core ported from kmp/shared, 50 tests (vectors, store, migrations, EmergencyAway) | done | ✅ |
| 1 | UniFFI on `nijii-core`: exported API, generated Kotlin; Android libs via cargo-ndk; Gradle wiring | agent **ffi-android** | starting |
| 2 | androidApp on the Rust core, then remove kmp/shared from Android; all Android gates green, DB carried over from a KMP install | agent **ffi-android** | after 1 |
| 3 | iOS: XCFramework + Swift bindings, built on the Mac; the Xcode project links it | agent **ffi-ios** | starting (pipeline first, adapts to phase 1's API) |
| 4 | iosApp on the Rust core; iOS gates green | agent **ffi-ios** | after 3 |
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
| ffi-android | `rust/ffi-android` | | starting |
| ffi-ios | `rust/ffi-ios` | | starting |

## Merge log

- 2026-10-10: core ported (from the first attempt's data lane): rules + vectors, the store
  with the Room v2 DDL and identity hash, MIGRATION_1_2, adoption of RN databases,
  EmergencyAway, an analytics sink generated from spec, startup analytics properties.
- 2026-10-10: the Dioxus/WebView UI was shelved by the owner; the native UIs stay. Removed
  `rust/app`, its tasks, and the app-scanning analytics test (the apps' own parity test
  covers it).

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

(none open)

Settled:

- The DB path follows the KMP app, so updates keep their data.
- Native dialogs and photo cropping come for free, because the UIs stay native.

## Notes

Each agent appends its notes here.
