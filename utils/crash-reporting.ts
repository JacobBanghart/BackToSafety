/**
 * Sentry crash reporting, performance tracing, and log capture.
 *
 * Initialises the Sentry SDK with release tracking, router instrumentation,
 * rich user context, custom breadcrumbs, and console error/warn log capture.
 *
 * Call `initCrashReporting(deviceId)` once at app startup.
 */

import * as Sentry from '@sentry/react-native';
import Constants from 'expo-constants';
import { getAppVersionLabel } from '@/utils/appInfo';

const sentryLogs: Array<{ level: string; message: string; args: unknown[] }> = [];
const MAX_LOG_BUFFER = 100;

const originalConsoleError = console.error;
const originalConsoleWarn = console.warn;

function captureConsoleLog(level: string, ...args: unknown[]): void {
  if (sentryLogs.length >= MAX_LOG_BUFFER) {
    sentryLogs.shift();
  }
  sentryLogs.push({ level, message: args.map(String).join(' '), args });

  Sentry.addBreadcrumb({
    category: 'console',
    level: level === 'error' ? 'error' : 'warning',
    message: args.map(String).join(' '),
  });
}

export function getCapturedLogs(): Array<{ level: string; message: string; args: unknown[] }> {
  return [...sentryLogs];
}

export function initCrashReporting(deviceId: string): void {
  const dsn = process.env.EXPO_PUBLIC_SENTRY_DSN;
  if (!dsn) {
    console.debug('[crash-reporting] EXPO_PUBLIC_SENTRY_DSN not set — crash reporting disabled');
    return;
  }

  const release = `${Constants.expoConfig?.extra?.eas?.projectId ?? 'back-to-safety'}@${getAppVersionLabel()}`;

  Sentry.init({
    dsn,
    release,
    tracesSampleRate: 1.0,
    enableAutoSessionTracking: true,
    beforeSend(event) {
      if (event.exception?.values?.length) {
        Sentry.withScope((scope) => {
          scope.setTag('logs_captured', 'true');
          sentryLogs.forEach((log) => {
            scope.addBreadcrumb({
              category: 'console',
              level: log.level === 'error' ? ('error' as const) : ('warning' as const),
              message: log.message,
            });
          });
        });
      }
      return event;
    },
  });

  Sentry.setUser({ id: deviceId });

  console.error = (...args: unknown[]) => {
    captureConsoleLog('error', ...args);
    originalConsoleError.apply(console, args);
  };

  console.warn = (...args: unknown[]) => {
    captureConsoleLog('warn', ...args);
    originalConsoleWarn.apply(console, args);
  };
}

export function setCrashReportingContext(context: Record<string, string>): void {
  Sentry.setContext('app', context);
}

export function addBreadcrumb(args: Sentry.Breadcrumb): void {
  Sentry.addBreadcrumb(args);
}

export const wrapAppWithSentry = Sentry.wrap;
