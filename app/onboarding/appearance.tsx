/**
 * Appearance Screen
 * Physical description for 911 dispatch
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
import { saveProfile } from '@/database/profile';

export default function AppearanceScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const [height, setHeight] = useState('');
  const [weight, setWeight] = useState('');
  const [hairColor, setHairColor] = useState('');
  const [eyeColor, setEyeColor] = useState('');
  const [identifyingMarks, setIdentifyingMarks] = useState('');

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
      router.push('/onboarding/contact' as any);
    } catch (err) {
      console.error('Error saving appearance:', err);
    }
  };

  const handleSkip = async () => {
    await completeStep('profile_appearance');
    router.push('/onboarding/contact' as any);
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
            <View style={[styles.progressDot, styles.progressActive]} />
            <View style={styles.progressDot} />
          </View>

          <ThemedText type="title" style={styles.title}>
            Physical description
          </ThemedText>

          <ThemedText style={styles.subtitle}>
            This helps 911 and searchers identify them. Be as specific as possible.
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.row}>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Height</ThemedText>
                <TextInput
                  style={styles.input}
                  value={height}
                  onChangeText={setHeight}
                  placeholder="5'6&quot;"
                  placeholderTextColor={neutral[400]}
                />
              </View>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Weight</ThemedText>
                <TextInput
                  style={styles.input}
                  value={weight}
                  onChangeText={setWeight}
                  placeholder="150 lbs"
                  placeholderTextColor={neutral[400]}
                  keyboardType="default"
                />
              </View>
            </View>

            <View style={styles.row}>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Hair Color</ThemedText>
                <TextInput
                  style={styles.input}
                  value={hairColor}
                  onChangeText={setHairColor}
                  placeholder="Gray, short"
                  placeholderTextColor={neutral[400]}
                />
              </View>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Eye Color</ThemedText>
                <TextInput
                  style={styles.input}
                  value={eyeColor}
                  onChangeText={setEyeColor}
                  placeholder="Blue"
                  placeholderTextColor={neutral[400]}
                />
              </View>
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Identifying Marks</ThemedText>
              <TextInput
                style={[styles.input, styles.textArea]}
                value={identifyingMarks}
                onChangeText={setIdentifyingMarks}
                placeholder="Tattoos, scars, birthmarks, glasses, hearing aids..."
                placeholderTextColor={neutral[400]}
                multiline
                numberOfLines={3}
              />
            </View>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <Pressable style={styles.skipButton} onPress={handleSkip}>
            <ThemedText style={styles.skipButtonText}>Skip for now</ThemedText>
          </Pressable>
          <Pressable style={styles.button} onPress={handleContinue}>
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
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  inputGroup: {
    gap: 8,
  },
  halfWidth: {
    flex: 1,
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
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
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
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
