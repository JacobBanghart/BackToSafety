/**
 * Backup file format + validation.
 * Pure module — no expo imports — so it can be unit tested under node/vitest
 * and reused by both the export and import flows in utils/backup-io.ts.
 */

import type { Contact } from '@/database/contacts';
import type { Destination } from '@/database/destinations';
import type { Profile } from '@/database/profile';

export const BACKUP_FORMAT = 'backtosafety-backup' as const;
export const BACKUP_FORMAT_VERSION = 1 as const;

/**
 * Reserved for a future encrypted backup format. Typed but unused in v1 —
 * plaintext exports must warn the user the file contains unencrypted medical info.
 */
export type BackupEncryption = {
  algorithm: string;
};

export type BackupFileV1 = {
  format: typeof BACKUP_FORMAT;
  formatVersion: typeof BACKUP_FORMAT_VERSION;
  exportedAt: string;
  appVersion: string;
  schemaVersion: number;
  encryption?: BackupEncryption;
  data: {
    profile: Profile | null;
    photoBase64?: string;
    contacts: Contact[];
    destinations: Destination[];
    // Allowlisted settings only — never device_id or last_seen.
    settings: Record<string, string>;
  };
};

export type BackupValidation =
  | {
      ok: true;
      backup: BackupFileV1;
      summary: {
        name: string | null;
        contactCount: number;
        destinationCount: number;
        exportedAt: string;
      };
    }
  | { ok: false; reason: 'not_backup' | 'unsupported_format_version' | 'newer_schema' | 'corrupt' };

function stripPhotoUri(profile: Profile): Profile {
  const { photoUri: _photoUri, ...rest } = profile;
  return rest as Profile;
}

export function buildBackupObject(input: {
  profile: Profile | null;
  photoBase64?: string;
  contacts: Contact[];
  destinations: Destination[];
  settings: Record<string, string>;
  schemaVersion: number;
  appVersion: string;
}): BackupFileV1 {
  return {
    format: BACKUP_FORMAT,
    formatVersion: BACKUP_FORMAT_VERSION,
    exportedAt: new Date().toISOString(),
    appVersion: input.appVersion,
    schemaVersion: input.schemaVersion,
    data: {
      profile: input.profile ? stripPhotoUri(input.profile) : null,
      photoBase64: input.photoBase64,
      contacts: input.contacts,
      destinations: input.destinations,
      settings: input.settings,
    },
  };
}

export function validateBackup(raw: string, currentSchemaVersion: number): BackupValidation {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { ok: false, reason: 'corrupt' };
  }

  if (typeof parsed !== 'object' || parsed === null) {
    return { ok: false, reason: 'not_backup' };
  }

  const candidate = parsed as Partial<BackupFileV1>;

  if (candidate.format !== BACKUP_FORMAT) {
    return { ok: false, reason: 'not_backup' };
  }

  if (candidate.formatVersion !== BACKUP_FORMAT_VERSION) {
    return { ok: false, reason: 'unsupported_format_version' };
  }

  if (
    typeof candidate.schemaVersion !== 'number' ||
    candidate.schemaVersion > currentSchemaVersion
  ) {
    return { ok: false, reason: 'newer_schema' };
  }

  if (
    !candidate.data ||
    typeof candidate.data !== 'object' ||
    !Array.isArray(candidate.data.contacts) ||
    !Array.isArray(candidate.data.destinations)
  ) {
    return { ok: false, reason: 'corrupt' };
  }

  const backup = candidate as BackupFileV1;

  return {
    ok: true,
    backup,
    summary: {
      name: backup.data.profile?.name ?? null,
      contactCount: backup.data.contacts.length,
      destinationCount: backup.data.destinations.length,
      exportedAt: backup.exportedAt,
    },
  };
}
