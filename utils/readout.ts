/**
 * Text the readout screen shows and copies: the 911 call script and the full
 * details block. Pure functions over the profile so both apps can be held to
 * spec/vectors/readout.json.
 *
 * `t` is bound to the `readout` namespace. Times arrive already formatted:
 * date formatting is left to each platform's native formatter.
 */

import type { Profile } from '@/database/profile';
import { ageOn } from '@/utils/age';

export type Translate = (key: string, vars?: Record<string, unknown>) => string;

export type ReadoutInput = {
  profile: Profile;
  /** Last-seen time, formatted for display. */
  lastSeenTime?: string;
  lastSeenCoords?: { lat: number; lon: number; accuracy?: number };
  /** For the age line; the screen passes the current date. */
  today: Date;
};

// Mobility values that imply an aid or vehicle worth checking for nearby.
const VEHICLE_MOBILITY_VALUES = [
  'motorized wheelchair',
  'mobility scooter',
  'bicycle',
  'has vehicle',
  'manual wheelchair',
  'uses walker',
  'uses cane',
];

export function needsVehicleCheck(mobilityLevel: string | undefined): boolean {
  if (!mobilityLevel) return false;
  const value = mobilityLevel.toLowerCase();
  return VEHICLE_MOBILITY_VALUES.some((v) => value.includes(v));
}

export function describeAppearance(profile: Profile, t: Translate): string {
  const parts: string[] = [];
  if (profile.height) parts.push(profile.height);
  if (profile.weight) parts.push(profile.weight);
  if (profile.hairColor) parts.push(t('copyBlock.hairColor', { color: profile.hairColor }));
  if (profile.eyeColor) parts.push(t('copyBlock.eyeColor', { color: profile.eyeColor }));
  if (profile.identifyingMarks) parts.push(profile.identifyingMarks);
  return parts.join(', ');
}

export function describeImportantDetails(profile: Profile, t: Translate): string {
  const parts: string[] = [];
  if (profile.medicalConditions) parts.push(profile.medicalConditions);
  if (profile.allergies) parts.push(t('copyBlock.allergies', { value: profile.allergies }));
  return parts.join('. ');
}

export function buildCopyBlock(input: ReadoutInput, t: Translate): string {
  const { profile, lastSeenTime, lastSeenCoords } = input;
  const appearanceDesc = describeAppearance(profile, t);
  const medicalDesc = describeImportantDetails(profile, t);

  const coordinates = lastSeenCoords
    ? `${lastSeenCoords.lat.toFixed(5)}, ${lastSeenCoords.lon.toFixed(5)}${
        lastSeenCoords.accuracy !== undefined ? ` (±${lastSeenCoords.accuracy}m)` : ''
      }`
    : t('copyBlock.unknown');

  return [
    profile.nickname
      ? t('copyBlock.nameWithNickname', { name: profile.name, nickname: profile.nickname })
      : t('copyBlock.name', { name: profile.name }),
    profile.dateOfBirth ? t('copyBlock.dob', { dob: profile.dateOfBirth }) : undefined,
    appearanceDesc ? t('copyBlock.appearance', { desc: appearanceDesc }) : undefined,
    medicalDesc ? t('copyBlock.importantDetails', { desc: medicalDesc }) : undefined,
    profile.medications ? t('copyBlock.medications', { value: profile.medications }) : undefined,
    profile.cognitiveStatus
      ? t('copyBlock.cognitiveStatus', { value: profile.cognitiveStatus })
      : undefined,
    profile.mobilityLevel ? t('copyBlock.mobility', { value: profile.mobilityLevel }) : undefined,
    needsVehicleCheck(profile.mobilityLevel) ? t('copyBlock.mobilityVehicleNote') : undefined,
    profile.communicationPreference
      ? t('copyBlock.communication', { value: profile.communicationPreference })
      : undefined,
    profile.dislikesTriggers
      ? t('copyBlock.triggers', { value: profile.dislikesTriggers })
      : undefined,
    profile.deescalationTechniques
      ? t('copyBlock.deescalation', { value: profile.deescalationTechniques })
      : undefined,
    profile.likes ? t('copyBlock.likes', { value: profile.likes }) : undefined,
    profile.approachGuidance
      ? t('copyBlock.approach', { value: profile.approachGuidance })
      : undefined,
    profile.safeWord ? t('copyBlock.safeWord', { value: profile.safeWord }) : undefined,
    t('copyBlock.lastSeen', { time: lastSeenTime ?? t('copyBlock.unknown') }),
    t('copyBlock.coordinates', { coords: coordinates }),
    profile.locativeDeviceInfo
      ? t('copyBlock.locator', { value: profile.locativeDeviceInfo })
      : undefined,
    profile.idBracelets ? t('copyBlock.idBracelet', { value: profile.idBracelets }) : undefined,
    profile.medicAlertId ? t('copyBlock.medicAlertId', { value: profile.medicAlertId }) : undefined,
    // Blank line before the reminder
    '',
    t('copyBlock.wearingReminder'),
  ]
    .filter((line) => line !== undefined)
    .join('\n');
}

export function buildScript(input: ReadoutInput, t: Translate): string {
  const { profile, lastSeenTime, lastSeenCoords, today } = input;
  const appearanceDesc = describeAppearance(profile, t);
  const medicalDesc = describeImportantDetails(profile, t);

  const scriptParts: string[] = [t('script.opening'), t('script.name', { name: profile.name })];

  const age = profile.dateOfBirth ? ageOn(profile.dateOfBirth, today) : null;
  if (age !== null) {
    scriptParts.push(t('script.age', { age }));
  }

  if (lastSeenTime) {
    scriptParts.push(t('script.lastSeenTime', { time: lastSeenTime }));
  } else {
    scriptParts.push(t('script.lastSeenUnknown'));
  }

  if (lastSeenCoords) {
    scriptParts.push(
      t('script.locationCoords', {
        lat: lastSeenCoords.lat.toFixed(5),
        lon: lastSeenCoords.lon.toFixed(5),
      }),
    );
  } else {
    scriptParts.push(t('script.locationUnknown'));
  }

  if (appearanceDesc) {
    scriptParts.push(t('script.appearance', { desc: appearanceDesc }));
  }

  if (medicalDesc) {
    scriptParts.push(t('script.additionalContext', { desc: medicalDesc }));
  }

  if (profile.medicAlertId) {
    scriptParts.push(t('script.medicAlertId', { id: profile.medicAlertId }));
  }

  if (profile.photoUri) {
    scriptParts.push(t('script.photoAvailable'));
  }
  scriptParts.push(t('script.silverAlert'));

  return scriptParts.join(' ');
}

/** What's missing for a stronger script. F-6: these are English, not translated. */
export function missingScriptDetails(input: ReadoutInput, t: Translate): string[] {
  const { profile, lastSeenTime, lastSeenCoords } = input;
  const missing: string[] = [];

  if (!lastSeenTime) missing.push('last seen time');
  if (!lastSeenCoords) missing.push('last known location');
  if (!describeAppearance(profile, t)) missing.push('appearance details');
  if (!describeImportantDetails(profile, t)) missing.push('important details');

  return missing;
}
