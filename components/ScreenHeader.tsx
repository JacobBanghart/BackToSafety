import React, { ReactNode } from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Spacing } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface ScreenHeaderProps {
  title: string;
  onBack?: () => void;
  rightElement?: ReactNode;
}

export function ScreenHeader({ title, onBack, rightElement }: ScreenHeaderProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const router = useRouter();

  const handleBack =
    onBack ??
    (() => {
      if (router.canGoBack()) {
        router.back();
      } else {
        router.replace('/(tabs)');
      }
    });

  return (
    <View
      style={[
        styles.header,
        { borderBottomColor: theme.border, backgroundColor: theme.card },
        getShadow('sm', colorScheme),
      ]}
    >
      {/* Left: back button — fixed 44px wide for balance */}
      <TouchableOpacity style={styles.sideSlot} onPress={handleBack} hitSlop={8}>
        <IconSymbol name="chevron.left" size={22} color={theme.tint} />
      </TouchableOpacity>

      {/* Center: title */}
      <ThemedText style={[styles.headerTitle, { color: theme.text }]} numberOfLines={1}>
        {title}
      </ThemedText>

      {/* Right: action or spacer — same width as left for visual balance */}
      <View style={[styles.sideSlot, styles.rightSlot]}>{rightElement ?? null}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    borderBottomWidth: 1,
    minHeight: 52,
  },
  sideSlot: {
    minWidth: 44,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  rightSlot: {
    alignItems: 'flex-end',
    flexShrink: 0,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    marginHorizontal: Spacing.sm,
    ...Typography.title,
  },
});
