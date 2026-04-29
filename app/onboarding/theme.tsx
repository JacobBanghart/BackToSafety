/**
 * Theme Selection Screen
 * Choose between light, dark, or system theme
 */

import { Href, useRouter } from 'expo-router';
import { track } from '@/utils/analytics';
import { Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors, neutral, primary } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { useTranslation } from 'react-i18next';

export default function ThemeScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
  const { completeStep } = useOnboarding();
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const themeOptions: {
    value: ThemePreference;
    label: string;
    icon: string;
    description: string;
  }[] = [
    {
      value: 'system',
      label: t('theme.options.system.label'),
      icon: '📱',
      description: t('theme.options.system.description'),
    },
    {
      value: 'light',
      label: t('theme.options.light.label'),
      icon: '☀️',
      description: t('theme.options.light.description'),
    },
    {
      value: 'dark',
      label: t('theme.options.dark.label'),
      icon: '🌙',
      description: t('theme.options.dark.description'),
    },
  ];

  const handleContinue = async () => {
    track('onboarding_step_completed', { step: 'theme' });
    await completeStep('welcome'); // Theme selection is part of welcome step
    router.push('/onboarding/name' as Href);
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.content}>
        <View style={styles.progress}>
          <View style={[styles.progressDot, styles.progressActive]} />
          <View style={styles.progressDot} />
          <View style={styles.progressDot} />
          <View style={styles.progressDot} />
          <View style={styles.progressDot} />
        </View>

        <ThemedText type="title" style={styles.title}>
          {t('theme.title')}
        </ThemedText>

        <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
          {t('theme.subtitle')}
        </ThemedText>

        <View style={styles.options}>
          {themeOptions.map((option) => (
            <Pressable
              key={option.value}
              style={[
                styles.option,
                {
                  borderColor: themePreference === option.value ? theme.tint : theme.border,
                  backgroundColor:
                    themePreference === option.value
                      ? colorScheme === 'dark'
                        ? primary[800]
                        : primary[50]
                      : theme.card,
                },
              ]}
              onPress={() => setThemePreference(option.value)}
            >
              <ThemedText style={styles.optionIcon}>{option.icon}</ThemedText>
              <View style={styles.optionText}>
                <ThemedText
                  style={[
                    styles.optionLabel,
                    themePreference === option.value && { color: theme.tint },
                  ]}
                >
                  {option.label}
                </ThemedText>
                <ThemedText style={[styles.optionDescription, { color: theme.textSecondary }]}>
                  {option.description}
                </ThemedText>
              </View>
              {themePreference === option.value && (
                <ThemedText style={[styles.checkmark, { color: theme.tint }]}>✓</ThemedText>
              )}
            </Pressable>
          ))}
        </View>
      </View>

      <View style={styles.footer}>
        <Pressable
          style={[styles.button, { backgroundColor: theme.tint }]}
          onPress={handleContinue}
        >
          <ThemedText style={styles.buttonText}>{t('theme.continue')}</ThemedText>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
    paddingHorizontal: Spacing.xl,
    paddingTop: Spacing.xl,
  },
  progress: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
    marginBottom: 32,
  },
  progressDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: neutral[400],
  },
  progressActive: {
    backgroundColor: primary[700],
    width: 24,
  },
  title: {
    marginBottom: 8,
  },
  subtitle: {
    ...Typography.body,
    marginBottom: Spacing.xxl,
  },
  options: {
    gap: 12,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: Spacing.lg,
    borderRadius: Radius.lg,
    borderWidth: 2,
    gap: Spacing.lg,
  },
  optionIcon: {
    fontSize: 32,
  },
  optionText: {
    flex: 1,
  },
  optionLabel: {
    ...Typography.bodyLarge,
    fontWeight: '600',
    marginBottom: Spacing.xxs,
  },
  optionDescription: {
    ...Typography.body,
  },
  checkmark: {
    fontSize: 20,
    fontWeight: '700',
  },
  footer: {
    paddingHorizontal: Spacing.xl,
    paddingBottom: Spacing.xl,
  },
  button: {
    paddingVertical: Spacing.lg,
    borderRadius: Radius.lg,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    ...Typography.bodyLarge,
    fontWeight: '600',
  },
});
