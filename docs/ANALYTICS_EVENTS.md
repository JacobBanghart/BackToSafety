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
| `emergency_completed`        | `checked_count: number`, `duration_s: number`      | User marked "Found — Safe"            |
| `emergency_cancelled`        | `checked_count: number`, `duration_s: number`      | User cancelled the protocol           |
| `emergency_911_called`       | `seconds_elapsed: number`, `checked_count: number` | User tapped Call 911 during protocol  |
| `emergency_contacts_alerted` | `recipient_count: number`                          | User sent SMS alerts                  |
| `emergency_leave`            | —                                                  | User left the emergency screen        |
| `emergency_sms_result`       | `result: string`, `recipient_count: number`        | What became of the alert text: `sent`, `cancelled` or `failed` (iOS composer), `handed_off` to the messaging app (Android, which doesn't report back), `unavailable` (the device can't text) |
| `countdown_alert`            | `kind: string`, `delivery: string`                 | The 5-minute `warning` or the `expired` alert reached the user: `in_app` on the emergency screen, or `catch_up` on returning to it after it came due. (Notifications while away aren't reported; the catch-up shows they were needed.) Sent by the shared core. |
| `emergency_resumed`          | `away_s: number`, `catch_up: string \| null`        | Back on the emergency screen after time away (another screen, or the app in the background), and the alert caught up on, if any. Sent by the shared core. |

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

### Failures

| Event         | Properties                                          | Description |
| ------------- | --------------------------------------------------- | ----------- |
| `dial_failed` | `target: string`, `screen: string`                  | The device couldn't place a call (no phone app: tablets, iPads) and showed the number instead. `target`: `emergency`, `contact` or `medicalert`. |
| `save_failed` | `screen: string`, `action: string`, `error: string` | A write failed and the user saw an error. `error` is the error's type name, never its message. |

Crashes and uncaught exceptions arrive as PostHog `$exception` events (error tracking autocapture, both apps).

### Settings

| Event                        | Properties         | Description              |
| ---------------------------- | ------------------ | ------------------------ |
| `settings_dev_mode_unlocked` | —                  | Developer mode unlocked  |
| `settings_account_deleted` | — | All data deleted (Delete Account) |
| `settings_theme_changed`     | `theme: string`    | Theme preference changed |
| `settings_language_changed`  | `language: string` | Language changed         |

### App lifecycle

| Event | Properties | Description |
| --- | --- | --- |
| `app_ready` | `startup_ms: number`, `prewarmed: boolean` (iOS); readiness: `has_profile`, `has_photo`, `has_medical`, `profile_fields_filled`, `contacts_count`, `alert_contacts_count`, `places_count`, `notifications_enabled`, `exact_alarms` (Android) | Once per cold start: process start to the first frame of the first screen. On iOS, a prewarmed launch measures from app init instead and says so. The readiness properties say how prepared the app is before anyone needs it. |
| `data_migrated` | `from_version`, `to_version`, `ok: boolean`, `duration_ms`; when ok `has_profile`, `contacts_count`, `places_count`, `incidents_count`; when not, `error` | The first launch after updating from the RN app converted its database. |
| `notifications_permission` | `granted: boolean` | The answer to the one-time notification permission prompt (on home after onboarding; Android 13 and up, and iOS). Sent by the shared core. |

### Navigation

| Event           | Properties       | Description       |
| --------------- | ---------------- | ----------------- |
| `screen_viewed` | `screen: string`, `source: string` | Screen view event |

---

## 4. Disabling Analytics

If `POSTHOG_KEY` is empty or absent at build time, PostHog isn't set up and no events are sent. The app still works normally.
