/**
 * Web storage implementation using AsyncStorage
 * Mimics SQLite API for compatibility
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { listPendingVersions, validateMigrationPlan } from './migrations';
import { ONBOARDING_STEPS, SCHEMA_VERSION } from './schema';

const STORAGE_KEYS = {
  SCHEMA_VERSION: '@nijii/schema_version',
  PROFILE: '@nijii/profile',
  CONTACTS: '@nijii/contacts',
  DESTINATIONS: '@nijii/destinations',
  INCIDENTS: '@nijii/incidents',
  SAFETY_CHECKS: '@nijii/safety_checks',
  ONBOARDING: '@nijii/onboarding',
  SETTINGS: '@nijii/settings',
} as const;

/**
 * Web migrations, keyed by the version they migrate TO — mirrors the
 * native MIGRATIONS map in schema.ts but runs plain functions against
 * AsyncStorage instead of SQL.
 */
const WEB_MIGRATIONS: Record<number, () => Promise<void>> = {
  // Version 1: fresh-install seeding (same defaults the old one-shot
  // initializeDatabase() used to write directly).
  1: async () => {
    const onboardingState: Record<string, { completed: boolean; skipped: boolean }> = {};
    for (const step of ONBOARDING_STEPS) {
      onboardingState[step] = { completed: false, skipped: false };
    }
    await AsyncStorage.setItem(STORAGE_KEYS.ONBOARDING, JSON.stringify(onboardingState));
  },
};

/**
 * Web doesn't need a real database connection, but we keep the interface
 */
export async function getDatabase(): Promise<null> {
  return null;
}

/**
 * Initialize storage with default data, running any pending migrations.
 * Safe to call again after a failed attempt — each migration is only
 * considered done once the schema_version key has been advanced past it,
 * so a retry simply resumes from wherever it left off.
 */
export async function initializeDatabase(): Promise<void> {
  const stored = await AsyncStorage.getItem(STORAGE_KEYS.SCHEMA_VERSION);
  const parsed = stored ? Number.parseInt(stored, 10) : 0;
  const currentVersion = Number.isFinite(parsed) ? parsed : 0;

  validateMigrationPlan(currentVersion, SCHEMA_VERSION, WEB_MIGRATIONS);

  for (const v of listPendingVersions(currentVersion, SCHEMA_VERSION)) {
    await WEB_MIGRATIONS[v]?.();
    await AsyncStorage.setItem(STORAGE_KEYS.SCHEMA_VERSION, String(v));
  }
}

/**
 * Check if onboarding is complete
 */
export async function isOnboardingComplete(): Promise<boolean> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.ONBOARDING);
    if (!data) return false;

    const onboarding = JSON.parse(data) as Record<string, { completed: boolean; skipped: boolean }>;

    return Object.values(onboarding).every((step) => step.completed || step.skipped);
  } catch {
    return false;
  }
}

/**
 * Mark an onboarding step as complete
 */
export async function completeOnboardingStep(
  step: string,
  skipped: boolean = false,
): Promise<void> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.ONBOARDING);
    const onboarding = data ? JSON.parse(data) : {};

    onboarding[step] = {
      completed: !skipped,
      skipped,
    };

    await AsyncStorage.setItem(STORAGE_KEYS.ONBOARDING, JSON.stringify(onboarding));
  } catch (error) {
    console.error('[Storage] Failed to complete onboarding step:', error);
  }
}

/**
 * Get current onboarding step
 */
export async function getCurrentOnboardingStep(): Promise<string | null> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.ONBOARDING);
    if (!data) return ONBOARDING_STEPS[0];

    const onboarding = JSON.parse(data) as Record<string, { completed: boolean; skipped: boolean }>;

    for (const step of ONBOARDING_STEPS) {
      if (!onboarding[step]?.completed && !onboarding[step]?.skipped) {
        return step;
      }
    }

    return null;
  } catch {
    return ONBOARDING_STEPS[0];
  }
}

/**
 * Reset onboarding (for testing)
 */
export async function resetOnboarding(): Promise<void> {
  const onboardingState: Record<string, { completed: boolean; skipped: boolean }> = {};
  for (const step of ONBOARDING_STEPS) {
    onboardingState[step] = { completed: false, skipped: false };
  }
  await AsyncStorage.setItem(STORAGE_KEYS.ONBOARDING, JSON.stringify(onboardingState));
}

/**
 * Clear all data and reset to fresh state (for dev/testing)
 */
export async function clearAllData(): Promise<void> {
  // Clear all nijii storage keys
  const keys = Object.values(STORAGE_KEYS);
  await AsyncStorage.multiRemove(keys);

  // Re-initialize with fresh state
  await initializeDatabase();
}

/**
 * Get a setting value by key
 */
export async function getSetting(key: string): Promise<string | null> {
  try {
    const settingsStr = await AsyncStorage.getItem(STORAGE_KEYS.SETTINGS);
    if (!settingsStr) return null;
    const settings = JSON.parse(settingsStr);
    return settings[key] ?? null;
  } catch {
    return null;
  }
}

/**
 * Save a setting value
 */
export async function saveSetting(key: string, value: string): Promise<void> {
  try {
    const settingsStr = await AsyncStorage.getItem(STORAGE_KEYS.SETTINGS);
    const settings = settingsStr ? JSON.parse(settingsStr) : {};
    settings[key] = value;
    await AsyncStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(settings));
  } catch (err) {
    console.error('Error saving setting:', err);
  }
}

/**
 * No-op for web
 */
export async function closeDatabase(): Promise<void> {
  // No-op for AsyncStorage
}

export async function getDatabaseSchemaVersion(): Promise<number> {
  try {
    const version = await AsyncStorage.getItem(STORAGE_KEYS.SCHEMA_VERSION);
    if (!version) {
      return 0;
    }

    const parsed = Number.parseInt(version, 10);
    return Number.isFinite(parsed) ? parsed : 0;
  } catch {
    return 0;
  }
}

// Platform identifier
export const STORAGE_TYPE = 'asyncstorage' as const;

// Export storage keys for use in other web modules
export { STORAGE_KEYS };
