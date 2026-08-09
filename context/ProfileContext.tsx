/**
 * Profile Context
 * Manages the person's profile data, loading from database
 */

import { createIncident, getIncidents } from '@/database';
import { Contact, getContacts, getEmergencyContacts } from '@/database/contacts';
import { Profile, saveProfile as dbSaveProfile, getProfile } from '@/database/profile';
import { getSetting, saveSetting } from '@/database/storage';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

export type LastSeen = {
  time?: string; // ISO
  coords?: { lat: number; lon: number; accuracy?: number };
};

export type Incident = {
  at: string; // ISO time
  // Matches the subset of database/incidents' outcome CHECK constraint
  // this app actually writes (emergency.tsx only ever passes these two;
  // 'ongoing' is the default for an incident still in progress).
  outcome: 'found' | '911_called' | 'ongoing';
  location?: { lat: number; lon: number; accuracy?: number };
  notes?: string;
  checked?: string[]; // checklist ids
};

const LAST_SEEN_SETTING_KEY = 'last_seen';

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

      try {
        const raw = await getSetting(LAST_SEEN_SETTING_KEY);
        if (raw) {
          const parsed = JSON.parse(raw) as LastSeen;
          if (parsed && typeof parsed === 'object') {
            setLastSeenState(parsed);
          }
        }
      } catch (err) {
        console.error('Error loading last seen:', err);
      }

      try {
        const stored = await getIncidents();
        setIncidents(
          stored.map((incident) => ({
            at: incident.startedAt,
            outcome: (incident.outcome ?? 'ongoing') as Incident['outcome'],
            location:
              incident.lastSeenLat != null && incident.lastSeenLon != null
                ? {
                    lat: incident.lastSeenLat,
                    lon: incident.lastSeenLon,
                    accuracy: incident.lastSeenAccuracy,
                  }
                : undefined,
            notes: incident.notes,
            checked: incident.areasChecked,
          })),
        );
      } catch (err) {
        console.error('Error loading incidents:', err);
      }

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

  // Update in-memory lastSeen immediately, then persist in the background.
  const setLastSeen = useCallback((ls: LastSeen) => {
    setLastSeenState(ls);
    saveSetting(LAST_SEEN_SETTING_KEY, JSON.stringify(ls)).catch((err) => {
      console.error('Error saving last seen:', err);
    });
  }, []);

  // Optimistic in-memory update, then fire-and-forget persistence. On DB
  // failure we keep the in-memory copy — an incident log entry lost from
  // memory during an active emergency is worse than one that never made it
  // to disk.
  const addIncident = useCallback(
    (incident: Incident) => {
      setIncidents((prev) => [incident, ...prev]);

      createIncident({
        startedAt: incident.at,
        outcome: incident.outcome,
        areasChecked: incident.checked,
        notes: incident.notes,
        lastSeenLat: lastSeen.coords?.lat,
        lastSeenLon: lastSeen.coords?.lon,
        lastSeenAccuracy: lastSeen.coords?.accuracy,
      }).catch((err) => {
        console.error('Error persisting incident:', err);
      });
    },
    [lastSeen],
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
      setLastSeen,
      addIncident,
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
      setLastSeen,
      addIncident,
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
