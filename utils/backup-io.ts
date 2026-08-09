/**
 * Native side effects for the backup/export/restore feature.
 * Talks to the database only through database/index.ts's public API.
 */

import * as DocumentPicker from 'expo-document-picker';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import {
  ONBOARDING_STEPS,
  SCHEMA_VERSION,
  completeOnboardingStep,
  createContact,
  createDestination,
  deleteContact,
  deleteDestination,
  getContacts,
  getDestinations,
  getProfile,
  saveProfile,
} from '@/database';
import { getSetting, saveSetting } from '@/database/storage';
import { getAppVersionLabel } from '@/utils/appInfo';
import { BackupFileV1, BackupValidation, buildBackupObject, validateBackup } from './backup';

// Allowlist only — never device_id or last_seen.
const BACKUP_SETTINGS_KEYS = ['theme_preference', 'language_preference'] as const;

function backupFilename(): string {
  const date = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
  return `BackToSafety-backup-${date}.json`;
}

export async function exportBackup(): Promise<'shared' | 'cancelled'> {
  const [profile, contacts, destinations] = await Promise.all([
    getProfile(),
    getContacts(),
    getDestinations(),
  ]);

  const settings: Record<string, string> = {};
  for (const key of BACKUP_SETTINGS_KEYS) {
    const value = await getSetting(key);
    if (value !== null) settings[key] = value;
  }

  let photoBase64: string | undefined;
  if (profile?.photoUri) {
    try {
      const photoFile = new File(profile.photoUri);
      if (photoFile.exists) {
        photoBase64 = await photoFile.base64();
      }
    } catch {
      // Tolerate missing/unreadable photo file — export continues without it.
    }
  }

  const backup = buildBackupObject({
    profile,
    photoBase64,
    contacts,
    destinations,
    settings,
    schemaVersion: SCHEMA_VERSION,
    appVersion: getAppVersionLabel(),
  });

  const file = new File(Paths.cache, backupFilename());

  try {
    file.write(JSON.stringify(backup));

    const canShare = await Sharing.isAvailableAsync();
    if (!canShare) {
      return 'cancelled';
    }

    await Sharing.shareAsync(file.uri, { mimeType: 'application/json' });
    return 'shared';
  } finally {
    try {
      if (file.exists) file.delete();
    } catch {
      // Best-effort cleanup of the temp file.
    }
  }
}

export async function importBackup(): Promise<BackupValidation | 'cancelled'> {
  const result = await DocumentPicker.getDocumentAsync({
    // Some Android pickers mangle the JSON mime type, so fall back to */*.
    type: ['application/json', '*/*'],
    copyToCacheDirectory: true,
  });

  if (result.canceled || !result.assets[0]) {
    return 'cancelled';
  }

  try {
    const file = new File(result.assets[0].uri);
    const raw = await file.text();
    return validateBackup(raw, SCHEMA_VERSION);
  } catch {
    return { ok: false, reason: 'corrupt' };
  }
}

export async function restoreBackup(backup: BackupFileV1): Promise<void> {
  const [existingContacts, existingDestinations] = await Promise.all([
    getContacts(),
    getDestinations(),
  ]);

  await Promise.all([
    ...existingContacts.map((c) => (c.id != null ? deleteContact(c.id) : undefined)),
    ...existingDestinations.map((d) => (d.id != null ? deleteDestination(d.id) : undefined)),
  ]);

  let photoUri: string | undefined;
  if (backup.data.photoBase64) {
    try {
      const destFile = new File(Paths.document, `profile_photo_${Date.now()}.jpg`);
      destFile.write(backup.data.photoBase64, { encoding: 'base64' });
      photoUri = destFile.uri;
    } catch {
      // Tolerate photo restore failure — profile still restores without a photo.
    }
  }

  if (backup.data.profile) {
    await saveProfile({ ...backup.data.profile, photoUri });
  }

  const sortedContacts = [...backup.data.contacts].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
  );
  for (const {
    id: _id,
    createdAt: _createdAt,
    updatedAt: _updatedAt,
    ...contact
  } of sortedContacts) {
    await createContact(contact);
  }

  const sortedDestinations = [...backup.data.destinations].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
  );
  for (const {
    id: _id,
    createdAt: _createdAt,
    updatedAt: _updatedAt,
    ...destination
  } of sortedDestinations) {
    await createDestination(destination);
  }

  for (const key of BACKUP_SETTINGS_KEYS) {
    const value = backup.data.settings[key];
    if (value !== undefined) {
      await saveSetting(key, value);
    }
  }

  for (const step of ONBOARDING_STEPS) {
    await completeOnboardingStep(step);
  }
}
