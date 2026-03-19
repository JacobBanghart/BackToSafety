import React, { ReactNode } from 'react';
import { View, TouchableOpacity, StyleSheet, ViewStyle } from 'react-native';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { Spacing } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface ListItemProps {
  label: string;
  value?: string;
  onPress?: () => void;
  rightElement?: ReactNode;
  style?: ViewStyle;
}

export function ListItem({ label, value, onPress, rightElement, style }: ListItemProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const inner = (
    <View style={[styles.row, { borderBottomColor: theme.border }, style]}>
      <ThemedText style={[styles.label, { color: theme.textSecondary }]}>
        {label}
      </ThemedText>

      {rightElement != null ? (
        rightElement
      ) : (
        <ThemedText style={styles.value}>{value ?? ''}</ThemedText>
      )}
    </View>
  );

  if (onPress) {
    return (
      <TouchableOpacity onPress={onPress} activeOpacity={0.7}>
        {inner}
      </TouchableOpacity>
    );
  }

  return inner;
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
  },
  label: {
    ...Typography.body,
  },
  value: {
    ...Typography.body,
  },
});
