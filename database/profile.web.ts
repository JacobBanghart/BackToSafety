/**
 * Profile operations (Web/AsyncStorage)
 * Handles the person with dementia's profile data
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from './storage.web';

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
 * Get the profile
 */
export async function getProfile(): Promise<Profile | null> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.PROFILE);
    if (!data) return null;
    return JSON.parse(data) as Profile;
  } catch {
    return null;
  }
}

/**
 * Check if profile exists
 */
export async function hasProfile(): Promise<boolean> {
  const profile = await getProfile();
  return profile !== null && !!profile.name;
}

/**
 * Create or update the profile
 */
export async function saveProfile(profile: Partial<Profile>): Promise<void> {
  try {
    const existing = await getProfile();
    const updated: Profile = {
      ...existing,
      ...profile,
      id: 1,
      updatedAt: new Date().toISOString(),
    } as Profile;

    if (!existing) {
      updated.createdAt = new Date().toISOString();
    }

    await AsyncStorage.setItem(STORAGE_KEYS.PROFILE, JSON.stringify(updated));
  } catch (error) {
    console.error('[Profile] Failed to save profile:', error);
    throw error;
  }
}

/**
 * Delete profile (for reset)
 */
export async function deleteProfile(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEYS.PROFILE);
}
