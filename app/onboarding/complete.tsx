/**
 * Complete Screen - Onboarding finished
 * Transition to main app
 */

import { useRouter } from 'expo-router';
import { track } from '@/utils/analytics';
import { posthog } from '@/utils/posthog';
import { useEffect } from 'react';
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

  useEffect(() => {
    track('onboarding_step_viewed', { step: 'complete' });
  }, []);

  const handleFinish = async () => {
    track('onboarding_step_completed', { step: 'complete' });
    track('onboarding_completed');
    posthog.screen('home', { source: 'onboarding' });
    await completeStep('complete');
    await refreshOnboardingState();
    // Replace the navigation stack to go to main app
    router.replace('/');
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

        <View style={styles.nextSteps}>
          <ThemedText style={[styles.nextStepsTitle, { color: theme.text }]}>
            {t('complete.addMoreLater')}
          </ThemedText>
          {[
            t('complete.nextSteps.details'),
            t('complete.nextSteps.deescalation'),
            t('complete.nextSteps.destinations'),
            t('complete.nextSteps.contacts'),
            t('complete.nextSteps.checklist'),
          ].map((item) => (
            <View
              key={item}
              style={[styles.nextStepRow, { borderLeftColor: theme.border }]}
            >
              <ThemedText style={[styles.nextStepItem, { color: theme.textSecondary }]}>
                {item}
              </ThemedText>
            </View>
          ))}
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
    backgroundColor: Colors.light.success,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.xxl,
  },
  icon: {
    fontSize: 48,
    color: Colors.light.textOnPrimary,
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
    width: '100%',
    gap: Spacing.sm,
  },
  nextStepsTitle: {
    ...Typography.title,
    marginBottom: Spacing.xs,
  },
  nextStepRow: {
    borderLeftWidth: 3,
    borderRadius: 2,
    paddingVertical: Spacing.sm,
    paddingHorizontal: Spacing.md,
  },
  nextStepItem: {
    ...Typography.bodyBold,
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
