/**
 * Back to Safety App — Typography Scale
 *
 * 8-step type scale designed for caretaker-friendly readability.
 * Larger base sizes account for a wide range of user vision needs.
 *
 * Usage:
 *   import { Typography } from '@/constants';
 *   style={{ ...Typography.body, color: theme.text }}
 */

import { TextStyle } from 'react-native';

export type TypographyVariant =
  | 'display'
  | 'headline'
  | 'title'
  | 'bodyLarge'
  | 'body'
  | 'bodyBold'
  | 'caption'
  | 'small';

type TypographyScale = Record<TypographyVariant, TextStyle>;

export const Typography: TypographyScale = {
  /** 32px / bold — Screen hero text, onboarding titles */
  display: {
    fontSize: 32,
    fontWeight: '700',
    lineHeight: 40,
    letterSpacing: -0.5,
  },

  /** 24px / bold — Section headers, modal titles */
  headline: {
    fontSize: 24,
    fontWeight: '700',
    lineHeight: 32,
    letterSpacing: -0.25,
  },

  /** 20px / semibold — Card titles, list headers, sub-headers */
  title: {
    fontSize: 20,
    fontWeight: '600',
    lineHeight: 28,
    letterSpacing: 0,
  },

  /** 18px / regular — Important body text, onboarding prompts, form labels */
  bodyLarge: {
    fontSize: 18,
    fontWeight: '400',
    lineHeight: 28,
    letterSpacing: 0,
  },

  /** 16px / regular — Standard body text, list content */
  body: {
    fontSize: 16,
    fontWeight: '400',
    lineHeight: 24,
    letterSpacing: 0,
  },

  /** 16px / semibold — Emphasized body text, primary button labels */
  bodyBold: {
    fontSize: 16,
    fontWeight: '600',
    lineHeight: 24,
    letterSpacing: 0,
  },

  /** 13px / regular — Secondary/helper text, input hints, timestamps */
  caption: {
    fontSize: 13,
    fontWeight: '400',
    lineHeight: 18,
    letterSpacing: 0.1,
  },

  /** 11px / regular — Badges, fine print, version numbers */
  small: {
    fontSize: 11,
    fontWeight: '400',
    lineHeight: 16,
    letterSpacing: 0.2,
  },
};
