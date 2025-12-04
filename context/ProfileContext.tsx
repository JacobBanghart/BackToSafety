/**
 * Profile Context
 * Manages the person's profile data, loading from database
 */

import { Contact, getContacts, getEmergencyContacts } from '@/database/contacts';
import { Profile, saveProfile as dbSaveProfile, getProfile } from '@/database/profile';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

export type LastSeen = {
  time?: string; // ISO
  coords?: { lat: number; lon: number; accuracy?: number };
};

export type Incident = {
  at: string; // ISO time
  outcome: 'found' | 'not_found' | '911_called';
  location?: { lat: number; lon: number; accuracy?: number };
  notes?: string;
  checked?: string[]; // checklist ids
};

type ProfileState = {
  // Loading state
  isLoading: boolean;

  // Profile from database
  profile: Profile | null;
  contacts: Contact[];
  emergencyContacts: Contact[];

  // Runtime state (not persisted yet)
  lastSeen: LastSeen;
  incidents: Incident[];

  // Actions
  refreshProfile: () => Promise<void>;
  refreshContacts: () => Promise<void>;
  saveProfile: (p: Partial<Profile>) => Promise<void>;
  setLastSeen: (ls: LastSeen) => void;
  addIncident: (i: Incident) => void;
};

const Ctx = createContext<ProfileState | undefined>(undefined);

export const ProfileProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [emergencyContacts, setEmergencyContacts] = useState<Contact[]>([]);
  const [lastSeen, setLastSeenState] = useState<LastSeen>({});
  const [incidents, setIncidents] = useState<Incident[]>([]);

  // Load profile from database
  const refreshProfile = useCallback(async () => {
    try {
      const p = await getProfile();
      setProfile(p);
    } catch (err) {
      console.error('Error loading profile:', err);
    }
  }, []);

  // Load contacts from database
  const refreshContacts = useCallback(async () => {
    try {
      const [all, emergency] = await Promise.all([getContacts(), getEmergencyContacts()]);
      setContacts(all);
      setEmergencyContacts(emergency);
    } catch (err) {
      console.error('Error loading contacts:', err);
    }
  }, []);

  // Initial load
  useEffect(() => {
    const load = async () => {
      setIsLoading(true);
      await Promise.all([refreshProfile(), refreshContacts()]);
      setIsLoading(false);
    };
    load();
  }, [refreshProfile, refreshContacts]);

  // Save profile updates
  const saveProfile = useCallback(
    async (updates: Partial<Profile>) => {
      try {
        await dbSaveProfile(updates);
        await refreshProfile();
      } catch (err) {
        console.error('Error saving profile:', err);
        throw err;
      }
    },
    [refreshProfile],
  );

  const value = useMemo(
    () => ({
      isLoading,
      profile,
      contacts,
      emergencyContacts,
      lastSeen,
      incidents,
      refreshProfile,
      refreshContacts,
      saveProfile,
      setLastSeen: (ls: LastSeen) => setLastSeenState(ls),
      addIncident: (i: Incident) => setIncidents((prev) => [i, ...prev]),
    }),
    [
      isLoading,
      profile,
      contacts,
      emergencyContacts,
      lastSeen,
      incidents,
      refreshProfile,
      refreshContacts,
      saveProfile,
    ],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
};

export const useProfile = () => {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useProfile must be used within ProfileProvider');
  return ctx;
};

// Re-export types for convenience
export type { Contact } from '@/database/contacts';
export type { Profile } from '@/database/profile';
