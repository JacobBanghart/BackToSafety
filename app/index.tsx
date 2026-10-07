/**
 * Home Screen - Main dashboard
 * Quick access to emergency flow, profile summary, and key actions
 */

import { useFocusEffect } from 'expo-router/react-navigation';
import { Image } from 'expo-image';
import { Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppCard } from '@/components/AppCard';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import { setPreviousRoute } from '@/utils/navigation';
import { posthog } from '@/utils/posthog';
import { track } from '@/utils/analytics';
import { loadActiveEmergency } from '@/utils/activeEmergency';
import { now } from '@/utils/clock';
import {
  formatCountdown,
  SEARCH_WINDOW_SECONDS,
  secondsRemaining,
  type ActiveEmergency,
} from '@/utils/emergency';

/** Emergency button colours — hardcoded, never adapt to light/dark mode */
const EMERGENCY_IDLE_BG = '#ef4444';
const EMERGENCY_ACTIVE_BG = '#b91c1c';
const EMERGENCY_SWEEP_COLOR = '#ef4444';

export default function HomeScreen() {
  const router = useRouter();
  const { profile, contacts, refreshProfile, refreshContacts } = useProfile();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const { t } = useTranslation('home');
  const { t: tCommon } = useTranslation('common');
  const emergencyNumber = tCommon('emergencyNumber');

  const [activeEmergency, setActiveEmergency] = useState<ActiveEmergency | null>(null);
  const [emergencySecondsLeft, setEmergencySecondsLeft] = useState(0);

  const checkEmergency = useCallback(async () => {
    try {
      const state = await loadActiveEmergency();
      setActiveEmergency(state);
      if (state) {
        setEmergencySecondsLeft(secondsRemaining(new Date(state.startedAt).getTime(), now()));
      }
    } catch {
      setActiveEmergency(null);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      posthog.screen('home');
      refreshProfile();
      refreshContacts();
      checkEmergency();
    }, [refreshProfile, refreshContacts, checkEmergency]),
  );

  useEffect(() => {
    if (!activeEmergency) return;
    // Re-derive from the start time each tick; counting ticks drifts in the background (F-16).
    const startedAtMs = new Date(activeEmergency.startedAt).getTime();
    const interval = setInterval(() => {
      setEmergencySecondsLeft(secondsRemaining(startedAtMs, now()));
    }, 1000);
    return () => clearInterval(interval);
  }, [activeEmergency]);

  const hasProfile = profile && profile.name;
  const contactCount = contacts?.length || 0;

  const emergencyProgress = activeEmergency
    ? ((SEARCH_WINDOW_SECONDS - emergencySecondsLeft) / SEARCH_WINDOW_SECONDS) * 100
    : 0;
  const timerExpired = activeEmergency && emergencySecondsLeft === 0;

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]} edges={['top']}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* ── Header ── */}
        <View style={styles.header}>
          <View style={styles.headerLeft}>
            {hasProfile && (
              <ThemedText style={[styles.caringLabel, { color: theme.textSecondary }]}>
                {t('caringFor')}
              </ThemedText>
            )}
            <ThemedText
              type="headline"
              style={{ color: theme.text }}
              numberOfLines={1}
              ellipsizeMode="tail"
            >
              {hasProfile ? profile.name : t('appTitle')}
            </ThemedText>
          </View>

          {/* Avatar */}
          <Pressable
            testID="home-profile"
            onPress={() => {
              track('screen_viewed', { screen: 'profile', source: 'home' });
              router.push('/profile' as Href);
            }}
            style={styles.avatarWrapper}
          >
            {profile?.photoUri ? (
              <Image
                testID="home-photo"
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
                <IconSymbol name="person.fill" size={28} color={theme.textSecondary} />
              </View>
            )}
            <View style={[styles.avatarBadge, { backgroundColor: theme.tint }]}>
              <IconSymbol name="pencil" size={10} color={Colors.light.textOnPrimary} />
            </View>
          </Pressable>
        </View>

        {/* ── Emergency Button ── */}
        <Pressable
          testID="home-start-emergency"
          style={[
            styles.emergencyButton,
            getShadow('md', colorScheme),
            { backgroundColor: activeEmergency ? EMERGENCY_ACTIVE_BG : EMERGENCY_IDLE_BG },
          ]}
          onPress={() => {
            // emergency_started is tracked by the emergency screen, only for new emergencies (F-18).
            setPreviousRoute('/');
            router.push('/emergency' as Href);
          }}
        >
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
                  <IconSymbol
                    name="exclamationmark.triangle.fill"
                    size={28}
                    color={Colors.light.textOnPrimary}
                  />
                </View>
                <View style={styles.emergencyTextContainer}>
                  <ThemedText style={styles.emergencyTitle}>
                    {timerExpired
                      ? t('emergencyButton.timerExpiredTitle', { emergencyNumber })
                      : t('emergencyButton.activeTitle')}
                  </ThemedText>
                  <ThemedText style={styles.emergencySubtitle}>
                    {timerExpired
                      ? t('emergencyButton.timerExpiredSubtitle')
                      : t('emergencyButton.remaining', {
                          time: formatCountdown(emergencySecondsLeft),
                          checked: activeEmergency.checkedSteps.length,
                          total: 11,
                        })}
                  </ThemedText>
                </View>
              </View>
              <IconSymbol name="chevron.right" size={20} color="rgba(255,255,255,0.7)" />
            </View>
          ) : (
            <View style={styles.emergencyActiveContent}>
              <View style={styles.emergencyContent}>
                <View style={styles.emergencyIconWrap}>
                  <IconSymbol
                    name="exclamationmark.triangle.fill"
                    size={28}
                    color={Colors.light.textOnPrimary}
                  />
                </View>
                <View style={styles.emergencyTextContainer}>
                  <ThemedText style={styles.emergencyTitle}>
                    {t('emergencyButton.startTitle')}
                  </ThemedText>
                  <ThemedText style={styles.emergencySubtitle}>
                    {t('emergencyButton.startSubtitle')}
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
            testID="home-contacts"
            onPress={() => {
              track('screen_viewed', { screen: 'contacts', source: 'home' });
              router.push('/contacts' as Href);
            }}
          >
            <View style={[styles.actionIconWrap, { backgroundColor: theme.primaryLight }]}>
              <ThemedText style={styles.actionIconEmoji}>📞</ThemedText>
            </View>
            <ThemedText style={[styles.actionTitle, { color: theme.text }]}>
              {t('quickActions.contacts')}
            </ThemedText>
            <ThemedText style={[styles.actionSubtitle, { color: theme.textSecondary }]}>
              {contactCount === 0
                ? t('quickActions.contactsNone')
                : t('quickActions.contactsSaved', { count: contactCount })}
            </ThemedText>
          </Pressable>

          <Pressable
            style={[styles.actionCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            testID="home-places"
            onPress={() => {
              track('destination_add_tapped', { source: 'home' });
              router.push('/destinations' as Href);
            }}
          >
            <View style={[styles.actionIconWrap, { backgroundColor: theme.primaryLight }]}>
              <ThemedText style={styles.actionIconEmoji}>📍</ThemedText>
            </View>
            <ThemedText style={[styles.actionTitle, { color: theme.text }]}>
              {t('quickActions.places')}
            </ThemedText>
            <ThemedText style={[styles.actionSubtitle, { color: theme.textSecondary }]}>
              {t('quickActions.placesSubtitle')}
            </ThemedText>
          </Pressable>
        </View>

        {/* ── Emergency Info Card ── */}
        {hasProfile && (
          <Pressable
            style={[styles.summaryCard, { backgroundColor: theme.card, borderColor: theme.border }]}
            testID="home-readout"
            onPress={() => {
              track('screen_viewed', { screen: 'readout', source: 'home' });
              router.push('/readout' as Href);
            }}
          >
            <View style={styles.summaryHeader}>
              <View style={styles.summaryHeaderLeft}>
                <ThemedText style={[styles.summaryTitle, { color: theme.text }]}>
                  {t('emergencyInfo.title')}
                </ThemedText>
              </View>
              <View style={[styles.viewScriptButton, { backgroundColor: theme.primaryLight }]}>
                <ThemedText style={[styles.viewScriptHint, { color: theme.tint }]}>
                  {t('emergencyInfo.script911')}
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
                      {t('emergencyInfo.healthNotes')}
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
                      {t('emergencyInfo.medications')}
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
                      {t('emergencyInfo.cognitiveStatus')}
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
                      {t('emergencyInfo.deescalation')}
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

        {/* ── Settings Link ── */}
        <AppCard style={styles.settingsCard}>
          <Pressable
            style={styles.settingsRow}
            testID="home-settings"
            onPress={() => {
              track('screen_viewed', { screen: 'settings', source: 'home' });
              router.push('/settings' as Href);
            }}
          >
            <IconSymbol name="gearshape" size={18} color={theme.textSecondary} />
            <ThemedText style={[styles.settingsLabel, { color: theme.text }]}>
              {t('settingsLink')}
            </ThemedText>
            <IconSymbol name="chevron.right" size={16} color={theme.textSecondary} />
          </Pressable>
        </AppCard>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: {
    padding: Spacing.lg,
    paddingBottom: Spacing.xxl,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.xl,
    paddingTop: Spacing.xs,
  },
  headerLeft: { flex: 1, gap: Spacing.xxs },
  caringLabel: {
    ...Typography.caption,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    fontWeight: '600',
  },
  avatarWrapper: { position: 'relative', marginLeft: Spacing.md },
  avatar: { width: 56, height: 56, borderRadius: 28 },
  avatarPlaceholder: { alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  avatarBadge: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 20,
    height: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: Colors.light.textOnPrimary,
  },
  emergencyButton: {
    backgroundColor: EMERGENCY_IDLE_BG,
    borderRadius: Radius.xl,
    paddingVertical: Spacing.lg,
    paddingHorizontal: Spacing.xl,
    marginBottom: Spacing.lg,
    overflow: 'hidden',
  },
  emergencyContent: { flexDirection: 'row', alignItems: 'center', flex: 1, gap: Spacing.md },
  emergencyIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  emergencyTextContainer: { flex: 1 },
  emergencyTitle: { color: Colors.light.textOnPrimary, ...Typography.bodyBold, marginBottom: 2 },
  emergencySubtitle: { color: 'rgba(255,255,255,0.8)', ...Typography.caption },
  emergencyProgressFill: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    backgroundColor: EMERGENCY_SWEEP_COLOR,
  },
  emergencyActiveContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flex: 1,
    zIndex: 1,
  },
  quickActions: { flexDirection: 'row', gap: Spacing.md, marginBottom: Spacing.lg },
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
  actionIconEmoji: { fontSize: 22 },
  actionTitle: { ...Typography.bodyBold },
  actionSubtitle: { ...Typography.caption },
  summaryCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    marginBottom: Spacing.lg,
  },
  summaryHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  summaryHeaderLeft: { flex: 1, gap: Spacing.xs },
  summaryTitle: { ...Typography.bodyBold },
  viewScriptButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xxs,
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xs,
    borderRadius: Radius.md,
  },
  viewScriptHint: { ...Typography.caption, fontWeight: '600' },
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
  summaryValue: { ...Typography.body },
  settingsCard: {
    marginBottom: Spacing.sm,
    padding: 0,
  },
  settingsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    padding: Spacing.lg,
  },
  settingsLabel: {
    ...Typography.body,
    flex: 1,
  },
});
