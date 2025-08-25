import * as Haptics from 'expo-haptics';
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Vibration } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useProfile } from '@/context/ProfileContext';
import { useColorScheme } from '@/hooks/useColorScheme';

type ChecklistItem = {
  id: string;
  title: string;
  hint?: string;
  checked: boolean;
};

const SEARCH_WINDOW_SECONDS = 15 * 60; // 15 minutes

export default function EmergencyScreen() {
  const router = useRouter();
  const colorScheme = useColorScheme() ?? 'light';
  const { setLastSeen, profile, addIncident } = useProfile();
  const [secondsLeft, setSecondsLeft] = useState<number>(SEARCH_WINDOW_SECONDS);
  const [startedAt] = useState<Date>(new Date());
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [containerHeight, setContainerHeight] = useState(0);
  const [contentHeight, setContentHeight] = useState(0);
  const [items, setItems] = useState<ChecklistItem[]>([
    {
      id: 'yard',
      title: 'Home & yard',
      hint: 'Front/back yard, porch, garage, shed',
      checked: false,
    },
    {
      id: 'street',
      title: 'Street outside home',
      hint: 'Walk 100–200 ft in both directions',
      checked: false,
    },
    {
      id: 'route',
      title: 'Usual route',
      hint: 'Favorite walks, coffee shop, mailbox',
      checked: false,
    },
    {
      id: 'edges',
      title: 'Edges & hazards',
      hint: 'Water, tree/brush lines, fences/gates',
      checked: false,
    },
    {
      id: 'dest',
      title: 'Likely destinations',
      hint: 'Past work, faith community, friends',
      checked: false,
    },
  ]);

  const allChecked = useMemo(() => items.every((i) => i.checked), [items]);

  useEffect(() => {
    // Start timer on mount
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    // Record last seen time and a placeholder location (real GPS wired later)
    setLastSeen({ time: new Date().toISOString() });
    // Try to capture current GPS
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status === 'granted') {
          const loc = await Location.getCurrentPositionAsync({
            accuracy: Location.Accuracy.Balanced,
          });
          setLastSeen({
            time: new Date().toISOString(),
            coords: {
              lat: loc.coords.latitude,
              lon: loc.coords.longitude,
              accuracy: loc.coords.accuracy ?? undefined,
            },
          });
        }
      } catch {}
    })();

    intervalRef.current = setInterval(() => {
      setSecondsLeft((prev) => {
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
    setItems((prev) => prev.map((i) => (i.id === id ? { ...i, checked: !i.checked } : i)));

  const onMarkFound = async () => {
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    addIncident({ at: new Date().toISOString(), outcome: 'found' });
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
    addIncident({ at: new Date().toISOString(), outcome: '911_called' });
    // Optionally, copy the script to clipboard in future.
    // Clipboard.setStringAsync(script)
  };

  const shouldScroll = contentHeight > containerHeight + 1;

  return (
    <SafeAreaView
      style={styles.container}
      edges={['top', 'left', 'right', 'bottom']}
      onLayout={(e) => setContainerHeight(e.nativeEvent.layout.height)}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        onContentSizeChange={(_, h) => setContentHeight(h)}
        scrollEnabled={shouldScroll}
        bounces={shouldScroll}
        showsVerticalScrollIndicator={shouldScroll}
        overScrollMode={shouldScroll ? 'auto' : 'never'}
      >
        <ThemedText type="title">Wandering Emergency</ThemedText>
        <ThemedText style={styles.meta}>
          Last seen: {new Date().toLocaleTimeString()} • Direction hint:{' '}
          {profile.dominantHand === 'left'
            ? 'May veer left'
            : profile.dominantHand === 'right'
              ? 'May veer right'
              : '—'}
        </ThemedText>
        <ThemedText style={styles.subtitleSmall}>
          Guided search — call 911 if not found by 15 minutes
        </ThemedText>

        <ThemedView
          style={[
            styles.timerCard,
            {
              backgroundColor: Colors[colorScheme].card,
              borderColor: Colors[colorScheme].border,
            },
          ]}
        >
          <ThemedText type="title" style={styles.timerText}>
            {mmss}
          </ThemedText>
          <ThemedText style={styles.timerHint}>
            Stay calm and check nearby places. Mark each step as you go.
          </ThemedText>
        </ThemedView>

        <ThemedView style={styles.list}>
          {items.map((item) => (
            <Pressable
              key={item.id}
              onPress={() => toggleItem(item.id)}
              style={[styles.row, { borderColor: Colors[colorScheme].border }]}
            >
              <ThemedView
                style={[
                  styles.checkbox,
                  {
                    borderColor:
                      colorScheme === 'dark' ? 'rgba(236,237,238,0.6)' : 'rgba(0,0,0,0.4)',
                  },
                  item.checked && styles.checkboxChecked,
                ]}
              />
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
          <Pressable
            onPress={() => router.push('/readout' as any)}
            style={[styles.button, styles.readout]}
          >
            <ThemedText style={styles.buttonText}>View 911 Read-out</ThemedText>
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
    paddingHorizontal: 12,
    paddingTop: 6,
    paddingBottom: 12,
    gap: 8,
    flexGrow: 1,
  },
  timerCard: {
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.08)',
    alignItems: 'center',
    gap: 4,
  },
  timerText: {
    fontSize: 44,
    lineHeight: 52,
    letterSpacing: 1,
  },
  timerHint: { textAlign: 'center', opacity: 0.8 },
  list: { gap: 6, marginTop: 2 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 6,
    paddingLeft: 12,
    paddingRight: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: 5,
    borderWidth: 2,
    borderColor: 'rgba(0,0,0,0.4)',
  },
  checkboxChecked: {
    backgroundColor: 'rgba(0, 122, 255, 0.9)',
    borderColor: 'rgba(0, 122, 255, 1)',
  },
  rowTextWrap: { flex: 1 },
  hint: { opacity: 0.7, marginTop: 1 },
  actions: { marginTop: 8, gap: 6 },
  button: {
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  found: {
    backgroundColor: '#2ecc71',
  },
  call: {
    backgroundColor: '#e74c3c',
  },
  readout: {
    backgroundColor: '#0a7ea4',
  },
  callDimmed: {
    opacity: 0.9,
  },
  buttonText: { color: 'white', fontSize: 16, fontWeight: '600' },
  tips: { marginTop: 6 },
  tipText: { opacity: 0.8 },
  subtitleSmall: { fontSize: 14, opacity: 0.9 },
  meta: { opacity: 0.8, marginTop: 2 },
});
