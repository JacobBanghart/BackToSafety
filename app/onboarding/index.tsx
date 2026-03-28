/**
 * Welcome Screen - First onboarding step
 * Explains app purpose, lets user pick theme, and starts the flow
 */

import { Image } from 'expo-image';
import { Href, useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors, neutral } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { ThemePreference, useTheme } from '@/context/ThemeContext';

export default function WelcomeScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const { themePreference, setThemePreference, colorScheme } = useTheme();

  const isDark = colorScheme === 'dark';
  const theme = Colors[colorScheme];

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'light', label: 'Light', icon: '☀️' },
    { value: 'dark', label: 'Dark', icon: '🌙' },
    { value: 'system', label: 'Auto', icon: '📱' },
  ];

  const handleContinue = async () => {
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
            Be prepared.{'\n'}Stay calm.
          </ThemedText>

          <ThemedText style={[styles.description, { color: colors.textSecondary }]}>
            A safety coordination app that helps families and supporters organize a search quickly.
          </ThemedText>
        </View>

        <View style={styles.features}>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>⏱️</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>
                15-Minute Timer
              </ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>
                Guided search protocol with step-by-step checklist
              </ThemedText>
            </View>
          </View>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>📋</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>
                911 Ready
              </ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>
                One-tap script with all critical info for dispatchers
              </ThemedText>
            </View>
          </View>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>🔒</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>
                100% Private
              </ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>
                All data stays on your device. No accounts needed.
              </ThemedText>
            </View>
          </View>
        </View>

        {/* Theme Selector */}
        <View style={styles.themeSection}>
          <ThemedText style={[styles.themeLabel, { color: colors.textFaint }]}>
            Choose your theme
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
      </ScrollView>

      <View style={styles.footer}>
        <ThemedText style={[styles.scrollHint, { color: colors.textFaint }]}>
          ↓ Scroll for more
        </ThemedText>
        <Pressable
          style={[styles.button, { backgroundColor: colors.buttonBg }]}
          onPress={handleContinue}
        >
          <ThemedText style={[styles.buttonText, { color: colors.buttonText }]}>
            Get Started
          </ThemedText>
        </Pressable>
        <ThemedText style={[styles.privacy, { color: colors.textVeryFaint }]}>
          Your information never leaves this device.
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
