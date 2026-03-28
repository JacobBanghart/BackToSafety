/**
 * Destinations database operations (Native/SQLite)
 * Likely places the person may wander to
 */

import { getDatabase } from './storage.native';

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

/**
 * Get all destinations ordered by sort order
 */
export async function getDestinations(): Promise<Destination[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<any>(`SELECT * FROM destinations ORDER BY sort_order, created_at`);
  return rows.map(mapRowToDestination);
}

/**
 * Get high-risk destinations (water, busy roads, etc.)
 */
export async function getHighRiskDestinations(): Promise<Destination[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<any>(
    `SELECT * FROM destinations WHERE risk_level = 'high' OR category = 'water' ORDER BY sort_order`,
  );
  return rows.map(mapRowToDestination);
}

/**
 * Get a single destination by ID
 */
export async function getDestination(id: number): Promise<Destination | null> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<any>(`SELECT * FROM destinations WHERE id = ?`, [id]);
  return row ? mapRowToDestination(row) : null;
}

/**
 * Create a new destination
 */
export async function createDestination(
  dest: Omit<Destination, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<number> {
  const db = await getDatabase();
  const sortOrderResult = await db.getFirstAsync<{ maxSortOrder: number | null }>(
    `SELECT MAX(sort_order) as maxSortOrder FROM destinations`,
  );
  const nextSortOrder = (sortOrderResult?.maxSortOrder ?? -1) + 1;

  const result = await db.runAsync(
    `INSERT INTO destinations (name, address, latitude, longitude, category, reason, distance_from_home, risk_level, notes, sort_order)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      dest.name,
      dest.address ?? null,
      dest.latitude ?? null,
      dest.longitude ?? null,
      dest.category ?? 'other',
      dest.reason ?? null,
      dest.distanceFromHome ?? null,
      dest.riskLevel ?? 'medium',
      dest.notes ?? null,
      dest.sortOrder ?? nextSortOrder,
    ],
  );

  return result.lastInsertRowId;
}

/**
 * Update an existing destination
 */
export async function updateDestination(id: number, dest: Partial<Destination>): Promise<void> {
  const db = await getDatabase();

  const updates: string[] = [];
  const values: any[] = [];

  if (dest.name !== undefined) {
    updates.push('name = ?');
    values.push(dest.name);
  }
  if (dest.address !== undefined) {
    updates.push('address = ?');
    values.push(dest.address);
  }
  if (dest.latitude !== undefined) {
    updates.push('latitude = ?');
    values.push(dest.latitude);
  }
  if (dest.longitude !== undefined) {
    updates.push('longitude = ?');
    values.push(dest.longitude);
  }
  if (dest.category !== undefined) {
    updates.push('category = ?');
    values.push(dest.category);
  }
  if (dest.reason !== undefined) {
    updates.push('reason = ?');
    values.push(dest.reason);
  }
  if (dest.distanceFromHome !== undefined) {
    updates.push('distance_from_home = ?');
    values.push(dest.distanceFromHome);
  }
  if (dest.riskLevel !== undefined) {
    updates.push('risk_level = ?');
    values.push(dest.riskLevel);
  }
  if (dest.notes !== undefined) {
    updates.push('notes = ?');
    values.push(dest.notes);
  }
  if (dest.sortOrder !== undefined) {
    updates.push('sort_order = ?');
    values.push(dest.sortOrder);
  }

  if (updates.length === 0) return;

  updates.push('updated_at = CURRENT_TIMESTAMP');
  values.push(id);

  await db.runAsync(`UPDATE destinations SET ${updates.join(', ')} WHERE id = ?`, values);
}

/**
 * Delete a destination
 */
export async function deleteDestination(id: number): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(`DELETE FROM destinations WHERE id = ?`, [id]);
}

/**
 * Get destination count
 */
export async function getDestinationCount(): Promise<number> {
  const db = await getDatabase();
  const result = await db.getFirstAsync<{ count: number }>(
    `SELECT COUNT(*) as count FROM destinations`,
  );
  return result?.count ?? 0;
}

function mapRowToDestination(row: any): Destination {
  return {
    id: row.id,
    name: row.name,
    address: row.address,
    latitude: row.latitude,
    longitude: row.longitude,
    category: row.category,
    reason: row.reason,
    distanceFromHome: row.distance_from_home,
    riskLevel: row.risk_level,
    notes: row.notes,
    sortOrder: row.sort_order,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
