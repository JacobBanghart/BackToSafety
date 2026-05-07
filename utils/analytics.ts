/**
 * Analytics helpers wired up to PostHog.
 *
 * Call `initAnalytics(deviceId)` once at app startup to identify the device,
 * then call `track()` anywhere to capture events.
 */

import { posthog } from '@/utils/posthog';

type AnalyticsEventName =
  | 'onboarding_step_completed'
  | 'onboarding_completed'
  | 'emergency_started'
  | 'emergency_step_completed'
  | 'emergency_completed'
  | 'emergency_cancelled'
  | 'settings_dev_mode_unlocked'
  | 'settings_data_cleared'
  | 'settings_theme_changed'
  | 'settings_language_changed'
  | 'contact_saved'
  | 'contact_deleted'
  | 'contact_imported'
  | 'destination_saved'
  | 'destination_deleted'
  | 'profile_saved'
  | 'emergency_contacts_alerted'
  | 'emergency_911_called';

type AnalyticsProperties = Record<string, string | number | boolean | null>;

export function initAnalytics(deviceId: string): void {
  posthog.identify(deviceId);
}

export function track(name: AnalyticsEventName, properties?: AnalyticsProperties): void {
  posthog.capture(name, properties);
}
