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
import { neutral, primary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { createContact } from '@/database/contacts';
import { formatPhoneInput } from '@/utils/phone';

export default function ContactScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
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
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          <View style={styles.progress}>
            <View style={[styles.progressDot, styles.progressComplete]} />
            <View style={[styles.progressDot, styles.progressComplete]} />
            <View style={[styles.progressDot, styles.progressComplete]} />
            <View style={[styles.progressDot, styles.progressActive]} />
          </View>

          <ThemedText type="title" style={styles.title}>
            Emergency contact
          </ThemedText>

          <ThemedText style={styles.subtitle}>
            Who should we notify if they wander? You can add more contacts later.
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Contact Name *</ThemedText>
              <TextInput
                style={styles.input}
                value={name}
                onChangeText={(text) => {
                  setName(text);
                  setError('');
                }}
                placeholder="e.g., John Smith"
                placeholderTextColor={neutral[400]}
                autoCapitalize="words"
                autoComplete="name"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Phone Number *</ThemedText>
              <TextInput
                style={styles.input}
                value={phone}
                onChangeText={(text) => {
                  setPhone(formatPhoneInput(text));
                  setError('');
                }}
                placeholder="(555) 123-4567"
                placeholderTextColor={neutral[400]}
                keyboardType="phone-pad"
                autoComplete="tel"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Relationship</ThemedText>
              <TextInput
                style={styles.input}
                value={relationship}
                onChangeText={setRelationship}
                placeholder="e.g., Son, Daughter, Neighbor"
                placeholderTextColor={neutral[400]}
                autoCapitalize="words"
              />
            </View>

            {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          </View>

          <View style={styles.infoBox}>
            <ThemedText style={styles.infoTitle}>What happens in an emergency?</ThemedText>
            <ThemedText style={styles.infoText}>
              • You&apos;ll have a one-tap button to text this contact{'\n'}• They&apos;ll receive
              the person&apos;s photo and last known location{'\n'}• You can add neighbors and
              family later
            </ThemedText>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <Pressable style={styles.skipButton} onPress={handleSkip}>
            <ThemedText style={styles.skipButtonText}>Skip for now</ThemedText>
          </Pressable>
          <Pressable
            style={[styles.button, (!name.trim() || !phone.trim()) && styles.buttonDisabled]}
            onPress={handleContinue}
          >
            <ThemedText style={styles.buttonText}>Continue</ThemedText>
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
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 20,
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
    backgroundColor: neutral[300],
  },
  progressActive: {
    backgroundColor: primary[700],
    width: 24,
  },
  progressComplete: {
    backgroundColor: primary[500],
  },
  title: {
    fontSize: 28,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    opacity: 0.7,
    marginBottom: 32,
  },
  form: {
    gap: 20,
  },
  inputGroup: {
    gap: 8,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
  },
  input: {
    borderWidth: 1,
    borderColor: neutral[300],
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 16,
    backgroundColor: '#fff',
  },
  error: {
    color: '#ef4444',
    fontSize: 14,
  },
  infoBox: {
    backgroundColor: primary[50],
    borderRadius: 12,
    padding: 16,
    marginTop: 24,
  },
  infoTitle: {
    fontWeight: '600',
    marginBottom: 8,
    color: primary[800],
  },
  infoText: {
    fontSize: 14,
    lineHeight: 22,
    color: primary[700],
  },
  footer: {
    padding: 24,
    paddingBottom: 32,
    gap: 12,
  },
  skipButton: {
    paddingVertical: 12,
    alignItems: 'center',
  },
  skipButtonText: {
    color: neutral[500],
    fontSize: 16,
  },
  button: {
    backgroundColor: primary[700],
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
