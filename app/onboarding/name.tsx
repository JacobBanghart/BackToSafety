/**
 * Name Input Screen
 * Collect name and nickname (essential for 911 calls)
 */

import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { Colors, primary } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';
import { saveProfile } from '@/database/profile';

export default function NameScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');

  const handleContinue = async () => {
    if (!name.trim()) {
      setError('Name is required for emergency calls');
      return;
    }

    try {
      await saveProfile({
        name: name.trim(),
        nickname: nickname.trim() || undefined,
      });
      await completeStep('profile_name');
      router.push('/onboarding/photo' as any);
    } catch (err) {
      console.error('Error saving profile:', err);
      setError('Failed to save. Please try again.');
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <View style={styles.content}>
          <View style={styles.progress}>
            <View style={[styles.progressDot, styles.progressActive]} />
            <View style={styles.progressDot} />
            <View style={styles.progressDot} />
            <View style={styles.progressDot} />
          </View>

          <ThemedText type="title" style={styles.title}>
            Who are you caring for?
          </ThemedText>

          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
            This name will be used when calling 911 and alerting your circle.
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Full Name *</ThemedText>
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
                placeholder="e.g., Jane Smith"
                placeholderTextColor={theme.inputPlaceholder}
                autoFocus
                autoCapitalize="words"
                autoComplete="name"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Nickname / Goes By</ThemedText>
              <TextInput
                style={[
                  styles.input,
                  {
                    backgroundColor: theme.inputBackground,
                    borderColor: theme.inputBorder,
                    color: theme.text,
                  },
                ]}
                value={nickname}
                onChangeText={setNickname}
                placeholder="e.g., Mom, Grandma, Jane"
                placeholderTextColor={theme.inputPlaceholder}
                autoCapitalize="words"
              />
              <ThemedText style={[styles.hint, { color: theme.textDisabled }]}>
                What name do they respond to best?
              </ThemedText>
            </View>

            {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          </View>
        </View>

        <View style={styles.footer}>
          <Pressable
            style={[
              styles.button,
              { backgroundColor: theme.primary },
              !name.trim() && styles.buttonDisabled,
            ]}
            onPress={handleContinue}
            disabled={!name.trim()}
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
  content: {
    flex: 1,
    paddingHorizontal: Spacing.xl,
    paddingTop: 20,
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
    backgroundColor: primary[300],
    opacity: 0.4,
  },
  progressActive: {
    backgroundColor: primary[700],
    opacity: 1,
    width: 24,
  },
  title: {
    marginBottom: Spacing.sm,
  },
  subtitle: {
    ...Typography.body,
    marginBottom: Spacing.xxl,
  },
  form: {
    gap: Spacing.xl,
  },
  inputGroup: {
    gap: Spacing.sm,
  },
  label: {
    ...Typography.bodyBold,
  },
  input: {
    ...Typography.bodyLarge,
    borderWidth: 1,
    borderRadius: Radius.lg,
    paddingHorizontal: Spacing.lg,
    height: 48,
    paddingVertical: 0,
    lineHeight: 22,
  },
  hint: {
    ...Typography.body,
  },
  error: {
    ...Typography.body,
    color: '#ef4444',
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
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: '#fff',
    ...Typography.bodyLarge,
    fontWeight: '600',
  },
});
