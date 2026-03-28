/**
 * Minimal navigation history tracker
 * Stores the previous route so we can navigate back properly even after refresh
 */

import { router } from 'expo-router';
import type { Href } from 'expo-router';

// Simple in-memory store for the previous route
let previousRoute: Href | null = null;

/**
 * Record the current route before navigating away
 */
export function setPreviousRoute(route: Href) {
  previousRoute = route;
}

/**
 * Get the previous route, or null if none recorded
 */
export function getPreviousRoute(): Href | null {
  return previousRoute;
}

/**
 * Navigate back to the previous route, or fallback to home
 */
export function goBack(fallback: Href = '/(tabs)') {
  if (previousRoute) {
    router.replace(previousRoute);
    previousRoute = null; // Clear after use
  } else {
    router.replace(fallback);
  }
}
