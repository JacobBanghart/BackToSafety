/**
 * OpenReplay session replay and product analytics.
 *
 * Initialises the OpenReplay tracker on web platforms only (the tracker
 * depends on browser DOM APIs and cannot run on React Native).
 * On native platforms all calls are safe no-ops.
 *
 * Call `initOpenReplay()` once at app startup, then call
 * `identifyOpenReplayUser()` after you have a stable user identifier.
 */

import { Platform } from 'react-native';

let tracker: {
  configure(options: Record<string, unknown>): void;
  use(fn: unknown): unknown;
  start(): Promise<unknown>;
  setUserID(id: string): void;
  setMetadata(key: string, value: string): void;
  event(key: string, payload?: unknown, issue?: boolean): void;
  setUserID(id: string): void;
} | null = null;

const SUPPRESSED_MESSAGES = [
  'Accessing element.ref was removed in React 19',
  'props.pointerEvents is deprecated',
];

const originalConsoleError = console.error;
const originalConsoleWarn = console.warn;

function shouldSuppress(...args: unknown[]): boolean {
  return (
    typeof args[0] === 'string' && SUPPRESSED_MESSAGES.some((m) => (args[0] as string).includes(m))
  );
}

function filteredConsoleError(...args: unknown[]): void {
  if (shouldSuppress(...args)) return;
  originalConsoleError.apply(console, args);
}

function filteredConsoleWarn(...args: unknown[]): void {
  if (shouldSuppress(...args)) return;
  originalConsoleWarn.apply(console, args);
}

function isWeb(): boolean {
  return Platform.OS === 'web';
}

export function initOpenReplay(): void {
  if (!isWeb()) return;

  const projectKey = process.env.EXPO_PUBLIC_OPENREPLAY_PROJECT_KEY;
  const ingestPoint = process.env.EXPO_PUBLIC_OPENREPLAY_INGEST_POINT;

  if (!projectKey) {
    console.warn(
      '[openreplay] EXPO_PUBLIC_OPENREPLAY_PROJECT_KEY not set — session replay disabled',
    );
    return;
  }

  console.log('[openreplay] initialising tracker, ingestPoint:', ingestPoint);

  // Permanently suppress upstream deprecation warnings from @openreplay/tracker-assist
  // (React 19 element.ref) and react-native-web (pointerEvents). These are harmless in dev
  // and don't affect functionality.
  console.error = filteredConsoleError;
  console.warn = filteredConsoleWarn;

  try {
    // Dynamic require: @openreplay/tracker requires browser APIs
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { tracker: openReplayTracker } = require('@openreplay/tracker') as {
      tracker: NonNullable<typeof tracker>;
    };

    tracker = openReplayTracker;
    tracker.configure({
      projectKey,
      ingestPoint,
    });

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const trackerAssist = require('@openreplay/tracker-assist') as {
      default: (opts?: Record<string, unknown>) => unknown;
    };
    tracker.use(trackerAssist.default());

    console.log('[openreplay] tracker configured, calling start()...');
    tracker.start().then(
      (result: unknown) => console.log('[openreplay] tracker session started:', result),
      (err: unknown) =>
        originalConsoleError.call(console, '[openreplay] tracker start failed:', err),
    );
  } catch (err: unknown) {
    originalConsoleError.call(console, '[openreplay] init failed:', err);
  }
}

export function identifyOpenReplayUser(userId: string, metadata?: Record<string, string>): void {
  if (!tracker) return;
  tracker.setUserID(userId);
  if (metadata) {
    Object.entries(metadata).forEach(([key, value]) => {
      tracker!.setMetadata(key, value);
    });
  }
}

export function trackOpenReplayEvent(eventName: string, payload?: Record<string, unknown>): void {
  if (!tracker) return;
  tracker.event(eventName, payload);
}
