/**
 * 911 Dispatch Read-out Screen
 * Displays all critical information for emergency calls
 * One-tap copy, maps integration, and Silver Alert guidance
 */

import { goBack } from '@/utils/navigation';
import { formatPhoneNumber } from '@/utils/phone';
import * as Clipboard from 'expo-clipboard';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, neutral, primary, secondary, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';

export default function ReadoutScreen() {
  const { profile, emergencyContacts, lastSeen, isLoading } = useProfile();
  const router = useRouter();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const [containerH, setContainerH] = useState(0);
  const [contentH, setContentH] = useState(0);

  // Build appearance string from profile fields
  const appearanceDesc = useMemo(() => {
    if (!profile) return '';
    const parts: string[] = [];
    if (profile.height) parts.push(profile.height);
    if (profile.weight) parts.push(profile.weight);
    if (profile.hairColor) parts.push(`${profile.hairColor} hair`);
    if (profile.eyeColor) parts.push(`${profile.eyeColor} eyes`);
    if (profile.identifyingMarks) parts.push(profile.identifyingMarks);
    return parts.join(', ');
  }, [profile]);

  // Build medical info string
  const medicalDesc = useMemo(() => {
    if (!profile) return '';
    const parts: string[] = [];
    if (profile.medicalConditions) parts.push(profile.medicalConditions);
    if (profile.allergies) parts.push(`Allergies: ${profile.allergies}`);
    return parts.join('. ');
  }, [profile]);

  const textBlock = useMemo(() => {
    if (!profile) return '';

    const ls = lastSeen.time ? new Date(lastSeen.time).toLocaleString() : 'Unknown';
    const gps = lastSeen.coords
      ? `${lastSeen.coords.lat.toFixed(5)}, ${lastSeen.coords.lon.toFixed(5)} (±${
          lastSeen.coords.accuracy ?? '—'
        }m)`
      : 'Unknown';

    return [
      `Name: ${profile.name}${profile.nickname ? ` (goes by "${profile.nickname}")` : ''}`,
      profile.dateOfBirth ? `DOB: ${profile.dateOfBirth}` : undefined,
      appearanceDesc ? `Appearance: ${appearanceDesc}` : undefined,
      medicalDesc ? `Medical Conditions: ${medicalDesc}` : undefined,
      profile.medications ? `Medications: ${profile.medications}` : undefined,
      profile.cognitiveStatus ? `Cognitive Status: ${profile.cognitiveStatus}` : undefined,
      profile.mobilityLevel ? `Mobility: ${profile.mobilityLevel}` : undefined,
      profile.communicationPreference
        ? `Communication: ${profile.communicationPreference}`
        : undefined,
      profile.dislikesTriggers ? `Triggers/Dislikes: ${profile.dislikesTriggers}` : undefined,
      profile.deescalationTechniques
        ? `De-escalation: ${profile.deescalationTechniques}`
        : undefined,
      profile.likes ? `Likes/Soothers: ${profile.likes}` : undefined,
      profile.approachGuidance ? `How to Approach: ${profile.approachGuidance}` : undefined,
      profile.safeWord ? `Family Safe Word: ${profile.safeWord}` : undefined,
      `Last seen: ${ls}`,
      `GPS: ${gps}`,
      profile.locativeDeviceInfo ? `Tracking Device: ${profile.locativeDeviceInfo}` : undefined,
      profile.idBracelets ? `ID Bracelet: ${profile.idBracelets}` : undefined,
      profile.medicAlertId ? `MedicAlert ID: ${profile.medicAlertId}` : undefined,
      '',
      '⚠️ FILL IN: What were they wearing? (Shirt, jacket, pants, shoes, hat)',
    ]
      .filter(Boolean)
      .join('\n');
  }, [profile, lastSeen, appearanceDesc, medicalDesc]);

  const script = useMemo(() => {
    if (!profile) return '';

    const ls = lastSeen.time ? new Date(lastSeen.time).toLocaleString() : 'Unknown time';
    const gpsText = lastSeen.coords
      ? `${lastSeen.coords.lat.toFixed(5)}, ${lastSeen.coords.lon.toFixed(5)}`
      : 'Unknown location';

    const age = profile.dateOfBirth
      ? `Age approximately ${new Date().getFullYear() - new Date(profile.dateOfBirth).getFullYear()}`
      : '';

    return `I'm reporting a missing vulnerable adult with dementia. Name: ${profile.name}. ${age}. Last seen: ${ls}. Location: ${gpsText}. Appearance: ${appearanceDesc || 'N/A'}. Medical conditions: ${medicalDesc || 'N/A'}. ${profile.medicAlertId ? `MedicAlert ID: ${profile.medicAlertId}.` : ''} Photo available. Please advise about issuing a local Silver/Purple Alert.`;
  }, [profile, lastSeen, appearanceDesc, medicalDesc]);

  const copyScript = async () => Clipboard.setStringAsync(script);
  const openMaps = () => {
    if (!lastSeen.coords) return;
    const { lat, lon } = lastSeen.coords;
    const url = Platform.select({
      ios: `http://maps.apple.com/?ll=${lat},${lon}`,
      default: `https://maps.google.com/?q=${lat},${lon}`,
    });
    Linking.openURL(url!);
  };

  const copyAll = async () => {
    await Clipboard.setStringAsync(textBlock);
  };

  const call911 = () => {
    Linking.openURL('tel:911');
  };

  const shouldScroll = contentH > containerH + 1;

  if (isLoading) {
    return (
      <SafeAreaView style={[styles.loadingContainer, { backgroundColor: theme.background }]}>
        <ActivityIndicator size="large" color={theme.primary} />
      </SafeAreaView>
    );
  }

  if (!profile) {
    return (
      <SafeAreaView style={[styles.loadingContainer, { backgroundColor: theme.background }]}>
        <ThemedText>No profile found. Please complete onboarding first.</ThemedText>
        <Pressable style={[styles.button, { backgroundColor: theme.primary }]} onPress={() => router.push('/onboarding')}>
          <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>Go to Onboarding</ThemedText>
        </Pressable>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView
      style={{ flex: 1, backgroundColor: theme.background }}
      edges={['top', 'left', 'right', 'bottom']}
      onLayout={(e) => setContainerH(e.nativeEvent.layout.height)}
    >
      <ScrollView
        contentContainerStyle={styles.container}
        onContentSizeChange={(_, h) => setContentH(h)}
        scrollEnabled={shouldScroll}
        bounces={shouldScroll}
        showsVerticalScrollIndicator={shouldScroll}
        overScrollMode={shouldScroll ? 'auto' : 'never'}
      >
        <Pressable onPress={() => goBack('/(tabs)')} style={styles.backLink}>
          <IconSymbol name="chevron.left" size={20} color={theme.tint} />
          <ThemedText style={[styles.backText, { color: theme.tint }]}>Back</ThemedText>
        </Pressable>

        <ThemedText type="title" style={[styles.title, { color: theme.text }]}>
          Missing Person Description
        </ThemedText>

        {/* Call 911 Button - Most prominent */}
        <Pressable style={styles.emergencyButton} onPress={call911}>
          <ThemedText style={styles.emergencyButtonText}>📞 Call 911</ThemedText>
        </Pressable>

        <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
          {/* Photo and basic info */}
          <View style={styles.headerRow}>
            {profile.photoUri && (
              <Image source={{ uri: profile.photoUri }} style={styles.photo} contentFit="cover" />
            )}
            <View style={styles.headerInfo}>
              <ThemedText type="title" style={{ color: theme.text }}>{profile.name}</ThemedText>
              {profile.nickname && (
                <ThemedText style={[styles.nickname, { color: theme.textSecondary }]}>
                  Goes by &ldquo;{profile.nickname}&rdquo;
                </ThemedText>
              )}
            </View>
          </View>

          {/* Full info block */}
          <ThemedText style={[styles.block, { color: theme.text }]}>{textBlock}</ThemedText>

          {/* Emergency Contacts */}
          {emergencyContacts.length > 0 && (
            <View style={[styles.contactsSection, { borderTopColor: theme.border }]}>
              <ThemedText type="bodyBold" style={[styles.sectionLabel, { color: theme.primary }]}>
                Emergency Contacts:
              </ThemedText>
              {emergencyContacts.map((c) => (
                <View key={c.id} style={styles.contactRow}>
                  <ThemedText style={[styles.contactText, { color: theme.text }]}>
                    {c.name} ({c.relationship || c.role})
                  </ThemedText>
                  <Pressable onPress={() => Linking.openURL(`tel:${c.phone}`)}>
                    <ThemedText style={[styles.phoneLink, { color: theme.tint }]}>
                      {formatPhoneNumber(c.phone)}
                    </ThemedText>
                  </Pressable>
                </View>
              ))}
            </View>
          )}

          {lastSeen.coords && (
            <Pressable style={[styles.button, styles.mapsButton]} onPress={openMaps}>
              <ThemedText style={styles.buttonText}>📍 Open Last Seen in Maps</ThemedText>
            </Pressable>
          )}

          <Pressable style={[styles.button, { backgroundColor: theme.primary }]} onPress={copyScript}>
            <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>📋 Copy 911 Script</ThemedText>
          </Pressable>

          <Pressable style={[styles.button, styles.secondary, { backgroundColor: theme.primaryPressed }]} onPress={copyAll}>
            <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>📄 Copy Full Details</ThemedText>
          </Pressable>
        </View>

        {/* Silver Alert Info */}
        <View
          style={[
            styles.card,
            styles.alertCard,
            {
              backgroundColor: colorScheme === 'dark' ? primary[900] : secondary[100],
              borderColor: colorScheme === 'dark' ? primary[700] : secondary[300],
            },
          ]}
        >
          <ThemedText
            type="bodyBold"
            style={[
              { color: colorScheme === 'dark' ? secondary[100] : primary[900] },
            ]}
          >
            💜 Request a Silver Alert
          </ThemedText>
          <ThemedText
            style={[
              styles.alertText,
              { color: colorScheme === 'dark' ? neutral[300] : neutral[700] },
            ]}
          >
            Ask the 911 dispatcher about issuing a Silver Alert (or Purple Alert/Feather Alert
            depending on your state). This broadcasts the missing person information to the public
            and media.
          </ThemedText>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.xl,
    gap: Spacing.lg,
  },
  container: {
    padding: Spacing.lg,
    gap: Spacing.md,
  },
  backLink: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    paddingVertical: Spacing.xs,
    marginBottom: Spacing.xxs,
    gap: Spacing.xs,
  },
  backText: {
    fontWeight: '600',
  },
  title: {
  },
  emergencyButton: {
    backgroundColor: semantic.error,
    paddingVertical: Spacing.lg,
    borderRadius: Radius.lg,
    alignItems: 'center',
    shadowColor: semantic.error,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: Spacing.sm,
    elevation: 4,
  },
  emergencyButtonText: {
    color: '#fff',
    fontSize: 20,
    fontWeight: '700',
  },
  card: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: Radius.lg,
    padding: Spacing.lg,
    gap: Spacing.md,
  },
  headerRow: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  photo: {
    width: 80,
    height: 80,
    borderRadius: Radius.md,
  },
  headerInfo: {
    flex: 1,
    justifyContent: 'center',
  },
  name: {
  },
  nickname: {
    fontStyle: 'italic',
    fontSize: 15,
  },
  block: {
    fontFamily: 'System',
    fontSize: 15,
    lineHeight: 22,
  },
  sectionLabel: {
    marginBottom: Spacing.xs,
  },
  contactsSection: {
    borderTopWidth: 1,
    paddingTop: Spacing.md,
    gap: Spacing.sm,
  },
  contactRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  contactText: {
    fontSize: 15,
  },
  phoneLink: {
    fontWeight: '600',
    fontSize: 15,
  },
  button: {
    marginTop: Spacing.xs,
    paddingVertical: Spacing.md,
    borderRadius: Radius.md,
    alignItems: 'center',
  },
  secondary: {
    // backgroundColor set inline via theme.primaryPressed
  },
  mapsButton: {
    backgroundColor: semantic.success,
  },
  buttonText: {
    color: 'white',
    fontWeight: '600',
    fontSize: 15,
  },
  alertCard: {
    // Background set dynamically
  },
  alertText: {
    fontSize: 15,
    lineHeight: 20,
    marginTop: Spacing.xs,
  },
});
