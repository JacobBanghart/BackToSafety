/**
 * Theme Selection Screen
 * Choose between light, dark, or system theme
 */

import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors, neutral, primary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { ThemePreference, useTheme } from '@/context/ThemeContext';

export default function ThemeScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const themeOptions: {
    value: ThemePreference;
    label: string;
    icon: string;
    description: string;
  }[] = [
    { value: 'system', label: 'System', icon: '📱', description: 'Match your device settings' },
    { value: 'light', label: 'Light', icon: '☀️', description: 'Bright and clear' },
    { value: 'dark', label: 'Dark', icon: '🌙', description: 'Easy on the eyes' },
  ];

  const handleContinue = async () => {
    await completeStep('welcome'); // Theme selection is part of welcome step
    router.push('/onboarding/name' as any);
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
          Choose your look
        </ThemedText>

        <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
          Select how you&apos;d like the app to appear. You can change this later in Settings.
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
          <ThemedText style={styles.buttonText}>Continue</ThemedText>
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
    paddingHorizontal: 24,
    paddingTop: 20,
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
    backgroundColor: primary[500],
    width: 24,
  },
  title: {
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    marginBottom: 32,
    lineHeight: 22,
  },
  options: {
    gap: 12,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    borderWidth: 2,
    gap: 16,
  },
  optionIcon: {
    fontSize: 32,
  },
  optionText: {
    flex: 1,
  },
  optionLabel: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 2,
  },
  optionDescription: {
    fontSize: 14,
  },
  checkmark: {
    fontSize: 20,
    fontWeight: '700',
  },
  footer: {
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  button: {
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
