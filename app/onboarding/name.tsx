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
import { neutral, primary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { saveProfile } from '@/database/profile';

export default function NameScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
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
    <SafeAreaView style={styles.container}>
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

          <ThemedText style={styles.subtitle}>
            This name will be used when calling 911 and alerting your circle.
          </ThemedText>

          <View style={styles.form}>
            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Full Name *</ThemedText>
              <TextInput
                style={styles.input}
                value={name}
                onChangeText={(text) => {
                  setName(text);
                  setError('');
                }}
                placeholder="e.g., Jane Smith"
                placeholderTextColor={neutral[400]}
                autoFocus
                autoCapitalize="words"
                autoComplete="name"
              />
            </View>

            <View style={styles.inputGroup}>
              <ThemedText style={styles.label}>Nickname / Goes By</ThemedText>
              <TextInput
                style={styles.input}
                value={nickname}
                onChangeText={setNickname}
                placeholder="e.g., Mom, Grandma, Jane"
                placeholderTextColor={neutral[400]}
                autoCapitalize="words"
              />
              <ThemedText style={styles.hint}>What name do they respond to best?</ThemedText>
            </View>

            {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          </View>
        </View>

        <View style={styles.footer}>
          <Pressable
            style={[styles.button, !name.trim() && styles.buttonDisabled]}
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
    paddingHorizontal: 24,
    paddingTop: 20,
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
    gap: 24,
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
    fontSize: 18,
    backgroundColor: '#fff',
  },
  hint: {
    fontSize: 14,
    opacity: 0.6,
  },
  error: {
    color: '#ef4444',
    fontSize: 14,
  },
  footer: {
    padding: 24,
    paddingBottom: 32,
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
