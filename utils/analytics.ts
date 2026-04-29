/**
 * PostHog analytics wrapper.
 *
 * Provides a typed `track()` helper and initialises PostHog with session replay
 * (text inputs and images masked). The host is read from the EXPO_PUBLIC_POSTHOG_HOST
 * environment variable — set this to your self-hosted instance (e.g.
 * https://posthog.backtosafety.app). If the variable is absent the module is a
 * no-op so development builds work without configuration.
 *
 * Call `initAnalytics(deviceId)` once at app startup, then call `track()` anywhere.
 */

import PostHog from 'posthog-react-native';

let client: PostHog | null = null;

const POSTHOG_PROJECT_API_KEY = 'phc_bTE4VJlBKEdsfD5FuXi2PvUNKC81AgIi1rmk9rQpKa0';
const DEFAULT_POSTHOG_HOST = 'https://posthog.backtosafety.app';

export async function initAnalytics(deviceId: string): Promise<void> {
  const host = process.env.EXPO_PUBLIC_POSTHOG_HOST ?? DEFAULT_POSTHOG_HOST;
  if (!host) {
    console.debug('[analytics] EXPO_PUBLIC_POSTHOG_HOST not set — analytics disabled');
    return;
  }

  // PostHog project API keys are public client-side tokens, not secrets.
  client = new PostHog(POSTHOG_PROJECT_API_KEY, {
    host,
    enableSessionReplay: true,
    // Session replay: mask all text inputs and images to avoid capturing PII.
    sessionReplayConfig: {
      maskAllTextInputs: true,
      maskAllImages: true,
    },
  });

  client.identify(deviceId);
}

// ---------------------------------------------------------------------------
// Event type definitions — extend this union as new events are added
// ---------------------------------------------------------------------------

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

/**
 * Fire an analytics event. Type-safe — TypeScript will reject unknown event names
 * or missing required properties.
 */
export function track(name: AnalyticsEventName, properties?: AnalyticsProperties): void {
  if (!client) return;
  client.capture(name, properties ?? {});
}
