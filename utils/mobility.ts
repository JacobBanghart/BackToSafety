/**
 * Mobility is stored as a comma-separated list of English option labels, plus any
 * free text entered under "Other" (spec/storage.md). Labels stay English in
 * storage; describeMobility translates them for display.
 */

export const MOBILITY_OPTIONS = [
  'Walks independently',
  'Uses cane',
  'Uses walker',
  'Manual wheelchair',
  'Motorized wheelchair',
  'Mobility scooter',
  'Bicycle',
  'Has vehicle',
  'Other',
] as const;

export type MobilityOption = (typeof MOBILITY_OPTIONS)[number];

/** i18n key (profile namespace) for each stored label. */
export const MOBILITY_OPTION_KEYS: Record<MobilityOption, string> = {
  'Walks independently': 'mobilityOptions.walksIndependently',
  'Uses cane': 'mobilityOptions.usesCane',
  'Uses walker': 'mobilityOptions.usesWalker',
  'Manual wheelchair': 'mobilityOptions.manualWheelchair',
  'Motorized wheelchair': 'mobilityOptions.motorizedWheelchair',
  'Mobility scooter': 'mobilityOptions.mobilityScooter',
  Bicycle: 'mobilityOptions.bicycle',
  'Has vehicle': 'mobilityOptions.hasVehicle',
  Other: 'mobilityOptions.other',
};

type Translate = (key: string, vars?: Record<string, unknown>) => string;

/** The stored mobility value with known labels translated; custom text passes through. */
export function describeMobility(stored: string, t: Translate): string {
  return stored
    .split(',')
    .map((token) => token.trim())
    .filter(Boolean)
    .map((token) =>
      token in MOBILITY_OPTION_KEYS
        ? t(MOBILITY_OPTION_KEYS[token as MobilityOption], { ns: 'profile' })
        : token,
    )
    .join(', ');
}
