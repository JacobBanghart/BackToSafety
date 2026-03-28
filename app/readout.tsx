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
import { getShadow } from '@/constants/Shadows';
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

  // Mobility values that imply a vehicle/aid to physically check nearby
  const VEHICLE_MOBILITY_VALUES = [
    'Motorized wheelchair',
    'Mobility scooter',
    'Bicycle',
    'Has vehicle',
    'Manual wheelchair',
    'Uses walker',
    'Uses cane',
  ];

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
      profile.mobilityLevel &&
      [
        'motorized wheelchair',
        'mobility scooter',
        'bicycle',
        'has vehicle',
        'manual wheelchair',
        'uses walker',
        'uses cane',
      ].some((v) => profile.mobilityLevel!.toLowerCase().includes(v))
        ? `⚠️ Check nearby for their mobility aid or vehicle — may indicate direction of travel`
        : undefined,
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
        <Pressable
          style={[styles.button, { backgroundColor: theme.primary }]}
          onPress={() => router.push('/onboarding')}
        >
          <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>
            Go to Onboarding
          </ThemedText>
        </Pressable>
      </SafeAreaView>
    );
  }

  const ls = lastSeen.time ? new Date(lastSeen.time).toLocaleString() : null;
  const gps = lastSeen.coords
    ? `${lastSeen.coords.lat.toFixed(5)}, ${lastSeen.coords.lon.toFixed(5)} (±${lastSeen.coords.accuracy ?? '—'}m)`
    : null;

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

        <ThemedText type="title" style={{ color: theme.text }}>
          Missing Person Description
        </ThemedText>

        {/* Call 911 Button - Most prominent */}
        <Pressable style={[styles.emergencyButton, getShadow('sm', colorScheme)]} onPress={call911}>
          <ThemedText style={styles.emergencyButtonText}>Call 911</ThemedText>
        </Pressable>

        {/* Identity Card */}
        <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
          <View style={styles.identityRow}>
            {profile.photoUri ? (
              <Image source={{ uri: profile.photoUri }} style={styles.photo} contentFit="cover" />
            ) : (
              <View style={[styles.photoPlaceholder, { backgroundColor: theme.primaryLight }]}>
                <IconSymbol name="person.fill" size={36} color={theme.primary} />
              </View>
            )}
            <View style={styles.identityInfo}>
              <ThemedText type="headline" style={{ color: theme.text }}>
                {profile.name}
              </ThemedText>
              {profile.nickname && (
                <ThemedText style={[styles.nickname, { color: theme.textSecondary }]}>
                  Goes by &ldquo;{profile.nickname}&rdquo;
                </ThemedText>
              )}
              {profile.dateOfBirth && (
                <View style={styles.infoChip}>
                  <IconSymbol name="calendar" size={12} color={theme.textSecondary} />
                  <ThemedText type="caption" style={{ color: theme.textSecondary }}>
                    DOB: {profile.dateOfBirth}
                  </ThemedText>
                </View>
              )}
            </View>
          </View>
        </View>

        {/* Last Seen Card */}
        {(ls || gps) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="clock.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                Last Known Location
              </ThemedText>
            </View>
            {ls && <InfoRow icon="clock" label="Time" value={ls} theme={theme} />}
            {gps && <InfoRow icon="location.fill" label="GPS" value={gps} theme={theme} />}
            {lastSeen.coords && (
              <Pressable
                style={[styles.mapsButton, { backgroundColor: semantic.success }]}
                onPress={openMaps}
              >
                <IconSymbol name="map.fill" size={16} color="#fff" />
                <ThemedText style={styles.mapsButtonText}>Open in Maps</ThemedText>
              </Pressable>
            )}
          </View>
        )}

        {/* Appearance Card */}
        {(profile.height ||
          profile.weight ||
          profile.hairColor ||
          profile.eyeColor ||
          profile.identifyingMarks) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="eye.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                Appearance
              </ThemedText>
            </View>
            <View style={styles.gridRow}>
              {profile.height && <InfoChip label="Height" value={profile.height} theme={theme} />}
              {profile.weight && <InfoChip label="Weight" value={profile.weight} theme={theme} />}
              {profile.hairColor && (
                <InfoChip label="Hair" value={profile.hairColor} theme={theme} />
              )}
              {profile.eyeColor && <InfoChip label="Eyes" value={profile.eyeColor} theme={theme} />}
              {profile.dominantHand && profile.dominantHand !== 'unknown' && (
                <InfoChip
                  label="Dominant Hand"
                  value={profile.dominantHand === 'left' ? 'Left' : 'Right'}
                  theme={theme}
                />
              )}
              {profile.mobilityLevel && (
                <InfoChip label="Mobility" value={profile.mobilityLevel} theme={theme} />
              )}
            </View>
            {profile.mobilityLevel &&
              VEHICLE_MOBILITY_VALUES.some((v) =>
                profile.mobilityLevel!.toLowerCase().includes(v.toLowerCase()),
              ) && (
                <View
                  style={[
                    styles.vehicleCheckNote,
                    {
                      backgroundColor: `${semantic.warning}15`,
                      borderColor: `${semantic.warning}40`,
                    },
                  ]}
                >
                  <IconSymbol
                    name="exclamationmark.triangle.fill"
                    size={14}
                    color={semantic.warning}
                  />
                  <ThemedText
                    style={[
                      styles.vehicleCheckText,
                      { color: colorScheme === 'dark' ? secondary[100] : neutral[700] },
                    ]}
                  >
                    Check nearby for their{' '}
                    {['vehicle', 'bicycle', 'bike', 'scooter'].some((w) =>
                      profile.mobilityLevel!.toLowerCase().includes(w),
                    )
                      ? 'vehicle or bike'
                      : 'mobility aid (walker, wheelchair, or cane)'}{' '}
                    — it may indicate where they went or provide shelter.
                  </ThemedText>
                </View>
              )}
            {profile.identifyingMarks && (
              <InfoRow
                icon="person.text.rectangle"
                label="Identifying Marks"
                value={profile.identifyingMarks}
                theme={theme}
              />
            )}
          </View>
        )}

        {/* Wearing reminder */}
        <View
          style={[
            styles.warnCard,
            { backgroundColor: `${semantic.warning}15`, borderColor: `${semantic.warning}40` },
          ]}
        >
          <IconSymbol name="exclamationmark.triangle.fill" size={16} color={semantic.warning} />
          <ThemedText
            style={[
              styles.warnText,
              { color: colorScheme === 'dark' ? secondary[100] : neutral[700] },
            ]}
          >
            Fill in: What are they wearing? (shirt, jacket, pants, shoes, hat)
          </ThemedText>
        </View>

        {/* Medical Card */}
        {(profile.medicalConditions ||
          profile.medications ||
          profile.allergies ||
          profile.cognitiveStatus) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="cross.fill" size={14} color={semantic.error} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: semantic.error }]}
              >
                Medical
              </ThemedText>
            </View>
            {profile.medicalConditions && (
              <InfoRow
                icon="heart.fill"
                label="Conditions"
                value={profile.medicalConditions}
                theme={theme}
              />
            )}
            {profile.medications && (
              <InfoRow
                icon="pills.fill"
                label="Medications"
                value={profile.medications}
                theme={theme}
              />
            )}
            {profile.allergies && (
              <InfoRow icon="allergens" label="Allergies" value={profile.allergies} theme={theme} />
            )}
            {profile.cognitiveStatus && (
              <InfoRow
                icon="brain.head.profile"
                label="Cognitive Status"
                value={profile.cognitiveStatus}
                theme={theme}
              />
            )}
          </View>
        )}

        {/* Approach & De-escalation Card */}
        {(profile.communicationPreference ||
          profile.deescalationTechniques ||
          profile.approachGuidance ||
          profile.likes ||
          profile.dislikesTriggers ||
          profile.safeWord) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="bubble.left.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                How to Approach
              </ThemedText>
            </View>
            {profile.communicationPreference && (
              <InfoRow
                icon="waveform"
                label="Communication"
                value={profile.communicationPreference}
                theme={theme}
              />
            )}
            {profile.approachGuidance && (
              <InfoRow
                icon="figure.walk.motion"
                label="Best Approach"
                value={profile.approachGuidance}
                theme={theme}
              />
            )}
            {profile.deescalationTechniques && (
              <InfoRow
                icon="hand.raised.fill"
                label="De-escalation"
                value={profile.deescalationTechniques}
                theme={theme}
              />
            )}
            {profile.likes && (
              <InfoRow
                icon="heart.fill"
                label="Likes / Comforts"
                value={profile.likes}
                theme={theme}
              />
            )}
            {profile.dislikesTriggers && (
              <InfoRow
                icon="exclamationmark.triangle.fill"
                label="Avoid / Triggers"
                value={profile.dislikesTriggers}
                theme={theme}
              />
            )}
            {profile.safeWord && (
              <InfoRow
                icon="key.fill"
                label="Family Safe Word"
                value={profile.safeWord}
                theme={theme}
              />
            )}
          </View>
        )}

        {/* Tracking & ID Card */}
        {(profile.locativeDeviceInfo || profile.idBracelets || profile.medicAlertId) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="location.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                Tracking &amp; ID
              </ThemedText>
            </View>
            {profile.locativeDeviceInfo && (
              <InfoRow
                icon="antenna.radiowaves.left.and.right"
                label="GPS Device"
                value={profile.locativeDeviceInfo}
                theme={theme}
              />
            )}
            {profile.idBracelets && (
              <InfoRow
                icon="person.badge.shield.checkmark.fill"
                label="ID Bracelet"
                value={profile.idBracelets}
                theme={theme}
              />
            )}
            {profile.medicAlertId && (
              <InfoRow
                icon="staroflife.fill"
                label="MedicAlert ID"
                value={profile.medicAlertId}
                theme={theme}
              />
            )}
          </View>
        )}

        {/* Emergency Contacts Card */}
        {emergencyContacts.length > 0 && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="phone.fill" size={14} color={semantic.success} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: semantic.success }]}
              >
                Emergency Contacts
              </ThemedText>
            </View>
            {emergencyContacts.map((c) => (
              <View key={c.id} style={[styles.contactRow, { borderTopColor: theme.border }]}>
                <View style={styles.contactInfo}>
                  <ThemedText type="bodyBold" style={{ color: theme.text }}>
                    {c.name}
                  </ThemedText>
                  <ThemedText type="caption" style={{ color: theme.textSecondary }}>
                    {c.relationship || c.role}
                  </ThemedText>
                </View>
                <Pressable
                  style={[styles.callButton, { backgroundColor: semantic.success }]}
                  onPress={() => Linking.openURL(`tel:${c.phone}`)}
                >
                  <IconSymbol name="phone.fill" size={14} color="#fff" />
                  <ThemedText style={styles.callButtonText}>
                    {formatPhoneNumber(c.phone)}
                  </ThemedText>
                </Pressable>
              </View>
            ))}
          </View>
        )}

        {/* Action Buttons */}
        <View style={styles.actionsCard}>
          <Pressable
            style={[styles.button, { backgroundColor: theme.primary }]}
            onPress={copyScript}
          >
            <IconSymbol name="doc.on.clipboard.fill" size={18} color="#fff" />
            <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>
              Copy 911 Script
            </ThemedText>
          </Pressable>
          <Pressable
            style={[
              styles.button,
              styles.buttonSecondary,
              { borderColor: theme.border, backgroundColor: theme.card },
            ]}
            onPress={copyAll}
          >
            <IconSymbol name="square.and.arrow.up" size={18} color={theme.text} />
            <ThemedText style={[styles.buttonText, { color: theme.text }]}>
              Copy Full Details
            </ThemedText>
          </Pressable>
        </View>

        {/* Silver Alert Info */}
        <View
          style={[
            styles.alertCard,
            {
              backgroundColor: colorScheme === 'dark' ? primary[900] : secondary[100],
              borderColor: colorScheme === 'dark' ? primary[700] : secondary[300],
            },
          ]}
        >
          <ThemedText
            type="bodyBold"
            style={{ color: colorScheme === 'dark' ? secondary[100] : primary[900] }}
          >
            Request a Silver Alert
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

// ── Sub-components ──────────────────────────────────────────────────────────

type ThemeColors = typeof Colors.light;

function InfoRow({
  icon,
  label,
  value,
  theme,
}: {
  icon: string;
  label: string;
  value: string;
  theme: ThemeColors;
}) {
  return (
    <View style={infoRowStyles.row}>
      <ThemedText type="caption" style={[infoRowStyles.label, { color: theme.textSecondary }]}>
        {label}
      </ThemedText>
      <ThemedText style={[infoRowStyles.value, { color: theme.text }]}>{value}</ThemedText>
    </View>
  );
}

function InfoChip({ label, value, theme }: { label: string; value: string; theme: ThemeColors }) {
  return (
    <View
      style={[infoChipStyles.chip, { backgroundColor: theme.surface, borderColor: theme.border }]}
    >
      <ThemedText type="small" style={{ color: theme.textSecondary }}>
        {label}
      </ThemedText>
      <ThemedText type="bodyBold" style={{ color: theme.text }}>
        {value}
      </ThemedText>
    </View>
  );
}

const infoRowStyles = StyleSheet.create({
  row: {
    paddingVertical: Spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(128,128,128,0.15)',
  },
  label: {
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 2,
  },
  value: {
    ...Typography.body,
    lineHeight: 22,
  },
});

const infoChipStyles = StyleSheet.create({
  chip: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    minWidth: 80,
  },
});

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
  emergencyButton: {
    backgroundColor: semantic.error,
    paddingVertical: Spacing.lg,
    borderRadius: Radius.lg,
    alignItems: 'center',
    shadowColor: semantic.error,
  },
  emergencyButtonText: {
    color: '#fff',
    ...Typography.bodyLarge,
    fontWeight: '700',
  },

  // Cards
  card: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: Radius.lg,
    padding: Spacing.lg,
    gap: Spacing.xs,
  },
  sectionLabel: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    marginBottom: Spacing.xs,
  },
  sectionLabelText: {
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    fontSize: 12,
  },

  // Identity
  identityRow: {
    flexDirection: 'row',
    gap: Spacing.md,
    alignItems: 'center',
  },
  photo: {
    width: 80,
    height: 80,
    borderRadius: Radius.md,
  },
  photoPlaceholder: {
    width: 80,
    height: 80,
    borderRadius: Radius.md,
    justifyContent: 'center',
    alignItems: 'center',
  },
  identityInfo: {
    flex: 1,
    gap: Spacing.xs,
  },
  nickname: {
    fontStyle: 'italic',
    ...Typography.body,
  },
  infoChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    marginTop: Spacing.xxs,
  },

  // Grid
  gridRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
    marginBottom: Spacing.xs,
  },

  // Mobility vehicle check note
  vehicleCheckNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.sm,
    padding: Spacing.md,
    borderRadius: Radius.lg,
    borderWidth: 1,
    marginTop: Spacing.sm,
  },
  vehicleCheckText: {
    flex: 1,
    ...Typography.body,
    lineHeight: 20,
  },

  // Warn card
  warnCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.sm,
    padding: Spacing.md,
    borderRadius: Radius.lg,
    borderWidth: 1,
  },
  warnText: {
    flex: 1,
    ...Typography.body,
    lineHeight: 20,
  },

  // Contacts
  contactRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: Spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: Spacing.md,
  },
  contactInfo: {
    flex: 1,
    gap: 2,
  },
  callButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
  },
  callButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },

  // Maps
  mapsButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.md,
    borderRadius: Radius.md,
    marginTop: Spacing.xs,
  },
  mapsButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },

  // Action buttons
  actionsCard: {
    gap: Spacing.sm,
  },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.md,
    borderRadius: Radius.md,
    minHeight: 48,
  },
  buttonSecondary: {
    borderWidth: 1,
  },
  buttonText: {
    fontWeight: '600',
    ...Typography.body,
  },

  // Silver Alert
  alertCard: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.lg,
    gap: Spacing.sm,
  },
  alertText: {
    ...Typography.body,
    lineHeight: 22,
  },
});
