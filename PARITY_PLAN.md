# Parity Harness Plan — freezing the RN app before a Kotlin rewrite

Goal: a Kotlin rewrite that loses no feature and no visual fidelity. The test suite, not
human review, decides that. Built on `reactor-incremental-rebuild/HARNESS_PLAN.md`: there, the
original game was the oracle and the Godot rebuild had to match it. Here, **the current RN app
is the oracle** and the Kotlin app has to match it.

The one constraint that shapes everything: **every gate has to be implementation-agnostic.**
Tests that import TypeScript modules or rely on React internals die with the rewrite. A test
belongs in this harness only if it can run unchanged against both apps: black-box UI driving,
language-neutral JSON, or pixels.

## Target architecture (owner decision, 2026-10-06)

**Kotlin Multiplatform for the shared core, native UI on each platform:** Jetpack Compose on
Android, SwiftUI on iOS. The shared module holds the models, the SQLite data layer, migrations,
the emergency protocol and timer, the readout/SMS text builders, phone formatting, and i18n
loading. Everything a user sees is written twice.

That makes this harness the thing that keeps two UIs in step, which changes three things:

- **iOS is a required gate, not a later add-on.** A SwiftUI screen can drift from the Compose
  one without either platform looking broken. Every L3 flow and L4 capture runs on both
  platforms before a screen counts as done.
- **L2 vectors run against the shared Kotlin module once**, and both UIs inherit the results.
  Logic that leaks into a UI layer (formatting in a SwiftUI view, say) escapes the vectors, so
  every screen's text has to come from the shared module.
- **Selectors are the cross-platform contract.** The same `testids.json` ID must exist as a
  Compose `testTag` and a SwiftUI `accessibilityIdentifier`. A contract test checks both apps'
  view hierarchies for every ID the screen declares.

## How reactor's test types map here

| Reactor lane                                                     | nijii equivalent                                                    | Runs against             |
| ---------------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------ |
| `spec/*.json` with citations + `SpecConformanceTests`            | `spec/`: design tokens, i18n, feature inventory, testID contract    | both (JSON)              |
| Sim golden-master JSON fixtures                                  | `spec/vectors/`: input→output vectors for every pure-logic function | Vitest now, Kotlin later |
| Invariant / property tests                                       | i18n key parity, readout never prints `undefined`, phone fuzz       | both                     |
| GUT E2E: real clicks → assert state + screen                     | Maestro flows: tap by testID → assert screen + state after relaunch | any APK/IPA              |
| Pixel gate: self-goldens (tier 1) + vs-original ratchet (tier 2) | Screenshot matrix: RN self-goldens + Kotlin-vs-RN similarity        | emulator / simulator     |
| `NativeSaveTests`, save migration                                | Install-over upgrade: RN DB fixtures must open in the Kotlin app    | emulator                 |
| Zero-tolerance error gate                                        | Any RedBox / logcat crash / `console.error` during a flow fails it  | both                     |
| Action replay / pacing bot                                       | Not needed; this is a form app, not a sim                           | —                        |

## Ground rules (from reactor, in force from the first ticket)

1. **Golden blessing.** Never edit a golden, vector, or reference screenshot to make a test pass.
   Changing one takes its own commit with the reason, approved by the owner.
2. **Findings, not silent fixes.** If the oracle turns out to be wrong (a real bug in the RN
   app), write it up. It gets fixed in RN and re-blessed **before** the freeze, so the
   Kotlin app never has to copy a bug on purpose.
3. **Selectors are a contract.** Flows select by `testID` from `spec/testids.json`, never by
   coordinates or by English text (text changes with the locale). The Kotlin app exposes the same
   IDs (`Modifier.testTag` + `testTagsAsResourceId` on Android, accessibilityIdentifier on iOS).
4. **Determinism before goldens.** Fixed clock, demo-mode status bar, animations off, pinned
   device images. No golden gets blessed until it is byte-stable across 10 runs (reactor's
   noise-floor procedure).

## Lanes

### L1 — Spec as data (`spec/`)

- `design-tokens.json`, generated from `constants/` (Colors, Typography, Spacing, Shadows). A
  Vitest conformance test pins the TS constants to it; later the Kotlin theme is generated
  from the same file.
- `i18n/locales/` is already language-neutral JSON, and the Kotlin app consumes the same files.
  `i18n/locales.test.ts` (landed): every locale matches `en` key-for-key, with no empty
  strings and the same `{{placeholders}}`.
- `features.json`: every user-visible feature gets an ID and the flows that cover it. The
  test fails if a feature has no covering flow. This is the asset-manifest idea applied to
  features: the red list is the coverage backlog.
- `testids.json`: the selector contract (rule 3).
- `analytics-events.json`: the PostHog event names and properties from `docs/ANALYTICS_EVENTS.md`.

### L2 — Logic vectors (`spec/vectors/*.json`)

Pure functions get JSON input→expected-output vectors, run by a Vitest adapter today and a
Kotlin `commonTest` adapter later:

- phone formatting/normalization (`utils/phone.ts`, partly covered already)
- the readout script and copy block (`app/readout.tsx`'s `useMemo` builders, en + es)
- the emergency checklist: step list, urgent flags, `{{emergencyNumber}}`, countdown math,
  resume-after-N-seconds, expiry
- schema migrations: DB at version N → expected tables/rows at the latest version

This means pulling the readout and emergency builders out of 1100-line screen files into pure
modules. L3's flows go first, so that move is covered by a test.

### L3 — Behavioural flows (Maestro)

Maestro drives any Android/iOS build through the accessibility tree, so the same YAML runs
against RN and Kotlin. One flow per feature in `features.json`. Each flow asserts:
the screen it lands on, the persisted state (kill the app, relaunch, data is still there), and
the side effects that can be observed:

- 911 dial: dialer intent opened with the right number
- SMS: compose intent with the right recipients and body
- contacts import: picker launches with a contact seeded through adb
- photo: picker launches with a seeded gallery image
- clipboard: copied text equals the readout vector

Haptics and the native share sheet can't be checked; they go on a short manual checklist.

The emergency timer needs a **debug-only clock seam** (a deep link that sets a clock offset)
so "resume at 14:59 left" and "expired" are testable without waiting 15 minutes.

Playwright on web stays as a fast smoke lane, but it can't gate Kotlin.

### L4 — Visual fidelity (screenshot matrix)

Matrix: every screen × meaningful states (empty / filled / validation error / modal open /
emergency running / expired) × light/dark × en/es × font scale 1.0/1.3, on Android and iOS
separately. iOS has its own visuals: SF Symbols (the tab bar is hidden, so its blur never shows).

- **Tier 1, self-goldens:** strict per-implementation regression (reactor's
  `compare_screenshots.py` approach, threshold set from a measured noise floor).
- **Tier 2, Kotlin vs RN reference:** two measures, because text rasterization differs between
  renderers and the images will never be byte-identical:
  1. **Layout rects:** every testID's bounds from the view hierarchy, compared within ±2dp.
     This is cheap and precise, and it is what catches spacing and sizing drift.
  2. **Pixel similarity with text masked:** catches colour, radius, shadow, and icon drift.
     Reactor's `fidelity_ratchet.json` model applies: per-screen marks only go up.

The RN captures blessed at freeze time become the permanent "original" references, the same
role the original game's captures play in reactor.

### L5 — Upgrade path (the most important gate)

The Kotlin app ships under the same package/bundle ID, so it **installs over the RN app**
and must read its data. Native data is all SQLite via `expo-sqlite`: Android stores it under
`files/SQLite/`, iOS under `Documents/SQLite/`. That covers profile, contacts, destinations,
incidents, settings (`active_emergency`, `device_id`, theme, language), and onboarding.
Photos live in the document directory.

- Contract: `spec/storage.md` (paths, settings keys, row semantics) and `spec/db-schema.json`
  (pinned from the real migrations by `spec/db-schema.test.ts`).
- Fixtures: DB files plus photos captured from the RN app at each schema version.
- Flow: install the RN build → onboard and fill every field → start an emergency → install the
  Kotlin build over it → assert every value is visible, the emergency resumes with the right time
  left, onboarding isn't shown again, and `device_id` hasn't changed.

A rewrite that silently drops a caregiver's profile is the worst failure this project can have.

## Infrastructure

- **Android:** the devbox (CT 201) runs a headless API 36 emulator on `/dev/kvm` (passed through
  2026-10-06; `k8s-homelab/terraform/lxc-devbox.tf`). Setup and commands: `maestro/README.md`.
- **iOS:** the self-hosted macOS runner (already used for iOS releases) runs the simulator. It's
  required before the Kotlin iOS app counts as matching (see Target architecture).
- Android and iOS goldens are separate sets. Each platform measures its own noise floor.

There is also no CI lane today that runs lint/typecheck/Vitest/Playwright on push. That's
reactor's Phase −1, and it lands first.

## Order of work

| Step                         | Status (2026-10-06)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Phase −1: CI on push + hooks | **Landed.** `.github/workflows/ci.yml` (lint, format, types, Vitest, `expo install --check`, web/android/ios bundles, Playwright); pre-commit gitleaks and pre-push lint/types/tests via mise.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| Phase 0: seams               | **testIDs landed** (117, `spec/testids.json`). Still open: the debug clock seam for the timer, and the determinism switches (both needed by L3/L4).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| L1: spec as data             | **Landed:** `design-tokens.json`, i18n parity, `testids.json`, `features.json` (60 features, flow ratchet), `analytics-events.json`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| L2: logic vectors            | **Landed** for phone, age, field formatters, readout text, and the emergency protocol (`spec/vectors/`, 140+ cases, en+es). The readout and emergency logic was extracted from the screens under Playwright pins. Schema pinned in `db-schema.json`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| L3: Maestro flows            | **Android landed:** `maestro/flows/` passes on the devbox emulator (`maestro/README.md`). It found F-26 and F-27. iOS needs the Mac runner.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| L4: screenshot matrix        | **Rig landed:** `maestro/states/` × light/dark/large-text, captured by `capture.sh` (PNG + testID layout in dp) and compared by `compare_screens.py`. Noise floor measured before blessing goldens.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| L5: upgrade path             | **Passes RN → Kotlin.** `maestro/upgrade/upgrade.sh` (install-over, capture fixture, restore fixture) with `fill.yaml` / `verify.yaml`; contract in `spec/storage.md`. The Kotlin app installed over the RN app reads everything the RN app wrote (profile, contacts, places, the running emergency's checklist and clothing, the 911 script), keeps the device ID, theme and active emergency, and reads back the committed fixture (2026-10-08).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| Freeze                       | Findings: every one is fixed except F-21's device check and F-0b's native-speaker review; F-10 is kept. Freeze after L3–L5 run green against the RN app.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Kotlin: shared core          | **Started (2026-10-07).** `kmp/shared` passes all 199 spec vectors and opens the RN fixture database with every row intact (Room, `MIGRATION_1_2`). CI job `kotlin-core`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Kotlin: Android UI           | **Every screen ported.** Onboarding, home, emergency, info sheet, contacts (import, drag to reorder), places (detail sheet, drag to reorder), profile (square crop with the cropper RN uses) and settings. All 14 Maestro flows and both reorder gestures pass against the Kotlin app unchanged. Analytics send the same PostHog events as RN (`AnalyticsEvent` is generated from `spec/analytics-events.json`; `AnalyticsParityTest` holds the Kotlin call sites to the RN ones), with screens reported by RN route path. Layout follows RN's Android text and Yoga rules: font size and line height rounded up to whole pixels, linear glyph advances, high-quality line breaks, icons sized in dp (vector icons ignore font scale), no 48dp touch-target growth, and spacing rounded half-to-even so long screens don't drift. Pixel marks: `spec/fidelity/android.json`; the few states past 2dp: `spec/fidelity/android-layout.json`. |

## Known findings (decide before freeze)

Each one gets fixed in RN and re-blessed before the freeze (rule 2), or explicitly kept.
Vector cases that pin current behavior carry the finding ID.

| ID   | Status                   | Finding                                                                                                                                                                                                                                                                                                                                                                                            |
| ---- | ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F-0a | Fixed                    | Readout age was one year too high before the birthday, and parsed MM/DD/YYYY with `new Date(string)` (`utils/age.ts`).                                                                                                                                                                                                                                                                             |
| F-0b | Fixed                    | Spanish was ~470 empty or missing strings, so Spanish-locale phones showed blank text in production. Production is English-only now; the strings are AI-translated. Before adding `es` to `SHIPPED_LANGUAGES`: native-speaker review, and decide whether the 911 script and copy block stay English for dispatchers.                                                                               |
| F-1  | Fixed                    | `formatPhoneInput` cuts international numbers to 10 digits (`+44 20 7946 0958` → `(442) 079-4609`), so an edited non-US contact saves the wrong number.                                                                                                                                                                                                                                            |
| F-2  | Fixed                    | `formatPhoneInput` silently drops the 11th digit of any 11-digit number not starting with 1.                                                                                                                                                                                                                                                                                                       |
| F-3  | Fixed                    | SMS recipients `+15551234567` and `5551234567` aren't treated as duplicates, so one person can get the alert twice.                                                                                                                                                                                                                                                                                |
| F-4  | Fixed                    | `formatPhoneNumber` folds extensions into the number (`555.123.4567 ext 9` → `555-123-45679`).                                                                                                                                                                                                                                                                                                     |
| F-5  | Fixed                    | The copy block's blank line before the "what were they wearing" reminder is removed by `filter(Boolean)`, so it never appears.                                                                                                                                                                                                                                                                     |
| F-6  | Fixed                    | Hardcoded English that bypasses i18n: readout "add for a stronger script" details, the copy buttons' labels ("Copy 911 Script", "Copied Full Details"), `AppModal`'s "Cancel" and default "Delete"/"OK", the settings device-ID "Copied" alerts.                                                                                                                                                   |
| F-7  | Fixed                    | Mobility is stored as English labels and copied into the readout raw, so it stays English in Spanish.                                                                                                                                                                                                                                                                                              |
| F-8  | Fixed                    | testIDs were also set as `accessibilityLabel`, so screen readers announced "onboarding-get-started" in place of the button text.                                                                                                                                                                                                                                                                   |
| F-9  | Fixed                    | `ScreenHeader`'s icon-only back button has no accessibility label.                                                                                                                                                                                                                                                                                                                                 |
| F-11 | Fixed                    | Backspacing the closing `"` of a height (`5'6"` → `5'6`) re-formats straight back to `5'6"`, so the field can't be cleared from the end.                                                                                                                                                                                                                                                           |
| F-12 | Fixed                    | The 911 script always says "Photo available.", even when the profile has no photo.                                                                                                                                                                                                                                                                                                                 |
| F-13 | Fixed                    | Coordinates without an accuracy value copy as `(±—m)`.                                                                                                                                                                                                                                                                                                                                             |
| F-14 | Fixed                    | `escalationSigns` ("What escalation looks like") and `medicAlertHotline` are saved but shown nowhere: not on the readout, not in the script or the copy block. The hotline's own hint says first responders call it.                                                                                                                                                                               |
| F-15 | Fixed                    | Call 911 always dials `tel:911`, ignoring the localized `emergencyNumber` the rest of the screen uses.                                                                                                                                                                                                                                                                                             |
| F-16 | **Fixed**                | Countdown drifted while backgrounded (it counted ticks; phones pause JS timers), so the "call 911" alert came late. Both screens now derive from `startedAt` every tick; alerts fire on crossing a threshold (`countdownAlerts`). Playwright proves it with the clock seam.                                                                                                                        |
| F-17 | Fixed                    | The Call 911 button _toggles_ the "Call 911" step, so pressing it a second time unchecks the step.                                                                                                                                                                                                                                                                                                 |
| F-18 | Fixed                    | `emergency_started` is tracked twice for one emergency (home button and screen init).                                                                                                                                                                                                                                                                                                              |
| F-19 | Fixed                    | The home button shows `9:05` while the emergency screen shows `09:05`.                                                                                                                                                                                                                                                                                                                             |
| F-20 | Fixed                    | If the device clock moves backwards mid-emergency, the countdown shows more than 15:00.                                                                                                                                                                                                                                                                                                            |
| F-21 | Fixed (verify on device) | `photo_uri` stores an absolute `file://` path. iOS changes the app container path across updates, so the profile photo can disappear after an update (verify on device).                                                                                                                                                                                                                           |
| F-22 | Fixed                    | Incident history was never saved (`addIncident` only updated React state). Each emergency is now an incidents row (spec/storage.md).                                                                                                                                                                                                                                                               |
| F-23 | Fixed                    | `lastSeen` was memory-only, so a restart mid-emergency lost the readout's last-seen time. Now rebuilt from `active_emergency` at launch.                                                                                                                                                                                                                                                           |
| F-24 | Fixed                    | **Corrected:** location permissions are deliberately _blocked_ (`blockedPermissions` in app.json, `tools:node="remove"` in the manifest; Bugs.md: "Remove tracking information, was wishful thinking"). What's left over: `expo-location` is installed but unused, and the readout's coordinates section and "Open in Maps" can never show.                                                        |
| F-25 | Fixed                    | Onboarding promises alerts carry "the person's photo and last known location"; the SMS has neither.                                                                                                                                                                                                                                                                                                |
| F-26 | Fixed                    | The readout's appearance card holds dominant hand, mobility and the mobility-aid warning, but only rendered when a height/weight/hair/eye/marks field was set, so those three could be hidden from responders. Found by the Maestro profile flow. The hand value was also hardcoded English.                                                                                                       |
| F-27 | Not reproduced           | A Get Started tap right after a fresh install was dropped about 1 in 10 times, but only on the overloaded emulator (swiftshader leak). On a healthy emulator: 0 of 16 runs. Instrumented logs show the redirect to onboarding lands 25ms after first render, well before any tap. Flows keep the retry.                                                                                            |
| F-28 | Fixed                    | The home "Complete Your Profile" card and the readout's "no profile found" state are unreachable: both need a profile without a name, and onboarding requires one. Dead UI; the Kotlin apps shouldn't copy it.                                                                                                                                                                                     |
| F-29 | Fixed                    | Three icons had no Material Icons mapping, so on Android they rendered blank: the gear on home's Settings row, the language globe (dev builds), and the notes icon in a place's detail. Found while porting icons to Kotlin (spec/icons.json).                                                                                                                                                     |
| F-30 | Fixed                    | Three more icons rendered blank on Android: both copy buttons on the info sheet and the help icon in a place's detail. `as IconMapping` typed every SF Symbol name as mapped, so the compiler never caught an unmapped one; the mapping now uses `satisfies`, and `IconSymbolName` is exactly the mapped names. Found while porting the info sheet.                                                |
| F-31 | Fixed                    | The info sheet's wearing reminder was hardcoded English (now `readout:wearingCard`, with Spanish), and its ages read the real clock instead of the app clock, so frozen-clock captures could drift on a birthday.                                                                                                                                                                                  |
| F-32 | Fixed                    | The info sheet showed a contact's raw role (`primary_caregiver`, which onboarding sets) when it had no relationship; it now shows the translated role, as the contacts screen does.                                                                                                                                                                                                                |
| F-33 | Fixed                    | After Found or End, the info sheet kept the finished emergency's last-seen time and 911 script line until the app restarted (it lived in memory only). Ending an emergency now clears it.                                                                                                                                                                                                          |
| F-34 | Fixed                    | Settings > About showed the version without its build number ("1.3.3", not "1.3.3 (13)"): Expo SDK 57 removed `Constants.nativeAppVersion`/`nativeBuildVersion`, and an index signature kept TypeScript quiet. It now reads them from expo-application. Found porting Settings.                                                                                                                    |
| F-35 | Fixed                    | Clearing a contact's relationship, address or notes, or a place's address, reason, distance or notes, in the edit form didn't save: the screens sent `undefined` for an empty field and the native update skips undefined fields, so the old value came back. Cleared fields are now sent as `null`. (The profile was unaffected: its update writes every key it's given.) Found porting Contacts. |
| F-36 | Fixed                    | More hardcoded English, found sweeping the screens while porting Places: the water warning on a place's form, the search tip above the places list, and the not-found screen. All three are now in i18n, with Spanish.                                                                                                                                                                             |
| F-37 | Fixed                    | The empty places screen read "Add locations where {{name}} might go": the string interpolates the person's name but the screen never passed it. It now passes the profile's name (falling back to "your loved one"). A scan of every placeholder string against its call sites found no other case.                                                                                                |
| F-38 | Fixed                    | Clearing the name on the profile screen and tapping Save did nothing: the name column is NOT NULL, the update failed, and the error went only to the console. Save now refuses an empty name with "Name is required.", and any other save failure shows an error instead of failing silently. Found porting Profile.                                                                               |
| F-39 | Fixed                    | `app/onboarding/theme.tsx` was a leftover onboarding step (theme choice moved onto the welcome screen): nothing navigated to it, but a `backtosafety://onboarding/theme` link still opened it. Removed, with its testIDs. Found mapping RN routes for the Kotlin app's screen analytics.                                                                                                           |
| F-40 | Fixed                    | On iOS, number and phone keypads (phone, date of birth, height, weight, MedicAlert hotline) have no Return key, and the forms didn't dismiss the keyboard on drag, so the only way out was finding blank space to tap. Forms now dismiss it on drag, as iOS apps do (`keyboardDismissMode="interactive"`, iOS only). Found running the Maestro flows on iOS.                                       |
| F-41 | Fixed                    | The navigators' containers used React Navigation's backgrounds (`rgb(242, 242, 242)`, `rgb(1, 1, 1)`), not the app's (`#ffffff`, `#0a0a0a`), so gaps around screens mid-transition (the edge strip Android 13/14's default push and pop uncover) showed gray in light mode. The navigation theme now takes the app's background. Found removing the Kotlin app's cross-fade.                       |
| F-10 | Kept                     | oxlint 1.87 `react/purity` / `react/set-state-in-effect` warnings at 4 sites (intentional patterns, warnings in `.oxlintrc.json`).                                                                                                                                                                                                                                                                 |
| F-42 | Fixed                    | The onboarding photo and "You're ready" screens didn't scroll: at large text on an iPhone 17 Pro the photo tip ran under "Skip for now", and larger accessibility sizes or smaller phones would hide content outright. Both now scroll (RN and Kotlin); when the content fits, nothing moves. Found capturing the iOS screens.                                                                     |
| F-43 | Fixed                    | The check on "You're ready!" showed as a "v" on iOS: a 48pt glyph in ThemedText's default 24 line height, which iOS clamps, cutting off its top. The icon now has a 56 line height (RN and Kotlin). Found porting onboarding to SwiftUI.                                                                                                                                                           |
