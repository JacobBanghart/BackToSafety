/**
 * Settings Screen
 * Theme selection and dev mode tools
 */

import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import i18n from 'i18next';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';
import { track } from '@/utils/analytics';

import { AppCard } from '@/components/AppCard';
import { ListItem } from '@/components/ListItem';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { useOnboarding } from '@/context/OnboardingContext';
import { clearAllData, getDatabaseSchemaVersion, saveSetting } from '@/database/storage';
import { getAppName, getAppVersionLabel } from '@/utils/appInfo';

const IS_DEV = __DEV__;
const TAPS_TO_UNLOCK = 7;

export default function SettingsScreen() {
  const router = useRouter();
  const { t } = useTranslation('settings');
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const { refreshOnboardingState } = useOnboarding();
  const theme = Colors[colorScheme];
  const [isClearing, setIsClearing] = useState(false);
  const [devModeEnabled, setDevModeEnabled] = useState(IS_DEV);
  const [tapCount, setTapCount] = useState(0);
  const [lastTapTime, setLastTapTime] = useState(0);
  const [dbSchemaVersion, setDbSchemaVersion] = useState<number | null>(null);
  const appName = getAppName();
  const appVersionLabel = getAppVersionLabel();

  useEffect(() => {
    async function loadSchemaVersion() {
      try {
        const version = await getDatabaseSchemaVersion();
        setDbSchemaVersion(version);
      } catch {
        setDbSchemaVersion(null);
      }
    }

    loadSchemaVersion();
  }, []);

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'system', label: t('themeOptions.system'), icon: '📱' },
    { value: 'light', label: t('themeOptions.light'), icon: '☀️' },
    { value: 'dark', label: t('themeOptions.dark'), icon: '🌙' },
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
        track('settings_dev_mode_unlocked');
        if (Platform.OS === 'web') {
          // No alert needed, just show the section
        } else {
          Alert.alert(t('devModeAlert.title'), t('devModeAlert.message'));
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
        track('settings_data_cleared');

        if (Platform.OS === 'web') {
          window.location.reload();
        } else {
          router.replace('/onboarding');
        }
      } catch (err) {
        console.error('Error clearing data:', err);
        Alert.alert(t('error', { ns: 'common' }), t('clearDataError'));
      } finally {
        setIsClearing(false);
      }
    };

    if (Platform.OS === 'web') {
      if (confirm('This will delete all data and restart onboarding. Are you sure?')) {
        confirmClear();
      }
    } else {
      Alert.alert(t('clearDataModal.title'), t('clearDataModal.message'), [
        { text: t('clearDataModal.cancel'), style: 'cancel' },
        { text: t('clearDataModal.confirm'), style: 'destructive', onPress: confirmClear },
      ]);
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText type="title" style={styles.title}>
          {t('screenTitle')}
        </ThemedText>

        {/* Theme Section */}
        <AppCard>
          <View style={styles.sectionHeader}>
            <IconSymbol name="paintbrush.fill" size={20} color={theme.text} />
            <ThemedText type="subtitle" style={styles.sectionTitle}>
              {t('sections.appearance.title')}
            </ThemedText>
          </View>
          <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
            {t('sections.appearance.description')}
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
                  onPress={() => {
                    track('settings_theme_changed', { theme: option.value });
                    setThemePreference(option.value);
                  }}
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
                {t('sections.devTools.title')}
              </ThemedText>
            </View>
            <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
              {t('sections.devTools.description')}
            </ThemedText>

            {/* Language toggle (experimental) */}
            <View style={styles.sectionHeader}>
              <IconSymbol name="globe" size={20} color={theme.text} />
              <ThemedText type="subtitle" style={styles.sectionTitle}>
                {t('languageSection.title')}
              </ThemedText>
            </View>
            <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
              {t('languageSection.description')}
            </ThemedText>
            <View style={styles.themeOptions}>
              {(['en', 'es'] as const).map((lang) => {
                const isSelected = i18n.language === lang;
                const langLabel = lang === 'en' ? 'English' : 'Español';
                return (
                  <Pressable
                    key={lang}
                    style={[
                      styles.themeOption,
                      {
                        borderColor: isSelected ? theme.tint : theme.border,
                        backgroundColor: isSelected ? theme.primaryLight : 'transparent',
                      },
                    ]}
                    onPress={() => {
                      track('settings_language_changed', { language: lang });
                      void i18n.changeLanguage(lang);
                      void saveSetting('language_preference', lang);
                    }}
                  >
                    <ThemedText
                      style={[
                        styles.themeLabel,
                        isSelected && { color: theme.tint, fontWeight: '600' },
                      ]}
                    >
                      {langLabel}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>

            <Pressable
              style={[styles.dangerButton, isClearing && styles.buttonDisabled]}
              onPress={handleClearData}
              disabled={isClearing}
            >
              <IconSymbol name="trash.fill" size={18} color="#fff" />
              <ThemedText style={styles.dangerButtonText}>
                {isClearing
                  ? t('sections.devTools.clearingButton')
                  : t('sections.devTools.clearButton')}
              </ThemedText>
            </Pressable>

            <ThemedText style={[styles.warning, { color: theme.textSecondary }]}>
              {t('sections.devTools.clearWarning')}
            </ThemedText>
          </AppCard>
        )}

        {/* App Info */}
        <AppCard>
          <ThemedText type="subtitle" style={styles.sectionTitle}>
            {t('sections.about.title')}
          </ThemedText>
          <ListItem label={t('sections.about.app')} value={appName} />
          <ListItem
            label={t('sections.about.version')}
            value={`${appVersionLabel}${devModeEnabled ? t('sections.about.devSuffix') : ''}`}
            onPress={handleVersionTap}
          />
          <ListItem label={t('sections.about.platform')} value={Platform.OS} />
          <ListItem
            label={t('sections.about.theme')}
            value={colorScheme}
            style={devModeEnabled ? undefined : { borderBottomColor: 'transparent' }}
          />
          {devModeEnabled && (
            <ListItem
              label={t('sections.about.dbSchema')}
              value={
                dbSchemaVersion === null
                  ? t('sections.about.dbSchemaUnknown')
                  : String(dbSchemaVersion)
              }
              style={{ borderBottomColor: 'transparent' }}
            />
          )}
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
