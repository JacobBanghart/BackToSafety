/**
 * Load and save the `active_emergency` settings record. Parsing lives in
 * utils/emergency.ts (pure, covered by vectors); this module adds storage.
 */

import { getSetting, saveSetting } from '@/database/storage';
import { parseActiveEmergency, type ActiveEmergency } from '@/utils/emergency';

const ACTIVE_EMERGENCY_KEY = 'active_emergency';

export async function loadActiveEmergency(): Promise<ActiveEmergency | null> {
  return parseActiveEmergency(await getSetting(ACTIVE_EMERGENCY_KEY));
}

export async function saveActiveEmergency(state: ActiveEmergency): Promise<void> {
  await saveSetting(ACTIVE_EMERGENCY_KEY, JSON.stringify(state));
}

/** Ends the emergency. Stored as an empty string, not deleted (spec/storage.md). */
export async function clearActiveEmergency(): Promise<void> {
  await saveSetting(ACTIVE_EMERGENCY_KEY, '');
}
