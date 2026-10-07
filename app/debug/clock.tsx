/**
 * Test seam, then back to the previous screen:
 * - `backtosafety://debug/clock?at=<ISO time>` freezes the app clock at that moment
 *   (screenshots then show the same countdown and times every run)
 * - `backtosafety://debug/clock?advance=<seconds>` moves it forward, frozen or not,
 *   so flows reach "14 minutes in" or "expired" without waiting
 * Does nothing unless test seams are enabled (dev builds, or EXPO_PUBLIC_TEST_SEAMS=1).
 */

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect } from 'react';

import { advanceClock, freezeClock, TEST_SEAMS_ENABLED } from '@/utils/clock';

export default function DebugClock() {
  const { at, advance } = useLocalSearchParams<{ at?: string; advance?: string }>();
  const router = useRouter();

  useEffect(() => {
    const atMs = at ? Date.parse(at) : NaN;
    if (TEST_SEAMS_ENABLED && Number.isFinite(atMs)) freezeClock(atMs);
    const seconds = Number(advance);
    if (TEST_SEAMS_ENABLED && advance && Number.isFinite(seconds)) advanceClock(seconds * 1000);
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }, [at, advance, router]);

  return null;
}
