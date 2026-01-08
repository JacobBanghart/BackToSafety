/**
 * Complete Screen - Onboarding finished
 * Transition to main app
 */

import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors, primary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';

export default function CompleteScreen() {
  const router = useRouter();
  const { completeStep, refreshOnboardingState } = useOnboarding();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const handleFinish = async () => {
    await completeStep('complete');
    await refreshOnboardingState();
    // Replace the navigation stack to go to main app
    router.replace('/(tabs)');
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.iconContainer}>
          <ThemedText style={styles.icon}>✓</ThemedText>
        </View>

        <ThemedText type="title" style={styles.title}>
          You&apos;re ready!
        </ThemedText>

        <ThemedText style={styles.description}>
          You have the essential information set up. In an emergency, tap the big button on the home
          screen to start the guided search.
        </ThemedText>

        <View style={[styles.nextSteps, { backgroundColor: theme.card, borderColor: theme.border, borderWidth: 1 }]}>
          <ThemedText style={styles.nextStepsTitle}>You can add more later:</ThemedText>
          <View style={styles.nextStepsList}>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>• Medical conditions & medications</ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>• De-escalation techniques</ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>• Likely destinations</ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>• More emergency contacts</ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>• Safety checklist</ThemedText>
          </View>
        </View>
      </View>

      <View style={styles.footer}>
        <Pressable style={styles.button} onPress={handleFinish}>
          <ThemedText style={styles.buttonText}>Go to Home</ThemedText>
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
    paddingTop: 60,
    alignItems: 'center',
  },
  iconContainer: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: '#22c55e',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 32,
  },
  icon: {
    fontSize: 48,
    color: '#fff',
  },
  title: {
    fontSize: 32,
    marginBottom: 16,
    textAlign: 'center',
  },
  description: {
    fontSize: 16,
    textAlign: 'center',
    opacity: 0.8,
    lineHeight: 24,
    marginBottom: 32,
  },
  nextSteps: {
    borderRadius: 12,
    padding: 20,
    width: '100%',
  },
  nextStepsTitle: {
    fontWeight: '600',
    marginBottom: 12,
  },
  nextStepsList: {
    gap: 8,
  },
  nextStepItem: {
    fontSize: 15,
  },
  footer: {
    padding: 24,
    paddingBottom: 32,
  },
  button: {
    backgroundColor: primary[700],
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
