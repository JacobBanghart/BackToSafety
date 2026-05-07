<wizard-report>
# PostHog post-wizard report

The wizard has completed a deep integration of PostHog analytics into Back to Safety — a React Native Expo app for coordinating emergency searches for people with dementia. The existing analytics stub (`utils/analytics.ts`) has been wired up to PostHog, and new events were added to key user flows.

## Files created / modified

| File | Change |
|------|--------|
| `utils/posthog.ts` | New PostHog client configured via `expo-constants` + `app.config.js` extras |
| `utils/analytics.ts` | Replaced no-op stub — `initAnalytics()` now calls `posthog.identify()`, `track()` calls `posthog.capture()` |
| `app/_layout.tsx` | Added `PostHogProvider` (autocapture), manual screen tracking via `posthog.screen()`, and device-ID identification |
| `app.config.js` | New file — extends `app.json` with `extra.posthogProjectToken` and `extra.posthogHost` from env vars |
| `app/emergency.tsx` | Added `emergency_contacts_alerted` and `emergency_911_called` events |
| `app/contacts.tsx` | Added `contact_saved`, `contact_deleted`, and `contact_imported` events |
| `app/destinations.tsx` | Added `destination_saved` and `destination_deleted` events |
| `app/profile.tsx` | Added `profile_saved` event |
| `.env` | Added `POSTHOG_PROJECT_TOKEN` and `POSTHOG_HOST` |

## Events

| Event | Description | File |
|-------|-------------|------|
| `onboarding_step_completed` | Each onboarding step completed | `app/onboarding/*.tsx` |
| `onboarding_completed` | Full onboarding flow finished | `app/onboarding/complete.tsx` |
| `emergency_started` | New emergency search initiated | `app/emergency.tsx` |
| `emergency_step_completed` | A checklist step checked off during emergency | `app/emergency.tsx` |
| `emergency_completed` | Emergency resolved — person found safe | `app/emergency.tsx` |
| `emergency_cancelled` | Emergency search ended early | `app/emergency.tsx` |
| `emergency_911_called` | User tapped Call 911 during an emergency | `app/emergency.tsx` |
| `emergency_contacts_alerted` | SMS alert circle notified successfully | `app/emergency.tsx` |
| `settings_dev_mode_unlocked` | Dev mode unlocked via version tap | `app/(tabs)/settings.tsx` |
| `settings_data_cleared` | All app data wiped | `app/(tabs)/settings.tsx` |
| `settings_theme_changed` | App theme preference changed | `app/(tabs)/settings.tsx` |
| `settings_language_changed` | Language toggled in dev tools | `app/(tabs)/settings.tsx` |
| `contact_saved` | Emergency contact created or updated | `app/contacts.tsx` |
| `contact_deleted` | Emergency contact removed | `app/contacts.tsx` |
| `contact_imported` | Contact imported from device address book | `app/contacts.tsx` |
| `destination_saved` | Familiar destination created or updated | `app/destinations.tsx` |
| `destination_deleted` | Familiar destination removed | `app/destinations.tsx` |
| `profile_saved` | Care recipient profile saved | `app/profile.tsx` |

## Next steps

We've built some insights and a dashboard for you to keep an eye on user behavior, based on the events we just instrumented:

- **Dashboard — Analytics basics**: https://us.posthog.com/project/413262/dashboard/1554350
- **Onboarding Completion Funnel**: https://us.posthog.com/project/413262/insights/Nes3PSwm
- **Emergency Flow Outcomes** (found / cancelled / 911): https://us.posthog.com/project/413262/insights/S2ugYCtU
- **Emergency Checklist Progress**: https://us.posthog.com/project/413262/insights/ffj92NMO
- **Profile & Contact Setup Rate**: https://us.posthog.com/project/413262/insights/GvB2uPbY
- **Emergency Started Weekly**: https://us.posthog.com/project/413262/insights/cCTGAhlb

### Agent skill

We've left an agent skill folder in your project. You can use this context for further agent development when using Claude Code. This will help ensure the model provides the most up-to-date approaches for integrating PostHog.

</wizard-report>
