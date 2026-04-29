/**
 * Emergency Contact Screen
 * At least one person to call in emergency
 */

import { Href, useRouter } from 'expo-router';
import { track } from '@/utils/analytics';
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
import { Typography } from '@/constants/Typography';
import { Spacing, Radius } from '@/constants/Spacing';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';
import { createContact } from '@/database/contacts';
import { formatPhoneInput } from '@/utils/phone';
import { useTranslation } from 'react-i18next';

export default function ContactScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
  const { completeStep } = useOnboarding();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [relationship, setRelationship] = useState('');
  const [error, setError] = useState('');

  const handleContinue = async () => {
    if (!name.trim() || !phone.trim()) {
      setError('Name and phone number are required');
      return;
    }

    try {
      await createContact({
        name: name.trim(),
        phone: phone.trim(),
        relationship: relationship.trim() || undefined,
        role: 'primary_caregiver',
        notifyOnEmergency: true,
        shareMedicalInfo: true,
      });
      track('onboarding_step_completed', { step: 'emergency_contact', skipped: false });
      await completeStep('emergency_contact');
      router.push('/onboarding/complete' as Href);
    } catch (err) {
      console.error('Error saving contact:', err);
      setError('Failed to save. Please try again.');
    }
  };

  const handleSkip = async () => {
    track('onboarding_step_completed', { step: 'emergency_contact', skipped: true });
    await completeStep('emergency_contact');
    router.push('/onboarding/complete' as Href);
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          <OnboardingStepHeader activeStep={4} totalSteps={4} />

          <ThemedText type="title" style={styles.title}>
            {t('contact.title')}
          </ThemedText>

          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
            {t('contact.subtitle')}
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>{t('contact.nameLabel')}</ThemedText>
              <TextInput
                style={[
                  styles.input,
                  {
                    backgroundColor: theme.inputBackground,
                    borderColor: theme.inputBorder,
                    color: theme.text,
                  },
                ]}
                value={name}
                onChangeText={(text) => {
                  setName(text);
                  setError('');
                }}
                placeholder={t('contact.namePlaceholder')}
                placeholderTextColor={theme.inputPlaceholder}
                autoCapitalize="words"
                autoComplete="name"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>{t('contact.phoneLabel')}</ThemedText>
              <TextInput
                style={[
                  styles.input,
                  {
                    backgroundColor: theme.inputBackground,
                    borderColor: theme.inputBorder,
                    color: theme.text,
                  },
                ]}
                value={phone}
                onChangeText={(text) => {
                  setPhone(formatPhoneInput(text));
                  setError('');
                }}
                placeholder={t('contact.phonePlaceholder')}
                placeholderTextColor={theme.inputPlaceholder}
                keyboardType="phone-pad"
                inputMode="tel"
                autoComplete="tel"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>{t('contact.relationshipLabel')}</ThemedText>
              <TextInput
                style={[
                  styles.input,
                  {
                    backgroundColor: theme.inputBackground,
                    borderColor: theme.inputBorder,
                    color: theme.text,
                  },
                ]}
                value={relationship}
                onChangeText={setRelationship}
                placeholder={t('contact.relationshipPlaceholder')}
                placeholderTextColor={theme.inputPlaceholder}
                autoCapitalize="words"
              />
            </View>

            {error ? (
              <ThemedText style={[styles.error, { color: theme.error }]}>{error}</ThemedText>
            ) : null}
          </View>

          <View style={[styles.infoBox, { backgroundColor: theme.primaryLight }]}>
            <ThemedText style={[styles.infoTitle, { color: theme.text }]}>
              {t('contact.infoBox.title')}
            </ThemedText>
            <ThemedText style={[styles.infoText, { color: theme.textSecondary }]}>
              {t('contact.infoBox.body')}
            </ThemedText>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <Pressable
            testID="onboarding-contact-skip"
            accessibilityLabel="onboarding-contact-skip"
            style={styles.skipButton}
            onPress={handleSkip}
          >
            <ThemedText style={[styles.skipButtonText, { color: theme.textDisabled }]}>
              {t('contact.skip')}
            </ThemedText>
          </Pressable>
          <Pressable
            style={[
              styles.button,
              { backgroundColor: theme.primary },
              (!name.trim() || !phone.trim()) && styles.buttonDisabled,
            ]}
            onPress={handleContinue}
          >
            <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>
              {t('contact.continue')}
            </ThemedText>
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
    marginBottom: Spacing.xxl,
  },
  form: {
    gap: 20,
  },
  inputGroup: {
    gap: Spacing.sm,
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
  error: {
    fontSize: 14,
  },
  infoBox: {
    borderRadius: Radius.lg,
    padding: Spacing.lg,
    marginTop: Spacing.xl,
  },
  infoTitle: {
    ...Typography.bodyBold,
    marginBottom: Spacing.sm,
  },
  infoText: {
    fontSize: 14,
    lineHeight: 22,
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
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    fontSize: 18,
    fontWeight: '600',
  },
});
