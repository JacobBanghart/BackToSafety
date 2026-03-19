/**
 * Back to Safety App — Spacing & Border Radius Scales
 *
 * All spacing is based on a 4pt grid. Use these tokens instead of
 * hardcoded pixel values for consistent layout across the app.
 *
 * Usage:
 *   import { Spacing, Radius } from '@/constants';
 *   style={{ padding: Spacing.lg, borderRadius: Radius.md }}
 */

/** 8-step spacing scale (4pt grid) */
export const Spacing = {
  /** 2px — Tight internal gaps (icon + label) */
  xxs: 2,
  /** 4px — Very tight gaps */
  xs: 4,
  /** 8px — Small gaps, inner padding */
  sm: 8,
  /** 12px — Medium gaps */
  md: 12,
  /** 16px — Standard padding, list item gaps */
  lg: 16,
  /** 24px — Section gaps, card padding */
  xl: 24,
  /** 32px — Large section separation */
  xxl: 32,
  /** 48px — Hero/onboarding breathing room */
  xxxl: 48,
} as const;

/** 6-step border radius scale */
export const Radius = {
  /** 0px — No rounding (dividers, full-width elements) */
  none: 0,
  /** 4px — Subtle rounding (tags, badges) */
  sm: 4,
  /** 8px — Standard rounding (inputs, small cards) */
  md: 8,
  /** 12px — Medium rounding (cards, modals) */
  lg: 12,
  /** 16px — Large rounding (bottom sheets, prominent cards) */
  xl: 16,
  /** 9999px — Fully round (pills, avatar containers, FABs) */
  full: 9999,
} as const;

export type SpacingKey = keyof typeof Spacing;
export type RadiusKey = keyof typeof Radius;
