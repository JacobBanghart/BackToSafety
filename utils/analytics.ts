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
  | 'emergency_911_called'
  | 'emergency_contacts_alerted'
  | 'emergency_leave'
  | 'settings_dev_mode_unlocked'
  | 'settings_data_cleared'
  | 'settings_theme_changed'
  | 'settings_language_changed'
  | 'contact_saved'
  | 'contact_deleted'
  | 'contact_imported'
  | 'contact_add_tapped'
  | 'contact_call_tapped'
  | 'contact_edit_tapped'
  | 'destination_saved'
  | 'destination_deleted'
  | 'destination_add_tapped'
  | 'destination_edit_tapped'
  | 'destination_open_in_maps'
  | 'profile_saved'
  | 'profile_photo_taken'
  | 'profile_photo_chosen'
  | 'readout_911_called'
  | 'readout_contact_called'
  | 'readout_script_copied'
  | 'readout_details_copied'
  | 'readout_open_in_maps'
  | 'screen_viewed';

type AnalyticsProperties = Record<string, string | number | boolean | null>;

export function initAnalytics(deviceId: string): void {
  posthog.identify(deviceId);
}

export function track(name: AnalyticsEventName, properties?: AnalyticsProperties): void {
  posthog.capture(name, properties);
}
