import PostHog from 'posthog-react-native';

const apiKey = process.env.EXPO_PUBLIC_POSTHOG_KEY;
const host = process.env.EXPO_PUBLIC_POSTHOG_HOST;

export const posthog = new PostHog(apiKey ?? '', {
  host: host ?? undefined,
  disabled: !apiKey,
  captureAppLifecycleEvents: true,
  preloadFeatureFlags: true,
  flushAt: 20,
  flushInterval: 10000,
  maxBatchSize: 100,
  maxQueueSize: 1000,
  requestTimeout: 10000,
  fetchRetryCount: 3,
  fetchRetryDelay: 3000,
  enableSessionReplay: true,
  sessionReplayConfig: {
    maskAllTextInputs: true,
    maskAllImages: true,
  },
  errorTracking: {
    autocapture: {
      uncaughtExceptions: true,
      unhandledRejections: true,
    },
  },
});
