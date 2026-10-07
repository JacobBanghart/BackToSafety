# Maestro parity harness

The device side of `PARITY_PLAN.md`. The same YAML drives the RN app now and the
Compose/SwiftUI apps later, so it selects **only by testID** (`spec/testids.json`),
never by coordinates. It matches visible text only where the text is the thing
being tested.

## One-time setup (devbox, Android)

- Android SDK in `~/Android/Sdk`: `platform-tools`, `emulator`, `platforms;android-36`,
  `build-tools;36.0.0`, `system-images;android-36;google_apis;x86_64`
- AVD: `avdmanager create avd -n nijii-pixel7 -k "system-images;android-36;google_apis;x86_64" -d pixel_7`,
  then `hw.ramSize=4096M` and `hw.cpu.ncore=8` in its `config.ini` (at 2 GB, adb drops under load)
- `/dev/kvm` usable (the devbox gets it from CT 201's `dev0`); Maestro comes from `mise.toml`

## Running

```sh
maestro/start-emulator.sh                       # boot headless + prepare-android.sh
adb install -r "$(maestro/build-android.sh)"    # release build with test seams
maestro test maestro/flows/                     # L3 behaviour flows
maestro/capture.sh /tmp/captures                # L4: every state × light/dark/large-text
python3 maestro/compare_screens.py /tmp/captures/light spec/goldens/android/light
maestro/upgrade/upgrade.sh install-over OLD.apk NEW.apk   # L5
python3 maestro/sync_features.py                # after adding a flow
```

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
- **F-27.** The first tap after a fresh launch is sometimes dropped, so onboarding retries it.
