/**
 * Settings Screen
 * Theme selection and dev mode tools
 */

import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppCard } from '@/components/AppCard';
import { ListItem } from '@/components/ListItem';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { useOnboarding } from '@/context/OnboardingContext';
import { clearAllData } from '@/database/storage';
import { getAppName, getAppVersionLabel } from '@/utils/appInfo';

const IS_DEV = __DEV__;
const TAPS_TO_UNLOCK = 7;

export default function SettingsScreen() {
  const router = useRouter();
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const { refreshOnboardingState } = useOnboarding();
  const theme = Colors[colorScheme];
  const [isClearing, setIsClearing] = useState(false);
  const [devModeEnabled, setDevModeEnabled] = useState(IS_DEV);
  const [tapCount, setTapCount] = useState(0);
  const [lastTapTime, setLastTapTime] = useState(0);
  const appName = getAppName();
  const appVersionLabel = getAppVersionLabel();

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
      }
    }
    setLastTapTime(now);
  };

  const handleClearData = async () => {
    const confirmClear = async () => {
      setIsClearing(true);
      try {
        await clearAllData();
        await refreshOnboardingState();

        if (Platform.OS === 'web') {
          window.location.reload();
        } else {
          router.replace('/onboarding');
        }
      } catch (err) {
        console.error('Error clearing data:', err);
        Alert.alert('Error', 'Failed to clear data. Please try again.');
      } finally {
        setIsClearing(false);
      }
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
        <AppCard>
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
            {themeOptions.map((option) => {
              const isSelected = themePreference === option.value;
              return (
                <Pressable
                  key={option.value}
                  style={[
                    styles.themeOption,
                    {
                      borderColor: isSelected ? theme.tint : theme.border,
                      backgroundColor: isSelected ? theme.primaryLight : 'transparent',
                    },
                  ]}
                  onPress={() => setThemePreference(option.value)}
                >
                  <ThemedText style={styles.themeIcon}>{option.icon}</ThemedText>
                  <ThemedText
                    style={[
                      styles.themeLabel,
                      isSelected && { color: theme.tint, fontWeight: '600' },
                    ]}
                  >
                    {option.label}
                  </ThemedText>
                </Pressable>
              );
            })}
          </View>
        </AppCard>

        {/* Dev Mode Section - Only visible when enabled */}
        {devModeEnabled && (
          <AppCard>
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
          </AppCard>
        )}

        {/* App Info */}
        <AppCard>
          <ThemedText type="subtitle" style={styles.sectionTitle}>
            About
          </ThemedText>
          <ListItem label="App" value={appName} />
          <ListItem
            label="Version"
            value={`${appVersionLabel}${devModeEnabled ? ' (Dev)' : ''}`}
            onPress={handleVersionTap}
          />
          <ListItem label="Platform" value={Platform.OS} />
          <ListItem
            label="Theme"
            value={colorScheme}
            style={{ borderBottomColor: 'transparent' }}
          />
        </AppCard>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: Spacing.xl,
  },
  title: {
    marginBottom: Spacing.xl,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    marginBottom: Spacing.sm,
  },
  sectionTitle: {},
  sectionDescription: {
    marginBottom: Spacing.lg,
  },
  themeOptions: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  themeOption: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: Spacing.lg,
    borderRadius: Radius.lg,
    borderWidth: 2,
  },
  themeIcon: {
    fontSize: 24,
    marginBottom: Spacing.xs,
  },
  themeLabel: {
    fontSize: 14,
  },
  dangerButton: {
    backgroundColor: semantic.error,
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: Radius.md,
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    gap: Spacing.sm,
  },
  dangerButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  warning: {
    ...Typography.caption,
    marginTop: Spacing.md,
    textAlign: 'center',
  },
});
