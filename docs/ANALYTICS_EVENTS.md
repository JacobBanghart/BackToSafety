# Analytics Events Reference

Back to Safety uses PostHog for anonymous, device-identifiable analytics. The event names are a
contract, `spec/analytics-events.json`: the shared core generates `AnalyticsEvent` from it, and
`AnalyticsParityTest` checks that both apps send every event.

---

## 1. Setup

- The key and host come from `POSTHOG_KEY` and `POSTHOG_HOST` at build time (the release
  workflows set them). Without a key, PostHog isn't set up and no events are sent.
- Session replay is on, with text inputs and images masked.
- The app's stable device ID (the `device_id` setting) is the PostHog identity (`identify`).
- Android: `MainActivity.setUpAnalytics`. iOS: `PostHogSetup.swift`.

---

## 2. Tracking

Both apps call `Analytics.track(event, properties)` in the shared core with an `AnalyticsEvent`;
named screens go through `Analytics.screen(name)` as PostHog `$screen` events. Properties are
strings, numbers, booleans or null.

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
| `destination_add_tapped`   | `source: string` (when from home) | Add-destination action started  |
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
| `readout_medicalert_hotline_called` | — | Call the MedicAlert hotline from readout |
| `readout_script_copied`  | —          | 911 script copied to clipboard         |
| `readout_details_copied` | —          | Full details copied to clipboard       |

### Settings

| Event                        | Properties         | Description              |
| ---------------------------- | ------------------ | ------------------------ |
| `settings_dev_mode_unlocked` | —                  | Developer mode unlocked  |
| `settings_account_deleted` | — | All data deleted (Delete Account) |
| `settings_theme_changed`     | `theme: string`    | Theme preference changed |
| `settings_language_changed`  | `language: string` | Language changed         |

### Navigation

| Event           | Properties       | Description       |
| --------------- | ---------------- | ----------------- |
| `screen_viewed` | `screen: string`, `source: string` | Screen view event |

---

## 4. Disabling Analytics

If `POSTHOG_KEY` is empty or absent at build time, PostHog isn't set up and no events are sent. The app still works normally.
