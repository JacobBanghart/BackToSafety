/**
 * Destinations operations (Web/AsyncStorage)
 * Likely places the person may wander to
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from './storage.web';

export interface Destination {
  id?: number;
  name: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  category?:
    | 'water'
    | 'former_workplace'
    | 'church'
    | 'store'
    | 'restaurant'
    | 'friend_family'
    | 'walking_route'
    | 'other';
  reason?: string;
  distanceFromHome?: string;
  riskLevel?: 'high' | 'medium' | 'low';
  notes?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

async function getAllDestinations(): Promise<Destination[]> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.DESTINATIONS);
    if (!data) return [];
    return JSON.parse(data) as Destination[];
  } catch {
    return [];
  }
}

async function saveAllDestinations(destinations: Destination[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEYS.DESTINATIONS, JSON.stringify(destinations));
}

const riskOrder = { high: 0, medium: 1, low: 2 };

/**
 * Get all destinations ordered by risk level and sort order
 */
export async function getDestinations(): Promise<Destination[]> {
  const destinations = await getAllDestinations();
  return destinations.sort((a, b) => {
    const riskA = riskOrder[a.riskLevel ?? 'low'] ?? 3;
    const riskB = riskOrder[b.riskLevel ?? 'low'] ?? 3;
    if (riskA !== riskB) return riskA - riskB;
    return (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
  });
}

/**
 * Get high-risk destinations
 */
export async function getHighRiskDestinations(): Promise<Destination[]> {
  const destinations = await getAllDestinations();
  return destinations
    .filter((d) => d.riskLevel === 'high' || d.category === 'water')
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

/**
 * Get a single destination by ID
 */
export async function getDestination(id: number): Promise<Destination | null> {
  const destinations = await getAllDestinations();
  return destinations.find((d) => d.id === id) ?? null;
}

/**
 * Create a new destination
 */
export async function createDestination(
  dest: Omit<Destination, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<number> {
  const destinations = await getAllDestinations();
  const maxId = destinations.reduce((max, d) => Math.max(max, d.id ?? 0), 0);
  const newId = maxId + 1;

  const newDest: Destination = {
    ...dest,
    id: newId,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  destinations.push(newDest);
  await saveAllDestinations(destinations);

  return newId;
}

/**
 * Update an existing destination
 */
export async function updateDestination(id: number, updates: Partial<Destination>): Promise<void> {
  const destinations = await getAllDestinations();
  const index = destinations.findIndex((d) => d.id === id);

  if (index === -1) return;

  destinations[index] = {
    ...destinations[index],
    ...updates,
    id,
    updatedAt: new Date().toISOString(),
  };

  await saveAllDestinations(destinations);
}

/**
 * Delete a destination
 */
export async function deleteDestination(id: number): Promise<void> {
  const destinations = await getAllDestinations();
  const filtered = destinations.filter((d) => d.id !== id);
  await saveAllDestinations(filtered);
}

/**
 * Get destination count
 */
export async function getDestinationCount(): Promise<number> {
  const destinations = await getAllDestinations();
  return destinations.length;
}
