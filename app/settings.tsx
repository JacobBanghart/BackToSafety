/**
 * Settings Screen
 * Theme selection and dev mode tools
 */

import { useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, Clipboard, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import i18n from 'i18next';
import { useFocusEffect } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';
import { track } from '@/utils/analytics';

import { AppCard } from '@/components/AppCard';
import { ListItem } from '@/components/ListItem';
import { ScreenHeader } from '@/components/ScreenHeader';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { useOnboarding } from '@/context/OnboardingContext';
import { clearAllData, getDatabaseSchemaVersion, saveSetting } from '@/database/storage';
import { getAppName, getAppVersionLabel } from '@/utils/appInfo';
import { getOrCreateDeviceId } from '@/utils/device-id';
import { posthog } from '@/utils/posthog';

const IS_DEV = __DEV__;
const TAPS_TO_UNLOCK = 7;

export default function SettingsScreen() {
  const router = useRouter();
  const { t } = useTranslation('settings');
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const { refreshOnboardingState } = useOnboarding();
  const theme = Colors[colorScheme];
  const [isDeletingAccount, setIsDeletingAccount] = useState(false);
  const [devModeEnabled, setDevModeEnabled] = useState(IS_DEV);
  const [tapCount, setTapCount] = useState(0);
  const [lastTapTime, setLastTapTime] = useState(0);
  const [dbSchemaVersion, setDbSchemaVersion] = useState<number | null>(null);
  const [deviceId, setDeviceId] = useState<string | null>(null);
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

    async function loadDeviceId() {
      try {
        const id = await getOrCreateDeviceId();
        setDeviceId(id);
      } catch {
        setDeviceId(null);
      }
    }

    loadSchemaVersion();
    loadDeviceId();
  }, []);

  useFocusEffect(
    useCallback(() => {
      posthog.screen('settings');
    }, []),
  );

  const themeOptions: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'system', label: t('themeOptions.system'), icon: '📱' },
    { value: 'light', label: t('themeOptions.light'), icon: '☀️' },
    { value: 'dark', label: t('themeOptions.dark'), icon: '🌙' },
  ];

  const handleVersionTap = () => {
    const now = Date.now();
    if (now - lastTapTime > 1000) {
      setTapCount(1);
    } else {
      const newCount = tapCount + 1;
      setTapCount(newCount);

      if (newCount >= TAPS_TO_UNLOCK && !devModeEnabled) {
        setDevModeEnabled(true);
        track('settings_dev_mode_unlocked');
        if (Platform.OS !== 'web') {
          Alert.alert(t('devModeAlert.title'), t('devModeAlert.message'));
        }
      }
    }
    setLastTapTime(now);
  };

  const handleDeleteAccount = async () => {
    const confirmDelete = async () => {
      setIsDeletingAccount(true);
      try {
        await clearAllData();
        await refreshOnboardingState();
        track('settings_account_deleted');

        if (Platform.OS === 'web') {
          window.location.reload();
        } else {
          router.replace('/onboarding');
        }
      } catch (err) {
        console.error('Error deleting account:', err);
        Alert.alert(t('error', { ns: 'common' }), t('deleteAccountError'));
      } finally {
        setIsDeletingAccount(false);
      }
    };

    if (Platform.OS === 'web') {
      if (confirm('This will permanently delete all data on this device. Are you sure?')) {
        confirmDelete();
      }
    } else {
      Alert.alert(t('deleteAccountModal.title'), t('deleteAccountModal.message'), [
        { text: t('deleteAccountModal.cancel'), style: 'cancel' },
        { text: t('deleteAccountModal.confirm'), style: 'destructive', onPress: confirmDelete },
      ]);
    }
  };

return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]} edges={['top']}>
      <ScreenHeader title={t('screenTitle')} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>

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

        {/* Delete Account Section */}
        <AppCard>
          <View style={styles.sectionHeader}>
            <IconSymbol name="trash.fill" size={20} color={semantic.error} />
            <ThemedText type="subtitle" style={styles.sectionTitle}>
              {t('sections.deleteAccount.title')}
            </ThemedText>
          </View>
          <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
            {t('sections.deleteAccount.description')}
          </ThemedText>
          <Pressable
            style={[styles.dangerButton, isDeletingAccount && styles.buttonDisabled]}
            onPress={handleDeleteAccount}
            disabled={isDeletingAccount}
          >
            <IconSymbol name="trash.fill" size={18} color={Colors.light.textOnPrimary} />
            <ThemedText style={styles.dangerButtonText}>
              {isDeletingAccount
                ? t('sections.deleteAccount.deletingButton')
                : t('sections.deleteAccount.button')}
            </ThemedText>
          </Pressable>
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
          <ListItem label={t('sections.about.theme')} value={colorScheme} />
          {devModeEnabled && (
            <ListItem
              label={t('sections.about.dbSchema')}
              value={
                dbSchemaVersion === null
                  ? t('sections.about.dbSchemaUnknown')
                  : String(dbSchemaVersion)
              }
            />
          )}
          <ListItem
            label={t('sections.about.deviceId')}
            value={deviceId ?? '—'}
            onPress={() => {
              if (deviceId) {
                Clipboard.setString(deviceId);
                if (Platform.OS === 'web') {
                  alert('Device ID copied!');
                } else {
                  Alert.alert('Copied', 'Device ID copied to clipboard.');
                }
              }
            }}
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
    gap: Spacing.lg,
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
    color: Colors.light.textOnPrimary,
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
