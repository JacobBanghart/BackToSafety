# Rust core under the native apps

The native apps (`kmp/androidApp`, Jetpack Compose; `kmp/iosApp`, SwiftUI) run on `rust/core`
through [UniFFI](https://mozilla.github.io/uniffi-rs/): every rule, the translations, the
store (rusqlite, the Room v2 schema), EmergencyAway, analytics and the app clock. Generated
Kotlin bindings serve Android and Swift bindings serve iOS; `rust/uniffi-bindgen` makes both.
The Kotlin Multiplatform core (`kmp/shared`) that this replaced is in git history, and so is
the first attempt (Dioxus in a WebView, `git log -- rust/app`), which the owner dropped.

## The gates are the oracle, and the UI must not change

The UIs stay as they were; only what's under them changed. The gates:

- `mise run check`: spec/check.py contracts, the Rust tests (all of `spec/vectors`, the store
  and migrations, EmergencyAway, the FFI surface) and AnalyticsParityTest.
- `mise run snapshots`: Roborazzi screen snapshots (Android; iOS snapshots run on CI's Mac).
- `mise run android:flows` / `android:fidelity`: Maestro flows and the goldens. `ios:*`: the same.

They must pass **without re-blessing anything**: no `RECORD=1`, no golden or vector edits.
A difference means the core behaves differently from what shipped, and that's a bug to fix.

## Design rules

- **Thin platform adapters keep UI call sites stable.** Where the Kotlin UI uses an
  idiom (the `Translate` function type with `invoke`, `AppClock`, `Analytics.track`, suspend
  Store calls), write a small adapter in the app (`androidApp/.../core/`, `iosApp/.../Core/`)
  over the generated bindings. Don't rewrite screens. Most screen edits should be imports.
- The core stays platform-free. The platform implements foreign traits:
  - `AlertScheduler` (AlarmManager / UNUserNotificationCenter)
  - the analytics sink (the existing PostHog SDK in each app)
- The database keeps the KMP app's file and path (`<filesDir>/SQLite/nijii.db` on Android,
  and where `iosApp/.../Core/AppStore.swift` opens it). The schema and Room identity hash
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
  service stops the newest emulator when memory runs low, and any emulator over 12 GB resident
  (one grew to 31 GB on 2026-10-11); see `journalctl --user -u
  nijii-watchdog`.
- Never run `maestro/stop-emulators.sh`, `capture-parallel.sh` or the `android:flows` /
  `android:fidelity` tasks as they are: they boot 4 emulators. Run them with `DEVICES=1`, or
  on your own emulator.
- Never run `adb kill-server`, `adb reconnect` or `./gradlew --stop` across the board. One adb
  server and the Gradle daemons serve every agent, and on 2026-10-10 an `adb kill-server` cut
  another agent's flows off mid-run. Pin every adb and maestro call to your serial.
- iOS work runs on the Mac (`ssh jacob@10.1.0.17`, see `mise run ios:remote`). The Mac is a
  laptop that may be asleep, so fail fast and say so.

## Working

- Commit early and often (WIP included) on your own branch, in your own worktree. Usage
  limits cut agents off, and commits are how work resumes.
- Never push, and never merge into `rust-port`; the orchestrator reviews and merges.
- Notes go in `rust/PORT_PLAN.md`: what's done, what's next, questions for the owner.
