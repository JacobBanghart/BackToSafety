/**
 * Incidents operations (Web/AsyncStorage)
 * Log of wandering incidents for pattern analysis
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from './storage.web';

export interface Incident {
  id?: number;
  startedAt: string;
  endedAt?: string;
  outcome?: 'found' | 'found_by_other' | '911_called' | 'returned_home' | 'ongoing';

  // Location data
  lastSeenLat?: number;
  lastSeenLon?: number;
  lastSeenAccuracy?: number;
  foundLat?: number;
  foundLon?: number;
  foundLocationName?: string;

  // Context
  weather?: string;
  timeOfDay?: string;
  triggerIdentified?: string;
  wearing?: string;

  // Search details
  areasChecked?: string[];
  peopleContacted?: string[];

  // Notes
  notes?: string;
  createdAt?: string;
}

async function getAllIncidents(): Promise<Incident[]> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.INCIDENTS);
    if (!data) return [];
    return JSON.parse(data) as Incident[];
  } catch {
    return [];
  }
}

async function saveAllIncidents(incidents: Incident[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEYS.INCIDENTS, JSON.stringify(incidents));
}

/**
 * Get all incidents, most recent first
 */
export async function getIncidents(): Promise<Incident[]> {
  const incidents = await getAllIncidents();
  return incidents.sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
  );
}

/**
 * Get recent incidents (last 30 days)
 */
export async function getRecentIncidents(): Promise<Incident[]> {
  const incidents = await getAllIncidents();
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

  return incidents
    .filter((i) => new Date(i.startedAt) >= thirtyDaysAgo)
    .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
}

/**
 * Get a single incident by ID
 */
export async function getIncident(id: number): Promise<Incident | null> {
  const incidents = await getAllIncidents();
  return incidents.find((i) => i.id === id) ?? null;
}

/**
 * Create a new incident
 */
export async function createIncident(incident: Partial<Incident>): Promise<number> {
  const incidents = await getAllIncidents();
  const maxId = incidents.reduce((max, i) => Math.max(max, i.id ?? 0), 0);
  const newId = maxId + 1;

  const newIncident: Incident = {
    ...incident,
    id: newId,
    startedAt: incident.startedAt ?? new Date().toISOString(),
    outcome: incident.outcome ?? 'ongoing',
    createdAt: new Date().toISOString(),
  } as Incident;

  incidents.push(newIncident);
  await saveAllIncidents(incidents);

  return newId;
}

/**
 * Update an incident
 */
export async function updateIncident(id: number, updates: Partial<Incident>): Promise<void> {
  const incidents = await getAllIncidents();
  const index = incidents.findIndex((i) => i.id === id);

  if (index === -1) return;

  incidents[index] = {
    ...incidents[index],
    ...updates,
    id,
  };

  await saveAllIncidents(incidents);
}

/**
 * Mark incident as resolved
 */
export async function resolveIncident(
  id: number,
  outcome: Incident['outcome'],
  foundLocation?: { lat: number; lon: number; name?: string },
): Promise<void> {
  const updates: Partial<Incident> = {
    endedAt: new Date().toISOString(),
    outcome,
  };

  if (foundLocation) {
    updates.foundLat = foundLocation.lat;
    updates.foundLon = foundLocation.lon;
    updates.foundLocationName = foundLocation.name;
  }

  await updateIncident(id, updates);
}

/**
 * Get incident count
 */
export async function getIncidentCount(): Promise<number> {
  const incidents = await getAllIncidents();
  return incidents.length;
}

/**
 * Get pattern analysis data
 */
export async function getIncidentPatterns(): Promise<{
  totalIncidents: number;
  byTimeOfDay: Record<string, number>;
  byOutcome: Record<string, number>;
  commonTriggers: string[];
  averageDurationMinutes: number | null;
}> {
  const incidents = await getAllIncidents();

  const byTimeOfDay: Record<string, number> = {};
  const byOutcome: Record<string, number> = {};
  const triggers: string[] = [];
  let totalDuration = 0;
  let durationCount = 0;

  for (const incident of incidents) {
    if (incident.timeOfDay) {
      byTimeOfDay[incident.timeOfDay] = (byTimeOfDay[incident.timeOfDay] ?? 0) + 1;
    }
    if (incident.outcome) {
      byOutcome[incident.outcome] = (byOutcome[incident.outcome] ?? 0) + 1;
    }
    if (incident.triggerIdentified) {
      triggers.push(incident.triggerIdentified);
    }
    if (incident.startedAt && incident.endedAt) {
      const duration =
        new Date(incident.endedAt).getTime() - new Date(incident.startedAt).getTime();
      totalDuration += duration;
      durationCount++;
    }
  }

  // Get most common triggers
  const triggerCounts = triggers.reduce(
    (acc, t) => {
      acc[t] = (acc[t] ?? 0) + 1;
      return acc;
    },
    {} as Record<string, number>,
  );

  const commonTriggers = Object.entries(triggerCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([trigger]) => trigger);

  return {
    totalIncidents: incidents.length,
    byTimeOfDay,
    byOutcome,
    commonTriggers,
    averageDurationMinutes:
      durationCount > 0 ? Math.round(totalDuration / durationCount / 60000) : null,
  };
}
