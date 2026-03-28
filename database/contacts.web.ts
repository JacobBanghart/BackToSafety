/**
 * Contacts operations (Web/AsyncStorage)
 * Emergency contacts for the inner circle
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from './storage.web';

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

async function getAllContacts(): Promise<Contact[]> {
  try {
    const data = await AsyncStorage.getItem(STORAGE_KEYS.CONTACTS);
    if (!data) return [];
    return JSON.parse(data) as Contact[];
  } catch {
    return [];
  }
}

async function saveAllContacts(contacts: Contact[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEYS.CONTACTS, JSON.stringify(contacts));
}

/**
 * Get all contacts ordered by sort order
 */
export async function getContacts(): Promise<Contact[]> {
  const contacts = await getAllContacts();
  return contacts.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

/**
 * Get contacts that should be notified in emergency
 */
export async function getEmergencyContacts(): Promise<Contact[]> {
  const contacts = await getAllContacts();
  return contacts
    .filter((c) => c.notifyOnEmergency)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

/**
 * Get a single contact by ID
 */
export async function getContact(id: number): Promise<Contact | null> {
  const contacts = await getAllContacts();
  return contacts.find((c) => c.id === id) ?? null;
}

/**
 * Create a new contact
 */
export async function createContact(
  contact: Omit<Contact, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<number> {
  const contacts = await getAllContacts();
  const maxId = contacts.reduce((max, c) => Math.max(max, c.id ?? 0), 0);
  const maxSortOrder = contacts.reduce((max, c) => Math.max(max, c.sortOrder ?? -1), -1);
  const newId = maxId + 1;

  const newContact: Contact = {
    ...contact,
    id: newId,
    sortOrder: contact.sortOrder ?? maxSortOrder + 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  contacts.push(newContact);
  await saveAllContacts(contacts);

  return newId;
}

/**
 * Update an existing contact
 */
export async function updateContact(id: number, updates: Partial<Contact>): Promise<void> {
  const contacts = await getAllContacts();
  const index = contacts.findIndex((c) => c.id === id);

  if (index === -1) return;

  contacts[index] = {
    ...contacts[index],
    ...updates,
    id,
    updatedAt: new Date().toISOString(),
  };

  await saveAllContacts(contacts);
}

/**
 * Delete a contact
 */
export async function deleteContact(id: number): Promise<void> {
  const contacts = await getAllContacts();
  const filtered = contacts.filter((c) => c.id !== id);
  await saveAllContacts(filtered);
}

/**
 * Get contact count
 */
export async function getContactCount(): Promise<number> {
  const contacts = await getAllContacts();
  return contacts.length;
}
