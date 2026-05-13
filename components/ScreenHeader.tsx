import React, { ReactNode } from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Spacing } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface TitleIcon {
  name: string;
  color: string;
  size?: number;
}

interface ScreenHeaderProps {
  title: string;
  onBack?: () => void;
  rightElement?: ReactNode;
  titleIcon?: TitleIcon;
}

export function ScreenHeader({ title, onBack, rightElement, titleIcon }: ScreenHeaderProps) {
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
        { backgroundColor: theme.background },
      ]}
    >
      {/* Left: back button */}
      <TouchableOpacity style={styles.sideSlot} onPress={handleBack} hitSlop={8}>
        <IconSymbol name="chevron.left" size={22} color={theme.tint} />
      </TouchableOpacity>

      {/* Center: absolutely positioned so it centers against the full header width */}
      <View style={styles.titleOverlay} pointerEvents="none">
        {titleIcon ? (
          <View style={styles.titleRow}>
            <IconSymbol name={titleIcon.name as any} size={titleIcon.size ?? 18} color={titleIcon.color} />
            <ThemedText style={[styles.titleRowText, { color: theme.text }]} numberOfLines={1}>
              {title}
            </ThemedText>
          </View>
        ) : (
          <ThemedText style={[styles.headerTitle, { color: theme.text }]} numberOfLines={1}>
            {title}
          </ThemedText>
        )}
      </View>

      {/* Right: action or spacer */}
      <View style={[styles.sideSlot, styles.rightSlot]}>{rightElement ?? null}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
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
  titleOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 44 + Spacing.md,
  },
  headerTitle: {
    textAlign: 'center',
    ...Typography.title,
  },
  titleRowText: {
    ...Typography.title,
    flexShrink: 1,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.xs,
  },
});
