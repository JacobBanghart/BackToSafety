/**
 * Analytics stub with Sentry breadcrumb integration.
 *
 * Provides a typed `track()` helper and initialises analytics.
 * Analytics calls are no-ops (PostHog removed), but each event
 * also adds a Sentry breadcrumb for crash context.
 *
 * Call `initAnalytics(deviceId)` once at app startup, then call `track()` anywhere.
 */

import { addBreadcrumb } from '@/utils/crash-reporting';
import { trackOpenReplayEvent } from '@/utils/openreplay';

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
  | 'settings_language_changed';

type AnalyticsProperties = Record<string, string | number | boolean | null>;

let initialized = false;

export async function initAnalytics(_deviceId: string): Promise<void> {
  initialized = true;
}

export function track(name: AnalyticsEventName, properties?: AnalyticsProperties): void {
  if (!initialized) {
    return;
  }

  addBreadcrumb({
    category: 'analytics',
    message: name,
    level: 'info',
    data: (properties as Record<string, string>) ?? {},
  });

  trackOpenReplayEvent(name, properties ?? undefined);
}
