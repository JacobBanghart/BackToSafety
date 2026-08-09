import { describe, expect, it } from 'vitest';

import type { Contact } from '@/database/contacts';
import type { Destination } from '@/database/destinations';
import type { Profile } from '@/database/profile';
import { BackupValidation, buildBackupObject, validateBackup } from './backup';

// Narrows a BackupValidation to its ok:true branch, failing loudly (not via a
// conditional expect) when validation unexpectedly failed.
function assertOk(result: BackupValidation): Extract<BackupValidation, { ok: true }> {
  if (!result.ok) {
    throw new Error(`Expected validation to succeed, but it failed with reason: ${result.reason}`);
  }
  return result;
}

const CURRENT_SCHEMA_VERSION = 1;

const profile: Profile = {
  name: 'Jane Doe',
  nickname: 'Janie',
  photoUri: 'file:///document/profile_photo_123.jpg',
  medicalConditions: 'Dementia',
};

const contacts: Contact[] = [
  {
    id: 1,
    name: 'John Doe',
    phone: '5551234567',
    notifyOnEmergency: true,
    shareMedicalInfo: true,
    sortOrder: 0,
  },
];

const destinations: Destination[] = [
  { id: 1, name: 'Lake Park', category: 'water', riskLevel: 'high', sortOrder: 0 },
];

const settings = { theme_preference: 'dark', language_preference: 'en' };

function buildRaw(overrides: { profile?: Profile | null } = {}): string {
  const backup = buildBackupObject({
    profile: overrides.profile !== undefined ? overrides.profile : profile,
    photoBase64: 'base64photodata',
    contacts,
    destinations,
    settings,
    schemaVersion: CURRENT_SCHEMA_VERSION,
    appVersion: '1.3.3',
  });
  return JSON.stringify(backup);
}

describe('backup utils', () => {
  describe('round trip', () => {
    it('builds, stringifies, and validates successfully', () => {
      const raw = buildRaw();
      const result = assertOk(validateBackup(raw, CURRENT_SCHEMA_VERSION));

      expect(result.backup.format).toBe('backtosafety-backup');
      expect(result.backup.formatVersion).toBe(1);
      expect(result.backup.data.profile?.name).toBe('Jane Doe');
      expect(result.backup.data.profile?.photoUri).toBeUndefined();
      expect(result.backup.data.photoBase64).toBe('base64photodata');
      expect(result.summary).toEqual({
        name: 'Jane Doe',
        contactCount: 1,
        destinationCount: 1,
        exportedAt: result.backup.exportedAt,
      });
    });

    it('handles a photo-less profile', () => {
      const backup = buildBackupObject({
        profile: { name: 'No Photo' },
        contacts: [],
        destinations: [],
        settings: {},
        schemaVersion: CURRENT_SCHEMA_VERSION,
        appVersion: '1.3.3',
      });

      expect(backup.data.photoBase64).toBeUndefined();
      expect(backup.data.profile?.photoUri).toBeUndefined();

      const result = assertOk(validateBackup(JSON.stringify(backup), CURRENT_SCHEMA_VERSION));
      expect(result.summary.contactCount).toBe(0);
      expect(result.summary.destinationCount).toBe(0);
    });

    it('handles a null profile', () => {
      const raw = buildRaw({ profile: null });
      const result = assertOk(validateBackup(raw, CURRENT_SCHEMA_VERSION));

      expect(result.backup.data.profile).toBeNull();
      expect(result.summary.name).toBeNull();
    });
  });

  describe('rejection reasons', () => {
    it('rejects non-JSON input as corrupt', () => {
      const result = validateBackup('not json at all {{{', CURRENT_SCHEMA_VERSION);
      expect(result).toEqual({ ok: false, reason: 'corrupt' });
    });

    it('rejects JSON with the wrong format field as not_backup', () => {
      const result = validateBackup(
        JSON.stringify({ format: 'some-other-app' }),
        CURRENT_SCHEMA_VERSION,
      );
      expect(result).toEqual({ ok: false, reason: 'not_backup' });
    });

    it('rejects an unsupported formatVersion', () => {
      const backup = JSON.parse(buildRaw());
      backup.formatVersion = 2;
      const result = validateBackup(JSON.stringify(backup), CURRENT_SCHEMA_VERSION);
      expect(result).toEqual({ ok: false, reason: 'unsupported_format_version' });
    });

    it('rejects a schemaVersion greater than current as newer_schema', () => {
      const backup = JSON.parse(buildRaw());
      backup.schemaVersion = CURRENT_SCHEMA_VERSION + 1;
      const result = validateBackup(JSON.stringify(backup), CURRENT_SCHEMA_VERSION);
      expect(result).toEqual({ ok: false, reason: 'newer_schema' });
    });

    it('rejects a malformed data payload as corrupt', () => {
      const backup = JSON.parse(buildRaw());
      backup.data = { profile: null };
      const result = validateBackup(JSON.stringify(backup), CURRENT_SCHEMA_VERSION);
      expect(result).toEqual({ ok: false, reason: 'corrupt' });
    });
  });
});
