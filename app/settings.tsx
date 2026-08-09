/**
 * Settings Screen
 * Theme selection and dev mode tools
 */

import * as Clipboard from 'expo-clipboard';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import i18n from 'i18next';
import { useFocusEffect } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';
import { track } from '@/utils/analytics';

import { AppCard } from '@/components/AppCard';
import { AppModal } from '@/components/AppModal';
import { ListItem } from '@/components/ListItem';
import { ScreenHeader } from '@/components/ScreenHeader';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { ThemePreference, useTheme } from '@/context/ThemeContext';
import { useOnboarding } from '@/context/OnboardingContext';
import { useProfile } from '@/context/ProfileContext';
import { clearAllData, getDatabaseSchemaVersion, saveSetting } from '@/database/storage';
import { getAppName, getAppVersionLabel } from '@/utils/appInfo';
import type { BackupValidation } from '@/utils/backup';
import { exportBackup, importBackup, restoreBackup } from '@/utils/backup-io';
import { getOrCreateDeviceId } from '@/utils/device-id';
import { posthog } from '@/utils/posthog';

type SuccessfulBackupValidation = Extract<BackupValidation, { ok: true }>;

const IS_DEV = __DEV__;
const TAPS_TO_UNLOCK = 7;

export default function SettingsScreen() {
  const router = useRouter();
  const { t } = useTranslation('settings');
  const { t: tb } = useTranslation('backup');
  const { themePreference, setThemePreference, colorScheme } = useTheme();
  const { refreshOnboardingState } = useOnboarding();
  const { refreshProfile, refreshContacts } = useProfile();
  const theme = Colors[colorScheme];
  const [isDeletingAccount, setIsDeletingAccount] = useState(false);
  const [devModeEnabled, setDevModeEnabled] = useState(IS_DEV);
  const [tapCount, setTapCount] = useState(0);
  const [lastTapTime, setLastTapTime] = useState(0);
  const [dbSchemaVersion, setDbSchemaVersion] = useState<number | null>(null);
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [isExportingBackup, setIsExportingBackup] = useState(false);
  const [isImportingBackup, setIsImportingBackup] = useState(false);
  const [isRestoringBackup, setIsRestoringBackup] = useState(false);
  const [pendingRestore, setPendingRestore] = useState<SuccessfulBackupValidation | null>(null);
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

  const performExportBackup = async () => {
    setIsExportingBackup(true);
    track('backup_export_started');
    try {
      const result = await exportBackup();
      if (result === 'shared') {
        track('backup_exported');
        Alert.alert(tb('export.successTitle'), tb('export.success'));
      } else {
        track('backup_export_failed', { reason: 'sharing_unavailable' });
        Alert.alert(tb('export.failureTitle'), tb('export.unavailable'));
      }
    } catch (err) {
      console.error('Error exporting backup:', err);
      track('backup_export_failed', { reason: 'error' });
      Alert.alert(tb('export.failureTitle'), tb('export.failure'));
    } finally {
      setIsExportingBackup(false);
    }
  };

  const handleExportBackup = () => {
    Alert.alert(tb('export.confirmTitle'), tb('export.confirmMessage'), [
      { text: tb('export.cancelButton'), style: 'cancel' },
      { text: tb('export.confirmButton'), onPress: performExportBackup },
    ]);
  };

  const handleImportBackup = async () => {
    setIsImportingBackup(true);
    track('backup_import_started');
    try {
      const result = await importBackup();
      if (result === 'cancelled') {
        return;
      }
      if (!result.ok) {
        track('backup_import_failed', { reason: result.reason });
        Alert.alert(tb('import.failureTitle'), tb(`import.errors.${result.reason}`));
        return;
      }
      setPendingRestore(result);
    } catch (err) {
      console.error('Error importing backup:', err);
      track('backup_import_failed', { reason: 'error' });
      Alert.alert(tb('import.failureTitle'), tb('import.failure'));
    } finally {
      setIsImportingBackup(false);
    }
  };

  const restoreSummaryMessage = (validation: SuccessfulBackupValidation): string => {
    const { name, contactCount, destinationCount, exportedAt } = validation.summary;
    const date = new Date(exportedAt).toLocaleDateString();
    const summary = name
      ? tb('import.summaryWithName', { name, date, contactCount, destinationCount })
      : tb('import.summaryWithoutName', { date, contactCount, destinationCount });
    return `${summary}\n\n${tb('import.overwriteWarning')}`;
  };

  const handleConfirmRestore = async () => {
    if (!pendingRestore) return;
    const { backup, summary } = pendingRestore;
    setPendingRestore(null);
    setIsRestoringBackup(true);
    try {
      await restoreBackup(backup);
      await Promise.all([refreshProfile(), refreshContacts(), refreshOnboardingState()]);
      track('backup_imported', {
        contact_count: summary.contactCount,
        destination_count: summary.destinationCount,
      });
      Alert.alert(tb('import.successTitle'), tb('import.success'));
    } catch (err) {
      console.error('Error restoring backup:', err);
      track('backup_import_failed', { reason: 'restore_error' });
      Alert.alert(tb('import.failureTitle'), tb('import.failure'));
    } finally {
      setIsRestoringBackup(false);
    }
  };

  const handleCopyDeviceId = async () => {
    if (!deviceId) return;
    await Clipboard.setStringAsync(deviceId);
    if (Platform.OS === 'web') {
      alert('Device ID copied!');
    } else {
      Alert.alert('Copied', 'Device ID copied to clipboard.');
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

        {/* Backup & Restore Section - native only, no file access on web */}
        {Platform.OS !== 'web' && (
          <AppCard>
            <View style={styles.sectionHeader}>
              <IconSymbol name="arrow.left.arrow.right" size={20} color={theme.text} />
              <ThemedText type="subtitle" style={styles.sectionTitle}>
                {tb('section.title')}
              </ThemedText>
            </View>
            <ThemedText style={[styles.sectionDescription, { color: theme.textSecondary }]}>
              {tb('section.description')}
            </ThemedText>

            <Pressable
              style={[
                styles.actionButton,
                { backgroundColor: theme.primary },
                isExportingBackup && styles.buttonDisabled,
              ]}
              onPress={handleExportBackup}
              disabled={isExportingBackup}
            >
              <IconSymbol name="arrow.up.right" size={18} color={Colors.light.textOnPrimary} />
              <ThemedText style={styles.actionButtonText}>{tb('export.label')}</ThemedText>
            </Pressable>

            <Pressable
              style={[
                styles.actionButtonSecondary,
                { borderColor: theme.border },
                (isImportingBackup || isRestoringBackup) && styles.buttonDisabled,
              ]}
              onPress={handleImportBackup}
              disabled={isImportingBackup || isRestoringBackup}
            >
              <IconSymbol name="square.and.arrow.down" size={18} color={theme.text} />
              <ThemedText style={[styles.actionButtonSecondaryText, { color: theme.text }]}>
                {tb('import.label')}
              </ThemedText>
            </Pressable>
          </AppCard>
        )}

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
            onPress={handleCopyDeviceId}
            style={{ borderBottomColor: 'transparent' }}
          />
        </AppCard>
      </ScrollView>

      <AppModal
        visible={pendingRestore !== null}
        onDismiss={() => setPendingRestore(null)}
        type="delete"
        title={tb('import.confirmTitle')}
        message={pendingRestore ? restoreSummaryMessage(pendingRestore) : ''}
        confirmLabel={tb('import.confirmButton')}
        onConfirm={handleConfirmRestore}
      />
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
  actionButton: {
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: Radius.md,
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    gap: Spacing.sm,
    marginBottom: Spacing.md,
  },
  actionButtonText: {
    color: Colors.light.textOnPrimary,
    ...Typography.bodyBold,
  },
  actionButtonSecondary: {
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    gap: Spacing.sm,
  },
  actionButtonSecondaryText: {
    ...Typography.bodyBold,
  },
});
