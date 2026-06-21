# Analytics Events Reference

Back to Safety uses PostHog for anonymous, device-identifiable analytics. `utils/analytics.ts` exports a typed `track(event, properties)` helper.

---

## 1. Setup

PostHog is configured in `utils/posthog.ts`:

- Reads `EXPO_PUBLIC_POSTHOG_KEY` and `EXPO_PUBLIC_POSTHOG_HOST`.
- Disabled when no API key is present.
- Session replay is enabled with text and image masking.
- A stable device ID is generated and used with `posthog.identify()`.

The app is wrapped with `PostHogProvider` in `app/_layout.tsx`.

---

## 2. `track()` Signature

```ts
type AnalyticsProperties = Record<string, string | number | boolean | null>;

export function track(name: AnalyticsEventName, properties?: AnalyticsProperties): void {
  posthog.capture(name, properties);
}
```

Properties must be JSON-serializable primitives (string, number, boolean, or null).

---

## 3. Event Catalog

### Onboarding

| Event                       | Properties     | Description                       |
| --------------------------- | -------------- | --------------------------------- |
| `onboarding_step_viewed`    | `step: string` | User landed on an onboarding step |
| `onboarding_step_completed` | `step: string` | User completed an onboarding step |
| `onboarding_step_skipped`   | `step: string` | User skipped an optional step     |
| `onboarding_completed`      | —              | User finished onboarding          |

### Emergency

| Event                        | Properties                                         | Description                           |
| ---------------------------- | -------------------------------------------------- | ------------------------------------- |
| `emergency_started`          | —                                                  | User started a new emergency protocol |
| `emergency_step_completed`   | `step: string`                                     | A protocol step was checked off       |
| `emergency_completed`        | `checked_count: number`                            | User marked "Found — Safe"            |
| `emergency_cancelled`        | `checked_count: number`                            | User cancelled the protocol           |
| `emergency_911_called`       | `seconds_elapsed: number`, `checked_count: number` | User tapped Call 911 during protocol  |
| `emergency_contacts_alerted` | `recipient_count: number`                          | User sent SMS alerts                  |
| `emergency_leave`            | —                                                  | User left the emergency screen        |

### Contacts

| Event                 | Properties                                                         | Description                        |
| --------------------- | ------------------------------------------------------------------ | ---------------------------------- |
| `contact_saved`       | `is_edit: boolean`, `role: string`, `notify_on_emergency: boolean` | Contact created or updated         |
| `contact_deleted`     | —                                                                  | Contact deleted                    |
| `contact_imported`    | —                                                                  | Contact imported from device       |
| `contact_add_tapped`  | —                                                                  | Add-contact action started         |
| `contact_call_tapped` | —                                                                  | Phone call initiated from contacts |
| `contact_edit_tapped` | —                                                                  | Edit-contact action started        |

### Destinations

| Event                      | Properties                                                   | Description                     |
| -------------------------- | ------------------------------------------------------------ | ------------------------------- |
| `destination_saved`        | `is_edit: boolean`, `category: string`, `risk_level: string` | Destination created or updated  |
| `destination_deleted`      | `category: string`, `risk_level: string`                     | Destination deleted             |
| `destination_add_tapped`   | —                                                            | Add-destination action started  |
| `destination_edit_tapped`  | —                                                            | Edit-destination action started |
| `destination_open_in_maps` | —                                                            | Destination opened in maps app  |

### Profile

| Event                  | Properties                                                               | Description                |
| ---------------------- | ------------------------------------------------------------------------ | -------------------------- |
| `profile_saved`        | `has_photo: boolean`, `has_medical: boolean`, `has_medications: boolean` | Profile saved              |
| `profile_photo_taken`  | —                                                                        | Photo captured with camera |
| `profile_photo_chosen` | —                                                                        | Photo chosen from library  |

### Readout

| Event                    | Properties | Description                            |
| ------------------------ | ---------- | -------------------------------------- |
| `readout_911_called`     | —          | Call 911 from readout                  |
| `readout_contact_called` | —          | Call an emergency contact from readout |
| `readout_script_copied`  | —          | 911 script copied to clipboard         |
| `readout_details_copied` | —          | Full details copied to clipboard       |
| `readout_open_in_maps`   | —          | Last-seen location opened in maps      |

### Settings

| Event                        | Properties         | Description              |
| ---------------------------- | ------------------ | ------------------------ |
| `settings_dev_mode_unlocked` | —                  | Developer mode unlocked  |
| `settings_data_cleared`      | —                  | Clear All Data used      |
| `settings_theme_changed`     | `theme: string`    | Theme preference changed |
| `settings_language_changed`  | `language: string` | Language changed         |

### Navigation

| Event           | Properties       | Description       |
| --------------- | ---------------- | ----------------- |
| `screen_viewed` | `screen: string` | Screen view event |

---

## 4. Disabling Analytics

If `EXPO_PUBLIC_POSTHOG_KEY` is empty or absent, PostHog is initialized in a disabled state and no events are sent. The app still functions normally.
