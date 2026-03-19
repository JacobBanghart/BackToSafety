import React from 'react';
import { TouchableOpacity, StyleSheet, ViewStyle } from 'react-native';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface SecondaryButtonProps {
  label: string;
  onPress: () => void;
  style?: ViewStyle;
}

export function SecondaryButton({ label, onPress, style }: SecondaryButtonProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  return (
    <TouchableOpacity
      style={[styles.button, { borderColor: theme.border }, style]}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <ThemedText style={[styles.label, { color: theme.text }]}>{label}</ThemedText>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.xl,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    ...Typography.bodyBold,
  },
});
