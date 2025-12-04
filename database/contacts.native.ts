/**
 * Contacts database operations (Native/SQLite)
 * Emergency contacts for the inner circle
 */

import { getDatabase } from './storage.native';

export interface Contact {
  id?: number;
  name: string;
  phone: string;
  relationship?: string;
  role?: 'primary_caregiver' | 'caregiver' | 'neighbor' | 'family' | 'friend' | 'other';
  address?: string;
  notifyOnEmergency: boolean;
  shareMedicalInfo: boolean;
  notes?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Get all contacts ordered by sort order
 */
export async function getContacts(): Promise<Contact[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<any>(`SELECT * FROM contacts ORDER BY sort_order, created_at`);
  return rows.map(mapRowToContact);
}

/**
 * Get contacts that should be notified in emergency
 */
export async function getEmergencyContacts(): Promise<Contact[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<any>(
    `SELECT * FROM contacts WHERE notify_on_emergency = 1 ORDER BY sort_order, created_at`,
  );
  return rows.map(mapRowToContact);
}

/**
 * Get a single contact by ID
 */
export async function getContact(id: number): Promise<Contact | null> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<any>(`SELECT * FROM contacts WHERE id = ?`, [id]);
  return row ? mapRowToContact(row) : null;
}

/**
 * Create a new contact
 */
export async function createContact(
  contact: Omit<Contact, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<number> {
  const db = await getDatabase();

  const result = await db.runAsync(
    `INSERT INTO contacts (name, phone, relationship, role, address, notify_on_emergency, share_medical_info, notes, sort_order)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      contact.name,
      contact.phone,
      contact.relationship ?? null,
      contact.role ?? 'other',
      contact.address ?? null,
      contact.notifyOnEmergency ? 1 : 0,
      contact.shareMedicalInfo ? 1 : 0,
      contact.notes ?? null,
      contact.sortOrder ?? 0,
    ],
  );

  return result.lastInsertRowId;
}

/**
 * Update an existing contact
 */
export async function updateContact(id: number, contact: Partial<Contact>): Promise<void> {
  const db = await getDatabase();

  const updates: string[] = [];
  const values: any[] = [];

  if (contact.name !== undefined) {
    updates.push('name = ?');
    values.push(contact.name);
  }
  if (contact.phone !== undefined) {
    updates.push('phone = ?');
    values.push(contact.phone);
  }
  if (contact.relationship !== undefined) {
    updates.push('relationship = ?');
    values.push(contact.relationship);
  }
  if (contact.role !== undefined) {
    updates.push('role = ?');
    values.push(contact.role);
  }
  if (contact.address !== undefined) {
    updates.push('address = ?');
    values.push(contact.address);
  }
  if (contact.notifyOnEmergency !== undefined) {
    updates.push('notify_on_emergency = ?');
    values.push(contact.notifyOnEmergency ? 1 : 0);
  }
  if (contact.shareMedicalInfo !== undefined) {
    updates.push('share_medical_info = ?');
    values.push(contact.shareMedicalInfo ? 1 : 0);
  }
  if (contact.notes !== undefined) {
    updates.push('notes = ?');
    values.push(contact.notes);
  }
  if (contact.sortOrder !== undefined) {
    updates.push('sort_order = ?');
    values.push(contact.sortOrder);
  }

  if (updates.length === 0) return;

  updates.push('updated_at = CURRENT_TIMESTAMP');
  values.push(id);

  await db.runAsync(`UPDATE contacts SET ${updates.join(', ')} WHERE id = ?`, values);
}

/**
 * Delete a contact
 */
export async function deleteContact(id: number): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(`DELETE FROM contacts WHERE id = ?`, [id]);
}

/**
 * Get contact count
 */
export async function getContactCount(): Promise<number> {
  const db = await getDatabase();
  const result = await db.getFirstAsync<{ count: number }>(
    `SELECT COUNT(*) as count FROM contacts`,
  );
  return result?.count ?? 0;
}

function mapRowToContact(row: any): Contact {
  return {
    id: row.id,
    name: row.name,
    phone: row.phone,
    relationship: row.relationship,
    role: row.role,
    address: row.address,
    notifyOnEmergency: row.notify_on_emergency === 1,
    shareMedicalInfo: row.share_medical_info === 1,
    notes: row.notes,
    sortOrder: row.sort_order,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
