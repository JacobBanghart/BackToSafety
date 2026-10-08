/**
 * Emergency Search Screen
 * 11-step guided protocol for finding someone who has wandered
 */

import { goBack, setPreviousRoute } from '@/utils/navigation';
import { track } from '@/utils/analytics';
import * as Haptics from 'expo-haptics';
import * as SMS from 'expo-sms';
import { Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Linking,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Vibration,
  View,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { KeyboardAvoidingScroll } from '@/components/KeyboardAvoidingScroll';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { ScreenHeader } from '@/components/ScreenHeader';
import { Colors, semantic, primary, neutral, secondary } from '@/constants/Colors';
import { getShadow } from '@/constants/Shadows';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import { getEmergencyContacts } from '@/database/contacts';
import { Destination, getDestinations } from '@/database/destinations';
import { createIncident, updateIncident, type Incident } from '@/database/incidents';
import {
  buildAlertSms,
  buildInitialSteps,
  countdownAlerts,
  directionHint,
  formatCountdown,
  SEARCH_WINDOW_SECONDS,
  secondsRemaining,
  type ActiveEmergency,
  type ChecklistStep,
} from '@/utils/emergency';
import {
  clearActiveEmergency,
  loadActiveEmergency,
  saveActiveEmergency,
} from '@/utils/activeEmergency';
import { now } from '@/utils/clock';
import { normalizeUniqueSmsRecipients } from '@/utils/phone';

export default function EmergencyScreen() {
  const router = useRouter();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const isDark = colorScheme === 'dark';
  const { setLastSeen, profile } = useProfile();
  const { t } = useTranslation('emergency');
  const { t: tCommon } = useTranslation('common');
  const emergencyNumber = tCommon('emergencyNumber');

  const [isLoading, setIsLoading] = useState(true);
  const [secondsLeft, setSecondsLeft] = useState<number>(SEARCH_WINDOW_SECONDS);
  const [startedAt, setStartedAt] = useState<Date>(new Date());
  const [wearing, setWearing] = useState('');
  const [steps, setSteps] = useState<ChecklistStep[]>(() => buildInitialSteps(t, emergencyNumber));
  const [destinations, setDestinations] = useState<Destination[]>([]);
  const [showWearingInput, setShowWearingInput] = useState(true);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'found' | 'leave' | 'noContacts' | 'smsError' | null>(
    null,
  );

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // The incidents row for this emergency.
  const incidentIdRef = useRef<number | undefined>(undefined);
  const scrollRef = useRef<ScrollView>(null);

  const timerExpired = secondsLeft === 0;
  const checkedCount = steps.filter((s) => s.checked).length;
  const progress = (checkedCount / steps.length) * 100;

  // Save emergency state to storage
  const saveEmergencyState = useCallback(async (state: ActiveEmergency) => {
    try {
      await saveActiveEmergency({
        ...state,
        ...(incidentIdRef.current !== undefined ? { incidentId: incidentIdRef.current } : {}),
      });
    } catch (error) {
      console.error('Failed to save emergency state:', error);
    }
  }, []);

  // Clear emergency state from storage
  const clearEmergencyState = useCallback(async () => {
    try {
      await clearActiveEmergency();
    } catch (error) {
      console.error('Failed to clear emergency state:', error);
    }
  }, []);

  // Record what happened in the incidents table (F-22). Never blocks the UI: a
  // failed write must not delay a 911 call or the found flow.
  const recordIncident = useCallback(
    (update: Partial<Incident>) => {
      const checked = steps.filter((s) => s.checked).map((s) => s.id);
      const details = { ...update, areasChecked: checked, wearing: wearing || undefined };
      void (async () => {
        try {
          if (incidentIdRef.current === undefined) {
            // An emergency started before incidents were recorded: create its row now.
            incidentIdRef.current = await createIncident({ startedAt: startedAt.toISOString() });
          }
          await updateIncident(incidentIdRef.current, details);
        } catch (error) {
          console.error('Failed to record incident:', error);
        }
      })();
    },
    [steps, wearing, startedAt],
  );

  // Load destinations for familiar places hints
  useEffect(() => {
    getDestinations().then(setDestinations).catch(console.error);
  }, []);

  // Load or create emergency state on mount
  useEffect(() => {
    const initEmergency = async () => {
      try {
        const state = await loadActiveEmergency();

        if (state) {
          // Resume existing emergency
          const started = new Date(state.startedAt);
          const remaining = secondsRemaining(started.getTime(), now());

          setStartedAt(started);
          setSecondsLeft(remaining);
          incidentIdRef.current = state.incidentId;
          setLastSeen({ time: state.startedAt });
          setWearing(state.wearing);
          setShowWearingInput(true);
          setSteps((prev) =>
            prev.map((step) => ({
              ...step,
              checked: state.checkedSteps.includes(step.id),
            })),
          );

          setIsLoading(false);

          startTimer(started.getTime());
          return;
        }

        // Start new emergency
        const startedNow = new Date(now());
        setStartedAt(startedNow);

        track('emergency_started');
        try {
          incidentIdRef.current = await createIncident({ startedAt: startedNow.toISOString() });
        } catch (error) {
          console.error('Failed to create incident:', error);
        }
        await saveEmergencyState({
          startedAt: startedNow.toISOString(),
          wearing: '',
          checkedSteps: [],
          isActive: true,
        });

        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
        setLastSeen({ time: startedNow.toISOString() });

        setIsLoading(false);
        startTimer(startedNow.getTime());
      } catch (error) {
        console.error('Failed to init emergency:', error);
        setIsLoading(false);
        startTimer(now());
      }
    };

    initEmergency();

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- Only run on mount
  }, []);

  // Countdown. Each tick re-derives the time left from the start time: JS timers
  // pause while the app is in the background, so counting ticks would fall behind
  // real time and the expiry alert would come late (F-16).
  const startTimer = (startedAtMs: number) => {
    if (intervalRef.current) clearInterval(intervalRef.current);

    if (secondsRemaining(startedAtMs, now()) <= 0) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      return;
    }

    intervalRef.current = setInterval(() => {
      setSecondsLeft((prev) => {
        const next = secondsRemaining(startedAtMs, now());
        for (const alert of countdownAlerts(prev, next)) {
          if (alert === 'warning') {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
          } else {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
            Vibration.vibrate([0, 500, 200, 500]);
          }
        }
        if (next === 0 && intervalRef.current) clearInterval(intervalRef.current);
        return next;
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

  const mmss = useMemo(() => formatCountdown(secondsLeft), [secondsLeft]);

  const toggleStep = useCallback(
    (id: string) => {
      const currentStep = steps.find((step) => step.id === id);
      const isCheckingStep = currentStep ? !currentStep.checked : true;

      if (isCheckingStep) {
        track('emergency_step_completed', { step: id });
      }

      if (Platform.OS !== 'web') {
        const style = isCheckingStep
          ? Haptics.ImpactFeedbackStyle.Medium
          : Haptics.ImpactFeedbackStyle.Light;
        void Haptics.impactAsync(style).catch(() => undefined);
      }

      setSteps((prev) =>
        prev.map((step) => (step.id === id ? { ...step, checked: !step.checked } : step)),
      );
    },
    [steps],
  );

  const onMarkFound = async () => {
    // Clear state first and wait for it
    await clearEmergencyState();
    // The info sheet's "last seen" belongs to the emergency; it ends with it.
    setLastSeen({});
    track('emergency_completed', { checked_count: steps.filter((s) => s.checked).length });
    recordIncident({ outcome: 'found', endedAt: new Date(now()).toISOString() });
    setModalType('found');
    setModalVisible(true);
  };

  const onCall911 = async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    track('emergency_911_called', {
      seconds_elapsed: SEARCH_WINDOW_SECONDS - secondsLeft,
      checked_count: checkedCount,
    });
    // Calling always marks the step done; a second call must not un-check it (F-17).
    if (!steps.find((step) => step.id === 'call_911')?.checked) toggleStep('call_911');
    Linking.openURL(`tel:${emergencyNumber}`);
    recordIncident({ outcome: '911_called' });
  };

  const onViewReadout = () => {
    track('screen_viewed', { screen: 'readout', source: 'emergency' });
    setPreviousRoute('/emergency');
    router.push('/readout' as Href);
  };

  const onAlertContacts = async () => {
    const contactsToNotify = await getEmergencyContacts();

    if (contactsToNotify.length === 0) {
      setModalType('noContacts');
      setModalVisible(true);
      return;
    }

    const message = buildAlertSms(t, {
      name: profile?.name,
      startedTime: startedAt.toLocaleTimeString(),
      wearing,
    });

    const recipients = normalizeUniqueSmsRecipients(
      contactsToNotify.map((contact) => contact.phone),
    );

    if (recipients.length === 0) {
      setModalType('noContacts');
      setModalVisible(true);
      return;
    }

    try {
      const isSmsAvailable = await SMS.isAvailableAsync();

      if (!isSmsAvailable) {
        setModalType('smsError');
        setModalVisible(true);
        return;
      }

      await SMS.sendSMSAsync(recipients, message);
      track('emergency_contacts_alerted', { recipient_count: recipients.length });
    } catch {
      const recipientList = recipients.join(',');
      const bodySeparator = Platform.OS === 'ios' ? '&' : '?';
      const fallbackSmsUrl = `sms:${recipientList}${bodySeparator}body=${encodeURIComponent(message)}`;

      try {
        await Linking.openURL(fallbackSmsUrl);
      } catch {
        setModalType('smsError');
        setModalVisible(true);
      }
    }
  };

  const getDirectionHint = () => directionHint(t, profile?.dominantHand);

  const onBackPress = () => {
    setModalType('leave');
    setModalVisible(true);
  };

  const handleModalAction = (action: 'dismiss' | 'leave' | 'end') => {
    setModalVisible(false);

    const navigateBack = () => {
      // Go back to where we came from, or home as fallback
      goBack('/');
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
      track('emergency_cancelled', { checked_count: steps.filter((s) => s.checked).length });
      // Ended without an outcome: stamp the end time, keep the outcome as it was.
      recordIncident({ endedAt: new Date(now()).toISOString() });
      setLastSeen({});
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
          <ThemedText style={{ color: theme.text }}>{t('loading')}</ThemedText>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView
      testID="emergency-screen"
      style={[styles.container, { backgroundColor: theme.background }]}
      edges={['top']}
    >
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
                <ThemedText style={[styles.modalTitle, { color: theme.text }]}>
                  {t('modal.found.title')}
                </ThemedText>
                <ThemedText style={[styles.modalMessage, { color: theme.textSecondary }]}>
                  {t('modal.found.message')}
                </ThemedText>
                <TouchableOpacity
                  style={[
                    styles.modalButton,
                    styles.modalButtonFullWidth,
                    { backgroundColor: semantic.success },
                  ]}
                  testID="emergency-modal-found-ok"
                  onPress={() => handleModalAction('dismiss')}
                >
                  <ThemedText style={styles.modalButtonText}>{tCommon('ok')}</ThemedText>
                </TouchableOpacity>
              </>
            )}

            {modalType === 'leave' && (
              <>
                <ThemedText style={[styles.modalTitle, { color: theme.text }]}>
                  {t('modal.leave.title')}
                </ThemedText>
                <ThemedText style={[styles.modalMessage, { color: theme.textSecondary }]}>
                  {t('modal.leave.message')}
                </ThemedText>
                <View style={styles.modalButtons}>
                  <TouchableOpacity
                    style={[
                      styles.modalButton,
                      styles.modalButtonRowItem,
                      styles.modalButtonOutline,
                      { borderColor: theme.border },
                    ]}
                    testID="emergency-modal-leave-stay"
                    onPress={() => setModalVisible(false)}
                  >
                    <ThemedText style={[styles.modalButtonText, { color: theme.text }]}>
                      {t('modal.leave.stay')}
                    </ThemedText>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[
                      styles.modalButton,
                      styles.modalButtonRowItem,
                      { backgroundColor: theme.primary },
                    ]}
                    testID="emergency-modal-leave-leave"
                    onPress={() => {
                      track('emergency_leave');
                      handleModalAction('leave');
                    }}
                  >
                    <ThemedText style={styles.modalButtonText}>{t('modal.leave.leave')}</ThemedText>
                  </TouchableOpacity>
                </View>
                <TouchableOpacity
                  style={styles.modalButtonDestructive}
                  testID="emergency-modal-leave-end"
                  onPress={() => handleModalAction('end')}
                >
                  <ThemedText
                    style={[styles.modalButtonDestructiveText, { color: semantic.error }]}
                  >
                    {t('modal.leave.end')}
                  </ThemedText>
                </TouchableOpacity>
              </>
            )}

            {(modalType === 'noContacts' || modalType === 'smsError') && (
              <>
                <ThemedText style={[styles.modalTitle, { color: theme.text }]}>
                  {modalType === 'noContacts'
                    ? t('modal.noContacts.title')
                    : t('modal.smsError.title')}
                </ThemedText>
                <ThemedText style={[styles.modalMessage, { color: theme.textSecondary }]}>
                  {modalType === 'noContacts'
                    ? t('modal.noContacts.message')
                    : t('modal.smsError.message')}
                </ThemedText>
                <TouchableOpacity
                  style={[
                    styles.modalButton,
                    styles.modalButtonFullWidth,
                    { backgroundColor: theme.primary },
                  ]}
                  testID="emergency-modal-info-ok"
                  onPress={() => setModalVisible(false)}
                >
                  <ThemedText style={styles.modalButtonText}>{tCommon('ok')}</ThemedText>
                </TouchableOpacity>
              </>
            )}
          </View>
        </View>
      </Modal>

      <ScreenHeader
        testID="emergency"
        title={t('screenTitle')}
        onBack={onBackPress}
        titleIcon={{ name: 'exclamationmark.triangle.fill', color: semantic.error, size: 18 }}
      />

      <KeyboardAvoidingScroll
        ref={scrollRef}
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
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
          <ThemedText testID="emergency-timer-label" style={styles.timerLabel}>
            {timerExpired ? t('timer.labelExpired', { emergencyNumber }) : t('timer.labelActive')}
          </ThemedText>
          <ThemedText testID="emergency-timer" style={styles.timerText}>
            {mmss}
          </ThemedText>
          <ThemedText style={styles.timerHint}>
            {timerExpired ? t('timer.hintExpired', { emergencyNumber }) : t('timer.hintActive')}
          </ThemedText>

          {/* Progress bar */}
          <View style={styles.progressContainer}>
            <View style={styles.progressBar}>
              <View style={[styles.progressFill, { flex: progress / 100 }]} />
              <View style={{ flex: (100 - progress) / 100 }} />
            </View>
            <ThemedText testID="emergency-progress" style={styles.progressText}>
              {t('timer.stepsProgress', { checked: checkedCount, total: steps.length })}
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
              {t('wearing.label')}
            </ThemedText>
            <ThemedText style={[styles.wearingHint, { color: theme.textSecondary }]}>
              {t('wearing.hint', { emergencyNumber })}
            </ThemedText>
            <TextInput
              testID="emergency-wearing-input"
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
              placeholder={t('wearing.placeholder')}
              placeholderTextColor={neutral[400]}
              multiline
            />
            <Pressable
              style={styles.wearingDismiss}
              testID="emergency-wearing-dismiss"
              onPress={() => setShowWearingInput(false)}
            >
              <ThemedText style={[styles.dismissText, { color: theme.textSecondary }]}>
                {t('wearing.dismiss')}
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
            testID="emergency-found"
            onPress={onMarkFound}
            activeOpacity={0.8}
          >
            <IconSymbol name="checkmark.circle.fill" size={22} color={Colors.light.textOnPrimary} />
            <ThemedText style={styles.actionButtonTextLarge}>{t('actions.foundSafe')}</ThemedText>
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
            testID="emergency-call-911"
            onPress={onCall911}
          >
            <ThemedText
              style={[
                styles.actionButtonTextLarge,
                { color: timerExpired ? Colors.light.textOnPrimary : semantic.error },
              ]}
            >
              {t('actions.call911', { emergencyNumber })}
            </ThemedText>
          </Pressable>

          {/* 3. Secondary actions — side by side */}
          <View style={styles.actionRow}>
            <Pressable
              style={[
                styles.actionButtonSecondary,
                { backgroundColor: theme.card, borderColor: theme.border },
              ]}
              testID="emergency-readout"
              onPress={onViewReadout}
            >
              <ThemedText style={[styles.actionButtonTextSmall, { color: theme.text }]}>
                {t('actions.infoSheet')}
              </ThemedText>
            </Pressable>

            <Pressable
              style={[
                styles.actionButtonSecondary,
                { backgroundColor: theme.card, borderColor: theme.border },
              ]}
              testID="emergency-alert-contacts"
              onPress={onAlertContacts}
            >
              <ThemedText style={[styles.actionButtonTextSmall, { color: theme.text }]}>
                {t('actions.alertCircle')}
              </ThemedText>
            </Pressable>
          </View>
        </View>

        {/* ── 11-Step Checklist ── */}
        <View style={styles.checklistSection}>
          <View style={styles.checklistHeader}>
            <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>
              {t('checklist.title')}
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
              testID={`emergency-step-${step.id}`}
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
                  <IconSymbol name="checkmark" size={14} color={Colors.light.textOnPrimary} />
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
                      <ThemedText style={styles.urgentText}>{t('checklist.priority')}</ThemedText>
                    </View>
                  )}
                </View>
                <ThemedText style={[styles.stepDescription, { color: theme.textSecondary }]}>
                  {step.description}
                </ThemedText>
                {step.hint && (
                  <ThemedText style={[styles.stepHint, { color: primary[600] }]}>
                    {`💡 ${step.hint}`}
                  </ThemedText>
                )}

                {/* Show saved destinations for familiar places step */}
                {step.id === 'familiar_places' && destinations.length > 0 && !step.checked && (
                  <View style={[styles.destinationsList, { borderTopColor: theme.border }]}>
                    <ThemedText style={[styles.destinationsLabel, { color: theme.textSecondary }]}>
                      {t('checklist.savedPlaces')}
                    </ThemedText>
                    <ThemedText
                      style={[styles.destinationItem, { color: primary[600] }]}
                      numberOfLines={2}
                      ellipsizeMode="tail"
                    >
                      {destinations
                        .slice(0, 5)
                        .map((d) => d.name)
                        .join(' • ')}
                      {destinations.length > 5 ? ` +${destinations.length - 5}` : ''}
                    </ThemedText>
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
            {t('tips.title')}
          </ThemedText>
          <ThemedText
            style={[styles.tipsText, { color: isDark ? primary[300] : primary[700] }]}
            numberOfLines={5}
            ellipsizeMode="tail"
          >
            {profile?.deescalationTechniques
              ? `${t('tips.body')}\n• ${profile.deescalationTechniques}`
              : t('tips.body')}
          </ThemedText>
        </View>

        <View style={{ height: Spacing.xxl }} />
      </KeyboardAvoidingScroll>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
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
    color: Colors.light.textOnPrimary,
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
    backgroundColor: Colors.light.textOnPrimary,
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
    ...Typography.body,
    lineHeight: 20,
    borderWidth: 1,
    minHeight: 64,
    maxHeight: 120,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
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
    color: Colors.light.textOnPrimary,
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
    color: Colors.light.textOnPrimary,
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
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.lg,
    borderRadius: Radius.md,
    alignItems: 'center',
    minHeight: 48,
    justifyContent: 'center',
  },
  modalButtonFullWidth: {
    width: '100%',
  },
  modalButtonRowItem: {
    flex: 1,
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
    color: Colors.light.textOnPrimary,
  },
});
