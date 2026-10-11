# nijii-app (Back to Safety)

Two native UIs on one Rust core: `kmp/androidApp` (Compose) and `kmp/iosApp` (SwiftUI), both on
`rust/core` through UniFFI. See `docs/ARCHITECTURE.md`.

`rust/AGENTS.md` has the rules for the core and the load limits for heavy builds (cargo, Gradle,
emulators); `rust/PORT_PLAN.md` is the history of the move from Kotlin Multiplatform.

## Change both apps together

A user-visible change lands in the Android and the iOS app in the same commit, and logic goes
in `rust/core` rather than in either UI. What the apps must agree on lives in `spec/`:

- New or renamed testIDs: `spec/testids.json`, then `python3 spec/check.py`.
- Analytics events: `spec/analytics-events.json` (`AnalyticsEvent` is generated from it).
- Colors, type and spacing: `spec/design-tokens.json` (`DesignTokens` is generated from it, in each app).
- Strings: `i18n/locales/` (both apps read the same JSON).
- Logic with fixed inputs and outputs: `spec/vectors/`, run by `rust/core/tests/vectors.rs`.

## Before pushing

`mise run check` (contracts, the core's tests and analytics parity), `mise run lint` (ktlint,
SwiftFormat; `FIX=1` fixes) and `mise run snapshots` (Android screens; `RECORD=1` after a
deliberate visual change). CI runs these, the iOS snapshot tests, and builds both apps. A
visual change re-records both platforms' snapshots in the same commit (`docs/ARCHITECTURE.md`).

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
