import React, { ReactNode } from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Spacing, Radius } from '@/constants/Spacing';

interface ScreenHeaderProps {
  title: string;
  onBack?: () => void;
  rightElement?: ReactNode;
}

export function ScreenHeader({ title, onBack, rightElement }: ScreenHeaderProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const router = useRouter();

  const handleBack = onBack ?? (() => router.replace('/(tabs)'));

  return (
    <View
      style={[
        styles.header,
        { borderBottomColor: theme.border, backgroundColor: theme.card },
      ]}
    >
      <TouchableOpacity style={styles.backButton} onPress={handleBack}>
        <IconSymbol name="chevron.left" size={24} color={theme.tint} />
      </TouchableOpacity>

      <ThemedText style={styles.headerTitle}>{title}</ThemedText>

      {rightElement != null ? (
        <View style={styles.headerRight}>{rightElement}</View>
      ) : (
        <View style={styles.headerRight} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
    borderBottomWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  backButton: {
    padding: Spacing.sm,
    marginLeft: -8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  headerRight: {
    width: 40,
    alignItems: 'flex-end',
  },
});
