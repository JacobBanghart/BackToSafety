import React, { createContext, useContext, useMemo, useState } from 'react';

export type PersonProfile = {
  name: string;
  age?: number;
  appearance?: string; // clothing, height, hair, etc.
  medicalInfo?: string; // conditions, allergies
  meds?: string; // key meds
  cognitiveStatus?: string; // baseline, wandering risk
  triggers?: string;
  soothers?: string;
  photo?: any; // require('...') or uri
  likelyDestinations?: string[];
  dominantHand?: 'left' | 'right' | 'unknown';
};

export type LastSeen = {
  time?: string; // ISO
  coords?: { lat: number; lon: number; accuracy?: number };
};

type ProfileState = {
  profile: PersonProfile;
  lastSeen: LastSeen;
  setProfile: (p: Partial<PersonProfile>) => void;
  setLastSeen: (ls: LastSeen) => void;
  incidents: Incident[];
  addIncident: (i: Incident) => void;
};

export type Incident = {
  at: string; // ISO time
  outcome: 'found' | 'not_found' | '911_called';
  location?: { lat: number; lon: number; accuracy?: number };
  notes?: string;
  checked?: string[]; // checklist ids
};

const defaultProfile: PersonProfile = {
  name: 'Jane Doe',
  age: 78,
  appearance: '5\'4\", short gray hair, blue cardigan, black pants, white sneakers',
  medicalInfo: "Alzheimer's, hypertension. Allergic to penicillin.",
  meds: 'Donepezil, Lisinopril',
  cognitiveStatus: 'Moderate dementia; may be disoriented; responds to first name.',
  triggers: 'Loud noises, crowded spaces',
  soothers: 'Calm tone, short sentences, show favorite family photo',
  photo: require('../assets/images/icon.png'),
  likelyDestinations: ['Maple St. coffee shop', "St. Mary's Church", 'Old workplace on 3rd Ave'],
  dominantHand: 'right',
};

const Ctx = createContext<ProfileState | undefined>(undefined);

export const ProfileProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [profile, setProfileState] = useState<PersonProfile>(defaultProfile);
  const [lastSeen, setLastSeenState] = useState<LastSeen>({});
  const [incidents, setIncidents] = useState<Incident[]>([]);

  const value = useMemo(
    () => ({
      profile,
      lastSeen,
      setProfile: (p: Partial<PersonProfile>) => setProfileState((prev) => ({ ...prev, ...p })),
      setLastSeen: (ls: LastSeen) => setLastSeenState(ls),
      incidents,
      addIncident: (i: Incident) => setIncidents((prev) => [i, ...prev]),
    }),
    [profile, lastSeen, incidents],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
};

export const useProfile = () => {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useProfile must be used within ProfileProvider');
  return ctx;
};
