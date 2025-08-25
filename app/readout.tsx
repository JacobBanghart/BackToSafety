import * as Clipboard from 'expo-clipboard';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { Linking, Platform, Pressable, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useProfile } from '@/context/ProfileContext';
import { useColorScheme } from '@/hooks/useColorScheme';

export default function ReadoutScreen() {
  const { profile, lastSeen } = useProfile();
  const router = useRouter();
  const colorScheme = useColorScheme() ?? 'light';
  const [containerH, setContainerH] = useState(0);
  const [contentH, setContentH] = useState(0);

  const textBlock = useMemo(() => {
    const ls = lastSeen.time ? new Date(lastSeen.time).toLocaleString() : 'Unknown';
    const gps = lastSeen.coords
      ? `${lastSeen.coords.lat.toFixed(5)}, ${lastSeen.coords.lon.toFixed(5)} (±${
          lastSeen.coords.accuracy ?? '—'
        }m)`
      : 'Unknown';
    return [
      `Name: ${profile.name}`,
      profile.age ? `Age: ${profile.age}` : undefined,
      profile.appearance ? `Appearance: ${profile.appearance}` : undefined,
      profile.medicalInfo ? `Medical: ${profile.medicalInfo}` : undefined,
      profile.meds ? `Meds: ${profile.meds}` : undefined,
      profile.cognitiveStatus ? `Cognitive: ${profile.cognitiveStatus}` : undefined,
      profile.triggers ? `Triggers: ${profile.triggers}` : undefined,
      profile.soothers ? `Soothers: ${profile.soothers}` : undefined,
      `Last seen: ${ls}`,
      `GPS: ${gps}`,
      profile.likelyDestinations && profile.likelyDestinations.length
        ? `Likely destinations: ${profile.likelyDestinations.join(', ')}`
        : undefined,
    ]
      .filter(Boolean)
      .join('\n');
  }, [profile, lastSeen]);

  const script = useMemo(() => {
    const ls = lastSeen.time ? new Date(lastSeen.time).toLocaleString() : 'Unknown time';
    const gpsText = lastSeen.coords
      ? `${lastSeen.coords.lat.toFixed(5)}, ${lastSeen.coords.lon.toFixed(5)}`
      : 'Unknown location';
    return `I'm reporting a missing vulnerable adult with dementia. Name: ${profile.name}. Last seen: ${ls}. Location: ${gpsText}. Appearance: ${profile.appearance ?? 'N/A'}. Risks: ${profile.medicalInfo ?? 'N/A'}. Photo and MedicAlert ID available. Please advise about issuing a local Silver/Purple Alert.`;
  }, [profile, lastSeen]);

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

  const shouldScroll = contentH > containerH + 1;

  return (
    <SafeAreaView
      style={{ flex: 1 }}
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
        <Pressable onPress={() => router.back()} style={styles.backLink}>
          <ThemedText style={styles.backText}>Back</ThemedText>
        </Pressable>
        <ThemedText type="title">Dispatch Read-out</ThemedText>
        <ThemedView
          style={[
            styles.card,
            { borderColor: Colors[colorScheme].border, backgroundColor: Colors[colorScheme].card },
          ]}
        >
          {profile.photo && (
            <Image
              source={profile.photo}
              style={{ width: 96, height: 96, borderRadius: 8, alignSelf: 'flex-start' }}
              contentFit="cover"
            />
          )}
          <ThemedText style={styles.block}>{textBlock}</ThemedText>
          {lastSeen.coords && (
            <Pressable style={[styles.button, styles.alt]} onPress={openMaps}>
              <ThemedText style={styles.buttonText}>Open Last Seen in Maps</ThemedText>
            </Pressable>
          )}
          <Pressable style={styles.button} onPress={copyScript}>
            <ThemedText style={styles.buttonText}>Copy 911 Script</ThemedText>
          </Pressable>
          <Pressable style={[styles.button, styles.secondary]} onPress={copyAll}>
            <ThemedText style={styles.buttonText}>Copy for 911</ThemedText>
          </Pressable>
        </ThemedView>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    gap: 12,
  },
  backLink: {
    alignSelf: 'flex-start',
    paddingVertical: 4,
    paddingHorizontal: 2,
    marginBottom: 2,
  },
  backText: {
    color: '#0a7ea4',
    fontWeight: '600',
  },
  card: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 12,
    padding: 12,
    gap: 12,
  },
  block: {
    fontFamily: 'System',
    lineHeight: 20,
  },
  button: {
    marginTop: 4,
    backgroundColor: '#0a7ea4',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  secondary: {
    backgroundColor: '#2A6B80',
  },
  alt: {
    backgroundColor: '#2ecc71',
  },
  buttonText: { color: 'white', fontWeight: '600' },
});
