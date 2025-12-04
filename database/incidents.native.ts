/**
 * Incidents database operations (Native/SQLite)
 * Log of wandering incidents for pattern analysis
 */

import { getDatabase } from './storage.native';

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

/**
 * Get all incidents, most recent first
 */
export async function getIncidents(): Promise<Incident[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<any>(`SELECT * FROM incidents ORDER BY started_at DESC`);
  return rows.map(mapRowToIncident);
}

/**
 * Get recent incidents (last 30 days)
 */
export async function getRecentIncidents(): Promise<Incident[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<any>(
    `SELECT * FROM incidents 
     WHERE started_at >= datetime('now', '-30 days') 
     ORDER BY started_at DESC`,
  );
  return rows.map(mapRowToIncident);
}

/**
 * Get a single incident by ID
 */
export async function getIncident(id: number): Promise<Incident | null> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<any>(`SELECT * FROM incidents WHERE id = ?`, [id]);
  return row ? mapRowToIncident(row) : null;
}

/**
 * Create a new incident (start of emergency)
 */
export async function createIncident(incident: Partial<Incident>): Promise<number> {
  const db = await getDatabase();

  const result = await db.runAsync(
    `INSERT INTO incidents (
      started_at, ended_at, outcome,
      last_seen_lat, last_seen_lon, last_seen_accuracy,
      found_lat, found_lon, found_location_name,
      weather, time_of_day, trigger_identified, wearing,
      areas_checked, people_contacted, notes
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      incident.startedAt ?? new Date().toISOString(),
      incident.endedAt ?? null,
      incident.outcome ?? 'ongoing',
      incident.lastSeenLat ?? null,
      incident.lastSeenLon ?? null,
      incident.lastSeenAccuracy ?? null,
      incident.foundLat ?? null,
      incident.foundLon ?? null,
      incident.foundLocationName ?? null,
      incident.weather ?? null,
      incident.timeOfDay ?? null,
      incident.triggerIdentified ?? null,
      incident.wearing ?? null,
      incident.areasChecked ? JSON.stringify(incident.areasChecked) : null,
      incident.peopleContacted ? JSON.stringify(incident.peopleContacted) : null,
      incident.notes ?? null,
    ],
  );

  return result.lastInsertRowId;
}

/**
 * Update an incident (mark found, add details, etc.)
 */
export async function updateIncident(id: number, incident: Partial<Incident>): Promise<void> {
  const db = await getDatabase();

  const updates: string[] = [];
  const values: any[] = [];

  if (incident.endedAt !== undefined) {
    updates.push('ended_at = ?');
    values.push(incident.endedAt);
  }
  if (incident.outcome !== undefined) {
    updates.push('outcome = ?');
    values.push(incident.outcome);
  }
  if (incident.lastSeenLat !== undefined) {
    updates.push('last_seen_lat = ?');
    values.push(incident.lastSeenLat);
  }
  if (incident.lastSeenLon !== undefined) {
    updates.push('last_seen_lon = ?');
    values.push(incident.lastSeenLon);
  }
  if (incident.lastSeenAccuracy !== undefined) {
    updates.push('last_seen_accuracy = ?');
    values.push(incident.lastSeenAccuracy);
  }
  if (incident.foundLat !== undefined) {
    updates.push('found_lat = ?');
    values.push(incident.foundLat);
  }
  if (incident.foundLon !== undefined) {
    updates.push('found_lon = ?');
    values.push(incident.foundLon);
  }
  if (incident.foundLocationName !== undefined) {
    updates.push('found_location_name = ?');
    values.push(incident.foundLocationName);
  }
  if (incident.weather !== undefined) {
    updates.push('weather = ?');
    values.push(incident.weather);
  }
  if (incident.timeOfDay !== undefined) {
    updates.push('time_of_day = ?');
    values.push(incident.timeOfDay);
  }
  if (incident.triggerIdentified !== undefined) {
    updates.push('trigger_identified = ?');
    values.push(incident.triggerIdentified);
  }
  if (incident.wearing !== undefined) {
    updates.push('wearing = ?');
    values.push(incident.wearing);
  }
  if (incident.areasChecked !== undefined) {
    updates.push('areas_checked = ?');
    values.push(JSON.stringify(incident.areasChecked));
  }
  if (incident.peopleContacted !== undefined) {
    updates.push('people_contacted = ?');
    values.push(JSON.stringify(incident.peopleContacted));
  }
  if (incident.notes !== undefined) {
    updates.push('notes = ?');
    values.push(incident.notes);
  }

  if (updates.length === 0) return;

  values.push(id);

  await db.runAsync(`UPDATE incidents SET ${updates.join(', ')} WHERE id = ?`, values);
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
  const db = await getDatabase();
  const result = await db.getFirstAsync<{ count: number }>(
    `SELECT COUNT(*) as count FROM incidents`,
  );
  return result?.count ?? 0;
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
  const db = await getDatabase();

  const total = await getIncidentCount();

  const timeOfDayRows = await db.getAllAsync<{ time_of_day: string; count: number }>(
    `SELECT time_of_day, COUNT(*) as count FROM incidents WHERE time_of_day IS NOT NULL GROUP BY time_of_day`,
  );

  const outcomeRows = await db.getAllAsync<{ outcome: string; count: number }>(
    `SELECT outcome, COUNT(*) as count FROM incidents WHERE outcome IS NOT NULL GROUP BY outcome`,
  );

  const triggerRows = await db.getAllAsync<{ trigger_identified: string }>(
    `SELECT trigger_identified FROM incidents WHERE trigger_identified IS NOT NULL`,
  );

  const durationResult = await db.getFirstAsync<{ avg_minutes: number }>(
    `SELECT AVG((julianday(ended_at) - julianday(started_at)) * 24 * 60) as avg_minutes 
     FROM incidents WHERE ended_at IS NOT NULL`,
  );

  return {
    totalIncidents: total,
    byTimeOfDay: Object.fromEntries(timeOfDayRows.map((r) => [r.time_of_day, r.count])),
    byOutcome: Object.fromEntries(outcomeRows.map((r) => [r.outcome, r.count])),
    commonTriggers: triggerRows.map((r) => r.trigger_identified),
    averageDurationMinutes: durationResult?.avg_minutes ?? null,
  };
}

function mapRowToIncident(row: any): Incident {
  return {
    id: row.id,
    startedAt: row.started_at,
    endedAt: row.ended_at,
    outcome: row.outcome,
    lastSeenLat: row.last_seen_lat,
    lastSeenLon: row.last_seen_lon,
    lastSeenAccuracy: row.last_seen_accuracy,
    foundLat: row.found_lat,
    foundLon: row.found_lon,
    foundLocationName: row.found_location_name,
    weather: row.weather,
    timeOfDay: row.time_of_day,
    triggerIdentified: row.trigger_identified,
    wearing: row.wearing,
    areasChecked: row.areas_checked ? JSON.parse(row.areas_checked) : undefined,
    peopleContacted: row.people_contacted ? JSON.parse(row.people_contacted) : undefined,
    notes: row.notes,
    createdAt: row.created_at,
  };
}
