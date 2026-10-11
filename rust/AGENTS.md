# Rust core under the native apps (experiment)

The goal: the existing native apps (`kmp/androidApp`, Jetpack Compose; `kmp/iosApp`, SwiftUI)
running on a Rust core instead of `kmp/shared`. `rust/core` is that core: every rule,
translations, the store (rusqlite, Room v2 schema), EmergencyAway and analytics, ported from
`kmp/shared`. 50 Rust tests cover them, including all of `spec/vectors`.

The apps reach the core through [UniFFI](https://mozilla.github.io/uniffi-rs/): generated
Kotlin bindings on Android and Swift bindings on iOS. `kmp/shared` retires. This all lives
on the `rust-port` branch; `main` is untouched and nothing ships from here.

History: a first attempt (2026-10-10) also wrote the UI in Rust (Dioxus in a WebView). The
owner stopped it because every native feature needed workarounds, and switched to this
design. It's in git history (`git log -- rust/app`) if anyone needs it.

## The oracle is the KMP app, and the UI must not change

The UIs stay exactly as they are; only what's under them changes. So the existing gates are
the oracle, unchanged:

- `mise run check`: spec/check.py contracts (testIDs, features, locales).
- `mise run snapshots`: Roborazzi screen snapshots.
- `mise run android:flows` / `android:fidelity`: Maestro flows and the RN goldens.
- `ios:*`: the same on iOS.

They must pass **without re-blessing anything**: no `RECORD=1`, no golden or vector edits.
A difference means the Rust path behaves differently, and that's a bug to fix. Behavior
questions are settled by the Kotlin code in `kmp/shared` (read it in git history once it's
removed); its JVM tests are ported in `rust/core/tests`.

## Design rules

- **Thin platform adapters keep UI call sites stable.** Where the Kotlin UI uses an
  idiom (the `Translate` function type with `invoke`, `AppClock`, `Analytics.track`, suspend
  Store calls), write a small adapter in the app (`androidApp/.../core/`, `iosApp/.../Core/`)
  over the generated bindings. Don't rewrite screens. Most screen edits should be imports.
- The core stays platform-free. The platform implements foreign traits:
  - `AlertScheduler` (AlarmManager / UNUserNotificationCenter)
  - the analytics sink (the existing PostHog SDK in each app)
- The database keeps the KMP app's file and path (`<filesDir>/SQLite/nijii.db` on Android,
  and the iOS equivalent from DatabaseBuilder.ios.kt). The schema and Room identity hash
  are identical, so an install updated from the KMP app keeps its data. That's a test.
- `DesignTokens` is UI, not core. Generate it inside each app from `spec/design-tokens.json`.
- Tools come only from mise (`mise.toml`): cargo-ndk, uniffi-bindgen (built from the
  workspace), the Rust targets.

## Load limits (since the 2026-10-10 reboot)

Seven parallel lanes (4 emulators, 7 builds at 16 jobs each, Gradle daemons) exhausted the
box's 48 GB. Reclaim and swap drove I/O pressure to ~90% and ssh died. So:

- **At most 2 agents run at once.**
- **Heavy builds are queued.** Cargo and Gradle builds run through
  `rust/scripts/heavy.sh <cmd…>`: one at a time across all worktrees, only with 8 GB+
  available, at low CPU and I/O priority. All worktrees share one `CARGO_TARGET_DIR`
  (`mise.toml`). Stop Gradle daemons after a build (`./gradlew --stop`).
- **Emulators:** at most 2. Use `rust/scripts/emulator.sh up <port>` (it waits for a slot and
  for 12 GB+ available) and `down <port>`. Each takes 5 to 9 GB. The `nijii-watchdog` user
  service stops the newest emulator when memory runs low; see `journalctl --user -u
  nijii-watchdog`.
- Never run `maestro/stop-emulators.sh`, `capture-parallel.sh` or the `android:flows` /
  `android:fidelity` tasks as they are: they boot 4 emulators. Run them with `DEVICES=1`, or
  on your own emulator.
- iOS work runs on the Mac (`ssh jacob@10.1.0.17`, see `mise run ios:remote`). The Mac is a
  laptop that may be asleep, so fail fast and say so.

## Working

- Commit early and often (WIP included) on your own branch, in your own worktree. Usage
  limits cut agents off, and commits are how work resumes.
- Never push, and never merge into `rust-port`; the orchestrator reviews and merges.
- Notes go in `rust/PORT_PLAN.md`: what's done, what's next, questions for the owner.
