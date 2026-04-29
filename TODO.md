# Back to Safety — Implementation TODO

## Analytics & Crash Reporting (PostHog + Sentry, self-hosted)

### Goal

Add anonymous device-identifiable analytics (PostHog) and crash reporting (Sentry), both
self-hosted. A random UUID is generated on first launch, persisted in SQLite, and passed to
both SDKs as the identifier — not linked to any real-world identity.

### Constraints

- Self-hosted only: PostHog at `posthog.backtosafety.app`, Sentry at `sentry.backtosafety.app`
- No PII in any event (no name, email, location, contact data)
- Session replay enabled with `maskAllTextInputs` + `maskAllImages`
- Sentry DSN is unknown until infra is deployed — use placeholder `EXPO_PUBLIC_SENTRY_DSN`

### Blocked

- `EXPO_PUBLIC_SENTRY_DSN` — only known after Sentry HelmRelease is running and a project
  is created inside the Sentry UI. Use placeholder in app config until then.

---

## Checklist

### Infra (flux repo)

- [ ] `clusters/k3s-cluster/posthog/posthog.yaml` — HelmRelease + Namespace + Ingress
- [ ] `clusters/k3s-cluster/sentry/sentry.yaml` — HelmRelease + Namespace + Ingress
- [ ] Manually create secrets:
  - `posthog` namespace: `posthog-secret` (POSTHOG_SECRET_KEY, POSTHOG_ENCRYPTION_SALT)
  - `sentry` namespace: `sentry-secret` (SENTRY_SECRET_KEY, email/SMTP creds if needed)

### App (nijii-app)

- [ ] `npm install @posthog/react-native @sentry/react-native expo-crypto`
- [ ] `utils/device-id.ts` — UUID gen (expo-crypto) + SQLite persistence via getSetting/saveSetting
- [ ] `utils/analytics.ts` — PostHog init, typed `track()`, session replay with masking
- [ ] `utils/crash-reporting.ts` — Sentry init, `user.id` set to device UUID
- [ ] `app/_layout.tsx` — init both SDKs on app start (after DB ready)
- [ ] `.env.example` — document EXPO_PUBLIC_POSTHOG_HOST and EXPO_PUBLIC_SENTRY_DSN
- [ ] Add `.env` to `.gitignore` if not already present

### Instrumentation

- [ ] `app/onboarding/index.tsx` — `onboarding_step_completed` { step: 'welcome' }
- [ ] `app/onboarding/name.tsx` — `onboarding_step_completed` { step: 'profile_name' }
- [ ] `app/onboarding/photo.tsx` — `onboarding_step_completed` { step: 'profile_photo' }
- [ ] `app/onboarding/appearance.tsx` — `onboarding_step_completed` { step: 'profile_appearance' }
- [ ] `app/onboarding/contact.tsx` — `onboarding_step_completed` { step: 'emergency_contact' }
- [ ] `app/onboarding/complete.tsx` — `onboarding_completed`
- [ ] `app/emergency.tsx` — `emergency_started`, `emergency_step_completed` { step }, `emergency_completed`, `emergency_cancelled`
- [ ] `app/(tabs)/settings.tsx` — `setting_changed` { setting: 'theme', value }, `language_changed` { language }
- [ ] `app/contacts.tsx` — `contact_added`, `contact_removed`, `contact_reordered`
- [ ] `app/destinations.tsx` — `destination_added`, `destination_removed`

### Privacy

- [ ] `docs/privacy-policy.md` — add analytics + crash reporting + session replay + device UUID + self-hosted endpoints
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
| `setting_changed`           | `setting: string`, `value: string`  |
| `language_changed`          | `language: string`                  |
| `contact_added`             | —                                   |
| `contact_removed`           | —                                   |
| `contact_reordered`         | —                                   |
| `destination_added`         | —                                   |
| `destination_removed`       | —                                   |
