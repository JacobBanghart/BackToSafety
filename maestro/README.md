# Maestro parity harness

The device side of `PARITY_PLAN.md`. The same YAML drives the Android (Compose) and iOS
(SwiftUI) apps, so it selects **only by testID** (`spec/testids.json`), never by coordinates. It matches visible text only where the text is the thing
being tested.

## Setup

The `mise` tasks in the main README (`android:flows`, `android:fidelity`, `ios:flows`,
`ios:fidelity`) wrap everything below; use them unless you need one step on its own. The
Android SDK and Maestro come from `mise.toml`, and `start-emulator.sh` creates its
`nijii-pixel7` emulator (API 36, 4 GB) the first time. Linux needs `/dev/kvm` usable (the devbox
gets it from CT 201's `dev0`). iOS needs the Mac with Xcode and XcodeGen.

## Running

```sh
APK="$(maestro/build-android.sh)"               # the Android app: release build with test seams
maestro/start-emulator.sh 4 "$APK"              # 4 fresh headless emulators, app installed
maestro test --shard-split 4 maestro/flows/    # L3 behaviour flows, spread over the emulators
maestro/capture-parallel.sh /tmp/captures       # L4: every state × light/dark/large-text, sharded
MODES=large-text maestro/capture-parallel.sh /tmp/captures home   # just some modes and states
python3 maestro/compare_screens.py /tmp/captures/light spec/goldens/android/light
maestro/stop-emulators.sh                       # always: emulator host memory grows per screenshot
maestro/upgrade/upgrade.sh install-over OLD.apk NEW.apk   # L5 (one emulator)
maestro/gestures/reorder.sh                     # drag-to-reorder (adb draganddrop; one emulator)
python3 maestro/sync_features.py                # after adding a flow
python3 maestro/fidelity.py /tmp/captures        # vs the goldens (tier 2 ratchet: pixels + layout)
```

iOS runs on the Mac (`ssh jacob@10.1.0.17`, plain bash 3):

```sh
maestro/build-ios.sh                            # the iOS app (kmp/iosApp), simulator build with test seams
maestro/start-simulator.sh 6 APP                # 6 "nijii-N" iPhone 17 Pro simulators, app installed
maestro/capture-parallel-ios.sh /tmp/captures   # every state x light/dark/large-text
python3 maestro/fidelity.py /tmp/captures --platform ios
maestro/gestures/reorder-ios.sh                 # drag-to-reorder (an XCUITest bundle drives the drag)
maestro/upgrade/upgrade-ios.sh install-over OLD.app NEW.app
```

Overlapping `maestro hierarchy` calls on different simulators all answer with one
simulator's tree, so the parallel capture takes turns reading layouts.

A full capture is 17 states × 3 modes. On 4 emulators that's about 12 minutes; serially it's
about 45. Emulators use `-gpu guest` (software rendering inside Android). Two full runs were
byte-identical at zero tolerance, apart from the system bars, which the comparer ignores.
`-gpu swiftshader_indirect` was just as stable but leaked about 15 GB of host memory per hour.

## Conventions

- **Header.** Every flow starts with `# Features: <ids from spec/features.json>`;
  `sync_features.py` turns those into coverage and lowers the ratchet.
- **Assert persistence.** Flows check what survives `stopApp` + `launchApp`, not only the screen.
- **Leave the app gently.** To come back from the dialer or SMS app the way a person would,
  use `launchApp: { stopApp: false }`. A plain `launchApp` force-stops the app and can race its writes.
- **Freeze time.** `subflows/freeze-clock.yaml` before anything time-dependent, so
  countdowns and times are identical in every capture. `backtosafety://debug/clock?advance=<s>`
  moves time forward. Both work only in builds with test seams.
- **Scroll before acting.** Elements below the fold need `scrollUntilVisible` first.
  Scroll back up before asserting on something near the top.
- **Dismiss the keyboard with `subflows/hide-keyboard.yaml`**, not `hideKeyboard`: iOS has no
  system dismiss, so on iOS it drags the form down (F-40).
- **F-27.** The first tap after a fresh launch is sometimes dropped, so onboarding retries it.

## Goldens

`spec/goldens/` are screenshots and layouts of the React Native app the native apps replaced
(captured with test seams before it was removed, 2026-10-09). They stay the reference: the
native apps are held to them by `fidelity.py`, and a deliberate visual change re-blesses
them from the native apps (`fidelity.py --update` for the marks).
