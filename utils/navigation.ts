/**
 * Minimal navigation history tracker
 * Stores the previous route so we can navigate back properly even after refresh
 */

import { router } from 'expo-router';

// Simple in-memory store for the previous route
let previousRoute: string | null = null;

/**
 * Record the current route before navigating away
 */
export function setPreviousRoute(route: string) {
  previousRoute = route;
}

/**
 * Get the previous route, or null if none recorded
 */
export function getPreviousRoute(): string | null {
  return previousRoute;
}

/**
 * Navigate back to the previous route, or fallback to home
 */
export function goBack(fallback: string = '/(tabs)') {
  if (previousRoute) {
    router.replace(previousRoute as any);
    previousRoute = null; // Clear after use
  } else {
    router.replace(fallback as any);
  }
}
