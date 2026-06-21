# Routing and Screens

This document describes every route and screen in the app, including navigation structure, entry guards, and the purpose of each screen.

---

## 1. Navigation Model

- **Router**: [Expo Router](https://docs.expo.dev/router/introduction/) uses a file-based routing convention.
- **Root navigator**: `Stack` in `app/_layout.tsx`. All screen headers are hidden; custom `ScreenHeader` components are used inside screens.
- **Tabs group**: `app/(tabs)/_layout.tsx` defines a `Tabs` navigator that has no visible tab bar. It exists primarily to support tab-based deep-link behavior and to keep `/settings` routable through the tab router.
- **Onboarding navigator**: nested `Stack` in `app/onboarding/_layout.tsx` with slide-from-right transitions and gestures disabled.

---

## 2. Root Layout Guard (`app/_layout.tsx`)

The root layout wraps the app in providers and performs an onboarding gate:

```tsx
const inOnboarding = segments[0] === 'onboarding';

if (!isOnboarded && !inOnboarding) {
  router.replace('/onboarding');
} else if (isOnboarded && inOnboarding) {
  router.replace('/');
}
```

Behavior:

1. If the user has not completed onboarding and is not already in the onboarding flow, redirect to `/onboarding`.
2. If the user has completed onboarding but somehow lands inside `/onboarding`, redirect back to `/`.
3. The redirect effect runs once `isOnboarded`, `isLoading`, or the current route segments change.

---

## 3. Route Reference

### `/` — Home Dashboard (`app/index.tsx`)

The main landing screen after onboarding. It loads profile, contacts, and any active emergency when focused.

**Contents:**

- Header with profile avatar, caring-for name, and a link to `/profile`.
- Big red "Start Emergency Search" button:
  - Starts a new emergency if none is active.
  - Resumes the active emergency if still within the 15-minute window.
  - Shows remaining time and completed steps when an emergency is active.
- Quick actions: Contacts and Destinations.
- Summary card with key profile data (conditions, medications, cognitive status, de-escalation) and a link to Readout.
- Settings link.
- A setup prompt when profile data is incomplete.

**Navigation targets:** `/emergency`, `/contacts`, `/destinations`, `/profile`, `/readout`, `/settings`.

---

### `/(tabs)` — Hidden Tabs (`app/(tabs)/_layout.tsx`)

A `Tabs` navigator that hides the visible tab bar:

```tsx
<Tabs tabBar={() => null}>
  <Tabs.Screen name="index" options={{ title: 'Home' }} />
  <Tabs.Screen name="settings" options={{ title: 'Settings', href: null }} />
</Tabs>
```

Purpose:

- Provides a tab-aware route entry point for `/`.
- Keeps `/settings` resolvable through the tab router without showing a tab bar.

---

### `/(tabs)/index` — Tab Redirector (`app/(tabs)/index.tsx`)

Immediately redirects to `/` on focus using `router.replace('/')`.

---

### `/onboarding/*` — Onboarding Flow

A nested `Stack` registered in `app/onboarding/_layout.tsx`:

| Screen     | Route                    | Purpose                                                   |
| ---------- | ------------------------ | --------------------------------------------------------- |
| Welcome    | `/onboarding/index`      | Feature list, theme selection, language selector (dev)    |
| Name       | `/onboarding/name`       | Name + nickname (required)                                |
| Photo      | `/onboarding/photo`      | Profile photo capture/selection (skippable)               |
| Appearance | `/onboarding/appearance` | Height, weight, hair, eyes, identifying marks (skippable) |
| Contact    | `/onboarding/contact`    | First emergency contact (skippable)                       |
| Complete   | `/onboarding/complete`   | Finish and go to home                                     |

**Stack options:**

```tsx
<Stack
  screenOptions={{
    headerShown: false,
    animation: 'slide_from_right',
    gestureEnabled: false,
  }}
>
  <Stack.Screen name="index" />
  <Stack.Screen name="name" />
  <Stack.Screen name="photo" />
  <Stack.Screen name="appearance" />
  <Stack.Screen name="contact" />
  <Stack.Screen name="complete" />
</Stack>
```

**Notes:**

- `app/onboarding/theme.tsx` exists in source but is **not registered** in the onboarding layout.
- The onboarding step keys in the database (`welcome`, `profile_name`, `profile_photo`, `profile_appearance`, `emergency_contact`, `complete`) mirror this route order.
- The `welcome` step is completed by tapping Continue on the welcome screen.
- The UI progress dots shown by `OnboardingStepHeader` represent only the four data-entry steps (`name`, `photo`, `appearance`, `contact`), so the visual progress count differs from the stored step keys.
- The welcome screen also exposes a language selector in `__DEV__` builds.

---

### `/emergency` — Emergency Search Protocol (`app/emergency.tsx`)

**Purpose:** Run the 15-minute guided search protocol.

**Behavior:**

- Persists active emergency state to `settings.active_emergency`.
- Maintains a 15-minute countdown timer.
- Presents 11 sequential checklist steps.
- Provides clothing/appearance input, direction-veer hint, saved destinations, SMS alerts, 911 call, and "Found — Safe" resolution.
- Triggers haptic and vibration feedback at key timer thresholds.

**Navigation targets:** `/readout`, external dialer for 911, SMS composer.

See [Emergency Protocol](./EMERGENCY_PROTOCOL.md) for full detail.

---

### `/readout` — First-Responder Info Sheet (`app/readout.tsx`)

**Purpose:** Display and copy a pre-formatted script for 911 dispatch, plus a full details block for first responders.

**Behavior:**

- Reads profile, emergency contacts, and last-seen location from `ProfileContext`.
- Builds a collapsible 911 script.
- Builds a full details text block with sections for identity, appearance, medical info, communication/de-escalation, last seen, and devices/IDs.
- One-tap copy for script and details.
- One-tap call 911 and open last coordinates in Maps.
- Shows a Silver/Purple Alert guidance card.

**Navigation targets:** external dialer for 911, external Maps app.

---

### `/profile` — Profile Editor (`app/profile.tsx`)

**Purpose:** Create or edit the full profile.

**Behavior:**

- Loads the existing profile into local form state.
- Supports photo capture/selection and copies the image into the app's document directory.
- Sections: Personal, Medical & Behavioral, Communication & De-escalation, Devices & IDs.
- Dominant hand picker with wandering-veer hint.
- Mobility multi-select chips with "Other" free text.
- Uses `useUnsavedChangesGuard` to warn when navigating back with unsaved changes.
- Saves via `ProfileContext.saveProfile`.

**Navigation targets:** back to `/`.

---

### `/contacts` — Emergency Contacts (`app/contacts.tsx`)

**Purpose:** Manage emergency contacts.

**Behavior:**

- Lists contacts with drag-to-reorder via `react-native-draggable-flatlist`.
- Inline add/edit form with name, phone, relationship, role, address, notes, and emergency toggles.
- One-tap phone call.
- Native contact import via `expo-contacts` `presentContactPickerAsync`.

**Navigation targets:** none; self-contained.

---

### `/destinations` — Familiar Destinations (`app/destinations.tsx`)

**Purpose:** Manage likely destinations.

**Behavior:**

- Lists saved destinations with drag-to-reorder.
- Add/edit form with name, address, coordinates, category, risk level, and notes.
- Detail modal when tapping a card.
- "Open in Maps" with platform-specific map URL.
- Special water-category warning.

**Navigation targets:** external Maps app.

---

### `/settings` — Settings (`app/settings.tsx`)

**Purpose:** App preferences and developer tools.

**Behavior:**

- Theme selector (system / light / dark).
- About section with version, platform, and device ID.
- Hidden **Dev Mode** unlocked by 7 quick taps (≤ 1 second apart) on the version row.
- Dev Mode reveals: language toggle, Clear All Data, DB schema version.
- Clear All Data resets onboarding and redirects to `/onboarding`.

**Navigation targets:** back to `/`.

---

### `/+not-found` — 404 (`app/+not-found.tsx`)

Standard Expo Router not-found screen.

---

## 4. Deep Linking

`app.json` defines the URL scheme:

```json
"scheme": "backtosafety"
```

Internal deep links use this scheme. All app routes are accessible through Expo Router's file-based path mapping.

---

## 5. Navigation Helpers

`utils/navigation.ts` tracks the previous route in memory:

```ts
setPreviousRoute(route: string)
getPreviousRoute(): string | null
goBack(fallback: string): void
```

This is useful when a screen can be reached from multiple places and should return to the caller.
