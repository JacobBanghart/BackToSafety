/**
 * Fatal error screen shown when the database fails to initialize.
 *
 * Deliberately does NOT use ThemeContext or any other data-reading provider
 * — this screen renders when the DB (and therefore every provider that
 * depends on it) is unavailable. It reads the system color scheme directly
 * and pulls colors straight from constants/Colors so it can render even
 * when nothing else in the app can.
 */

import { Colors } from '@/constants/Colors';
import { Radius, Spacing } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { StyleSheet, Text, TouchableOpacity, useColorScheme, View } from 'react-native';

type FatalErrorScreenProps = {
  onRetry: () => void;
};

export function FatalErrorScreen({ onRetry }: FatalErrorScreenProps) {
  const { t } = useTranslation('common');
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const theme = Colors[scheme];

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Text style={[styles.title, { color: theme.text }]}>{t('fatalError.title')}</Text>
      <Text style={[styles.body, { color: theme.textSecondary }]}>{t('fatalError.body')}</Text>
      <TouchableOpacity
        style={[styles.button, { backgroundColor: theme.primary }]}
        onPress={onRetry}
        activeOpacity={0.8}
      >
        <Text style={[styles.buttonLabel, { color: theme.textOnPrimary }]}>
          {t('fatalError.retry')}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.xl,
  },
  title: {
    ...Typography.headline,
    textAlign: 'center',
    marginBottom: Spacing.md,
  },
  body: {
    ...Typography.body,
    textAlign: 'center',
    marginBottom: Spacing.xl,
  },
  button: {
    minHeight: 48,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.xl,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    ...Typography.bodyBold,
  },
});
