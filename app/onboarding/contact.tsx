/**
 * Emergency Contact Screen
 * At least one person to call in emergency
 */

import { useRouter } from 'expo-router';
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

import { ThemedText } from '@/components/ThemedText';
import { Colors } from '@/constants/Colors';
import { Typography } from '@/constants/Typography';
import { Spacing, Radius } from '@/constants/Spacing';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';
import { createContact } from '@/database/contacts';
import { formatPhoneInput } from '@/utils/phone';

export default function ContactScreen() {
  const router = useRouter();
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
      await completeStep('emergency_contact');
      router.push('/onboarding/complete' as any);
    } catch (err) {
      console.error('Error saving contact:', err);
      setError('Failed to save. Please try again.');
    }
  };

  const handleSkip = async () => {
    await completeStep('emergency_contact');
    router.push('/onboarding/complete' as any);
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          <View style={styles.progress}>
            <View style={[styles.progressDot, { backgroundColor: theme.primary }]} />
            <View style={[styles.progressDot, { backgroundColor: theme.primary }]} />
            <View style={[styles.progressDot, { backgroundColor: theme.primary }]} />
            <View
              style={[
                styles.progressDot,
                styles.progressActive,
                { backgroundColor: theme.primary },
              ]}
            />
          </View>

          <ThemedText type="title" style={styles.title}>
            Emergency contact
          </ThemedText>

          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
            Who should we notify if they wander? You can add more contacts later.
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Contact Name *</ThemedText>
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
                placeholder="e.g., John Smith"
                placeholderTextColor={theme.inputPlaceholder}
                autoCapitalize="words"
                autoComplete="name"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Phone Number *</ThemedText>
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
                placeholder="(555) 123-4567"
                placeholderTextColor={theme.inputPlaceholder}
                keyboardType="phone-pad"
                autoComplete="tel"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Relationship</ThemedText>
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
                placeholder="e.g., Son, Daughter, Neighbor"
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
              What happens in an emergency?
            </ThemedText>
            <ThemedText style={[styles.infoText, { color: theme.textSecondary }]}>
              • You&apos;ll have a one-tap button to text this contact{'\n'}• They&apos;ll receive
              the person&apos;s photo and last known location{'\n'}• You can add neighbors and
              family later
            </ThemedText>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <Pressable style={styles.skipButton} onPress={handleSkip}>
            <ThemedText style={[styles.skipButtonText, { color: theme.textDisabled }]}>
              Skip for now
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
              Continue
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
  progress: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: Spacing.sm,
    marginBottom: Spacing.xxl,
  },
  progressDot: {
    width: 8,
    height: 8,
    borderRadius: Radius.sm,
  },
  progressActive: {
    width: 24,
  },
  progressComplete: {},
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
    padding: Spacing.xl,
    paddingBottom: Spacing.xxl,
    gap: Spacing.md,
  },
  skipButton: {
    paddingVertical: Spacing.md,
    alignItems: 'center',
  },
  skipButtonText: {
    ...Typography.body,
  },
  button: {
    paddingVertical: Spacing.lg,
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
