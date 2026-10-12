# nijii-app (Back to Safety)

Two native apps on one Kotlin Multiplatform core: `kmp/androidApp` (Compose) and `kmp/iosApp`
(SwiftUI), both on `kmp/shared`. See `docs/ARCHITECTURE.md`.

## Change both apps together

A user-visible change lands in the Android and the iOS app in the same commit, and logic goes
in `kmp/shared` rather than in either UI. What the apps must agree on lives in `spec/`:

- New or renamed testIDs: `spec/testids.json`, then `python3 spec/check.py`.
- Analytics events: `spec/analytics-events.json` (`AnalyticsEvent` is generated from it).
- Colors, type and spacing: `spec/design-tokens.json` (`DesignTokens` is generated from it).
- Strings: `i18n/locales/` (both apps read the same JSON).
- Logic with fixed inputs and outputs: `spec/vectors/`, run by `VectorsTest`.

## Before pushing

`mise run check` (contracts and the shared core's tests), `mise run lint` (ktlint,
SwiftFormat; `FIX=1` fixes) and `mise run snapshots` (Android screens; `RECORD=1` after a
deliberate visual change). CI runs these, the iOS snapshot tests, and builds both apps. A
visual change re-records both platforms' snapshots in the same commit (`docs/ARCHITECTURE.md`).

## Writing code here

This app is used in an emergency, so a failure path matters as much as the happy path. Each
rule below comes from a defect this codebase shipped or nearly shipped (review of 2026-10-11).

- **Lifetimes.** Before you `launch` or start a `Task`, decide who owns it and who waits for
  it. Work that must outlive a screen runs in the app's scope (`AppServices.scope` on
  Android), and any write the user is leaving after is awaited before navigating. A write
  launched in `rememberCoroutineScope` and then navigated away from is cancelled halfway.
- **Errors aren't optional by default.**
  - On Android, wrap suspend calls in `runCatchingCancellable` (`ui/Failures.kt`), never
    `runCatching`, which swallows cancellation.
  - On iOS, use `try?` on a store call only when failing really doesn't matter. Otherwise
    catch it, report `save_failed` (`reportSaveFailed`) and show it.
  - Keep "it failed" and "there's nothing there" apart. A failed read must never be treated
    as empty and then overwritten (an emergency was nearly restarted this way).
- **Critical before optional.** On a screen's load path, nothing optional (profile,
  places) sits before or blocks the critical read (the active emergency).
- **Shared core.** Every public suspend function Swift calls is `@Throws(Exception::class)`;
  without it, a Kotlin exception crashes iOS (`ThrowsGuardTest` enforces this). A write of
  more than one statement goes in one transaction (`Store.inTransaction`).
- **Effects.**
  - A `LaunchedEffect` or `.onChange` is keyed on everything it reads.
  - An effect that persists state must not run again after that state was cleared. When an
    emergency ends, `EmergencyRecorder` stops saving it.
  - Nothing that does disk work or decodes images runs on the main thread or during
    composition.
- **Types, not strings, across a boundary.**
  - Build URIs with `Uri.fromParts` or `URLComponents`, never by concatenating strings
    (`dialString` for phone numbers).
  - Settings keys, outcomes, profile fields (`ProfileField`) and step ids are shared
    constants or enums, used by both apps.
  - Parse a value once, where it comes in (`ActiveEmergency.startedAtMs`); don't re-parse it
    at each use.
  - No `!!` where a type would do.
- **Parity on failure.** Both apps behave the same when something fails, not just when it
  works: the same error shown, the same event reported.
- **Hostile conditions.** Consider what happens on reboot, process death, rotation, a
  font-size change (the Android activity is recreated) and a full disk. State that must
  survive goes in `rememberSaveable` or the store.
- **Deletion.** Anything the app creates (files, photos, notifications, analytics identity)
  is removed by Delete Account (`Store.clearAllData` and `photoCleaner`).
- **Tests prove the fix.** A fix comes with a test that fails without it, including the
  failure path. Make the store throw, cancel the scope, deny the permission. Prefer behaviour
  tests that drive the real click path over snapshots.
- **Strings and access.** Every user-facing string comes from `i18n/locales/`, including
  "OK". Controls have an accessibility role, state and label.
- **Comments say the rule.** State why in a sentence. A finding id (F-n) or "Port of …" can
  follow it, but can't replace it.

## Keyboard avoidance

Every screen with a text field keeps its focused field and its bottom button above the
keyboard. Android: the screen takes the IME inset (`imePadding()` on the scroll container, or
`windowInsetsPadding(WindowInsets.safeDrawing)`, which includes it, as `OnboardingScaffold`
does). iOS: fields sit in a
`ScrollView` with `.scrollDismissesKeyboard(.interactively)` (number and phone keypads have no
Return key, so dragging the form is the way to close them). Screens shipped without this twice
in the past.

## Visual fidelity

The apps are held to `spec/goldens/` (light, dark, large text) by `maestro/fidelity.py`;
`maestro/README.md` has the commands. A deliberate visual change re-blesses the goldens and
lowers or raises the marks in its own commit.
