/**
 * Home Screen - Main dashboard
 * Quick access to emergency flow, profile summary, and key actions
 */

import { useFocusEffect } from '@react-navigation/native';
import { Image } from 'expo-image';
import { Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import { getSetting } from '@/database/storage';
import { setPreviousRoute } from '@/utils/navigation';

const SEARCH_WINDOW_SECONDS = 15 * 60; // 15 minutes
/** Dark red button background used when an emergency is active but the timer has not expired */
const EMERGENCY_ACTIVE_BG = '#4A1515';

type EmergencyState = {
  startedAt: string;
  wearing: string;
  checkedSteps: string[];
  isActive: boolean;
};

export default function HomeScreen() {
  const router = useRouter();
  const { profile, emergencyContacts, refreshProfile, refreshContacts } = useProfile();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const [activeEmergency, setActiveEmergency] = useState<EmergencyState | null>(null);
  const [emergencySecondsLeft, setEmergencySecondsLeft] = useState(0);

  // Check for active emergency
  const checkEmergency = useCallback(async () => {
    try {
      const saved = await getSetting('active_emergency');
      if (saved) {
        const state: EmergencyState = JSON.parse(saved);
        if (state.isActive) {
          const started = new Date(state.startedAt);
          const elapsed = Math.floor((Date.now() - started.getTime()) / 1000);
          const remaining = Math.max(0, SEARCH_WINDOW_SECONDS - elapsed);
          setActiveEmergency(state);
          setEmergencySecondsLeft(remaining);
        } else {
          setActiveEmergency(null);
        }
      } else {
        setActiveEmergency(null);
      }
    } catch {
      setActiveEmergency(null);
    }
  }, []);

  // Refresh data when screen is focused (coming back from other screens)
  useFocusEffect(
    useCallback(() => {
      refreshProfile();
      refreshContacts();
      checkEmergency();
    }, [refreshProfile, refreshContacts, checkEmergency]),
  );

  // Update timer every second if emergency is active
  useEffect(() => {
    if (!activeEmergency) return;

    const interval = setInterval(() => {
      setEmergencySecondsLeft((prev) => Math.max(0, prev - 1));
    }, 1000);

    return () => clearInterval(interval);
  }, [activeEmergency]);

  const hasProfile = profile && profile.name;
  const contactCount = emergencyContacts?.length || 0;

  // Calculate emergency progress
  const emergencyProgress = activeEmergency
    ? ((SEARCH_WINDOW_SECONDS - emergencySecondsLeft) / SEARCH_WINDOW_SECONDS) * 100
    : 0;
  const emergencyMinutes = Math.floor(emergencySecondsLeft / 60);
  const emergencySeconds = emergencySecondsLeft % 60;
  const timerExpired = activeEmergency && emergencySecondsLeft === 0;

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]} edges={['top']}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* ── Header ── */}
        <View style={styles.header}>
          {/* Profile card — avatar + name grouped together */}
          <Pressable
            onPress={() => router.push('/profile' as Href)}
            style={[styles.profileCard, { backgroundColor: theme.card, borderColor: theme.border }]}
          >
            {/* Avatar */}
            <View style={styles.avatarWrapper}>
              {profile?.photoUri ? (
                <Image
                  source={{ uri: profile.photoUri }}
                  style={styles.avatar}
                  contentFit="cover"
                />
              ) : (
                <View
                  style={[
                    styles.avatar,
                    styles.avatarPlaceholder,
                    { backgroundColor: theme.primaryLight, borderColor: theme.border },
                  ]}
                >
                  <ThemedText style={styles.avatarPlaceholderText}>👤</ThemedText>
                </View>
              )}
              {/* Edit badge */}
              <View style={[styles.avatarBadge, { backgroundColor: theme.tint }]}>
                <IconSymbol name="pencil" size={10} color="#fff" />
              </View>
            </View>

            {/* Name / label */}
            <View style={styles.profileCardText}>
              {hasProfile && (
                <ThemedText style={[styles.caringLabel, { color: theme.textSecondary }]}>
                  Caring for
                </ThemedText>
              )}
              <ThemedText type="headline" style={{ color: theme.text }}>
                {hasProfile ? profile.name : 'Wandering'}
              </ThemedText>
            </View>
          </Pressable>
        </View>

        {/* ── Emergency Button ── */}
        <Pressable
          style={[
            styles.emergencyButton,
            getShadow('md', colorScheme),
            activeEmergency && {
              backgroundColor: timerExpired ? semantic.error : EMERGENCY_ACTIVE_BG,
            },
          ]}
          onPress={() => {
            setPreviousRoute('/(tabs)');
            router.push('/emergency' as Href);
          }}
        >
          {/* Progress background fill */}
          {activeEmergency && !timerExpired && (
            <View
              style={[
                styles.emergencyProgressFill,
                { width: `${Math.min(emergencyProgress, 100)}%` },
              ]}
            />
          )}

          {activeEmergency ? (
            <View style={styles.emergencyActiveContent}>
              <View style={styles.emergencyContent}>
                <View style={styles.emergencyIconWrap}>
                  <IconSymbol name="exclamationmark.triangle.fill" size={28} color="#fff" />
                </View>
                <View style={styles.emergencyTextContainer}>
                  <ThemedText style={styles.emergencyTitle}>
                    {timerExpired ? 'Timer Expired — Call 911' : 'Emergency In Progress'}
                  </ThemedText>
                  <ThemedText style={styles.emergencySubtitle}>
                    {timerExpired
                      ? 'Tap to continue search protocol'
                      : `${emergencyMinutes}:${emergencySeconds.toString().padStart(2, '0')} remaining · ${activeEmergency.checkedSteps.length}/11 steps`}
                  </ThemedText>
                </View>
              </View>
              <IconSymbol name="chevron.right" size={20} color="rgba(255,255,255,0.7)" />
            </View>
          ) : (
            <View style={styles.emergencyActiveContent}>
              <View style={styles.emergencyContent}>
                <View style={styles.emergencyIconWrap}>
                  <IconSymbol name="exclamationmark.triangle.fill" size={28} color="#fff" />
                </View>
                <View style={styles.emergencyTextContainer}>
                  <ThemedText style={styles.emergencyTitle}>Start Emergency Search</ThemedText>
                  <ThemedText style={styles.emergencySubtitle}>
                    15-minute guided timer with checklist
                  </ThemedText>
                </View>
              </View>
              <IconSymbol name="chevron.right" size={20} color="rgba(255,255,255,0.7)" />
            </View>
          )}
        </Pressable>

        {/* ── Quick Actions ── */}
        <View style={styles.quickActions}>
          <Pressable
            style={[styles.actionCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => router.push('/contacts' as Href)}
          >
            <View style={[styles.actionIconWrap, { backgroundColor: theme.primaryLight }]}>
              <ThemedText style={styles.actionIconEmoji}>📞</ThemedText>
            </View>
            <ThemedText style={[styles.actionTitle, { color: theme.text }]}>Contacts</ThemedText>
            <ThemedText style={[styles.actionSubtitle, { color: theme.textSecondary }]}>
              {contactCount === 0 ? 'None added' : `${contactCount} saved`}
            </ThemedText>
          </Pressable>

          <Pressable
            style={[styles.actionCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => router.push('/destinations' as Href)}
          >
            <View style={[styles.actionIconWrap, { backgroundColor: theme.primaryLight }]}>
              <ThemedText style={styles.actionIconEmoji}>📍</ThemedText>
            </View>
            <ThemedText style={[styles.actionTitle, { color: theme.text }]}>Places</ThemedText>
            <ThemedText style={[styles.actionSubtitle, { color: theme.textSecondary }]}>
              Likely spots
            </ThemedText>
          </Pressable>
        </View>

        {/* ── Emergency Info Card ── */}
        {hasProfile && (
          <Pressable
            style={[styles.summaryCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => router.push('/readout' as Href)}
          >
            <View style={styles.summaryHeader}>
              <View style={styles.summaryHeaderLeft}>
                <ThemedText style={[styles.summaryTitle, { color: theme.text }]}>
                  Emergency Info
                </ThemedText>
                {!(
                  profile.medicalConditions ||
                  profile.medications ||
                  profile.cognitiveStatus ||
                  profile.deescalationTechniques
                ) && (
                  <Pressable onPress={() => router.push('/profile' as Href)}>
                    <ThemedText style={[styles.summaryEmpty, { color: theme.tint }]}>
                      + Add medical info
                    </ThemedText>
                  </Pressable>
                )}
              </View>
              <View style={[styles.viewScriptButton, { backgroundColor: theme.primaryLight }]}>
                <ThemedText style={[styles.viewScriptHint, { color: theme.tint }]}>
                  911 Script
                </ThemedText>
                <IconSymbol name="chevron.right" size={14} color={theme.tint} />
              </View>
            </View>

            {(profile.medicalConditions ||
              profile.medications ||
              profile.cognitiveStatus ||
              profile.deescalationTechniques) && (
              <View style={[styles.summaryGrid, { borderTopColor: theme.border }]}>
                {profile.medicalConditions && (
                  <View style={styles.summaryItem}>
                    <ThemedText style={[styles.summaryLabel, { color: theme.textSecondary }]}>
                      Medical Conditions
                    </ThemedText>
                    <ThemedText
                      style={[styles.summaryValue, { color: theme.text }]}
                      numberOfLines={2}
                    >
                      {profile.medicalConditions}
                    </ThemedText>
                  </View>
                )}
                {profile.medications && (
                  <View style={styles.summaryItem}>
                    <ThemedText style={[styles.summaryLabel, { color: theme.textSecondary }]}>
                      Medications
                    </ThemedText>
                    <ThemedText
                      style={[styles.summaryValue, { color: theme.text }]}
                      numberOfLines={2}
                    >
                      {profile.medications}
                    </ThemedText>
                  </View>
                )}
                {profile.cognitiveStatus && (
                  <View style={styles.summaryItem}>
                    <ThemedText style={[styles.summaryLabel, { color: theme.textSecondary }]}>
                      Cognitive Status
                    </ThemedText>
                    <ThemedText
                      style={[styles.summaryValue, { color: theme.text }]}
                      numberOfLines={2}
                    >
                      {profile.cognitiveStatus}
                    </ThemedText>
                  </View>
                )}
                {profile.deescalationTechniques && (
                  <View style={styles.summaryItem}>
                    <ThemedText style={[styles.summaryLabel, { color: theme.textSecondary }]}>
                      De-escalation
                    </ThemedText>
                    <ThemedText
                      style={[styles.summaryValue, { color: theme.text }]}
                      numberOfLines={2}
                    >
                      {profile.deescalationTechniques}
                    </ThemedText>
                  </View>
                )}
              </View>
            )}
          </Pressable>
        )}

        {/* ── Setup Prompt ── */}
        {!hasProfile && (
          <View
            style={[
              styles.setupCard,
              { backgroundColor: theme.card, borderColor: theme.border },
              getShadow('sm', colorScheme),
            ]}
          >
            <View style={[styles.setupIconWrap, { backgroundColor: theme.primaryLight }]}>
              <ThemedText style={styles.setupIconEmoji}>📝</ThemedText>
            </View>
            <ThemedText style={[styles.setupTitle, { color: theme.text }]}>
              Complete Your Profile
            </ThemedText>
            <ThemedText style={[styles.setupText, { color: theme.textSecondary }]}>
              Add information about the person you care for. This will be used during emergencies to
              help 911 and searchers.
            </ThemedText>
            <Pressable
              style={[styles.setupButton, { backgroundColor: theme.primary }]}
              onPress={() => router.push('/profile' as Href)}
            >
              <ThemedText style={styles.setupButtonText}>Start Profile</ThemedText>
              <IconSymbol name="chevron.right" size={16} color="#fff" />
            </Pressable>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: Spacing.lg,
    paddingBottom: Spacing.xxl,
  },

  // Header
  header: {
    marginBottom: Spacing.xl,
    paddingTop: Spacing.xs,
  },
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.md,
    borderWidth: 1,
    borderRadius: Radius.xl,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.lg,
  },
  profileCardText: {
    flex: 1,
    gap: Spacing.xxs,
  },
  caringLabel: {
    ...Typography.caption,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    fontWeight: '600',
  },

  // Avatar
  avatarWrapper: {
    position: 'relative',
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
  },
  avatarPlaceholder: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  avatarPlaceholderText: {
    fontSize: 22,
  },
  avatarBadge: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 18,
    height: 18,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#fff',
  },

  // Emergency button
  emergencyButton: {
    backgroundColor: semantic.error,
    borderRadius: Radius.xl,
    paddingVertical: Spacing.lg,
    paddingHorizontal: Spacing.xl,
    marginBottom: Spacing.lg,
    overflow: 'hidden',
  },
  emergencyContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    gap: Spacing.md,
  },
  emergencyIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  emergencyTextContainer: {
    flex: 1,
  },
  emergencyTitle: {
    color: '#fff',
    ...Typography.bodyBold,
    marginBottom: 2,
  },
  emergencySubtitle: {
    color: 'rgba(255,255,255,0.8)',
    ...Typography.caption,
  },
  emergencyProgressFill: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    backgroundColor: 'rgba(255,255,255,0.15)',
  },
  emergencyActiveContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flex: 1,
    zIndex: 1,
  },

  // Quick actions
  quickActions: {
    flexDirection: 'row',
    gap: Spacing.md,
    marginBottom: Spacing.lg,
  },
  actionCard: {
    flex: 1,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    alignItems: 'center',
    minHeight: 110,
    justifyContent: 'center',
    gap: Spacing.xs,
  },
  actionIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.xs,
  },
  actionIconEmoji: {
    fontSize: 22,
  },
  actionTitle: {
    ...Typography.bodyBold,
  },
  actionSubtitle: {
    ...Typography.caption,
  },

  // Summary card
  summaryCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    marginBottom: Spacing.lg,
  },
  summaryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  summaryHeaderLeft: {
    flex: 1,
    gap: Spacing.xs,
  },
  summaryTitle: {
    ...Typography.bodyBold,
  },
  viewScriptButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xxs,
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xs,
    borderRadius: Radius.md,
  },
  viewScriptHint: {
    ...Typography.caption,
    fontWeight: '600',
  },
  summaryEmpty: {
    ...Typography.caption,
    fontStyle: 'italic',
  },
  summaryGrid: {
    gap: Spacing.md,
    marginTop: Spacing.md,
    paddingTop: Spacing.md,
    borderTopWidth: 1,
  },
  summaryItem: {},
  summaryLabel: {
    ...Typography.small,
    fontWeight: '700',
    marginBottom: Spacing.xxs,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  summaryValue: {
    ...Typography.body,
  },

  // Setup card
  setupCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.xl,
    alignItems: 'center',
    marginBottom: Spacing.lg,
    gap: Spacing.md,
  },
  setupIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.xs,
  },
  setupIconEmoji: {
    fontSize: 32,
  },
  setupTitle: {
    ...Typography.title,
    textAlign: 'center',
  },
  setupText: {
    ...Typography.body,
    textAlign: 'center',
  },
  setupButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
    borderRadius: Radius.md,
    marginTop: Spacing.xs,
    minHeight: 48,
  },
  setupButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },
});
