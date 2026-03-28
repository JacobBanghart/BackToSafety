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
import { Colors } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';
import { saveProfile } from '@/database/profile';

export default function AppearanceScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
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
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          <View style={styles.progress}>
            <View style={[styles.progressDot, { backgroundColor: theme.primary }]} />
            <View style={[styles.progressDot, { backgroundColor: theme.primary }]} />
            <View
              style={[
                styles.progressDot,
                styles.progressActive,
                { backgroundColor: theme.primary },
              ]}
            />
            <View style={[styles.progressDot, { backgroundColor: theme.border }]} />
          </View>

          <ThemedText type="title" style={styles.title}>
            Physical description
          </ThemedText>

          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
            This helps 911 and searchers identify them. Be as specific as possible.
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.row}>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Height</ThemedText>
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
                  onChangeText={setHeight}
                  placeholder="5'6&quot;"
                  placeholderTextColor={theme.inputPlaceholder}
                />
              </View>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Weight</ThemedText>
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
                  onChangeText={setWeight}
                  placeholder="150 lbs"
                  placeholderTextColor={theme.inputPlaceholder}
                  keyboardType="default"
                />
              </View>
            </View>

            <View style={styles.row}>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Hair Color</ThemedText>
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
                  placeholder="Gray, short"
                  placeholderTextColor={theme.inputPlaceholder}
                />
              </View>
              <View style={[styles.inputGroup, styles.halfWidth]}>
                <ThemedText style={styles.label}>Eye Color</ThemedText>
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
                  placeholder="Blue"
                  placeholderTextColor={theme.inputPlaceholder}
                />
              </View>
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Identifying Marks</ThemedText>
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
                placeholder="Tattoos, scars, birthmarks, glasses, hearing aids..."
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
              Skip for now
            </ThemedText>
          </Pressable>
          <Pressable
            style={[styles.button, { backgroundColor: theme.primary }]}
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
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
