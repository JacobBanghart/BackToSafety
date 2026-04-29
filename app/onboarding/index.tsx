/**
 * Welcome Screen - First onboarding step
 * Explains app purpose, lets user pick theme, and starts the flow
 */

import { Image } from 'expo-image';
import { Href, useRouter } from 'expo-router';
import i18n from 'i18next';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors, neutral } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { saveSetting } from '@/database/storage';
import { track } from '@/utils/analytics';

export default function WelcomeScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
  const { completeStep } = useOnboarding();
  const { themePreference, setThemePreference, colorScheme } = useTheme();

  const isDark = colorScheme === 'dark';
  const theme = Colors[colorScheme];

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'light', label: t('welcome.themeOptions.light'), icon: '☀️' },
    { value: 'dark', label: t('welcome.themeOptions.dark'), icon: '🌙' },
    { value: 'system', label: t('welcome.themeOptions.auto'), icon: '📱' },
  ];

  const handleContinue = async () => {
    track('onboarding_step_completed', { step: 'welcome' });
    await completeStep('welcome');
    router.push('/onboarding/name' as Href);
  };

  // Map legacy `colors.*` references to theme tokens
  const colors = {
    background: theme.background,
    text: theme.text,
    textSecondary: theme.textSecondary,
    textMuted: theme.textSecondary,
    textFaint: theme.textDisabled,
    textVeryFaint: theme.textDisabled,
    featureBg: isDark ? 'rgba(255,255,255,0.08)' : neutral[100],
    featureIconBg: theme.primaryLight,
    optionBg: isDark ? 'rgba(255,255,255,0.1)' : neutral[100],
    optionActiveBg: theme.primaryLight,
    optionBorder: theme.tint,
    optionActiveText: theme.tint,
    buttonBg: theme.primary,
    buttonText: theme.textOnPrimary,
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator
      >
        <View style={styles.heroSection}>
          <View style={styles.logoContainer}>
            <Image
              source={require('@/assets/images/logo-full.png')}
              style={styles.logoImage}
              contentFit="contain"
            />
          </View>

          <ThemedText type="title" style={[styles.title, { color: colors.text }]}>
            {t('welcome.title')}
          </ThemedText>

          <ThemedText style={[styles.description, { color: colors.textSecondary }]}>
            {t('welcome.description')}
          </ThemedText>
        </View>

        <View style={styles.features}>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>⏱️</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>
                {t('welcome.features.timer.title')}
              </ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>
                {t('welcome.features.timer.description')}
              </ThemedText>
            </View>
          </View>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>📋</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>
                {t('welcome.features.readout.title')}
              </ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>
                {t('welcome.features.readout.description')}
              </ThemedText>
            </View>
          </View>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>🔒</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>
                {t('welcome.features.privacy.title')}
              </ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>
                {t('welcome.features.privacy.description')}
              </ThemedText>
            </View>
          </View>
        </View>

        {/* Theme Selector */}
        <View style={styles.themeSection}>
          <ThemedText style={[styles.themeLabel, { color: colors.textFaint }]}>
            {t('welcome.themeLabel')}
          </ThemedText>
          <View style={styles.themeOptions}>
            {themeOptions.map((option) => (
              <Pressable
                key={option.value}
                style={[
                  styles.themeOption,
                  { backgroundColor: colors.optionBg },
                  themePreference === option.value && {
                    backgroundColor: colors.optionActiveBg,
                    borderColor: colors.optionBorder,
                  },
                ]}
                onPress={() => setThemePreference(option.value)}
              >
                <ThemedText style={styles.themeIcon}>{option.icon}</ThemedText>
                <ThemedText
                  style={[
                    styles.themeText,
                    { color: colors.textMuted },
                    themePreference === option.value && {
                      color: colors.optionActiveText,
                      fontWeight: '600',
                    },
                  ]}
                >
                  {option.label}
                </ThemedText>
              </Pressable>
            ))}
          </View>
        </View>

        {/* Language Selector — dev builds only */}
        {__DEV__ && (
          <View style={styles.themeSection}>
            <ThemedText style={[styles.themeLabel, { color: colors.textFaint }]}>
              {t('welcome.languageLabel')}
            </ThemedText>
            <View style={styles.themeOptions}>
              {(['en', 'es'] as const).map((lang) => {
                const isSelected = i18n.language === lang;
                const label = lang === 'en' ? 'English' : 'Español';
                return (
                  <Pressable
                    key={lang}
                    style={[
                      styles.themeOption,
                      { backgroundColor: colors.optionBg },
                      isSelected && {
                        backgroundColor: colors.optionActiveBg,
                        borderColor: colors.optionBorder,
                      },
                    ]}
                    onPress={() => {
                      void i18n.changeLanguage(lang);
                      void saveSetting('language_preference', lang);
                    }}
                  >
                    <ThemedText
                      style={[
                        styles.themeText,
                        { color: colors.textMuted },
                        isSelected && {
                          color: colors.optionActiveText,
                          fontWeight: '600',
                        },
                      ]}
                    >
                      {label}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>
          </View>
        )}
      </ScrollView>

      <View style={styles.footer}>
        <ThemedText style={[styles.scrollHint, { color: colors.textFaint }]}>
          {t('welcome.scrollHint')}
        </ThemedText>
        <Pressable
          testID="onboarding-get-started"
          accessibilityLabel="onboarding-get-started"
          style={[styles.button, { backgroundColor: colors.buttonBg }]}
          onPress={handleContinue}
        >
          <ThemedText style={[styles.buttonText, { color: colors.buttonText }]}>
            {t('welcome.getStarted')}
          </ThemedText>
        </Pressable>
        <ThemedText style={[styles.privacy, { color: colors.textVeryFaint }]}>
          {t('welcome.privacy')}
        </ThemedText>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  content: {
    paddingHorizontal: Spacing.xl,
    paddingTop: Spacing.xxxl,
    paddingBottom: Spacing.xl,
  },
  heroSection: {
    marginBottom: Spacing.xxl,
    alignItems: 'center',
  },
  logoContainer: {
    marginBottom: Spacing.xl,
    alignItems: 'center',
  },
  logoImage: {
    width: 180,
    height: 180,
  },
  title: {
    ...Typography.display,
    fontSize: 36,
    marginBottom: Spacing.lg,
    lineHeight: 44,
    textAlign: 'center',
  },
  description: {
    ...Typography.bodyLarge,
    textAlign: 'center',
  },
  features: {
    gap: Spacing.lg,
    marginBottom: Spacing.xxl,
  },
  feature: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 16,
    padding: Spacing.lg,
    borderRadius: Radius.lg,
  },
  featureIconContainer: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  featureIcon: {
    fontSize: 22,
  },
  featureContent: {
    flex: 1,
    gap: Spacing.xs,
  },
  featureTitle: {
    ...Typography.bodyBold,
  },
  featureText: {
    ...Typography.body,
  },
  themeSection: {
    marginBottom: Spacing.xl,
  },
  themeLabel: {
    ...Typography.caption,
    marginBottom: Spacing.md,
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '600',
  },
  themeOptions: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  themeOption: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
    borderRadius: Radius.lg,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  themeIcon: {
    fontSize: 20,
  },
  themeText: {
    ...Typography.bodyLarge,
    fontWeight: '500',
  },
  footer: {
    padding: Spacing.lg,
    paddingBottom: Spacing.lg,
    gap: Spacing.lg,
  },
  scrollHint: {
    ...Typography.caption,
    textAlign: 'center',
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '600',
  },
  button: {
    paddingVertical: Spacing.md,
    borderRadius: Radius.lg,
    alignItems: 'center',
    minHeight: 46,
    justifyContent: 'center',
  },
  buttonText: {
    ...Typography.bodyLarge,
    fontWeight: '600',
  },
  privacy: {
    ...Typography.caption,
    textAlign: 'center',
  },
});
