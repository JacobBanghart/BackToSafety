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

## Building

Tools come from `mise.toml` (`mise install`): Java 17, the Android SDK, Maestro, gitleaks.

```sh
mise run android                       # build and run the Android app on a phone or emulator
cd kmp && ./gradlew :shared:jvmTest     # shared core tests (vectors, store, migrations, parity)
python3 spec/check.py                   # contracts: testIDs in both apps, feature coverage
mise run lint                          # ktlint and SwiftFormat (FIX=1 to fix)
```

iOS needs the Mac (Xcode and XcodeGen; `mise run ios:setup` checks):

```sh
mise run ios                           # build and run on a simulator
cd kmp/iosApp && xcodegen generate && open BackToSafety.xcodeproj   # or work in Xcode; Run rebuilds the Kotlin core
mise run ios:remote ios:flows          # from another machine: sync this copy to the Mac, run a task there
```

Device checks (both build the app, start devices, run, and shut down):

```sh
mise run android:flows      mise run ios:flows        # every Maestro flow plus the reorder gestures
mise run android:fidelity   mise run ios:fidelity     # every screen in light/dark/large text vs the goldens
mise run ios:runner                                   # make sure the Mac's release runner is up
```

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
