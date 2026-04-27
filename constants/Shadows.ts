/**
 * Back to Safety App — Shadow Tokens
 *
 * Three shadow levels for both light and dark modes.
 * Dark mode shadows use reduced opacity since dark surfaces
 * don't scatter light the same way as light surfaces.
 *
 * Native (iOS/Android): shadowColor, shadowOffset, shadowOpacity, shadowRadius, elevation
 * Web: boxShadow CSS string (shadow* props are deprecated on web)
 *
 * Usage:
 *   import { getShadow } from '@/constants/Shadows';
 *   style={getShadow('md', colorScheme)}
 */

import { Platform, ViewStyle } from 'react-native';

type ShadowLevel = 'sm' | 'md' | 'lg';

// Native shadow tokens only — used on iOS/Android
type NativeShadow = {
  shadowColor: string;
  shadowOffset: { width: number; height: number };
  shadowOpacity: number;
  shadowRadius: number;
  elevation: number;
};

// Web shadow tokens — boxShadow CSS string
type WebShadow = {
  boxShadow: string;
};

type ShadowToken = NativeShadow | WebShadow;
type ShadowSet = Record<ShadowLevel, ShadowToken>;

const nativeLight: Record<ShadowLevel, NativeShadow> = {
  sm: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 2,
    elevation: 2,
  },
  md: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
  lg: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.18,
    shadowRadius: 12,
    elevation: 8,
  },
};

const nativeDark: Record<ShadowLevel, NativeShadow> = {
  sm: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.3,
    shadowRadius: 2,
    elevation: 2,
  },
  md: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.4,
    shadowRadius: 6,
    elevation: 4,
  },
  lg: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.5,
    shadowRadius: 12,
    elevation: 8,
  },
};

// Equivalent CSS box-shadow values derived from the native tokens above
const webLight: Record<ShadowLevel, WebShadow> = {
  sm: { boxShadow: '0px 1px 2px rgba(0,0,0,0.08)' },
  md: { boxShadow: '0px 2px 6px rgba(0,0,0,0.12)' },
  lg: { boxShadow: '0px 4px 12px rgba(0,0,0,0.18)' },
};

const webDark: Record<ShadowLevel, WebShadow> = {
  sm: { boxShadow: '0px 1px 2px rgba(0,0,0,0.30)' },
  md: { boxShadow: '0px 2px 6px rgba(0,0,0,0.40)' },
  lg: { boxShadow: '0px 4px 12px rgba(0,0,0,0.50)' },
};

export const Shadows: { light: ShadowSet; dark: ShadowSet } = {
  light: Platform.OS === 'web' ? webLight : nativeLight,
  dark: Platform.OS === 'web' ? webDark : nativeDark,
};

/**
 * Convenience helper — returns the correct shadow for the current color scheme.
 *
 * @example
 * const shadow = getShadow('md', colorScheme);
 * <View style={[styles.card, shadow]} />
 */
export function getShadow(level: ShadowLevel, colorScheme: 'light' | 'dark'): ViewStyle {
  return Shadows[colorScheme][level] as ViewStyle;
}
