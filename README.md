# Back to Safety

An app for caregivers of people with dementia or other wandering risks: a guided 15-minute
search when someone goes missing, a one-tap info sheet for 911, and the profile, emergency
contacts and familiar places it draws on. Everything is stored on the device.

Native on both platforms: Jetpack Compose on Android and SwiftUI on iOS, on a shared Kotlin
Multiplatform core. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

- Privacy policy: https://backtosafety.app/privacy
- Android package and iOS bundle ID: `com.backtosafety.app`

## Features

- Emergency protocol: an 11-step checklist with a 15-minute countdown, haptic alerts, one-tap
  911 calling, and an SMS to the alert circle
- Info sheet: the 911 call script and full details, ready to read or copy
- Profile: photo, appearance, medical, communication and device details
- Emergency contacts: import from the address book or add by hand, drag to reorder
- Familiar places: where to look first, with risk levels, drag to reorder
- Light and dark themes; text that scales with Dynamic Type and font size

## Setup

Tools are pinned in `mise.toml` and installed by [mise](https://mise.jdx.dev): Java 17, the
Android SDK, Maestro, ktlint, SwiftFormat and gitleaks.

```sh
curl https://mise.run | sh          # or: brew install mise (see mise.jdx.dev for shell activation)
mise trust && mise install          # the pinned tools
git config core.hooksPath .githooks # pre-commit secret scan, pre-push contract check
mise tasks                          # every task below, with descriptions
```

Not installed by mise:

| Needed for | What |
| --- | --- |
| Contract checks, screenshot comparison (`spec/check.py`, `maestro/*.py`) | Python 3 (the fidelity tasks install Pillow into `.venv` themselves) |
| `ios:runner`, `ios:snapshots` | The [GitHub CLI](https://cli.github.com) (`gh`), signed in |
| Android emulators on Linux | KVM (`/dev/kvm` writable, or the `kvm` group) |
| Anything iOS (on the Mac) | Xcode with an iOS simulator runtime, and XcodeGen (`brew install xcodegen`); `mise run ios:setup` checks both |
| `ios:remote` from another machine | SSH access to the Mac; `NIJII_MAC` (default `jacob@10.1.0.17`) and `NIJII_MAC_DIR` (default `nijii-remote`) say where |

## Tasks

Fast, no device (CI runs these on every push):

| Task | What it does |
| --- | --- |
| `mise run check` | Spec contracts (`spec/check.py`) and the shared core's tests |
| `mise run lint` | ktlint and SwiftFormat; `FIX=1` fixes what they can |
| `mise run snapshots` | Android screen snapshots against `src/test/snapshots`; `RECORD=1` re-records |
| `mise run ios:snapshots` | Copies the iOS snapshots CI recorded for this commit into the repo (iOS snapshots run on CI's Mac) |

Run the apps:

| Task | What it does |
| --- | --- |
| `mise run android` | Build and run the Android app on a connected phone, or an emulator it starts (`android:run`) |
| `mise run android:emulator` | Start an emulator with a window (creates it the first time) |
| `mise run android:sdk` | Install the SDK packages the build needs (the other Android tasks do this first) |
| `mise run ios` | Build and run the iOS app on a simulator (`ios:run`; on the Mac) |
| `mise run ios:sim [N]` | Boot N simulators with the harness's fixed status bar and contact |
| `mise run ios:setup` | Check the Mac has Xcode, XcodeGen and a simulator runtime |

To work in Xcode instead: `cd kmp/iosApp && xcodegen generate && open BackToSafety.xcodeproj`.
Its first build phase rebuilds the shared Kotlin core, so Run always picks up Kotlin changes.

On devices (build the app, start fresh devices, run, shut down; `DEVICES=n` sets how many):

| Task | What it does |
| --- | --- |
| `mise run android:flows` / `ios:flows` | Every Maestro flow and the drag-to-reorder gestures |
| `mise run android:fidelity` / `ios:fidelity` | Every screen in light, dark and large text against the goldens |

The Mac:

| Task | What it does |
| --- | --- |
| `mise run ios:remote <task>` | From another machine: copy this working copy to the Mac and run a task there (says so quickly when the Mac is asleep) |
| `mise run ios:runner` | Check the Mac's GitHub Actions runner (iOS releases) and start it if the Mac is up |

## Repository

```
kmp/shared/       Kotlin Multiplatform core: store, migrations, protocol, readout, i18n
kmp/androidApp/   Compose app
kmp/iosApp/       SwiftUI app (project.yml -> Xcode project)
spec/             Contracts both apps meet: tokens, events, testIDs, schema, vectors, goldens
maestro/          Device flows, screenshot fidelity, upgrade and gesture tests
i18n/locales/     Strings for both apps
site/             Public website and privacy policy (GitHub Pages)
docs/             Release, signing, store and product docs
```

## Releases

Push a `v*` tag: CI builds and signs both apps, uploads iOS to App Store Connect and attaches
the Android bundle to the GitHub release. See
[docs/release-step-by-step.md](docs/release-step-by-step.md).
