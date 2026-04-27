/**
 * Photo Upload Screen
 * Critical for search - recent photo helps neighbors/responders identify person
 */

import { File, Paths } from 'expo-file-system';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { Href, useRouter } from 'expo-router';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
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

export default function PhotoScreen() {
  const router = useRouter();
  const { t } = useTranslation('onboarding');
  const { completeStep } = useOnboarding();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      alert('We need photo library access to save a photo for emergencies.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });

    if (!result.canceled && result.assets[0]) {
      await savePhotoLocally(result.assets[0].uri);
    }
  };

  const takePhoto = async () => {
    if (Platform.OS === 'web') {
      alert(t('photo.webCameraUnavailable'));
      return;
    }

    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== 'granted') {
      alert('We need camera access to take a photo.');
      return;
    }

    const result = await ImagePicker.launchCameraAsync({
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });

    if (!result.canceled && result.assets[0]) {
      await savePhotoLocally(result.assets[0].uri);
    }
  };

  const savePhotoLocally = async (uri: string) => {
    try {
      if (Platform.OS === 'web') {
        // On web, just use the blob URI directly (it's already local to the browser)
        setPhotoUri(uri);
        return;
      }

      // On native, save to app's document directory for persistence
      const filename = `profile_photo_${Date.now()}.jpg`;
      const sourceFile = new File(uri);
      const destFile = new File(Paths.document, filename);

      await sourceFile.copy(destFile);

      setPhotoUri(destFile.uri);
    } catch (error) {
      console.error('Error saving photo:', error);
      alert('Failed to save photo. Please try again.');
    }
  };

  const handleContinue = async () => {
    setIsLoading(true);
    try {
      if (photoUri) {
        await saveProfile({ photoUri });
      }
      await completeStep('profile_photo');
      router.push('/onboarding/appearance' as Href);
    } catch (err) {
      console.error('Error saving photo:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSkip = async () => {
    await completeStep('profile_photo');
    router.push('/onboarding/appearance' as Href);
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.content}>
        <OnboardingStepHeader activeStep={2} totalSteps={4} />

        <ThemedText type="title" style={styles.title}>
          {t('photo.title')}
        </ThemedText>

        <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>
          {t('photo.subtitle')}
        </ThemedText>

        <View style={styles.photoContainer}>
          {photoUri ? (
            <Image source={{ uri: photoUri }} style={styles.photo} contentFit="cover" />
          ) : (
            <View
              style={[
                styles.photoPlaceholder,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <ThemedText style={styles.photoPlaceholderText}>📷</ThemedText>
              <ThemedText style={[styles.photoPlaceholderHint, { color: theme.textSecondary }]}>
                {t('photo.noPhoto')}
              </ThemedText>
            </View>
          )}
        </View>

        <View style={styles.photoButtons}>
          <Pressable
            style={[styles.photoButton, { borderColor: theme.primary }]}
            onPress={takePhoto}
          >
            <ThemedText style={[styles.photoButtonText, { color: theme.primary }]}>
              {t('photo.takePhoto')}
            </ThemedText>
          </Pressable>
          <Pressable
            style={[styles.photoButton, { borderColor: theme.primary }]}
            onPress={pickImage}
          >
            <ThemedText style={[styles.photoButtonText, { color: theme.primary }]}>
              {t('photo.chooseLibrary')}
            </ThemedText>
          </Pressable>
        </View>

        <ThemedText style={[styles.tip, { color: theme.textDisabled }]}>
          {t('photo.tip')}
        </ThemedText>
      </View>

      <View style={styles.footer}>
        <Pressable
          testID="onboarding-photo-skip"
          accessibilityLabel="onboarding-photo-skip"
          style={styles.skipButton}
          onPress={handleSkip}
        >
          <ThemedText style={[styles.skipButtonText, { color: theme.textDisabled }]}>
            {t('photo.skip')}
          </ThemedText>
        </Pressable>
        <Pressable
          style={[
            styles.button,
            { backgroundColor: theme.primary },
            !photoUri && styles.buttonDisabled,
          ]}
          onPress={handleContinue}
          disabled={!photoUri || isLoading}
        >
          <ThemedText style={styles.buttonText}>
            {isLoading ? 'Saving...' : t('photo.continue')}
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
    paddingTop: 20,
  },
  title: {
    marginBottom: Spacing.sm,
  },
  subtitle: {
    ...Typography.body,
    marginBottom: Spacing.xl,
  },
  photoContainer: {
    alignItems: 'center',
    marginBottom: Spacing.xl,
  },
  photo: {
    width: 200,
    height: 200,
    borderRadius: 100,
  },
  photoPlaceholder: {
    width: 200,
    height: 200,
    borderRadius: 100,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderStyle: 'dashed',
  },
  photoPlaceholderText: {
    fontSize: 48,
    lineHeight: 56,
    paddingTop: Spacing.xs,
  },
  photoPlaceholderHint: {
    marginTop: Spacing.sm,
  },
  photoButtons: {
    gap: Spacing.md,
    marginBottom: Spacing.xl,
  },
  photoButton: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    paddingVertical: 14,
    alignItems: 'center',
  },
  photoButtonText: {
    ...Typography.bodyBold,
  },
  tip: {
    fontSize: 14,
    textAlign: 'center',
    fontStyle: 'italic',
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
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
