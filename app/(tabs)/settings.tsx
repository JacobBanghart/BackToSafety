/**
 * Settings Screen
 * Theme selection and dev mode tools
 */

import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, primary, semantic } from '@/constants/Colors';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { clearAllData } from '@/database/storage';

const IS_DEV = __DEV__;
const TAPS_TO_UNLOCK = 7;

export default function SettingsScreen() {
  const router = useRouter();
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const [isClearing, setIsClearing] = useState(false);
  const [devModeEnabled, setDevModeEnabled] = useState(IS_DEV);
  const [tapCount, setTapCount] = useState(0);
  const [lastTapTime, setLastTapTime] = useState(0);

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'system', label: 'System', icon: '📱' },
    { value: 'light', label: 'Light', icon: '☀️' },
    { value: 'dark', label: 'Dark', icon: '🌙' },
  ];

  const handleVersionTap = () => {
    const now = Date.now();
    // Reset if more than 1 second between taps
    if (now - lastTapTime > 1000) {
      setTapCount(1);
    } else {
      const newCount = tapCount + 1;
      setTapCount(newCount);

      if (newCount >= TAPS_TO_UNLOCK && !devModeEnabled) {
        setDevModeEnabled(true);
        if (Platform.OS === 'web') {
          // No alert needed, just show the section
        } else {
          Alert.alert('Developer Mode', 'Developer options are now enabled!');
        }
      } else if (newCount >= 3 && newCount < TAPS_TO_UNLOCK && !devModeEnabled) {
        // Give feedback on progress
        const remaining = TAPS_TO_UNLOCK - newCount;
        console.log(`${remaining} taps to enable developer mode`);
      }
    }
    setLastTapTime(now);
  };

  const handleClearData = async () => {
    const confirmClear = () => {
      setIsClearing(true);
      clearAllData()
        .then(() => {
          if (Platform.OS === 'web') {
            window.location.reload();
          } else {
            router.replace('/onboarding');
          }
        })
        .catch((err: Error) => {
          console.error('Error clearing data:', err);
          Alert.alert('Error', 'Failed to clear data. Please try again.');
        })
        .finally(() => {
          setIsClearing(false);
        });
    };

    if (Platform.OS === 'web') {
      if (confirm('This will delete all data and restart onboarding. Are you sure?')) {
        confirmClear();
      }
    } else {
      Alert.alert(
        'Clear All Data',
        'This will delete all data and restart onboarding. This cannot be undone.',
        [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Clear Data', style: 'destructive', onPress: confirmClear },
        ],
      );
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText type="title" style={styles.title}>
          Settings
        </ThemedText>

        {/* Theme Section */}
        <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <View style={styles.sectionHeader}>
            <IconSymbol name="paintbrush.fill" size={20} color={theme.text} />
            <ThemedText type="subtitle" style={styles.sectionTitle}>
              Appearance
            </ThemedText>
          </View>
          <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
            Choose your preferred color theme.
          </ThemedText>

          <View style={styles.themeOptions}>
            {themeOptions.map((option) => (
              <Pressable
                key={option.value}
                style={[
                  styles.themeOption,
                  {
                    borderColor: themePreference === option.value ? theme.tint : theme.border,
                    backgroundColor:
                      themePreference === option.value
                        ? colorScheme === 'dark'
                          ? primary[800]
                          : primary[50]
                        : 'transparent',
                  },
                ]}
                onPress={() => setThemePreference(option.value)}
              >
                <ThemedText style={styles.themeIcon}>{option.icon}</ThemedText>
                <ThemedText
                  style={[
                    styles.themeLabel,
                    themePreference === option.value && { color: theme.tint, fontWeight: '600' },
                  ]}
                >
                  {option.label}
                </ThemedText>
              </Pressable>
            ))}
          </View>
        </View>

        {/* Dev Mode Section - Only visible when enabled */}
        {devModeEnabled && (
          <View
            style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}
          >
            <View style={styles.sectionHeader}>
              <IconSymbol name="wrench.fill" size={20} color={theme.text} />
              <ThemedText type="subtitle" style={styles.sectionTitle}>
                Developer Tools
              </ThemedText>
            </View>
            <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
              These tools are for testing during development.
            </ThemedText>

            <Pressable
              style={[styles.dangerButton, isClearing && styles.buttonDisabled]}
              onPress={handleClearData}
              disabled={isClearing}
            >
              <IconSymbol name="trash.fill" size={18} color="#fff" />
              <ThemedText style={styles.dangerButtonText}>
                {isClearing ? 'Clearing...' : 'Clear All Data & Restart Onboarding'}
              </ThemedText>
            </Pressable>

            <ThemedText style={[styles.warning, { color: theme.textSecondary }]}>
              This will permanently delete all profile data, contacts, incidents, and settings.
            </ThemedText>
          </View>
        )}

        {/* App Info */}
        <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <ThemedText type="subtitle" style={styles.sectionTitle}>
            About
          </ThemedText>
          <View style={[styles.infoRow, { borderBottomColor: theme.border }]}>
            <ThemedText style={[styles.infoLabel, { color: theme.textSecondary }]}>App</ThemedText>
            <ThemedText style={styles.infoValue}>Nijii</ThemedText>
          </View>
          <Pressable
            style={({ pressed }) => [
              styles.infoRow,
              styles.infoRowTappable,
              { borderBottomColor: theme.border },
              pressed && { opacity: 0.5, backgroundColor: theme.border },
            ]}
            onPress={handleVersionTap}
          >
            <ThemedText style={[styles.infoLabel, { color: theme.textSecondary }]}>
              Version
            </ThemedText>
            <ThemedText style={styles.infoValue}>0.1.0{devModeEnabled ? ' (Dev)' : ''}</ThemedText>
          </Pressable>
          <View style={[styles.infoRow, { borderBottomColor: theme.border }]}>
            <ThemedText style={[styles.infoLabel, { color: theme.textSecondary }]}>
              Platform
            </ThemedText>
            <ThemedText style={styles.infoValue}>{Platform.OS}</ThemedText>
          </View>
          <View style={[styles.infoRow, { borderBottomColor: 'transparent' }]}>
            <ThemedText style={[styles.infoLabel, { color: theme.textSecondary }]}>
              Theme
            </ThemedText>
            <ThemedText style={styles.infoValue}>{colorScheme}</ThemedText>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: 24,
  },
  title: {
    marginBottom: 24,
  },
  section: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  sectionTitle: {},
  sectionDescription: {
    marginBottom: 16,
  },
  themeOptions: {
    flexDirection: 'row',
    gap: 12,
  },
  themeOption: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 16,
    borderRadius: 12,
    borderWidth: 2,
  },
  themeIcon: {
    fontSize: 24,
    marginBottom: 4,
  },
  themeLabel: {
    fontSize: 14,
  },
  dangerButton: {
    backgroundColor: semantic.error,
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 8,
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
  },
  dangerButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  warning: {
    fontSize: 13,
    marginTop: 12,
    textAlign: 'center',
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: 1,
  },
  infoRowTappable: {
    // @ts-ignore - web only
    userSelect: 'none',
    cursor: 'pointer',
  },
  infoLabel: {},
  infoValue: {
    fontWeight: '500',
  },
});
