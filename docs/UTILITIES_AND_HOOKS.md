# Utilities and Hooks

This document covers the shared hooks and utility modules in `hooks/` and `utils/`.

---

## 1. Hooks

### `useColorScheme`

Re-exports React Native's `useColorScheme` hook.

- Native: returns the current color scheme (`'light'` or `'dark'`) or `null`.
- Web: returns `'light'` to avoid hydration mismatches.

### `useThemeColor`

Resolves a design token to the current theme color.

```ts
const color = useThemeColor({ light: '#fff', dark: '#000' }, 'background');
```

- First argument is optional per-mode overrides.
- Second argument is the token name in `Colors`.

### `useUnsavedChangesGuard`

Warns the user when leaving a screen with unsaved form changes.

```ts
useUnsavedChangesGuard({
  navigation,
  hasUnsavedChanges,
  isSaving,
  title,
  message,
  cancelLabel,
  confirmLabel,
  onDiscard,
});
```

- Intercepts the `beforeRemove` navigation event.
- Shows an alert with "Discard" and "Keep Editing" options.
- Calls `onDiscard` if the user confirms.

Used in `app/profile.tsx` and other form-heavy screens to prevent accidental data loss.

---

## 2. Utilities

### `utils/phone.ts`

Phone number normalization and formatting helpers.

```ts
formatPhoneNumber(phone: string): string
```

Formats a phone number for display.

```ts
formatPhoneInput(value: string): string
```

Formats a phone number as the user types.

```ts
stripPhoneFormatting(phone: string): string
```

Returns digits only.

```ts
normalizeSmsRecipient(phone: string): string
```

Strips formatting while preserving the leading `+`.

```ts
normalizeUniqueSmsRecipients(phones: string[]): string[]
```

Deduplicates recipients, handling numbers with and without leading `+`.

### `utils/posthog.ts`

Initializes and exports the PostHog client.

- Reads `EXPO_PUBLIC_POSTHOG_KEY` and `EXPO_PUBLIC_POSTHOG_HOST`.
- Returns a configured `PostHog` instance.
- Disabled when no API key is present.

### `utils/analytics.ts`

Wraps PostHog capture in a typed helper.

```ts
type AnalyticsEventName = // 35 event names
export function track(name: AnalyticsEventName, properties?: AnalyticsProperties): void
```

See [Analytics Events Reference](./ANALYTICS_EVENTS.md) for the full event catalog.

### `utils/device-id.ts`

Generates and caches a stable device ID.

- Uses `expo-crypto` to generate a UUID.
- Caches in memory.
- Persists to settings under key `device_id`.

Used for analytics identification.

### `utils/appInfo.ts`

Returns app metadata from `expo-constants`.

```ts
getAppName(): string
getAppVersionLabel(): string
```

Used in Settings > About.

### `utils/navigation.ts`

In-memory previous-route tracker.

```ts
setPreviousRoute(route: string): void
getPreviousRoute(): string | null
goBack(fallback: string): void
```

Useful when a screen has multiple entry points and should return to its caller.

### `utils/draggable-flatlist.ts`

Thin re-export of `react-native-draggable-flatlist`.

- Native and base files export from the package root.
- Web file imports from the CommonJS build path for compatibility.

Used by `app/contacts.tsx` and `app/destinations.tsx` for drag-to-reorder lists.

---

## 3. Adding New Utilities

When adding a new helper:

1. Place it in the appropriate folder (`hooks/` for React hooks, `utils/` for pure logic).
2. Export a clear, typed API.
3. Add unit tests if the utility contains conditional logic or formatting rules.
4. Import using the `@/` alias (e.g., `import { formatPhoneNumber } from '@/utils/phone'`).
