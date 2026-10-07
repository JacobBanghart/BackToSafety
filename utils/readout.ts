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
import { describeMobility } from '@/utils/mobility';

export type Translate = (key: string, vars?: Record<string, unknown>) => string;

export type ReadoutInput = {
  profile: Profile;
  /** Last-seen time, formatted for display. */
  lastSeenTime?: string;
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

/** Which note to show for a vehicle check: a vehicle or bike, or a mobility aid. */
export function vehicleCheckKind(mobilityLevel: string): 'vehicle' | 'aid' {
  const value = mobilityLevel.toLowerCase();
  return ['vehicle', 'bicycle', 'bike', 'scooter'].some((w) => value.includes(w))
    ? 'vehicle'
    : 'aid';
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
  const { profile, lastSeenTime } = input;
  const appearanceDesc = describeAppearance(profile, t);
  const medicalDesc = describeImportantDetails(profile, t);

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
    profile.mobilityLevel
      ? t('copyBlock.mobility', { value: describeMobility(profile.mobilityLevel, t) })
      : undefined,
    needsVehicleCheck(profile.mobilityLevel) ? t('copyBlock.mobilityVehicleNote') : undefined,
    profile.communicationPreference
      ? t('copyBlock.communication', { value: profile.communicationPreference })
      : undefined,
    profile.escalationSigns
      ? t('copyBlock.escalation', { value: profile.escalationSigns })
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
    // The app doesn't collect location (blocked on purpose); this is a line to fill in.
    t('copyBlock.coordinates', { coords: t('copyBlock.unknown') }),
    profile.locativeDeviceInfo
      ? t('copyBlock.locator', { value: profile.locativeDeviceInfo })
      : undefined,
    profile.idBracelets ? t('copyBlock.idBracelet', { value: profile.idBracelets }) : undefined,
    profile.medicAlertId ? t('copyBlock.medicAlertId', { value: profile.medicAlertId }) : undefined,
    profile.medicAlertHotline
      ? t('copyBlock.medicAlertHotline', { value: profile.medicAlertHotline })
      : undefined,
    // Blank line before the reminder
    '',
    t('copyBlock.wearingReminder'),
  ]
    .filter((line) => line !== undefined)
    .join('\n');
}

export function buildScript(input: ReadoutInput, t: Translate): string {
  const { profile, lastSeenTime, today } = input;
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

  // Location isn't collected (blocked on purpose): the caller fills it in.
  scriptParts.push(t('script.locationUnknown'));

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

/** What's missing for a stronger script, as translated phrases. */
export function missingScriptDetails(input: ReadoutInput, t: Translate): string[] {
  const { profile, lastSeenTime } = input;
  const missing: string[] = [];

  if (!lastSeenTime) missing.push(t('sections.script.missing.lastSeenTime'));
  if (!describeAppearance(profile, t)) missing.push(t('sections.script.missing.appearanceDetails'));
  if (!describeImportantDetails(profile, t)) {
    missing.push(t('sections.script.missing.importantDetails'));
  }

  return missing;
}
