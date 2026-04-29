/**
 * Sentry crash reporting wrapper.
 *
 * Initialises the Sentry SDK and sets the anonymous device UUID as the Sentry
 * user identifier so crash reports can be correlated with PostHog events for the
 * same device. No other user data (name, email, IP) is attached.
 *
 * The DSN is read from the EXPO_PUBLIC_SENTRY_DSN environment variable. If it is
 * absent, we fall back to the currently provisioned self-hosted Back to Safety
 * Sentry project DSN so production builds still report crashes.
 *
 * Call `initCrashReporting(deviceId)` once at app startup.
 */

import * as Sentry from '@sentry/react-native';

const DEFAULT_SENTRY_DSN = 'https://802fc1ddcbece62db1b7095deed08b19@sentry.backtosafety.app/1';

export function initCrashReporting(deviceId: string): void {
  const dsn = process.env.EXPO_PUBLIC_SENTRY_DSN ?? DEFAULT_SENTRY_DSN;
  if (!dsn) {
    console.debug('[crash-reporting] EXPO_PUBLIC_SENTRY_DSN not set — crash reporting disabled');
    return;
  }

  Sentry.init({
    dsn,
    // Capture 20 % of transactions for performance monitoring — adjust as needed
    tracesSampleRate: 0.2,
  });

  // Identify this device anonymously — no name, email, or IP
  Sentry.setUser({ id: deviceId });
}
