/**
 * Back to Safety App — Shadow Tokens
 *
 * Three shadow levels for both light and dark modes.
 * Dark mode shadows use reduced opacity since dark surfaces
 * don't scatter light the same way as light surfaces.
 *
 * Usage:
 *   import { getShadow } from '@/constants';
 *   style={getShadow('md', colorScheme)}
 *
 * Or use raw tokens:
 *   import { Shadows } from '@/constants';
 *   style={Shadows.light.md}
 */

import { ViewStyle } from 'react-native';

type ShadowLevel = 'sm' | 'md' | 'lg';
type ShadowSet = Record<ShadowLevel, ViewStyle>;

export const Shadows: { light: ShadowSet; dark: ShadowSet } = {
  light: {
    /** Subtle elevation — cards, inputs */
    sm: {
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 1 },
      shadowOpacity: 0.08,
      shadowRadius: 2,
      elevation: 2,
    },
    /** Standard elevation — modals, dropdowns */
    md: {
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.12,
      shadowRadius: 6,
      elevation: 4,
    },
    /** Prominent elevation — bottom sheets, alerts */
    lg: {
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.18,
      shadowRadius: 12,
      elevation: 8,
    },
  },
  dark: {
    /** Subtle elevation — cards, inputs */
    sm: {
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 1 },
      shadowOpacity: 0.3,
      shadowRadius: 2,
      elevation: 2,
    },
    /** Standard elevation — modals, dropdowns */
    md: {
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.4,
      shadowRadius: 6,
      elevation: 4,
    },
    /** Prominent elevation — bottom sheets, alerts */
    lg: {
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.5,
      shadowRadius: 12,
      elevation: 8,
    },
  },
};

/**
 * Convenience helper — returns the correct shadow for the current color scheme.
 *
 * @example
 * const shadow = getShadow('md', colorScheme);
 * <View style={[styles.card, shadow]} />
 */
export function getShadow(level: ShadowLevel, colorScheme: 'light' | 'dark'): ViewStyle {
  return Shadows[colorScheme][level];
}
