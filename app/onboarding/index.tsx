/**
 * Welcome Screen - First onboarding step
 * Explains app purpose, lets user pick theme, and starts the flow
 */

import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { neutral, primary, secondary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { ThemePreference, useTheme } from '@/context/ThemeContext';

export default function WelcomeScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const { themePreference, setThemePreference, colorScheme } = useTheme();

  const isDark = colorScheme === 'dark';

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'light', label: 'Light', icon: '☀️' },
    { value: 'dark', label: 'Dark', icon: '🌙' },
    { value: 'system', label: 'Auto', icon: '📱' },
  ];

  const handleContinue = async () => {
    await completeStep('welcome');
    router.push('/onboarding/name' as any);
  };

  // Dynamic colors based on theme
  const colors = {
    background: isDark ? primary[900] : neutral[50],
    text: isDark ? '#fff' : neutral[900],
    textSecondary: isDark ? 'rgba(255,255,255,0.85)' : neutral[600],
    textMuted: isDark ? 'rgba(255,255,255,0.7)' : neutral[500],
    textFaint: isDark ? 'rgba(255,255,255,0.6)' : neutral[400],
    textVeryFaint: isDark ? 'rgba(255,255,255,0.5)' : neutral[400],
    featureBg: isDark ? 'rgba(255,255,255,0.08)' : neutral[100],
    featureIconBg: isDark ? 'rgba(255,255,255,0.15)' : primary[100],
    optionBg: isDark ? 'rgba(255,255,255,0.1)' : neutral[100],
    optionActiveBg: isDark ? 'rgba(255,255,255,0.2)' : primary[100],
    optionBorder: isDark ? secondary[100] : primary[700],
    optionActiveText: isDark ? secondary[100] : primary[700],
    buttonBg: isDark ? secondary[100] : primary[700],
    buttonText: isDark ? primary[900] : '#fff',
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
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
            A guided emergency response app for caregivers when someone with dementia wanders.
          </ThemedText>
        </View>

        <View style={styles.features}>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>⏱️</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>15-Minute Timer</ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>Guided search protocol with step-by-step checklist</ThemedText>
            </View>
          </View>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>📋</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>911 Ready</ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>One-tap script with all critical info for dispatchers</ThemedText>
            </View>
          </View>
          <View style={[styles.feature, { backgroundColor: colors.featureBg }]}>
            <View style={[styles.featureIconContainer, { backgroundColor: colors.featureIconBg }]}>
              <ThemedText style={styles.featureIcon}>🔒</ThemedText>
            </View>
            <View style={styles.featureContent}>
              <ThemedText style={[styles.featureTitle, { color: colors.text }]}>100% Private</ThemedText>
              <ThemedText style={[styles.featureText, { color: colors.textMuted }]}>All data stays on your device. No accounts needed.</ThemedText>
            </View>
          </View>
        </View>

        {/* Theme Selector */}
        <View style={styles.themeSection}>
          <ThemedText style={[styles.themeLabel, { color: colors.textFaint }]}>Choose your theme</ThemedText>
          <View style={styles.themeOptions}>
            {themeOptions.map((option) => (
              <Pressable
                key={option.value}
                style={[
                  styles.themeOption,
                  { backgroundColor: colors.optionBg },
                  themePreference === option.value && { backgroundColor: colors.optionActiveBg, borderColor: colors.optionBorder },
                ]}
                onPress={() => setThemePreference(option.value)}
              >
                <ThemedText style={styles.themeIcon}>{option.icon}</ThemedText>
                <ThemedText
                  style={[
                    styles.themeText,
                    { color: colors.textMuted },
                    themePreference === option.value && { color: colors.optionActiveText, fontWeight: '600' },
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
        <Pressable style={[styles.button, { backgroundColor: colors.buttonBg }]} onPress={handleContinue}>
          <ThemedText style={[styles.buttonText, { color: colors.buttonText }]}>Get Started</ThemedText>
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
    backgroundColor: primary[900],
  },
  scrollView: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 24,
    paddingTop: 48,
    paddingBottom: 20,
  },
  heroSection: {
    marginBottom: 40,
    alignItems: 'center',
  },
  logoContainer: {
    marginBottom: 24,
    alignItems: 'center',
  },
  logoImage: {
    width: 180,
    height: 180,
  },
  title: {
    fontSize: 36,
    color: '#fff',
    marginBottom: 16,
    lineHeight: 44,
    textAlign: 'center',
  },
  description: {
    fontSize: 17,
    color: 'rgba(255,255,255,0.85)',
    lineHeight: 26,
    textAlign: 'center',
  },
  features: {
    gap: 16,
    marginBottom: 32,
  },
  feature: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 16,
    backgroundColor: 'rgba(255,255,255,0.08)',
    padding: 16,
    borderRadius: 12,
  },
  featureIconContainer: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  featureIcon: {
    fontSize: 22,
  },
  featureContent: {
    flex: 1,
    gap: 4,
  },
  featureTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  featureText: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.7)',
    lineHeight: 20,
  },
  themeSection: {
    marginBottom: 24,
  },
  themeLabel: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.6)',
    marginBottom: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '600',
  },
  themeOptions: {
    flexDirection: 'row',
    gap: 12,
  },
  themeOption: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 14,
    paddingHorizontal: 12,
    borderRadius: 10,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  themeOptionActive: {
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderColor: secondary[100],
  },
  themeIcon: {
    fontSize: 20,
  },
  themeText: {
    fontSize: 15,
    color: 'rgba(255,255,255,0.8)',
    fontWeight: '500',
  },
  themeTextActive: {
    color: secondary[100],
    fontWeight: '600',
  },
  footer: {
    padding: 24,
    paddingBottom: 32,
    gap: 16,
  },
  button: {
    backgroundColor: secondary[100],
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonText: {
    color: primary[900],
    fontSize: 18,
    fontWeight: '600',
  },
  privacy: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.5)',
    textAlign: 'center',
  },
});
