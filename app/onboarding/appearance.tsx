/**
 * Appearance Screen
 * Physical description for 911 dispatch
 */

import { Href, useRouter } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { OnboardingStepHeader } from '@/components/OnboardingStepHeader';
import { ThemedText } from '@/components/ThemedText';
import { Colors } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';
import { saveProfile } from '@/database/profile';
import { useTranslation } from 'react-i18next';

export default function AppearanceScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
  const { completeStep } = useOnboarding();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const [height, setHeight] = useState('');
  const [weight, setWeight] = useState('');
  const [hairColor, setHairColor] = useState('');
  const [eyeColor, setEyeColor] = useState('');
  const [identifyingMarks, setIdentifyingMarks] = useState('');

  const formatHeightInput = (value: string): string => {
    const digits = value.replace(/\D/g, '').slice(0, 3);
    if (!digits) return '';
    if (digits.length === 1) return digits;
    if (digits.length === 2) return `${digits[0]}'${digits[1]}"`;
    return `${digits[0]}'${digits.slice(1)}"`;
  };

  const formatWeightInput = (value: string): string => {
    const digits = value.replace(/\D/g, '').slice(0, 4);
    if (!digits) return '';
    return digits.length > 3 ? `${digits.slice(0, -3)},${digits.slice(-3)}` : digits;
  };

  const handleContinue = async () => {
    try {
      await saveProfile({
        height: height.trim() || undefined,
        weight: weight.trim() || undefined,
        hairColor: hairColor.trim() || undefined,
        eyeColor: eyeColor.trim() || undefined,
        identifyingMarks: identifyingMarks.trim() || undefined,
      });
      await completeStep('profile_appearance');
      router.push('/onboarding/contact' as Href);
    } catch (err) {
      console.error('Error saving appearance:', err);
    }
  };

  const handleSkip = async () => {
    await completeStep('profile_appearance');
    router.push('/onboarding/contact' as Href);
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          <OnboardingStepHeader activeStep={3} totalSteps={4} />

          <ThemedText type="title" style={styles.title}>
            {t('appearance.title')}
          </ThemedText>

          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
            {t('appearance.subtitle')}
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.row}>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>{t('appearance.heightLabel')}</ThemedText>
                <TextInput
                  style={[
                    styles.input,
                    {
                      backgroundColor: theme.inputBackground,
                      borderColor: theme.inputBorder,
                      color: theme.text,
                    },
                  ]}
                  value={height}
                  onChangeText={(value) => setHeight(formatHeightInput(value))}
                  placeholder={t('appearance.heightPlaceholder')}
                  placeholderTextColor={theme.inputPlaceholder}
                  keyboardType="number-pad"
                  inputMode="numeric"
                />
              </View>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>{t('appearance.weightLabel')}</ThemedText>
                <TextInput
                  style={[
                    styles.input,
                    {
                      backgroundColor: theme.inputBackground,
                      borderColor: theme.inputBorder,
                      color: theme.text,
                    },
                  ]}
                  value={weight}
                  onChangeText={(value) => setWeight(formatWeightInput(value))}
                  placeholder={t('appearance.weightPlaceholder')}
                  placeholderTextColor={theme.inputPlaceholder}
                  keyboardType="number-pad"
                  inputMode="numeric"
                />
              </View>
            </View>

            <View style={styles.row}>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>{t('appearance.hairLabel')}</ThemedText>
                <TextInput
                  style={[
                    styles.input,
                    {
                      backgroundColor: theme.inputBackground,
                      borderColor: theme.inputBorder,
                      color: theme.text,
                    },
                  ]}
                  value={hairColor}
                  onChangeText={setHairColor}
                  placeholder={t('appearance.hairPlaceholder')}
                  placeholderTextColor={theme.inputPlaceholder}
                />
              </View>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>{t('appearance.eyeLabel')}</ThemedText>
                <TextInput
                  style={[
                    styles.input,
                    {
                      backgroundColor: theme.inputBackground,
                      borderColor: theme.inputBorder,
                      color: theme.text,
                    },
                  ]}
                  value={eyeColor}
                  onChangeText={setEyeColor}
                  placeholder={t('appearance.eyePlaceholder')}
                  placeholderTextColor={theme.inputPlaceholder}
                />
              </View>
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>{t('appearance.marksLabel')}</ThemedText>
              <TextInput
                style={[
                  styles.input,
                  styles.textArea,
                  {
                    backgroundColor: theme.inputBackground,
                    borderColor: theme.inputBorder,
                    color: theme.text,
                  },
                ]}
                value={identifyingMarks}
                onChangeText={setIdentifyingMarks}
                placeholder={t('appearance.marksPlaceholder')}
                placeholderTextColor={theme.inputPlaceholder}
                multiline
                numberOfLines={3}
              />
            </View>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <Pressable style={styles.skipButton} onPress={handleSkip}>
            <ThemedText style={[styles.skipButtonText, { color: theme.textDisabled }]}>
              {t('appearance.skip')}
            </ThemedText>
          </Pressable>
          <Pressable
            testID="onboarding-appearance-continue"
            accessibilityLabel="onboarding-appearance-continue"
            style={[styles.button, { backgroundColor: theme.primary }]}
            onPress={handleContinue}
          >
            <ThemedText style={styles.buttonText}>{t('appearance.continue')}</ThemedText>
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  keyboardView: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: Spacing.xl,
    paddingTop: 20,
    paddingBottom: 20,
  },
  title: {
    marginBottom: Spacing.sm,
  },
  subtitle: {
    ...Typography.body,
    opacity: 0.7,
    marginBottom: Spacing.xxl,
  },
  form: {
    gap: 20,
  },
  row: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  inputGroup: {
    gap: Spacing.sm,
  },
  halfWidth: {
    flex: 1,
  },
  label: {
    ...Typography.bodyBold,
  },
  input: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    paddingHorizontal: Spacing.lg,
    height: 48,
    paddingVertical: 0,
    ...Typography.body,
    lineHeight: 20,
  },
  textArea: {
    minHeight: 80,
    height: 'auto',
    paddingVertical: Spacing.sm,
    textAlignVertical: 'top',
  },
  footer: {
    padding: Spacing.lg,
    paddingBottom: Spacing.lg,
    gap: Spacing.md,
  },
  skipButton: {
    paddingVertical: Spacing.sm,
    alignItems: 'center',
  },
  skipButtonText: {
    ...Typography.body,
  },
  button: {
    paddingVertical: Spacing.md,
    borderRadius: Radius.lg,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
