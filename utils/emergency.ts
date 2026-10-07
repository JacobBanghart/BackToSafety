/**
 * The emergency search protocol: the checklist, the 15-minute countdown, and the
 * contact alert text. Pure functions so both apps can be held to
 * spec/vectors/emergency.json. `t` is bound to the `emergency` namespace.
 */

export type Translate = (key: string, vars?: Record<string, unknown>) => string;

export const SEARCH_WINDOW_SECONDS = 15 * 60;

export type ChecklistStep = {
  id: string;
  step: number;
  title: string;
  description: string;
  hint?: string;
  urgent?: boolean;
  checked: boolean;
};

export const buildInitialSteps = (t: Translate, emergencyNumber: string): ChecklistStep[] => [
  {
    id: 'home_search',
    step: 1,
    title: t('steps.home_search.title'),
    description: t('steps.home_search.description'),
    hint: t('steps.home_search.hint'),
    checked: false,
  },
  {
    id: 'outside_immediate',
    step: 2,
    title: t('steps.outside_immediate.title'),
    description: t('steps.outside_immediate.description'),
    checked: false,
  },
  {
    id: 'neighbors',
    step: 3,
    title: t('steps.neighbors.title'),
    description: t('steps.neighbors.description'),
    checked: false,
  },
  {
    id: 'radius_search',
    step: 4,
    title: t('steps.radius_search.title'),
    description: t('steps.radius_search.description'),
    checked: false,
  },
  {
    id: 'high_risk',
    step: 5,
    title: t('steps.high_risk.title'),
    description: t('steps.high_risk.description'),
    urgent: true,
    checked: false,
  },
  {
    id: 'familiar_places',
    step: 6,
    title: t('steps.familiar_places.title'),
    description: t('steps.familiar_places.description'),
    checked: false,
  },
  {
    id: 'call_911',
    step: 7,
    title: t('steps.call_911.title', { emergencyNumber }),
    description: t('steps.call_911.description'),
    urgent: true,
    checked: false,
  },
  {
    id: 'silver_alert',
    step: 8,
    title: t('steps.silver_alert.title'),
    description: t('steps.silver_alert.description', { emergencyNumber }),
    checked: false,
  },
  {
    id: 'share_info',
    step: 9,
    title: t('steps.share_info.title'),
    description: t('steps.share_info.description'),
    checked: false,
  },
  {
    id: 'coordinate',
    step: 10,
    title: t('steps.coordinate.title'),
    description: t('steps.coordinate.description'),
    checked: false,
  },
  {
    id: 'document',
    step: 11,
    title: t('steps.document.title'),
    description: t('steps.document.description'),
    checked: false,
  },
];

/** Seconds left in the search window, between 0 and the full window. */
export function secondsRemaining(startedAtMs: number, nowMs: number): number {
  const elapsedSeconds = Math.floor((nowMs - startedAtMs) / 1000);
  return Math.min(SEARCH_WINDOW_SECONDS, Math.max(0, SEARCH_WINDOW_SECONDS - elapsedSeconds));
}

export const WARNING_AT_SECONDS = 5 * 60;

/**
 * Alerts due when the countdown moves from `prev` to `next` seconds left. Ticks can
 * skip seconds (the app was in the background), so alerts fire on crossing a
 * threshold, not on landing exactly on it.
 */
export function countdownAlerts(prev: number, next: number): ('warning' | 'expired')[] {
  const alerts: ('warning' | 'expired')[] = [];
  if (prev >= WARNING_AT_SECONDS && next < WARNING_AT_SECONDS && next > 0) alerts.push('warning');
  if (prev > 0 && next === 0) alerts.push('expired');
  return alerts;
}

/** MM:SS for the countdown. */
export function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0');
  const s = (seconds % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

/** Text sent to emergency contacts. `startedTime` arrives already formatted. */
export function buildAlertSms(
  t: Translate,
  input: { name?: string; startedTime: string; wearing: string },
): string {
  const wearingText = input.wearing ? t('smsWearing', { wearing: input.wearing }) : '';
  return t('smsMessage', {
    // F-6: English fallback that bypasses i18n.
    name: input.name || 'Our loved one',
    time: input.startedTime,
    wearing: wearingText,
  });
}

/** Which way they may veer, from their dominant hand. */
export function directionHint(
  t: Translate,
  dominantHand: 'left' | 'right' | 'unknown' | undefined,
): string | null {
  if (dominantHand === 'left') return t('directionHint.left');
  if (dominantHand === 'right') return t('directionHint.right');
  return null;
}

/** M:SS for the home screen's emergency button. F-19: unpadded minutes, unlike formatCountdown. */
export function formatCountdownShort(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${(seconds % 60).toString().padStart(2, '0')}`;
}
