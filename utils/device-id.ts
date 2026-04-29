/**
 * Persistent anonymous device identifier.
 *
 * Generates a random UUID v4 on first launch using expo-crypto, then stores it
 * in SQLite via the app's saveSetting/getSetting API so it survives app restarts.
 * The ID is not linked to any real-world identity — it exists only to correlate
 * analytics events and crash reports for the same device over time.
 */

import * as Crypto from 'expo-crypto';

import { getSetting, saveSetting } from '@/database/storage';

const DEVICE_ID_KEY = 'device_id';

let cachedDeviceId: string | null = null;

/**
 * Returns the persistent device ID, creating one if this is the first launch.
 * Safe to call multiple times — the result is cached in memory after the first call.
 */
export async function getOrCreateDeviceId(): Promise<string> {
  if (cachedDeviceId) return cachedDeviceId;

  const stored = await getSetting(DEVICE_ID_KEY);
  if (stored) {
    cachedDeviceId = stored;
    return cachedDeviceId;
  }

  const newId = Crypto.randomUUID();
  await saveSetting(DEVICE_ID_KEY, newId);
  cachedDeviceId = newId;
  return cachedDeviceId;
}
