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
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import { getSetting } from '@/database/storage';
import { setPreviousRoute } from '@/utils/navigation';

const SEARCH_WINDOW_SECONDS = 15 * 60; // 15 minutes

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
      <ScrollView contentContainerStyle={styles.content}>
        {/* Header */}
        <View style={styles.header}>
          <View style={styles.headerText}>
            <ThemedText type="title" style={styles.title}>
              {hasProfile ? `Caring for ${profile.name}` : 'Wandering'}
            </ThemedText>
          </View>
          <Pressable onPress={() => router.push('/profile' as Href)}>
            {profile?.photoUri ? (
              <Image source={{ uri: profile.photoUri }} style={styles.avatar} contentFit="cover" />
            ) : (
              <View
                style={[styles.avatar, styles.avatarPlaceholder, { backgroundColor: theme.primaryLight, borderColor: theme.secondary }]}
              >
                <ThemedText style={styles.avatarPlaceholderText}>👤</ThemedText>
              </View>
            )}
          </Pressable>
        </View>

        {/* Emergency Button */}
        <Pressable
          style={[
            styles.emergencyButton,
            activeEmergency && { backgroundColor: timerExpired ? semantic.error : '#4A1515' },
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
                <IconSymbol
                  name="exclamationmark.triangle.fill"
                  size={32}
                  color="#fff"
                  style={styles.emergencyIcon}
                />
                <View style={styles.emergencyTextContainer}>
                  <ThemedText style={styles.emergencyTitle}>
                    {timerExpired ? 'Timer Expired — Call 911' : 'Emergency In Progress'}
                  </ThemedText>
                  <ThemedText style={styles.emergencySubtitle}>
                    {timerExpired
                      ? 'Tap to continue search protocol'
                      : `${emergencyMinutes}:${emergencySeconds.toString().padStart(2, '0')} remaining • ${activeEmergency.checkedSteps.length}/11 steps`}
                  </ThemedText>
                </View>
              </View>
              <IconSymbol name="chevron.right" size={24} color="#fff" />
            </View>
          ) : (
            <>
              <View style={styles.emergencyContent}>
                <IconSymbol
                  name="exclamationmark.triangle.fill"
                  size={32}
                  color="#fff"
                  style={styles.emergencyIcon}
                />
                <View style={styles.emergencyTextContainer}>
                  <ThemedText style={styles.emergencyTitle}>Start Emergency Search</ThemedText>
                  <ThemedText style={styles.emergencySubtitle}>
                    15-minute guided timer with checklist
                  </ThemedText>
                </View>
              </View>
              <IconSymbol name="chevron.right" size={24} color="#fff" />
            </>
          )}
        </Pressable>

        {/* Quick Actions */}
        <View style={styles.quickActions}>
          <Pressable
            style={[styles.actionCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => router.push('/contacts' as Href)}
          >
            <ThemedText style={styles.actionIcon}>📞</ThemedText>
            <ThemedText style={[styles.actionTitle, { color: theme.text }]}>Contacts</ThemedText>
            <ThemedText style={[styles.actionSubtitle, { color: theme.textSecondary }]}>
              {contactCount} saved
            </ThemedText>
          </Pressable>

          <Pressable
            style={[styles.actionCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => router.push('/destinations' as Href)}
          >
            <ThemedText style={styles.actionIcon}>📍</ThemedText>
            <ThemedText style={[styles.actionTitle, { color: theme.text }]}>Places</ThemedText>
            <ThemedText style={[styles.actionSubtitle, { color: theme.textSecondary }]}>
              Likely spots
            </ThemedText>
          </Pressable>
        </View>

        {/* Profile Summary Card - Tap to view 911 Script */}
        {hasProfile && (
          <Pressable
            style={[styles.summaryCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            onPress={() => router.push('/readout' as Href)}
          >
            <View style={styles.summaryHeader}>
              <View style={styles.summaryHeaderLeft}>
                <ThemedText type="subtitle" style={{ color: theme.text }}>
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
              <View style={styles.viewScriptButton}>
                <ThemedText style={[styles.viewScriptHint, { color: theme.tint }]}>
                  View 911 Script
                </ThemedText>
                <IconSymbol name="chevron.right" size={16} color={theme.tint} />
              </View>
            </View>

            {(profile.medicalConditions ||
              profile.medications ||
              profile.cognitiveStatus ||
              profile.deescalationTechniques) && (
              <View style={styles.summaryGrid}>
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
        {/* Setup Prompts */}
        {!hasProfile && (
          <View
            style={[
              styles.setupCard,
              { backgroundColor: theme.primaryLight, borderColor: theme.secondary },
            ]}
          >
            <ThemedText style={styles.setupIcon}>📝</ThemedText>
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
              <ThemedText style={styles.setupButtonText}>Start Profile →</ThemedText>
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
    padding: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.xl,
  },
  headerText: {
    flex: 1,
  },
  title: {
  },
  avatar: {
    width: 50,
    height: 50,
    borderRadius: 25,
  },
  avatarPlaceholder: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderStyle: 'dashed',
  },
  avatarPlaceholderText: {
    fontSize: 24,
  },
  emergencyButton: {
    backgroundColor: semantic.error,
    borderRadius: Radius.xl,
    padding: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
    shadowColor: semantic.error,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: Spacing.sm,
    elevation: 4,
    overflow: 'hidden',
  },
  emergencyContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  emergencyIcon: {
    marginRight: Spacing.lg,
  },
  emergencyTextContainer: {
    flex: 1,
  },
  emergencyTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: Spacing.xs,
  },
  emergencySubtitle: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 14,
  },
  emergencyProgressFill: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    backgroundColor: semantic.error,
    borderRadius: Radius.xl,
  },
  emergencyActiveContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flex: 1,
    zIndex: 1,
  },
  quickActions: {
    flexDirection: 'row',
    gap: Spacing.md,
    marginBottom: 20,
  },
  actionCard: {
    flex: 1,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    alignItems: 'center',
  },
  actionIcon: {
    fontSize: 28,
    marginBottom: Spacing.sm,
  },
  actionTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: Spacing.xs,
  },
  actionSubtitle: {
    fontSize: 12,
  },
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
  },
  viewScriptButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  viewScriptHint: {
    fontSize: 14,
    fontWeight: '500',
  },
  summaryEmpty: {
    fontSize: 12,
    marginTop: Spacing.xs,
    fontStyle: 'italic',
  },
  summaryGrid: {
    gap: Spacing.md,
    marginTop: Spacing.lg,
  },
  summaryItem: {
    marginBottom: Spacing.sm,
  },
  summaryLabel: {
    fontSize: 12,
    fontWeight: '600',
    marginBottom: Spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  summaryValue: {
    fontSize: 15,
    lineHeight: 20,
  },
  setupCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: 20,
    alignItems: 'center',
    marginBottom: Spacing.lg,
  },
  setupIcon: {
    fontSize: 40,
    marginBottom: Spacing.md,
  },
  setupTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: Spacing.sm,
  },
  setupText: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: Spacing.lg,
  },
  setupButton: {
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
    borderRadius: Radius.md,
  },
  setupButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },
});
