# Back to Safety — Implementation TODO

## Analytics (PostHog — to be re-integrated)

### Goal

Re-integrate PostHog for anonymous device-identifiable analytics.

### App (nijii-app)

- [ ] `npm install posthog-react-native`
- [ ] `utils/analytics.ts` — replace stub with PostHog init, typed `track()`
- [ ] `app/_layout.tsx` — init PostHog on app start (after DB ready)
- [ ] `.env.example` — document EXPO_PUBLIC_POSTHOG_KEY and EXPO_PUBLIC_POSTHOG_HOST
- [ ] Add `.env` to `.gitignore` if not already present

### Instrumentation

- [ ] `app/onboarding/index.tsx` — `onboarding_step_completed` { step: 'welcome' }
- [ ] `app/onboarding/name.tsx` — `onboarding_step_completed` { step: 'profile_name' }
- [ ] `app/onboarding/photo.tsx` — `onboarding_step_completed` { step: 'profile_photo' }
- [ ] `app/onboarding/appearance.tsx` — `onboarding_step_completed` { step: 'profile_appearance' }
- [ ] `app/onboarding/contact.tsx` — `onboarding_step_completed` { step: 'emergency_contact' }
- [ ] `app/onboarding/complete.tsx` — `onboarding_completed`
- [ ] `app/emergency.tsx` — `emergency_started`, `emergency_step_completed` { step }, `emergency_completed`, `emergency_cancelled`
- [ ] `app/(tabs)/settings.tsx` — `settings_theme_changed`, `settings_language_changed`

### Privacy

- [ ] `docs/privacy-policy.md` — update analytics section to reflect PostHog re-integration
- [ ] `site/privacy.html` — sync to match docs version

---

## Event Schema Reference

| Event                       | Properties                          |
| --------------------------- | ----------------------------------- |
| `onboarding_step_completed` | `step: string`, `skipped?: boolean` |
| `onboarding_completed`      | —                                   |
| `emergency_started`         | —                                   |
| `emergency_step_completed`  | `step: number`, `step_name: string` |
| `emergency_completed`       | `steps_completed: number`           |
| `emergency_cancelled`       | `steps_completed: number`           |
| `settings_theme_changed`    | `theme: string`                     |
| `settings_language_changed` | `language: string`                  |
