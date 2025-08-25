import { useEffect, useMemo, useRef, useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Vibration } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import * as Haptics from 'expo-haptics';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';

type ChecklistItem = {
  id: string;
  title: string;
  hint?: string;
  checked: boolean;
};

const SEARCH_WINDOW_SECONDS = 15 * 60; // 15 minutes

export default function EmergencyScreen() {
  const router = useRouter();
  const [secondsLeft, setSecondsLeft] = useState<number>(SEARCH_WINDOW_SECONDS);
  const [startedAt] = useState<Date>(new Date());
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [items, setItems] = useState<ChecklistItem[]>([
    { id: 'yard', title: 'Home & yard', hint: 'Front/back yard, porch, garage, shed', checked: false },
    { id: 'street', title: 'Street outside home', hint: 'Walk 100–200 ft in both directions', checked: false },
    { id: 'route', title: 'Usual route', hint: 'Favorite walks, coffee shop, mailbox', checked: false },
    { id: 'edges', title: 'Edges & hazards', hint: 'Water, tree/brush lines, fences/gates', checked: false },
    { id: 'dest', title: 'Likely destinations', hint: 'Past work, faith community, friends', checked: false },
  ]);

  const allChecked = useMemo(() => items.every(i => i.checked), [items]);

  useEffect(() => {
    // Start timer on mount
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    intervalRef.current = setInterval(() => {
      setSecondsLeft(prev => {
        if (prev <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
          Vibration.vibrate(800);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  const mmss = useMemo(() => {
    const m = Math.floor(secondsLeft / 60)
      .toString()
      .padStart(2, '0');
    const s = (secondsLeft % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }, [secondsLeft]);

  const toggleItem = (id: string) =>
    setItems(prev => prev.map(i => (i.id === id ? { ...i, checked: !i.checked } : i)));

  const onMarkFound = async () => {
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    router.back();
  };

  const onCall911 = async () => {
    const script = `I\'m reporting a missing vulnerable adult with dementia. Last seen around ${
      startedAt.toLocaleTimeString() || 'now'
    }. Location: home. Wearing [clothes]. Risks: [medical]. Please advise on issuing a local Silver/Purple Alert.`;
    // Open dialer. Many regions accept 911; this simply opens the dialer with 911.
    try {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
      Linking.openURL('tel:911');
    } catch (e) {
      // no-op
    }
    // Optionally, copy the script to clipboard in future.
    // Clipboard.setStringAsync(script)
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'left', 'right', 'bottom']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
      <ThemedText type="title">Wandering Emergency</ThemedText>
      <ThemedText type="subtitle">Guided search — call 911 if not found by 15 minutes</ThemedText>

      <ThemedView style={styles.timerCard}>
        <ThemedText type="title" style={styles.timerText}>
          {mmss}
        </ThemedText>
        <ThemedText style={styles.timerHint}>
          Stay calm and check nearby places. Mark each step as you go.
        </ThemedText>
      </ThemedView>

      <ThemedView style={styles.list}>
        {items.map(item => (
          <Pressable key={item.id} onPress={() => toggleItem(item.id)} style={styles.row}>
            <ThemedView style={[styles.checkbox, item.checked && styles.checkboxChecked]} />
            <ThemedView style={styles.rowTextWrap}>
              <ThemedText type="defaultSemiBold">{item.title}</ThemedText>
              {!!item.hint && <ThemedText style={styles.hint}>{item.hint}</ThemedText>}
            </ThemedView>
          </Pressable>
        ))}
      </ThemedView>

  <ThemedView style={styles.actions}>
        <Pressable onPress={onMarkFound} style={[styles.button, styles.found]}> 
          <ThemedText style={styles.buttonText}>Found — De-escalate</ThemedText>
        </Pressable>
        {secondsLeft === 0 ? (
          <Pressable onPress={onCall911} style={[styles.button, styles.call]}> 
            <ThemedText style={styles.buttonText}>Call 911</ThemedText>
          </Pressable>
        ) : (
          <Pressable onPress={onCall911} style={[styles.button, styles.call, styles.callDimmed]}> 
            <ThemedText style={styles.buttonText}>If not found, Call 911</ThemedText>
          </Pressable>
        )}
      </ThemedView>

      <ThemedView style={styles.tips}>
        <ThemedText type="defaultSemiBold">Quiet approach tips</ThemedText>
        <ThemedText style={styles.tipText}>
          Speak slowly, short sentences, give time to respond. Offer to call their caregiver.
        </ThemedText>
      </ThemedView>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    gap: 12,
  },
  timerCard: {
    borderRadius: 12,
  paddingVertical: 20,
  paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.08)',
    alignItems: 'center',
    gap: 6,
  },
  timerText: {
    fontSize: 48,
  lineHeight: 56,
    letterSpacing: 1,
  },
  timerHint: { textAlign: 'center', opacity: 0.8 },
  list: { gap: 8, marginTop: 4 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 10,
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: 'rgba(0,0,0,0.4)',
  },
  checkboxChecked: {
    backgroundColor: 'rgba(0, 122, 255, 0.9)',
    borderColor: 'rgba(0, 122, 255, 1)',
  },
  rowTextWrap: { flex: 1 },
  hint: { opacity: 0.7, marginTop: 2 },
  actions: { marginTop: 'auto', gap: 8 },
  button: {
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
  },
  found: {
    backgroundColor: '#2ecc71',
  },
  call: {
    backgroundColor: '#e74c3c',
  },
  callDimmed: {
    opacity: 0.9,
  },
  buttonText: { color: 'white', fontSize: 16, fontWeight: '600' },
  tips: { marginTop: 8 },
  tipText: { opacity: 0.8 },
});
