import React, { ReactNode } from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol, type IconSymbolName } from '@/components/ui/IconSymbol';
import { Spacing } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface TitleIcon {
  name: IconSymbolName;
  color: string;
  size?: number;
}

interface ScreenHeaderProps {
  title: string;
  onBack?: () => void;
  rightElement?: ReactNode;
  titleIcon?: TitleIcon;
  /** Screen ID; the back button gets `<testID>-back` and the title `<testID>-title`. */
  testID?: string;
}

export function ScreenHeader({
  title,
  onBack,
  rightElement,
  titleIcon,
  testID,
}: ScreenHeaderProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const router = useRouter();
  const { t } = useTranslation('common');

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
    <View style={[styles.header, { backgroundColor: theme.background }]}>
      {/* Left: back button */}
      <TouchableOpacity
        testID={testID && `${testID}-back`}
        accessibilityRole="button"
        accessibilityLabel={t('back')}
        style={styles.sideSlot}
        onPress={handleBack}
        hitSlop={8}
      >
        <IconSymbol name="chevron.left" size={22} color={theme.tint} />
      </TouchableOpacity>

      {/* Center: absolutely positioned so it centers against the full header width */}
      <View style={styles.titleOverlay} pointerEvents="none">
        {titleIcon ? (
          <View style={styles.titleRow}>
            <IconSymbol name={titleIcon.name} size={titleIcon.size ?? 18} color={titleIcon.color} />
            <ThemedText
              testID={testID && `${testID}-title`}
              style={[styles.titleRowText, { color: theme.text }]}
              numberOfLines={1}
            >
              {title}
            </ThemedText>
          </View>
        ) : (
          <ThemedText
            testID={testID && `${testID}-title`}
            style={[styles.headerTitle, { color: theme.text }]}
            numberOfLines={1}
          >
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
    ...StyleSheet.absoluteFill,
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
