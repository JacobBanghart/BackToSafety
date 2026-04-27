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
import { useTranslation } from 'react-i18next';

export default function CompleteScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
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
          <ThemedText style={styles.icon}>{t('complete.icon')}</ThemedText>
        </View>

        <ThemedText type="display" style={styles.title}>
          {t('complete.title')}
        </ThemedText>

        <ThemedText style={[styles.description, { color: theme.textSecondary }]}>
          {t('complete.description')}
        </ThemedText>

        <View
          style={[
            styles.nextSteps,
            { backgroundColor: theme.card, borderColor: theme.border, borderWidth: 1 },
          ]}
        >
          <ThemedText style={styles.nextStepsTitle}>{t('complete.addMoreLater')}</ThemedText>
          <View style={styles.nextStepsList}>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>
              {t('complete.nextSteps.details')}
            </ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>
              {t('complete.nextSteps.deescalation')}
            </ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>
              {t('complete.nextSteps.destinations')}
            </ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>
              {t('complete.nextSteps.contacts')}
            </ThemedText>
            <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>
              {t('complete.nextSteps.checklist')}
            </ThemedText>
          </View>
        </View>
      </View>

      <View style={styles.footer}>
        <Pressable
          testID="onboarding-complete-home"
          accessibilityLabel="onboarding-complete-home"
          style={[styles.button, { backgroundColor: theme.primary }]}
          onPress={handleFinish}
        >
          <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>
            {t('complete.goHome')}
          </ThemedText>
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
