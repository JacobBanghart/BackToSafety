# Android Baseline Profile

Compose ships as a library, not with the OS, so without a profile the first launches after an
install run it, and the app's own code, interpreted or JIT-compiled. A Baseline Profile lists
the classes and methods to compile ahead of time; the startup profile is the part R8 puts
first in the dex, so a cold start reads fewer pages.

- `kmp/baselineprofile`: the generator (`BaselineProfileGenerator`) and the benchmarks that
  measure it (`StartupBenchmark`, `ScrollBenchmark`).
- `kmp/androidApp/src/release/generated/baselineProfiles`: the generated `baseline-prof.txt`
  and `startup-prof.txt`, committed, so builds and CI need no device.
- `androidx.profileinstaller` in the app: Play installs the profile with the app; on any other
  install (sideloads, other stores) profileinstaller writes it on the first launch and the
  system compiles it in the background later (when the phone is idle and charging).

## What the generator covers

- `startup` (also the startup profile): a cold start to a settled home, onboarded, with 14
  contacts.
- `onboarding`: a fresh install's first launch, the welcome screen and every onboarding step.
- `home`: home to the contacts list and a scroll through it, then places, profile and
  settings.

Release builds have no PostHog key locally, so analytics setup isn't in the profile.

## Regenerating

Regenerate after a change to what launch or home runs (new screens on the start path, a new
library there), and commit the two files.

1. Connect a phone, or start an API 33+ emulator (below 33 a profile needs root). An emulator
   gives the same profile as a phone; only its timings differ.
2. Uninstall any `com.backtosafety.app` signed with another key (a Play build, or one an AVD
   image carries). The run installs the release build signed with the debug key, and if that
   install fails, the task still succeeds, with only the warning "No baseline profile rules
   were generated".
3. From `kmp/`, with `ANDROID_SERIAL` naming the device when more than one is connected:

   ```sh
   ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:generateReleaseBaselineProfile
   ```

   It takes about 8 minutes on the emulator. The generator onboards and adds the contacts
   itself, and grants the notification permission so home's prompt doesn't cover it.

## Measuring

`./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` runs the startup and scroll
benchmarks with no profile (`CompilationMode.None`) and with it (`Partial`). On an emulator the
results are only relative, and every frame's shaders are compiled again at each launch (the
emulator's GL keeps no shader cache), which adds about 0.6 s to the first frame of home that
a phone doesn't pay.
