/**
 * Test seam: `backtosafety://debug/clock?advance=<seconds>` moves the app clock
 * forward, then returns to the previous screen. Maestro flows use it to reach
 * "14 minutes in" or "expired" without waiting. Does nothing unless test seams
 * are enabled (dev builds, or EXPO_PUBLIC_TEST_SEAMS=1).
 */

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect } from 'react';

import { advanceClock, TEST_SEAMS_ENABLED } from '@/utils/clock';

export default function DebugClock() {
  const { advance } = useLocalSearchParams<{ advance?: string }>();
  const router = useRouter();

  useEffect(() => {
    const seconds = Number(advance);
    if (TEST_SEAMS_ENABLED && Number.isFinite(seconds)) advanceClock(seconds * 1000);
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }, [advance, router]);

  return null;
}
