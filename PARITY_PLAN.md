# Parity Harness Plan — freezing the RN app before a Kotlin rewrite

Goal: a Kotlin rewrite that loses no feature and no visual fidelity. The test suite, not
human review, decides that. Built on `reactor-incremental-rebuild/HARNESS_PLAN.md`: there, the
original game was the oracle and the Godot rebuild had to match it. Here, **the current RN app
is the oracle** and the Kotlin app has to match it.

The one constraint that shapes everything: **every gate has to be implementation-agnostic.**
Tests that import TypeScript modules or rely on React internals die with the rewrite. A test
belongs in this harness only if it can run unchanged against both apps: black-box UI driving,
language-neutral JSON, or pixels.

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
separately. iOS has its own visuals: SF Symbols and the blurred tab bar.

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

- Fixtures: DB files plus photos captured from the RN app at each schema version.
- Flow: install the RN build → onboard and fill every field → start an emergency → install the
  Kotlin build over it → assert every value is visible, the emergency resumes with the right time
  left, onboarding isn't shown again, and `device_id` hasn't changed.

A rewrite that silently drops a caregiver's profile is the worst failure this project can have.

## Infrastructure

This dev box (an LXC container) has **no `/dev/kvm`**, so it can't run an Android emulator. Options:

- **Self-hosted macOS runner** (recommended): it already exists for iOS releases, and it can run
  both the iOS simulator and an arm64 Android emulator. One capture host for both platforms,
  and therefore one noise floor.
- Pass `/dev/kvm` through to this container on the Proxmox host for fast local Android loops.
  This is an optional add-on; iOS still needs the Mac.

There is also no CI lane today that runs lint/typecheck/Vitest/Playwright on push. That's
reactor's Phase −1, and it lands first.

## Order of work

1. **Phase −1:** a CI workflow (lint, typecheck, Vitest, Playwright, secret scan) plus a pre-push hook.
2. **Phase 0, seams:** add testIDs from `spec/testids.json` (no visual change), the debug clock
   seam, and the determinism switches. Fix or bless the known findings (below).
3. L1 + L2 (no emulator needed).
4. L3 Maestro flows, Android first, then iOS on the Mac runner.
5. L4 capture rig, noise floor, RN self-goldens.
6. L5 fixtures and the install-over flow.
7. **Freeze:** bless the RN references. The Kotlin work starts here, and every lane runs against
   both apps from then on.

## Known findings (decide before freeze)

- **Fixed:** the readout age was one year too high before the birthday (`utils/age.ts`).
- **Fixed:** Spanish was ~470 empty or missing strings, and Spanish-locale phones showed blank
  text in production. Production is now English-only, and the strings are AI-translated.
  **Before adding `es` to `SHIPPED_LANGUAGES`:** get a native speaker's review, and decide whether
  the 911 script and the copy block should stay in English, since dispatchers may not speak Spanish.
- oxlint 1.87 `react/purity` / `react/set-state-in-effect` warnings at 4 sites (intentional
  patterns, downgraded to warnings in `.oxlintrc.json`).
