/**
 * Profile database operations (Native/SQLite)
 * Handles the person with dementia's profile data
 */

import { getDatabase } from './storage.native';

export interface Profile {
  id?: number;

  // Personal Info
  name: string;
  nickname?: string;
  dateOfBirth?: string;
  photoUri?: string;
  height?: string;
  weight?: string;
  hairColor?: string;
  eyeColor?: string;
  identifyingMarks?: string;

  // Medical & Behavioral
  medicalConditions?: string;
  medications?: string;
  allergies?: string;
  cognitiveStatus?: string;
  dominantHand?: 'left' | 'right' | 'unknown';
  mobilityLevel?: string;

  // Communication & De-escalation
  communicationPreference?: string;
  escalationSigns?: string;
  deescalationTechniques?: string;
  approachGuidance?: string;
  likes?: string;
  dislikesTriggers?: string;
  safeWord?: string;

  // Devices & IDs
  locativeDeviceInfo?: string;
  idBracelets?: string;
  medicAlertId?: string;
  medicAlertHotline?: string;

  // Metadata
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Get the profile (there's only one)
 */
export async function getProfile(): Promise<Profile | null> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<any>(`SELECT * FROM profile WHERE id = 1`);

  if (!row) return null;

  return mapRowToProfile(row);
}

/**
 * Check if profile exists
 */
export async function hasProfile(): Promise<boolean> {
  const db = await getDatabase();
  const result = await db.getFirstAsync<{ count: number }>(`SELECT COUNT(*) as count FROM profile`);
  return (result?.count ?? 0) > 0;
}

/**
 * Create or update the profile
 */
export async function saveProfile(profile: Partial<Profile>): Promise<void> {
  const db = await getDatabase();
  const exists = await hasProfile();

  if (exists) {
    await updateProfile(profile);
  } else {
    await createProfile(profile);
  }
}

/**
 * Create initial profile
 */
async function createProfile(profile: Partial<Profile>): Promise<void> {
  const db = await getDatabase();

  await db.runAsync(
    `INSERT INTO profile (
      id, name, nickname, date_of_birth, photo_uri, height, weight, hair_color, eye_color, identifying_marks,
      medical_conditions, medications, allergies, cognitive_status, dominant_hand, mobility_level,
      communication_preference, escalation_signs, deescalation_techniques, approach_guidance, likes, dislikes_triggers, safe_word,
      locative_device_info, id_bracelets, medic_alert_id, medic_alert_hotline
    ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      profile.name ?? '',
      profile.nickname ?? null,
      profile.dateOfBirth ?? null,
      profile.photoUri ?? null,
      profile.height ?? null,
      profile.weight ?? null,
      profile.hairColor ?? null,
      profile.eyeColor ?? null,
      profile.identifyingMarks ?? null,
      profile.medicalConditions ?? null,
      profile.medications ?? null,
      profile.allergies ?? null,
      profile.cognitiveStatus ?? null,
      profile.dominantHand ?? 'unknown',
      profile.mobilityLevel ?? null,
      profile.communicationPreference ?? null,
      profile.escalationSigns ?? null,
      profile.deescalationTechniques ?? null,
      profile.approachGuidance ?? null,
      profile.likes ?? null,
      profile.dislikesTriggers ?? null,
      profile.safeWord ?? null,
      profile.locativeDeviceInfo ?? null,
      profile.idBracelets ?? null,
      profile.medicAlertId ?? null,
      profile.medicAlertHotline ?? null,
    ],
  );
}

/**
 * Update existing profile
 */
async function updateProfile(profile: Partial<Profile>): Promise<void> {
  const db = await getDatabase();

  // Build dynamic update query based on provided fields
  const updates: string[] = [];
  const values: any[] = [];

  const fieldMap: Record<keyof Profile, string> = {
    name: 'name',
    nickname: 'nickname',
    dateOfBirth: 'date_of_birth',
    photoUri: 'photo_uri',
    height: 'height',
    weight: 'weight',
    hairColor: 'hair_color',
    eyeColor: 'eye_color',
    identifyingMarks: 'identifying_marks',
    medicalConditions: 'medical_conditions',
    medications: 'medications',
    allergies: 'allergies',
    cognitiveStatus: 'cognitive_status',
    dominantHand: 'dominant_hand',
    mobilityLevel: 'mobility_level',
    communicationPreference: 'communication_preference',
    escalationSigns: 'escalation_signs',
    deescalationTechniques: 'deescalation_techniques',
    approachGuidance: 'approach_guidance',
    likes: 'likes',
    dislikesTriggers: 'dislikes_triggers',
    safeWord: 'safe_word',
    locativeDeviceInfo: 'locative_device_info',
    idBracelets: 'id_bracelets',
    medicAlertId: 'medic_alert_id',
    medicAlertHotline: 'medic_alert_hotline',
    id: 'id',
    createdAt: 'created_at',
    updatedAt: 'updated_at',
  };

  for (const [key, value] of Object.entries(profile)) {
    if (key === 'id' || key === 'createdAt' || key === 'updatedAt') continue;
    const column = fieldMap[key as keyof Profile];
    if (column) {
      updates.push(`${column} = ?`);
      values.push(value ?? null);
    }
  }

  if (updates.length === 0) return;

  updates.push('updated_at = CURRENT_TIMESTAMP');

  await db.runAsync(`UPDATE profile SET ${updates.join(', ')} WHERE id = 1`, values);
}

/**
 * Delete profile (for reset)
 */
export async function deleteProfile(): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(`DELETE FROM profile WHERE id = 1`);
}

/**
 * Map database row to Profile interface
 */
function mapRowToProfile(row: any): Profile {
  return {
    id: row.id,
    name: row.name,
    nickname: row.nickname,
    dateOfBirth: row.date_of_birth,
    photoUri: row.photo_uri,
    height: row.height,
    weight: row.weight,
    hairColor: row.hair_color,
    eyeColor: row.eye_color,
    identifyingMarks: row.identifying_marks,
    medicalConditions: row.medical_conditions,
    medications: row.medications,
    allergies: row.allergies,
    cognitiveStatus: row.cognitive_status,
    dominantHand: row.dominant_hand,
    mobilityLevel: row.mobility_level,
    communicationPreference: row.communication_preference,
    escalationSigns: row.escalation_signs,
    deescalationTechniques: row.deescalation_techniques,
    approachGuidance: row.approach_guidance,
    likes: row.likes,
    dislikesTriggers: row.dislikes_triggers,
    safeWord: row.safe_word,
    locativeDeviceInfo: row.locative_device_info,
    idBracelets: row.id_bracelets,
    medicAlertId: row.medic_alert_id,
    medicAlertHotline: row.medic_alert_hotline,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
