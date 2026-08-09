/**
 * Persistent anonymous device identifier.
 *
 * Generates a random UUID v4 on first launch using expo-crypto. On native
 * platforms it's stored in expo-secure-store (the OS keychain/keystore);
 * SecureStore isn't available on web, so web keeps using the SQLite-backed
 * settings table via saveSetting/getSetting. The ID is not linked to any
 * real-world identity — it exists only to correlate analytics events and
 * crash reports for the same device over time.
 *
 * Any device ID written by an older build (before SecureStore was wired up)
 * lives in the settings table — on native we migrate it into SecureStore on
 * first read and stop writing it back to settings.
 */

import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { getSetting, saveSetting } from '@/database/storage';

const DEVICE_ID_KEY = 'device_id';

let cachedDeviceId: string | null = null;

async function readSecureDeviceId(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(DEVICE_ID_KEY);
  } catch (err) {
    console.error('[DeviceId] SecureStore read failed, falling back to settings:', err);
    return null;
  }
}

/** Returns true if the value was written to SecureStore successfully. */
async function writeSecureDeviceId(id: string): Promise<boolean> {
  try {
    await SecureStore.setItemAsync(DEVICE_ID_KEY, id);
    return true;
  } catch (err) {
    console.error('[DeviceId] SecureStore write failed, falling back to settings:', err);
    return false;
  }
}

async function getOrCreateNativeDeviceId(): Promise<string> {
  const secureStored = await readSecureDeviceId();
  if (secureStored) {
    return secureStored;
  }

  // Migrate a pre-SecureStore device ID out of settings, if one exists.
  const legacyStored = await getSetting(DEVICE_ID_KEY);
  if (legacyStored) {
    await writeSecureDeviceId(legacyStored);
    return legacyStored;
  }

  const newId = Crypto.randomUUID();
  const savedSecurely = await writeSecureDeviceId(newId);
  if (!savedSecurely) {
    // SecureStore unavailable for some reason (e.g. simulator quirks) —
    // fall back to the pre-SecureStore settings path so we still persist.
    await saveSetting(DEVICE_ID_KEY, newId);
  }
  return newId;
}

async function getOrCreateWebDeviceId(): Promise<string> {
  const stored = await getSetting(DEVICE_ID_KEY);
  if (stored) {
    return stored;
  }

  const newId = Crypto.randomUUID();
  await saveSetting(DEVICE_ID_KEY, newId);
  return newId;
}

/**
 * Returns the persistent device ID, creating one if this is the first launch.
 * Safe to call multiple times — the result is cached in memory after the first call.
 */
export async function getOrCreateDeviceId(): Promise<string> {
  if (cachedDeviceId) return cachedDeviceId;

  cachedDeviceId =
    Platform.OS === 'web' ? await getOrCreateWebDeviceId() : await getOrCreateNativeDeviceId();
  return cachedDeviceId;
}
