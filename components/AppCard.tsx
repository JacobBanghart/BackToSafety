import React, { ReactNode } from 'react';
import { View, StyleSheet, ViewStyle } from 'react-native';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { Spacing, Radius } from '@/constants/Spacing';

interface AppCardProps {
  children: ReactNode;
  style?: ViewStyle;
  /**
   * 'default' — bordered, no shadow (lists, settings)
   * 'elevated' — shadow instead of border (dashboard cards)
   * 'surface' — uses surface background color (subtler than card)
   */
  variant?: 'default' | 'elevated' | 'surface';
}

export function AppCard({ children, style, variant = 'default' }: AppCardProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const variantStyle: ViewStyle =
    variant === 'elevated'
      ? {
          backgroundColor: theme.card,
          borderWidth: 0,
          ...getShadow('sm', colorScheme),
        }
      : variant === 'surface'
        ? {
            backgroundColor: theme.surface,
            borderColor: theme.border,
          }
        : {
            backgroundColor: theme.card,
            borderColor: theme.border,
          };

  return <View style={[styles.card, variantStyle, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    marginBottom: Spacing.md,
  },
});
