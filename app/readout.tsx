/**
 * 911 Dispatch Read-out Screen
 * Displays all critical information for emergency calls
 * One-tap copy, maps integration, and Silver Alert guidance
 */

import { track } from '@/utils/analytics';
import { goBack } from '@/utils/navigation';
import { now } from '@/utils/clock';
import { describeMobility } from '@/utils/mobility';
import { formatPhoneNumber, stripPhoneFormatting } from '@/utils/phone';
import * as Clipboard from 'expo-clipboard';
import { Image } from 'expo-image';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ScreenHeader } from '@/components/ScreenHeader';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, neutral, primary, secondary, semantic } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { Radius, Spacing } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import {
  buildCopyBlock,
  buildScript,
  missingScriptDetails,
  needsVehicleCheck,
  vehicleCheckKind,
  type ReadoutInput,
} from '@/utils/readout';

export default function ReadoutScreen() {
  const { profile, emergencyContacts, lastSeen, isLoading } = useProfile();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const { t } = useTranslation('readout');
  const [isScriptExpanded, setIsScriptExpanded] = useState(false);
  const [copiedType, setCopiedType] = useState<'script' | 'all' | null>(null);
  const copiedTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const readoutInput = useMemo<ReadoutInput | null>(
    () =>
      profile
        ? {
            profile,
            lastSeenTime: lastSeen.time ? new Date(lastSeen.time).toLocaleString() : undefined,
            today: new Date(now()),
          }
        : null,
    [profile, lastSeen],
  );
  const textBlock = useMemo(
    () => (readoutInput ? buildCopyBlock(readoutInput, t) : ''),
    [readoutInput, t],
  );
  const script = useMemo(
    () => (readoutInput ? buildScript(readoutInput, t) : ''),
    [readoutInput, t],
  );
  const missingDetails = useMemo(
    () => (readoutInput ? missingScriptDetails(readoutInput, t) : []),
    [readoutInput, t],
  );

  useEffect(() => {
    return () => {
      if (copiedTimeoutRef.current) {
        clearTimeout(copiedTimeoutRef.current);
      }
    };
  }, []);

  const showCopyConfirmation = (type: 'script' | 'all') => {
    setCopiedType(type);

    if (copiedTimeoutRef.current) {
      clearTimeout(copiedTimeoutRef.current);
    }

    copiedTimeoutRef.current = setTimeout(() => {
      setCopiedType(null);
      copiedTimeoutRef.current = null;
    }, 1800);
  };

  const copyScript = async () => {
    try {
      await Clipboard.setStringAsync(script);
      track('readout_script_copied');
      showCopyConfirmation('script');
    } catch {
      Alert.alert(t('copyFailed'), t('copyScriptFailed'));
    }
  };
  const copyAll = async () => {
    try {
      await Clipboard.setStringAsync(textBlock);
      track('readout_details_copied');
      showCopyConfirmation('all');
    } catch {
      Alert.alert(t('copyFailed'), t('copyDetailsFailed'));
    }
  };

  const call911 = () => {
    track('readout_911_called');
    Linking.openURL(`tel:${t('emergencyNumber', { ns: 'common' })}`);
  };

  if (isLoading) {
    return (
      <SafeAreaView style={[styles.loadingContainer, { backgroundColor: theme.background }]}>
        <ActivityIndicator size="large" color={theme.primary} />
      </SafeAreaView>
    );
  }

  // Onboarding always creates the profile; this only covers a render before it loads.
  if (!profile) return null;

  const ls = lastSeen.time ? new Date(lastSeen.time).toLocaleString() : null;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['top']}>
      <ScreenHeader
        testID="readout"
        title={t('screenTitle')}
        onBack={() => {
          track('screen_viewed', { screen: 'home', source: 'readout_back' });
          goBack('/');
        }}
      />
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        {/* Call 911 Button - Most prominent */}
        <Pressable
          style={[styles.emergencyButton, getShadow('sm', colorScheme)]}
          testID="readout-call-911"
          onPress={call911}
        >
          <ThemedText style={styles.emergencyButtonText}>{t('callButton')}</ThemedText>
        </Pressable>

        {/* 911 Script Card */}
        <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
          <Pressable
            style={styles.scriptHeaderButton}
            testID="readout-script-toggle"
            onPress={() => setIsScriptExpanded((prev) => !prev)}
          >
            <View style={styles.sectionLabel}>
              <IconSymbol name="phone.connection.fill" size={14} color={semantic.success} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: semantic.success }]}
              >
                {t('sections.script.label')}
              </ThemedText>
            </View>
            <IconSymbol
              name={isScriptExpanded ? 'chevron.up' : 'chevron.down'}
              size={16}
              color={theme.textSecondary}
            />
          </Pressable>
          {isScriptExpanded ? (
            <>
              <ThemedText style={[styles.scriptHint, { color: theme.textSecondary }]}>
                {t('sections.script.hint')}
              </ThemedText>
              <ThemedText
                testID="readout-script-text"
                style={[styles.scriptText, { color: theme.text }]}
              >
                {script}
              </ThemedText>
              {missingDetails.length > 0 && (
                <ThemedText
                  testID="readout-script-missing"
                  style={[styles.scriptMissingText, { color: semantic.warning }]}
                >
                  {t('sections.script.missingDetails', {
                    details: missingDetails.join(', '),
                  })}
                </ThemedText>
              )}
            </>
          ) : (
            <ThemedText style={[styles.scriptHint, { color: theme.textSecondary }]}>
              {t('sections.script.collapsed')}
            </ThemedText>
          )}
        </View>

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
              <ThemedText
                type="headline"
                style={{ color: theme.text }}
                numberOfLines={1}
                ellipsizeMode="tail"
              >
                {profile.name}
              </ThemedText>
              {profile.nickname && (
                <ThemedText
                  style={[styles.nickname, { color: theme.textSecondary }]}
                  numberOfLines={1}
                  ellipsizeMode="tail"
                >
                  {t('sections.identity.goesBy', { nickname: profile.nickname })}
                </ThemedText>
              )}
              {profile.dateOfBirth && (
                <View style={styles.infoChip}>
                  <IconSymbol name="calendar" size={12} color={theme.textSecondary} />
                  <ThemedText type="caption" style={{ color: theme.textSecondary }}>
                    {t('sections.identity.dob', { dob: profile.dateOfBirth })}
                  </ThemedText>
                </View>
              )}
            </View>
          </View>
        </View>

        {/* Last Seen Card */}
        {ls && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="clock.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                {t('sections.location.title')}
              </ThemedText>
            </View>
            <InfoRow icon="clock" label={t('sections.location.time')} value={ls} theme={theme} />
          </View>
        )}

        {/* Appearance Card */}
        {/* Mobility and dominant hand live here too, so they alone must show the card (F-26). */}
        {(profile.height ||
          profile.weight ||
          profile.hairColor ||
          profile.eyeColor ||
          profile.identifyingMarks ||
          (profile.dominantHand && profile.dominantHand !== 'unknown') ||
          profile.mobilityLevel) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="eye.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                {t('sections.appearance.title')}
              </ThemedText>
            </View>
            <View style={styles.gridRow}>
              {profile.height && (
                <InfoChip
                  label={t('sections.appearance.height')}
                  value={profile.height}
                  theme={theme}
                />
              )}
              {profile.weight && (
                <InfoChip
                  label={t('sections.appearance.weight')}
                  value={profile.weight}
                  theme={theme}
                />
              )}
              {profile.hairColor && (
                <InfoChip
                  label={t('sections.appearance.hair')}
                  value={profile.hairColor}
                  theme={theme}
                />
              )}
              {profile.eyeColor && (
                <InfoChip
                  label={t('sections.appearance.eyes')}
                  value={profile.eyeColor}
                  theme={theme}
                />
              )}
              {profile.dominantHand && profile.dominantHand !== 'unknown' && (
                <InfoChip
                  label={t('sections.appearance.dominantHand')}
                  value={
                    profile.dominantHand === 'left'
                      ? t('sections.appearance.handLeft')
                      : t('sections.appearance.handRight')
                  }
                  theme={theme}
                />
              )}
              {profile.mobilityLevel && (
                <InfoChip
                  label={t('sections.appearance.mobility')}
                  value={describeMobility(profile.mobilityLevel, t)}
                  theme={theme}
                />
              )}
            </View>
            {needsVehicleCheck(profile.mobilityLevel) && (
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
                  {t(`vehicleCheck.${vehicleCheckKind(profile.mobilityLevel!)}`)}
                </ThemedText>
              </View>
            )}
            {profile.identifyingMarks && (
              <InfoRow
                icon="person.text.rectangle"
                label={t('sections.appearance.identifyingMarks')}
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
            {t('wearingCard')}
          </ThemedText>
        </View>

        {/* Important Details Card */}
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
                {t('sections.medical.title')}
              </ThemedText>
            </View>
            {profile.medicalConditions && (
              <InfoRow
                icon="heart.fill"
                label={t('sections.medical.conditions')}
                value={profile.medicalConditions}
                theme={theme}
              />
            )}
            {profile.medications && (
              <InfoRow
                icon="pills.fill"
                label={t('sections.medical.medications')}
                value={profile.medications}
                theme={theme}
              />
            )}
            {profile.allergies && (
              <InfoRow
                icon="allergens"
                label={t('sections.medical.allergies')}
                value={profile.allergies}
                theme={theme}
              />
            )}
            {profile.cognitiveStatus && (
              <InfoRow
                icon="brain.head.profile"
                label={t('sections.medical.cognitiveStatus')}
                value={profile.cognitiveStatus}
                theme={theme}
              />
            )}
          </View>
        )}

        {/* Approach & De-escalation Card */}
        {(profile.communicationPreference ||
          profile.escalationSigns ||
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
                {t('sections.communication.title')}
              </ThemedText>
            </View>
            {profile.communicationPreference && (
              <InfoRow
                icon="waveform"
                label={t('sections.communication.communication')}
                value={profile.communicationPreference}
                theme={theme}
              />
            )}
            {profile.approachGuidance && (
              <InfoRow
                icon="figure.walk.motion"
                label={t('sections.communication.approach')}
                value={profile.approachGuidance}
                theme={theme}
              />
            )}
            {profile.escalationSigns && (
              <InfoRow
                icon="waveform.path.ecg"
                label={t('sections.communication.escalation')}
                value={profile.escalationSigns}
                theme={theme}
              />
            )}
            {profile.deescalationTechniques && (
              <InfoRow
                icon="hand.raised.fill"
                label={t('sections.communication.deescalation')}
                value={profile.deescalationTechniques}
                theme={theme}
              />
            )}
            {profile.likes && (
              <InfoRow
                icon="heart.fill"
                label={t('sections.communication.likes')}
                value={profile.likes}
                theme={theme}
              />
            )}
            {profile.dislikesTriggers && (
              <InfoRow
                icon="exclamationmark.triangle.fill"
                label={t('sections.communication.triggers')}
                value={profile.dislikesTriggers}
                theme={theme}
              />
            )}
            {profile.safeWord && (
              <InfoRow
                icon="key.fill"
                label={t('sections.communication.safeWord')}
                value={profile.safeWord}
                theme={theme}
              />
            )}
          </View>
        )}

        {/* Locator & ID Card */}
        {(profile.locativeDeviceInfo ||
          profile.idBracelets ||
          profile.medicAlertId ||
          profile.medicAlertHotline) && (
          <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.card }]}>
            <View style={styles.sectionLabel}>
              <IconSymbol name="location.fill" size={14} color={theme.primary} />
              <ThemedText
                type="bodyBold"
                style={[styles.sectionLabelText, { color: theme.primary }]}
              >
                {t('sections.devices.title')}
              </ThemedText>
            </View>
            {profile.locativeDeviceInfo && (
              <InfoRow
                icon="antenna.radiowaves.left.and.right"
                label={t('sections.devices.locator')}
                value={profile.locativeDeviceInfo}
                theme={theme}
              />
            )}
            {profile.idBracelets && (
              <InfoRow
                icon="person.badge.shield.checkmark.fill"
                label={t('sections.devices.idBracelet')}
                value={profile.idBracelets}
                theme={theme}
              />
            )}
            {profile.medicAlertId && (
              <InfoRow
                icon="staroflife.fill"
                label={t('sections.devices.medicAlertId')}
                value={profile.medicAlertId}
                theme={theme}
              />
            )}
            {profile.medicAlertHotline && (
              <Pressable
                testID="readout-medicalert-hotline"
                accessibilityRole="button"
                onPress={() => {
                  track('readout_medicalert_hotline_called');
                  Linking.openURL(`tel:${stripPhoneFormatting(profile.medicAlertHotline!)}`);
                }}
              >
                <InfoRow
                  icon="phone.fill"
                  label={t('sections.devices.medicAlertHotline')}
                  value={profile.medicAlertHotline}
                  theme={theme}
                />
              </Pressable>
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
                {t('contacts.title')}
              </ThemedText>
            </View>
            {emergencyContacts.map((c, index) => (
              <View key={c.id} style={[styles.contactRow, { borderTopColor: theme.border }]}>
                <View style={styles.contactInfo}>
                  <ThemedText
                    type="bodyBold"
                    style={{ color: theme.text }}
                    numberOfLines={1}
                    ellipsizeMode="tail"
                  >
                    {c.name}
                  </ThemedText>
                  <ThemedText
                    type="caption"
                    style={{ color: theme.textSecondary }}
                    numberOfLines={1}
                    ellipsizeMode="tail"
                  >
                    {c.relationship || c.role}
                  </ThemedText>
                </View>
                <Pressable
                  style={[styles.callButton, { backgroundColor: semantic.success }]}
                  testID={`readout-contact-${index}-call`}
                  onPress={() => {
                    track('readout_contact_called');
                    Linking.openURL(`tel:${c.phone}`);
                  }}
                >
                  <IconSymbol name="phone.fill" size={14} color={Colors.light.textOnPrimary} />
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
            testID="readout-copy-script"
            onPress={copyScript}
          >
            <IconSymbol
              name={copiedType === 'script' ? 'checkmark.circle.fill' : 'doc.on.clipboard.fill'}
              size={18}
              color={Colors.light.textOnPrimary}
            />
            <ThemedText style={[styles.buttonText, { color: theme.textOnPrimary }]}>
              {copiedType === 'script' ? t('copiedScriptButton') : t('copyScriptButton')}
            </ThemedText>
          </Pressable>
          <Pressable
            style={[
              styles.button,
              styles.buttonSecondary,
              { borderColor: theme.border, backgroundColor: theme.card },
            ]}
            testID="readout-copy-all"
            onPress={copyAll}
          >
            <IconSymbol
              name={copiedType === 'all' ? 'checkmark.circle.fill' : 'square.and.arrow.up'}
              size={18}
              color={copiedType === 'all' ? semantic.success : theme.text}
            />
            <ThemedText style={[styles.buttonText, { color: theme.text }]}>
              {copiedType === 'all' ? t('copiedFullButton') : t('copyFullButton')}
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
            {t('silverAlert.title')}
          </ThemedText>
          <ThemedText
            style={[
              styles.alertText,
              { color: colorScheme === 'dark' ? neutral[300] : neutral[700] },
            ]}
          >
            {t('silverAlert.body', { emergencyNumber: t('emergencyNumber', { ns: 'common' }) })}
          </ThemedText>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

// ── Sub-components ──────────────────────────────────────────────────────────

type ThemeColors = typeof Colors.light;

function InfoRow({
  icon: _icon,
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
      <ThemedText
        style={[infoRowStyles.value, { color: theme.text }]}
        numberOfLines={4}
        ellipsizeMode="tail"
      >
        {value}
      </ThemedText>
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
      <ThemedText
        type="bodyBold"
        style={{ color: theme.text }}
        numberOfLines={1}
        ellipsizeMode="tail"
      >
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
    maxWidth: 160,
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

  emergencyButton: {
    backgroundColor: semantic.error,
    paddingVertical: Spacing.lg,
    borderRadius: Radius.lg,
    alignItems: 'center',
  },
  emergencyButtonText: {
    color: Colors.light.textOnPrimary,
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
  scriptHeaderButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
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
    color: Colors.light.textOnPrimary,
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

  scriptHint: {
    ...Typography.caption,
    marginTop: Spacing.xxs,
    marginBottom: Spacing.xs,
  },
  scriptText: {
    ...Typography.body,
    lineHeight: 22,
  },
  scriptMissingText: {
    ...Typography.caption,
    marginTop: Spacing.sm,
    lineHeight: 18,
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
