/**
 * Name Input Screen
 * Collect name and nickname (essential for 911 calls)
 */

import { Href, useRouter } from 'expo-router';
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

import { OnboardingStepHeader } from '@/components/OnboardingStepHeader';
import { ThemedText } from '@/components/ThemedText';
import { Colors } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useOnboarding } from '@/context/OnboardingContext';
import { useTheme } from '@/context/ThemeContext';
import { saveProfile } from '@/database/profile';
import { useTranslation } from 'react-i18next';

export default function NameScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
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
      router.push('/onboarding/photo' as Href);
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
          <OnboardingStepHeader activeStep={1} totalSteps={4} />

          <ThemedText type="title" style={styles.title}>
            {t('name.title')}
          </ThemedText>

          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
            {t('name.subtitle')}
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>{t('name.nameLabel')}</ThemedText>
              <TextInput
                testID="onboarding-name-input"
                accessibilityLabel="onboarding-name-input"
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
                placeholder={t('name.namePlaceholder')}
                placeholderTextColor={theme.inputPlaceholder}
                autoFocus
                autoCapitalize="words"
                autoComplete="name"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>{t('name.nicknameLabel')}</ThemedText>
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
                placeholder={t('name.nicknamePlaceholder')}
                placeholderTextColor={theme.inputPlaceholder}
                autoCapitalize="words"
              />
              <ThemedText style={[styles.hint, { color: theme.textDisabled }]}>
                {t('name.nicknameHint')}
              </ThemedText>
            </View>

            {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          </View>
        </View>

        <View style={styles.footer}>
          <Pressable
            testID="onboarding-name-continue"
            accessibilityLabel="onboarding-name-continue"
            style={[
              styles.button,
              { backgroundColor: theme.primary },
              !name.trim() && styles.buttonDisabled,
            ]}
            onPress={handleContinue}
            disabled={!name.trim()}
          >
            <ThemedText style={styles.buttonText}>{t('name.continue')}</ThemedText>
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
    padding: Spacing.lg,
    paddingBottom: Spacing.lg,
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
    color: '#fff',
    ...Typography.bodyLarge,
    fontWeight: '600',
  },
});
