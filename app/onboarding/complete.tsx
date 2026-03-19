/**
 * Complete Screen - Onboarding finished
 * Transition to main app
 */

import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
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
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.content}>
        <View style={styles.iconContainer}>
          <ThemedText style={styles.icon}>✓</ThemedText>
        </View>

        <ThemedText type="display" style={styles.title}>
          You&apos;re ready!
        </ThemedText>

        <ThemedText style={[styles.description, { color: theme.textSecondary }]}>
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
        <Pressable style={[styles.button, { backgroundColor: theme.primary }]} onPress={handleFinish}>
          <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>Go to Home</ThemedText>
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
    marginBottom: Spacing.xxl,
  },
  icon: {
    fontSize: 48,
    color: '#fff',
  },
  title: {
    marginBottom: Spacing.lg,
    textAlign: 'center',
  },
  description: {
    ...Typography.body,
    textAlign: 'center',
    marginBottom: Spacing.xxl,
  },
  nextSteps: {
    borderRadius: Radius.lg,
    padding: 20,
  },
  nextStepsTitle: {
    ...Typography.bodyBold,
    marginBottom: Spacing.md,
  },
  nextStepsList: {
    gap: Spacing.sm,
  },
  nextStepItem: {
    fontSize: 15,
  },
  footer: {
    padding: Spacing.xl,
    paddingBottom: Spacing.xxl,
  },
  button: {
    paddingVertical: Spacing.lg,
    borderRadius: Radius.lg,
    alignItems: 'center',
  },
  buttonText: {
    fontSize: 18,
    fontWeight: '600',
  },
});
