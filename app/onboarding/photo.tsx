/**
 * Photo Upload Screen
 * Critical for search - recent photo helps neighbors/responders identify person
 */

import { File, Paths } from 'expo-file-system';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { neutral, primary } from '@/constants/Colors';
import { useOnboarding } from '@/context/OnboardingContext';
import { saveProfile } from '@/database/profile';

export default function PhotoScreen() {
  const router = useRouter();
  const { completeStep } = useOnboarding();
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
      router.push('/onboarding/appearance' as any);
    } catch (err) {
      console.error('Error saving photo:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSkip = async () => {
    await completeStep('profile_photo');
    router.push('/onboarding/appearance' as any);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.progress}>
          <View style={[styles.progressDot, styles.progressComplete]} />
          <View style={[styles.progressDot, styles.progressActive]} />
          <View style={styles.progressDot} />
          <View style={styles.progressDot} />
        </View>

        <ThemedText type="title" style={styles.title}>
          Add a recent photo
        </ThemedText>

        <ThemedText style={styles.subtitle}>
          A clear, recent photo is critical for neighbors and first responders to identify them
          quickly.
        </ThemedText>

        <View style={styles.photoContainer}>
          {photoUri ? (
            <Image source={{ uri: photoUri }} style={styles.photo} contentFit="cover" />
          ) : (
            <View style={styles.photoPlaceholder}>
              <ThemedText style={styles.photoPlaceholderText}>📷</ThemedText>
              <ThemedText style={styles.photoPlaceholderHint}>No photo yet</ThemedText>
            </View>
          )}
        </View>

        <View style={styles.photoButtons}>
          <Pressable style={styles.photoButton} onPress={takePhoto}>
            <ThemedText style={styles.photoButtonText}>Take Photo</ThemedText>
          </Pressable>
          <Pressable style={styles.photoButton} onPress={pickImage}>
            <ThemedText style={styles.photoButtonText}>Choose from Library</ThemedText>
          </Pressable>
        </View>

        <ThemedText style={styles.tip}>
          💡 Tip: Use a photo from the last 3-6 months that shows their face clearly.
        </ThemedText>
      </View>

      <View style={styles.footer}>
        <Pressable style={styles.skipButton} onPress={handleSkip}>
          <ThemedText style={styles.skipButtonText}>Skip for now</ThemedText>
        </Pressable>
        <Pressable
          style={[styles.button, !photoUri && styles.buttonDisabled]}
          onPress={handleContinue}
          disabled={!photoUri || isLoading}
        >
          <ThemedText style={styles.buttonText}>{isLoading ? 'Saving...' : 'Continue'}</ThemedText>
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
    marginBottom: 24,
  },
  photoContainer: {
    alignItems: 'center',
    marginBottom: 24,
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
    backgroundColor: neutral[200],
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: neutral[300],
    borderStyle: 'dashed',
  },
  photoPlaceholderText: {
    fontSize: 48,
  },
  photoPlaceholderHint: {
    marginTop: 8,
    color: neutral[500],
  },
  photoButtons: {
    gap: 12,
    marginBottom: 24,
  },
  photoButton: {
    borderWidth: 1,
    borderColor: primary[700],
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  photoButtonText: {
    color: primary[700],
    fontSize: 16,
    fontWeight: '600',
  },
  tip: {
    fontSize: 14,
    color: neutral[600],
    textAlign: 'center',
    fontStyle: 'italic',
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
