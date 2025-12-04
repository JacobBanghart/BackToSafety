/**
 * Welcome Screen - First onboarding step
 * Explains app purpose, lets user pick theme, and starts the flow
 */

import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { primary, secondary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { ThemePreference, useTheme } from '@/context/ThemeContext';

export default function WelcomeScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const { themePreference, setThemePreference } = useTheme();

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'light', label: 'Light', icon: '☀️' },
    { value: 'dark', label: 'Dark', icon: '🌙' },
    { value: 'system', label: 'Auto', icon: '📱' },
  ];

  const handleContinue = async () => {
    await completeStep('welcome');
    router.push('/onboarding/name' as any);
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        <View style={styles.logoContainer}>
          <ThemedText style={styles.logoText}>nijii</ThemedText>
        </View>

        <ThemedText type="title" style={styles.title}>
          Welcome
        </ThemedText>

        <ThemedText style={styles.description}>
          Nijii helps caregivers respond quickly and calmly when someone with dementia wanders.
        </ThemedText>

        <View style={styles.features}>
          <View style={styles.feature}>
            <ThemedText style={styles.featureIcon}>⏱️</ThemedText>
            <ThemedText style={styles.featureText}>Guided 15-minute search timer</ThemedText>
          </View>
          <View style={styles.feature}>
            <ThemedText style={styles.featureIcon}>📋</ThemedText>
            <ThemedText style={styles.featureText}>Ready-to-read 911 script</ThemedText>
          </View>
          <View style={styles.feature}>
            <ThemedText style={styles.featureIcon}>🔒</ThemedText>
            <ThemedText style={styles.featureText}>All data stays on your device</ThemedText>
          </View>
        </View>

        {/* Theme Selector */}
        <View style={styles.themeSection}>
          <ThemedText style={styles.themeLabel}>Choose your theme</ThemedText>
          <View style={styles.themeOptions}>
            {themeOptions.map((option) => (
              <Pressable
                key={option.value}
                style={[
                  styles.themeOption,
                  themePreference === option.value && styles.themeOptionActive,
                ]}
                onPress={() => setThemePreference(option.value)}
              >
                <ThemedText style={styles.themeIcon}>{option.icon}</ThemedText>
                <ThemedText
                  style={[
                    styles.themeText,
                    themePreference === option.value && styles.themeTextActive,
                  ]}
                >
                  {option.label}
                </ThemedText>
              </Pressable>
            ))}
          </View>
        </View>

        <ThemedText style={styles.privacy}>
          Your information never leaves this device. No accounts, no cloud, no tracking.
        </ThemedText>
      </ScrollView>

      <View style={styles.footer}>
        <Pressable style={styles.button} onPress={handleContinue}>
          <ThemedText style={styles.buttonText}>Get Started</ThemedText>
        </Pressable>
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
    paddingTop: 40,
    paddingBottom: 20,
  },
  logoContainer: {
    marginBottom: 32,
  },
  logoText: {
    fontSize: 28,
    fontWeight: '300',
    color: secondary[100],
    letterSpacing: 4,
  },
  title: {
    fontSize: 36,
    color: '#fff',
    marginBottom: 16,
  },
  description: {
    fontSize: 18,
    color: 'rgba(255,255,255,0.9)',
    lineHeight: 26,
    marginBottom: 32,
  },
  features: {
    gap: 16,
    marginBottom: 32,
  },
  feature: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  featureIcon: {
    fontSize: 24,
  },
  featureText: {
    fontSize: 16,
    color: 'rgba(255,255,255,0.85)',
    flex: 1,
  },
  themeSection: {
    marginBottom: 24,
  },
  themeLabel: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.7)',
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
  privacy: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.5)',
    fontStyle: 'italic',
  },
  footer: {
    padding: 24,
    paddingBottom: 32,
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
});
