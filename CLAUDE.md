# nijii-app

## Keyboard avoidance — mandatory pattern

Any screen with a `TextInput` or `AppTextInput` MUST render it inside
`components/KeyboardAvoidingScroll.tsx` — never hand-roll `KeyboardAvoidingView`

- `ScrollView` directly, and never put an input under a plain `View`/`ScrollView`
  with no keyboard handling at all.

This app has no root-level layout that can wrap every screen in keyboard
avoidance — Expo Router's `<Stack>` mounts each screen independently, screens
have heterogeneous layouts (some have footer CTAs, some don't; some are pure
scroll views, some aren't scrollable at all), and stack screens stay mounted
underneath the active one, so a single wrapper above the `<Stack>` can't scope
itself to whichever screen currently has a focused input. Per-screen is the
correct pattern here, not a limitation to work around — but that makes it easy
for a screen to be added without the mechanism, which is exactly what happened
twice already:

- `app/onboarding/name.tsx` shipped with a plain `View` instead of any keyboard
  wrapper — the field just sat there, keyboard covered the Continue button.
- `app/emergency.tsx`'s "what are they wearing" input was in a bare `ScrollView`
  with no `KeyboardAvoidingView` at all — never wired up in the first place.

`KeyboardAvoidingScroll` also encodes a subtlety that's easy to get wrong by
hand: when a screen has a `footer` (bottom CTA button that must stay above the
keyboard), the wrapper needs `'padding'` behavior on iOS to shift the whole
footer+scroll stack together. But the ScrollView's own
`automaticallyAdjustKeyboardInsets` does the _same_ keyboard-height
compensation independently — running both at once double-pads the bottom of
the scroll content (huge gap under the last field, easy over-scroll). The
component handles this by disabling `automaticallyAdjustKeyboardInsets`
whenever a `footer` is passed. Do not re-enable it alongside a footer.

When adding a new screen with text input:

```tsx
<KeyboardAvoidingScroll
  style={styles.scrollView}
  contentContainerStyle={styles.content}
  footer={<View style={styles.footer}>{/* bottom CTA, if any */}</View>}
>
  {/* form fields */}
</KeyboardAvoidingScroll>
```

See `docs/COMPONENT_LIBRARY.md` for the component's full prop reference.
