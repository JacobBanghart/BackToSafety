import React from 'react';
import { TouchableOpacity, ActivityIndicator, StyleSheet, ViewStyle } from 'react-native';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface PrimaryButtonProps {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
}

export function PrimaryButton({
  label,
  onPress,
  disabled = false,
  loading = false,
  style,
}: PrimaryButtonProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  return (
    <TouchableOpacity
      style={[
        styles.button,
        { backgroundColor: disabled ? theme.textDisabled : theme.primary },
        style,
      ]}
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.8}
    >
      {loading ? (
        <ActivityIndicator color="#fff" />
      ) : (
        <ThemedText style={styles.label}>{label}</ThemedText>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.xl,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: '#fff',
    ...Typography.bodyBold,
  },
});
