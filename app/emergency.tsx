/**
 * Emergency Search Screen
 * 11-step guided protocol for finding someone who has wandered
 */

import { goBack, setPreviousRoute } from '@/utils/navigation';
import * as Haptics from 'expo-haptics';
import { Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Linking,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Vibration,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic, primary, neutral, secondary } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import { Destination, getDestinations } from '@/database/destinations';
import { getSetting, saveSetting } from '@/database/storage';

// Emergency state stored in settings
const EMERGENCY_STATE_KEY = 'active_emergency';

type EmergencyState = {
  startedAt: string; // ISO timestamp
  wearing: string;
  checkedSteps: string[];
  isActive: boolean;
};

type ChecklistStep = {
  id: string;
  step: number;
  title: string;
  description: string;
  hint?: string;
  urgent?: boolean;
  checked: boolean;
};

const SEARCH_WINDOW_SECONDS = 15 * 60; // 15 minutes

const INITIAL_STEPS: ChecklistStep[] = [
  {
    id: 'home_search',
    step: 1,
    title: 'Search home thoroughly',
    description: 'Check every room, closet, under beds, bathrooms, garage, basement, sheds',
    hint: 'People often seek small, quiet spaces',
    checked: false,
  },
  {
    id: 'outside_immediate',
    step: 2,
    title: 'Check outside areas',
    description: 'Yard, porches, paths, driveways, inside vehicles (locked or unlocked)',
    checked: false,
  },
  {
    id: 'neighbors',
    step: 3,
    title: 'Alert neighbors',
    description: 'Show photo, ask them to call if seen. Check their yards too.',
    checked: false,
  },
  {
    id: 'radius_search',
    step: 4,
    title: 'Search 1-1.5 mile radius',
    description: 'Most people are found within this distance from home',
    checked: false,
  },
  {
    id: 'high_risk',
    step: 5,
    title: 'Check high-risk areas first',
    description: 'Water (pools, ponds, streams), wooded areas, ditches, busy roads',
    urgent: true,
    checked: false,
  },
  {
    id: 'familiar_places',
    step: 6,
    title: 'Search familiar places',
    description: 'Former home, church, old workplace, favorite walking routes',
    checked: false,
  },
  {
    id: 'call_911',
    step: 7,
    title: 'Call 911',
    description: 'If not found within 15 minutes, call immediately',
    urgent: true,
    checked: false,
  },
  {
    id: 'silver_alert',
    step: 8,
    title: 'Request Silver/Feather Alert',
    description: 'Ask 911 dispatcher about activating state alert program',
    checked: false,
  },
  {
    id: 'share_info',
    step: 9,
    title: 'Share medical/behavioral info',
    description: 'Provide responders with conditions, medications, de-escalation tips',
    checked: false,
  },
  {
    id: 'coordinate',
    step: 10,
    title: 'Coordinate search efforts',
    description: 'Assign areas to helpers, avoid duplicating coverage',
    checked: false,
  },
  {
    id: 'document',
    step: 11,
    title: 'Document everything',
    description: 'Note times, areas checked, people contacted for responders',
    checked: false,
  },
];

export default function EmergencyScreen() {
  const router = useRouter();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const isDark = colorScheme === 'dark';
  const { setLastSeen, profile, emergencyContacts, addIncident } = useProfile();

  const [isLoading, setIsLoading] = useState(true);
  const [secondsLeft, setSecondsLeft] = useState<number>(SEARCH_WINDOW_SECONDS);
  const [startedAt, setStartedAt] = useState<Date>(new Date());
  const [wearing, setWearing] = useState('');
  const [steps, setSteps] = useState<ChecklistStep[]>(INITIAL_STEPS);
  const [destinations, setDestinations] = useState<Destination[]>([]);
  const [showWearingInput, setShowWearingInput] = useState(true);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'found' | 'leave' | 'noContacts' | 'smsError' | null>(
    null,
  );

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const scrollRef = useRef<ScrollView>(null);

  const timerExpired = secondsLeft === 0;
  const checkedCount = steps.filter((s) => s.checked).length;
  const progress = (checkedCount / steps.length) * 100;

  // Save emergency state to storage
  const saveEmergencyState = useCallback(async (state: EmergencyState) => {
    try {
      await saveSetting(EMERGENCY_STATE_KEY, JSON.stringify(state));
    } catch (error) {
      console.error('Failed to save emergency state:', error);
    }
  }, []);

  // Clear emergency state from storage
  const clearEmergencyState = useCallback(async () => {
    try {
      await saveSetting(EMERGENCY_STATE_KEY, '');
    } catch (error) {
      console.error('Failed to clear emergency state:', error);
    }
  }, []);

  // Load destinations for familiar places hints
  useEffect(() => {
    getDestinations().then(setDestinations).catch(console.error);
  }, []);

  // Load or create emergency state on mount
  useEffect(() => {
    const initEmergency = async () => {
      try {
        const savedState = await getSetting(EMERGENCY_STATE_KEY);

        if (savedState) {
          const state: EmergencyState = JSON.parse(savedState);

          if (state.isActive) {
            // Resume existing emergency
            const started = new Date(state.startedAt);
            const elapsedSeconds = Math.floor((Date.now() - started.getTime()) / 1000);
            const remaining = Math.max(0, SEARCH_WINDOW_SECONDS - elapsedSeconds);

            setStartedAt(started);
            setSecondsLeft(remaining);
            setWearing(state.wearing);
            setShowWearingInput(true);
            setSteps((prev) =>
              prev.map((step) => ({
                ...step,
                checked: state.checkedSteps.includes(step.id),
              })),
            );

            setIsLoading(false);

            // Start timer from remaining time
            startTimer(remaining);
            return;
          }
        }

        // Start new emergency
        const now = new Date();
        setStartedAt(now);

        await saveEmergencyState({
          startedAt: now.toISOString(),
          wearing: '',
          checkedSteps: [],
          isActive: true,
        });

        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
        setLastSeen({ time: now.toISOString() });

        setIsLoading(false);
        startTimer(SEARCH_WINDOW_SECONDS);
      } catch (error) {
        console.error('Failed to init emergency:', error);
        setIsLoading(false);
        startTimer(SEARCH_WINDOW_SECONDS);
      }
    };

    initEmergency();

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- Only run on mount
  }, []);

  // Start the countdown timer
  const startTimer = (initialSeconds: number) => {
    if (intervalRef.current) clearInterval(intervalRef.current);

    if (initialSeconds <= 0) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      return;
    }

    intervalRef.current = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
          Vibration.vibrate([0, 500, 200, 500]);
          return 0;
        }
        // Warning vibration at 5 minutes
        if (prev === 5 * 60) {
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
        }
        return prev - 1;
      });
    }, 1000);
  };

  // Persist state changes (wearing and steps)
  useEffect(() => {
    if (!isLoading) {
      saveEmergencyState({
        startedAt: startedAt.toISOString(),
        wearing,
        checkedSteps: steps.filter((s) => s.checked).map((s) => s.id),
        isActive: true,
      });
    }
  }, [wearing, steps, isLoading, startedAt, saveEmergencyState]);

  const mmss = useMemo(() => {
    const m = Math.floor(secondsLeft / 60)
      .toString()
      .padStart(2, '0');
    const s = (secondsLeft % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }, [secondsLeft]);

  const toggleStep = useCallback((id: string) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setSteps((prev) => prev.map((s) => (s.id === id ? { ...s, checked: !s.checked } : s)));
  }, []);

  const onMarkFound = async () => {
    // Clear state first and wait for it
    await clearEmergencyState();
    addIncident({
      at: new Date().toISOString(),
      outcome: 'found',
      checked: steps.filter((s) => s.checked).map((s) => s.id),
    });
    setModalType('found');
    setModalVisible(true);
  };

  const onCall911 = async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    toggleStep('call_911');
    addIncident({
      at: new Date().toISOString(),
      outcome: '911_called',
      checked: steps.filter((s) => s.checked).map((s) => s.id),
    });
    Linking.openURL('tel:911');
  };

  const onViewReadout = () => {
    setPreviousRoute('/emergency');
    router.push('/readout' as Href);
  };

  const onAlertContacts = async () => {
    if (emergencyContacts.length === 0) {
      setModalType('noContacts');
      setModalVisible(true);
      return;
    }

    const message = `URGENT: ${profile?.name || 'Our loved one'} is missing. Last seen ${startedAt.toLocaleTimeString()}. ${wearing ? `Wearing: ${wearing}. ` : ''}Please help search or call if you see them.`;

    // Open SMS with first contact
    const firstContact = emergencyContacts[0];
    const smsUrl = `sms:${firstContact.phone}?body=${encodeURIComponent(message)}`;

    try {
      await Linking.openURL(smsUrl);
    } catch {
      setModalType('smsError');
      setModalVisible(true);
    }
  };

  const getDirectionHint = () => {
    if (profile?.dominantHand === 'left') return '← May veer left (left-handed)';
    if (profile?.dominantHand === 'right') return '→ May veer right (right-handed)';
    return null;
  };

  const onBackPress = () => {
    setModalType('leave');
    setModalVisible(true);
  };

  const handleModalAction = (action: 'dismiss' | 'leave' | 'end') => {
    setModalVisible(false);

    const navigateBack = () => {
      // Go back to where we came from, or home as fallback
      goBack('/(tabs)');
    };

    if (action === 'dismiss') {
      if (modalType === 'found') {
        navigateBack();
      }
      return;
    }
    if (action === 'leave') {
      navigateBack();
    }
    if (action === 'end') {
      clearEmergencyState().then(() => navigateBack());
    }
  };

  if (isLoading) {
    return (
      <SafeAreaView
        style={[styles.container, { backgroundColor: theme.background }]}
        edges={['top']}
      >
        <View style={styles.loadingContainer}>
          <ThemedText style={{ color: theme.text }}>Loading emergency state...</ThemedText>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]} edges={['top']}>
      {/* Modal */}
      <Modal
        visible={modalVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setModalVisible(false)}
      >
        <View style={[styles.modalOverlay, { backgroundColor: theme.overlay }]}>
          <View
            style={[
              styles.modalContent,
              { backgroundColor: theme.card },
              getShadow('lg', colorScheme),
            ]}
          >
            {modalType === 'found' && (
              <>
                <View style={[styles.modalIconWrap, { backgroundColor: `${semantic.success}20` }]}>
                  <IconSymbol name="checkmark.circle.fill" size={40} color={semantic.success} />
                </View>
                <ThemedText style={[styles.modalTitle, { color: theme.text }]}>Found!</ThemedText>
                <ThemedText style={[styles.modalMessage, { color: theme.textSecondary }]}>
                  Great news! Remember to use calm, reassuring language. Offer water and rest.
                </ThemedText>
                <TouchableOpacity
                  style={[styles.modalButton, { backgroundColor: semantic.success }]}
                  onPress={() => handleModalAction('dismiss')}
                >
                  <ThemedText style={styles.modalButtonText}>OK</ThemedText>
                </TouchableOpacity>
              </>
            )}

            {modalType === 'leave' && (
              <>
                <ThemedText style={[styles.modalTitle, { color: theme.text }]}>
                  Leave Emergency Search?
                </ThemedText>
                <ThemedText style={[styles.modalMessage, { color: theme.textSecondary }]}>
                  The timer will continue in the background. You can return to resume.
                </ThemedText>
                <View style={styles.modalButtons}>
                  <TouchableOpacity
                    style={[
                      styles.modalButton,
                      styles.modalButtonOutline,
                      { borderColor: theme.border },
                    ]}
                    onPress={() => setModalVisible(false)}
                  >
                    <ThemedText style={[styles.modalButtonText, { color: theme.text }]}>
                      Stay
                    </ThemedText>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.modalButton, { backgroundColor: theme.primary }]}
                    onPress={() => handleModalAction('leave')}
                  >
                    <ThemedText style={styles.modalButtonText}>Leave</ThemedText>
                  </TouchableOpacity>
                </View>
                <TouchableOpacity
                  style={styles.modalButtonDestructive}
                  onPress={() => handleModalAction('end')}
                >
                  <ThemedText
                    style={[styles.modalButtonDestructiveText, { color: semantic.error }]}
                  >
                    End Emergency
                  </ThemedText>
                </TouchableOpacity>
              </>
            )}

            {(modalType === 'noContacts' || modalType === 'smsError') && (
              <>
                <ThemedText style={[styles.modalTitle, { color: theme.text }]}>
                  {modalType === 'noContacts' ? 'No Contacts' : 'Error'}
                </ThemedText>
                <ThemedText style={[styles.modalMessage, { color: theme.textSecondary }]}>
                  {modalType === 'noContacts'
                    ? 'Add emergency contacts to send alerts.'
                    : 'Could not open messaging app.'}
                </ThemedText>
                <TouchableOpacity
                  style={[styles.modalButton, { backgroundColor: theme.primary }]}
                  onPress={() => setModalVisible(false)}
                >
                  <ThemedText style={styles.modalButtonText}>OK</ThemedText>
                </TouchableOpacity>
              </>
            )}
          </View>
        </View>
      </Modal>

      {/* Header */}
      <View
        style={[
          styles.header,
          { borderBottomColor: theme.border, backgroundColor: theme.card },
          getShadow('sm', colorScheme),
        ]}
      >
        <TouchableOpacity
          style={styles.backButton}
          onPress={onBackPress}
          activeOpacity={0.7}
          hitSlop={8}
        >
          <IconSymbol name="chevron.left" size={22} color={theme.tint} />
        </TouchableOpacity>
        <View style={styles.headerCenter}>
          <View style={styles.headerTitleRow}>
            <IconSymbol name="exclamationmark.triangle.fill" size={18} color={semantic.error} />
            <ThemedText style={[styles.headerTitle, { color: theme.text }]}>
              Emergency Search
            </ThemedText>
          </View>
        </View>
        <View style={styles.headerRight} />
      </View>

      <ScrollView
        ref={scrollRef}
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Timer Card */}
        <View
          style={[
            styles.timerCard,
            {
              backgroundColor: timerExpired ? semantic.error : primary[700],
            },
            getShadow('md', colorScheme),
          ]}
        >
          <ThemedText style={styles.timerLabel}>
            {timerExpired ? 'TIME TO CALL 911' : 'Time remaining'}
          </ThemedText>
          <ThemedText style={styles.timerText}>{mmss}</ThemedText>
          <ThemedText style={styles.timerHint}>
            {timerExpired
              ? 'Call 911 immediately and request a Silver Alert'
              : 'Stay calm. Check each step systematically.'}
          </ThemedText>

          {/* Progress bar */}
          <View style={styles.progressContainer}>
            <View style={styles.progressBar}>
              <View style={[styles.progressFill, { flex: progress / 100 }]} />
              <View style={{ flex: (100 - progress) / 100 }} />
            </View>
            <ThemedText style={styles.progressText}>
              {checkedCount}/{steps.length} steps complete
            </ThemedText>
          </View>
        </View>

        {/* Direction hint */}
        {getDirectionHint() && (
          <View
            style={[
              styles.hintCard,
              { backgroundColor: secondary[100], borderColor: secondary[300] },
            ]}
          >
            <IconSymbol name="arrow.left.arrow.right" size={16} color={primary[800]} />
            <ThemedText style={[styles.hintText, { color: primary[800] }]}>
              {getDirectionHint()}
            </ThemedText>
          </View>
        )}

        {/* What are they wearing? */}
        {showWearingInput && (
          <View
            style={[styles.wearingCard, { backgroundColor: theme.card, borderColor: theme.border }]}
          >
            <ThemedText style={[styles.wearingLabel, { color: theme.text }]}>
              What are they wearing?
            </ThemedText>
            <ThemedText style={[styles.wearingHint, { color: theme.textSecondary }]}>
              {"You'll need this for 911"}
            </ThemedText>
            <TextInput
              style={[
                styles.wearingInput,
                {
                  backgroundColor: isDark ? neutral[800] : neutral[50],
                  color: theme.text,
                  borderColor: theme.inputBorder,
                },
              ]}
              value={wearing}
              onChangeText={setWearing}
              placeholder="e.g., Blue jacket, gray pants, white sneakers"
              placeholderTextColor={neutral[400]}
              multiline
            />
            <Pressable style={styles.wearingDismiss} onPress={() => setShowWearingInput(false)}>
              <ThemedText style={[styles.dismissText, { color: theme.textSecondary }]}>
                Dismiss
              </ThemedText>
            </Pressable>
          </View>
        )}

        {/* ── Action Buttons — ordered by priority ── */}
        <View style={styles.actionButtons}>
          {/* 1. Found Safe — most prominent, always available */}
          <TouchableOpacity
            style={[
              styles.actionButtonPrimary,
              { backgroundColor: semantic.success },
              getShadow('sm', colorScheme),
            ]}
            onPress={onMarkFound}
            activeOpacity={0.8}
          >
            <IconSymbol name="checkmark.circle.fill" size={22} color="#fff" />
            <ThemedText style={styles.actionButtonTextLarge}>Found — Safe</ThemedText>
          </TouchableOpacity>

          {/* 2. Call 911 — red, urgent state-aware */}
          <Pressable
            style={[
              styles.actionButtonPrimary,
              {
                backgroundColor: timerExpired ? semantic.error : 'transparent',
                borderWidth: timerExpired ? 0 : 2,
                borderColor: semantic.error,
              },
              timerExpired && getShadow('sm', colorScheme),
            ]}
            onPress={onCall911}
          >
            <ThemedText
              style={[
                styles.actionButtonTextLarge,
                { color: timerExpired ? '#fff' : semantic.error },
              ]}
            >
              📞 Call 911
            </ThemedText>
          </Pressable>

          {/* 3. Secondary actions — side by side */}
          <View style={styles.actionRow}>
            <Pressable
              style={[
                styles.actionButtonSecondary,
                { backgroundColor: theme.card, borderColor: theme.border },
              ]}
              onPress={onViewReadout}
            >
              <ThemedText style={[styles.actionButtonTextSmall, { color: theme.text }]}>
                📋 Info Sheet
              </ThemedText>
            </Pressable>

            <Pressable
              style={[
                styles.actionButtonSecondary,
                { backgroundColor: theme.card, borderColor: theme.border },
              ]}
              onPress={onAlertContacts}
            >
              <ThemedText style={[styles.actionButtonTextSmall, { color: theme.text }]}>
                📱 Alert Circle
              </ThemedText>
            </Pressable>
          </View>
        </View>

        {/* ── 11-Step Checklist ── */}
        <View style={styles.checklistSection}>
          <View style={styles.checklistHeader}>
            <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>
              Search Protocol
            </ThemedText>
            <ThemedText style={[styles.checklistCount, { color: theme.textSecondary }]}>
              {checkedCount}/{steps.length}
            </ThemedText>
          </View>

          {steps.map((step) => (
            <Pressable
              key={step.id}
              style={[
                styles.stepCard,
                {
                  backgroundColor: step.checked
                    ? isDark
                      ? primary[900]
                      : primary[50]
                    : theme.card,
                  borderColor:
                    step.urgent && !step.checked
                      ? semantic.error
                      : step.checked
                        ? primary[300]
                        : theme.border,
                  borderWidth: step.urgent && !step.checked ? 2 : 1,
                  opacity: step.checked ? 0.75 : 1,
                },
              ]}
              onPress={() => toggleStep(step.id)}
            >
              {/* Step number / checkmark */}
              <View
                style={[
                  styles.stepNumber,
                  {
                    backgroundColor: step.checked
                      ? primary[600]
                      : step.urgent
                        ? `${semantic.error}18`
                        : isDark
                          ? neutral[700]
                          : neutral[200],
                    borderWidth: step.urgent && !step.checked ? 1.5 : 0,
                    borderColor: step.urgent && !step.checked ? semantic.error : 'transparent',
                  },
                ]}
              >
                {step.checked ? (
                  <IconSymbol name="checkmark" size={14} color="#fff" />
                ) : (
                  <ThemedText
                    style={[
                      styles.stepNumberText,
                      {
                        color: step.urgent ? semantic.error : isDark ? neutral[300] : neutral[600],
                      },
                    ]}
                  >
                    {step.step}
                  </ThemedText>
                )}
              </View>

              <View style={styles.stepContent}>
                <View style={styles.stepHeader}>
                  <ThemedText
                    style={[
                      styles.stepTitle,
                      { color: theme.text },
                      step.checked && styles.stepTitleChecked,
                    ]}
                  >
                    {step.title}
                  </ThemedText>
                  {step.urgent && !step.checked && (
                    <View style={[styles.urgentBadge, { backgroundColor: semantic.error }]}>
                      <ThemedText style={styles.urgentText}>PRIORITY</ThemedText>
                    </View>
                  )}
                </View>
                <ThemedText style={[styles.stepDescription, { color: theme.textSecondary }]}>
                  {step.description}
                </ThemedText>
                {step.hint && (
                  <ThemedText style={[styles.stepHint, { color: primary[600] }]}>
                    💡 {step.hint}
                  </ThemedText>
                )}

                {/* Show saved destinations for familiar places step */}
                {step.id === 'familiar_places' && destinations.length > 0 && !step.checked && (
                  <View style={[styles.destinationsList, { borderTopColor: theme.border }]}>
                    <ThemedText style={[styles.destinationsLabel, { color: theme.textSecondary }]}>
                      Saved places to check:
                    </ThemedText>
                    {destinations.slice(0, 3).map((dest) => (
                      <ThemedText
                        key={dest.id}
                        style={[styles.destinationItem, { color: primary[600] }]}
                      >
                        • {dest.name} {dest.address ? `(${dest.address})` : ''}
                      </ThemedText>
                    ))}
                    {destinations.length > 3 && (
                      <ThemedText style={[styles.destinationItem, { color: neutral[500] }]}>
                        +{destinations.length - 3} more in Places
                      </ThemedText>
                    )}
                  </View>
                )}
              </View>
            </Pressable>
          ))}
        </View>

        {/* De-escalation Tips */}
        <View
          style={[
            styles.tipsCard,
            {
              backgroundColor: isDark ? `${primary[900]}60` : primary[50],
              borderColor: isDark ? primary[700] : primary[100],
            },
          ]}
        >
          <ThemedText style={[styles.tipsTitle, { color: isDark ? primary[200] : primary[800] }]}>
            When you find them
          </ThemedText>
          <ThemedText style={[styles.tipsText, { color: isDark ? primary[300] : primary[700] }]}>
            • Approach calmly from the front{'\n'}• Speak slowly with short sentences{'\n'}• Give
            time to respond{'\n'}• Offer to walk with them{'\n'}• Don&apos;t argue or correct
            {profile?.deescalationTechniques ? `\n• ${profile.deescalationTechniques}` : ''}
          </ThemedText>
        </View>

        <View style={{ height: Spacing.xxl }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    borderBottomWidth: 1,
    minHeight: 52,
  },
  backButton: {
    width: 44,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  headerCenter: {
    flex: 1,
    alignItems: 'center',
  },
  headerTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  headerTitle: {
    ...Typography.title,
  },
  headerRight: {
    width: 44,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing.lg,
    gap: Spacing.lg,
  },

  // Timer card
  timerCard: {
    borderRadius: Radius.xl,
    padding: Spacing.xl,
    alignItems: 'center',
    gap: Spacing.xs,
  },
  timerLabel: {
    color: 'rgba(255,255,255,0.75)',
    ...Typography.caption,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  timerText: {
    color: '#fff',
    fontSize: 60,
    fontWeight: '700',
    letterSpacing: 2,
    lineHeight: 72,
  },
  timerHint: {
    color: 'rgba(255,255,255,0.9)',
    ...Typography.body,
    textAlign: 'center',
    marginTop: Spacing.xs,
  },
  progressContainer: {
    width: '100%',
    marginTop: Spacing.md,
    gap: Spacing.xs,
  },
  progressBar: {
    height: 6,
    backgroundColor: 'rgba(255,255,255,0.25)',
    borderRadius: 3,
    overflow: 'hidden',
    flexDirection: 'row',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#fff',
    borderRadius: 3,
  },
  progressText: {
    color: 'rgba(255,255,255,0.9)',
    ...Typography.caption,
    textAlign: 'center',
  },

  // Direction hint
  hintCard: {
    borderRadius: Radius.lg,
    padding: Spacing.md,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  hintText: {
    ...Typography.bodyBold,
  },

  // Wearing card
  wearingCard: {
    borderRadius: Radius.lg,
    padding: Spacing.lg,
    borderWidth: 1,
    gap: Spacing.sm,
  },
  wearingLabel: {
    ...Typography.bodyBold,
  },
  wearingHint: {
    ...Typography.caption,
    marginTop: -Spacing.xs,
  },
  wearingInput: {
    borderRadius: Radius.md,
    padding: Spacing.md,
    ...Typography.body,
    borderWidth: 1,
    minHeight: 64,
    textAlignVertical: 'top',
  },
  wearingDismiss: {
    alignSelf: 'flex-end',
    paddingVertical: Spacing.xs,
  },
  dismissText: {
    ...Typography.caption,
  },

  // Action buttons
  actionButtons: {
    gap: Spacing.sm,
  },
  actionButtonPrimary: {
    flexDirection: 'row',
    borderRadius: Radius.lg,
    paddingVertical: Spacing.lg,
    paddingHorizontal: Spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    minHeight: 56,
  },
  actionButtonTextLarge: {
    color: '#fff',
    ...Typography.bodyBold,
    fontSize: 18,
  },
  actionRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  actionButtonSecondary: {
    flex: 1,
    borderRadius: Radius.lg,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    borderWidth: 1,
  },
  actionButtonTextSmall: {
    ...Typography.bodyBold,
  },

  // Checklist
  checklistSection: {
    gap: Spacing.sm,
  },
  checklistHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.xs,
  },
  sectionTitle: {
    ...Typography.title,
  },
  checklistCount: {
    ...Typography.bodyBold,
  },
  stepCard: {
    flexDirection: 'row',
    borderRadius: Radius.lg,
    padding: Spacing.md,
    gap: Spacing.md,
    alignItems: 'flex-start',
  },
  stepNumber: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    marginTop: 2,
  },
  stepNumberText: {
    ...Typography.caption,
    fontWeight: '700',
  },
  stepContent: {
    flex: 1,
    gap: Spacing.xs,
  },
  stepHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    flexWrap: 'wrap',
  },
  stepTitle: {
    ...Typography.bodyBold,
    flex: 1,
  },
  stepTitleChecked: {
    textDecorationLine: 'line-through',
    opacity: 0.6,
  },
  stepDescription: {
    ...Typography.body,
    lineHeight: 20,
  },
  stepHint: {
    ...Typography.caption,
    fontStyle: 'italic',
  },
  urgentBadge: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xxs,
    borderRadius: Radius.sm,
  },
  urgentText: {
    color: '#fff',
    ...Typography.small,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  destinationsList: {
    marginTop: Spacing.sm,
    paddingTop: Spacing.sm,
    borderTopWidth: 1,
    gap: Spacing.xxs,
  },
  destinationsLabel: {
    ...Typography.small,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: Spacing.xxs,
  },
  destinationItem: {
    ...Typography.caption,
  },

  // Tips card
  tipsCard: {
    borderRadius: Radius.lg,
    padding: Spacing.lg,
    gap: Spacing.sm,
    borderWidth: 1,
  },
  tipsTitle: {
    ...Typography.bodyBold,
  },
  tipsText: {
    ...Typography.body,
    lineHeight: 24,
  },

  // Loading
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },

  // Modal
  modalOverlay: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.xl,
  },
  modalContent: {
    borderRadius: Radius.xl,
    padding: Spacing.xl,
    width: '100%',
    maxWidth: 340,
    alignItems: 'center',
    gap: Spacing.md,
  },
  modalIconWrap: {
    width: 72,
    height: 72,
    borderRadius: 36,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.xs,
  },
  modalTitle: {
    ...Typography.title,
    textAlign: 'center',
  },
  modalMessage: {
    ...Typography.body,
    textAlign: 'center',
    lineHeight: 22,
  },
  modalButtons: {
    flexDirection: 'row',
    gap: Spacing.md,
    width: '100%',
    marginTop: Spacing.xs,
  },
  modalButton: {
    flex: 1,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.lg,
    borderRadius: Radius.md,
    alignItems: 'center',
    minHeight: 48,
    justifyContent: 'center',
  },
  modalButtonOutline: {
    backgroundColor: 'transparent',
    borderWidth: 1,
  },
  modalButtonDestructive: {
    paddingVertical: Spacing.md,
    marginTop: Spacing.xs,
  },
  modalButtonDestructiveText: {
    ...Typography.bodyBold,
  },
  modalButtonText: {
    ...Typography.bodyBold,
    color: '#fff',
  },
});
