/**
 * The app's notion of "now". Everything time-sensitive (the emergency countdown,
 * resume) reads it, so tests can move time with the debug clock seam.
 */

/** Test seams (debug clock) are on in dev builds, or in builds made with EXPO_PUBLIC_TEST_SEAMS=1. */
export const TEST_SEAMS_ENABLED = __DEV__ || process.env.EXPO_PUBLIC_TEST_SEAMS === '1';

let offsetMs = 0;

export function now(): number {
  return Date.now() + offsetMs;
}

/** Moves the app clock forward (or back). Ignored unless test seams are enabled. */
export function advanceClock(ms: number): void {
  if (TEST_SEAMS_ENABLED) offsetMs += ms;
}

// On web a deep link reloads the page (and resets the offset), so Playwright
// moves the clock through this global instead.
if (TEST_SEAMS_ENABLED && typeof window !== 'undefined') {
  (window as unknown as { __nijiiAdvanceClock: typeof advanceClock }).__nijiiAdvanceClock =
    advanceClock;
}
