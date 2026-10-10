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

## Lanes (wave 1)

Each lane has its worktree at `~/github/nijii-rust-<lane>` on branch `rust/<lane>`, cut
from `rust-port`.

| Lane | Port | Owns | Agent | Status |
|---|---|---|---|---|
| data | 5570 | `core/src/store/` (rusqlite; Room v2 schema), `EmergencyAway`, analytics sink, app-level store context | | starting |
| shell | 5556 | `main.rs` routing/startup, `ui/`, `platform/` (JNI base, edge-to-edge, dark mode, font scale, back, deeplinks + debug clock seam, keyboard) | | starting |
| onboarding | 5558 | `screens/onboarding.*` (name, photo, appearance, contact, complete), `platform/photo.rs` | | starting |
| home | 5560 | `screens/home.*`, `screens/settings.*` | | starting |
| emergency | 5562 | `screens/emergency.*`, `platform/alerts.rs` (AlarmManager), SMS and dial intents | | starting |
| readout | 5564 | `screens/readout.*`, `screens/profile.*` (clipboard, share) | | starting |
| contacts | 5566 | `screens/contacts.*`, `screens/destinations.*` (contact picker, reorder gestures) | | starting |

The orchestrator uses port 5554.

**Order inside the wave:**

1. *data* lands the store API first: entity types and `Store` method signatures named after
   `Store.kt` in snake_case. The orchestrator merges it into `rust-port` right away, and
   screen lanes `git merge rust-port`.
2. Until then, screen lanes build layout and CSS against the oracle.

## Merge log

- 2026-10-10: `rust-port` base: core + vectors, Welcome, skeleton (every route stubbed),
  harness tasks.

## Owner questions

(none yet)

## Lane notes

Each lane appends here on its own branch: what's done, what's next, any surprises.
