/**
 * SQLite Database Schema for Nijii App
 * All data stored locally - no cloud sync
 *
 * MIGRATION STRATEGY:
 * - Each schema version has a migration function
 * - Migrations run sequentially from current version to latest
 * - Never modify existing migrations, only add new ones
 */

export const DATABASE_NAME = 'nijii.db';
export const SCHEMA_VERSION = 1;

/**
 * Migration definitions
 * Key = version number to migrate TO
 * Value = SQL statements to run
 */
export const MIGRATIONS: Record<number, string> = {
  // Version 1: Initial schema
  1: `
    -- Schema version tracking
    CREATE TABLE IF NOT EXISTS schema_version (
      version INTEGER PRIMARY KEY,
      migrated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );

    -- Person Profile (the individual with dementia)
    CREATE TABLE IF NOT EXISTS profile (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      
      -- Personal Info
      name TEXT NOT NULL,
      nickname TEXT,
      date_of_birth TEXT,
      photo_uri TEXT,
      height TEXT,
      weight TEXT,
      hair_color TEXT,
      eye_color TEXT,
      identifying_marks TEXT,
      
      -- Medical & Behavioral
      medical_conditions TEXT,
      medications TEXT,
      allergies TEXT,
      cognitive_status TEXT,
      dominant_hand TEXT CHECK (dominant_hand IN ('left', 'right', 'unknown')),
      mobility_level TEXT,
      
      -- Communication & De-escalation
      communication_preference TEXT,
      escalation_signs TEXT,
      deescalation_techniques TEXT,
      approach_guidance TEXT,
      likes TEXT,
      dislikes_triggers TEXT,
      safe_word TEXT,
      
      -- Devices & IDs
      locative_device_info TEXT,
      id_bracelets TEXT,
      medic_alert_id TEXT,
      medic_alert_hotline TEXT,
      
      -- Metadata
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );

    -- Emergency Contacts
    CREATE TABLE IF NOT EXISTS contacts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      phone TEXT NOT NULL,
      relationship TEXT,
      role TEXT CHECK (role IN ('primary_caregiver', 'caregiver', 'neighbor', 'family', 'friend', 'other')),
      address TEXT,
      notify_on_emergency INTEGER DEFAULT 1,
      share_medical_info INTEGER DEFAULT 0,
      notes TEXT,
      sort_order INTEGER DEFAULT 0,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );

    -- Likely Destinations
    CREATE TABLE IF NOT EXISTS destinations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      address TEXT,
      latitude REAL,
      longitude REAL,
      category TEXT CHECK (category IN ('water', 'former_workplace', 'church', 'store', 'restaurant', 'friend_family', 'walking_route', 'other')),
      reason TEXT,
      distance_from_home TEXT,
      risk_level TEXT CHECK (risk_level IN ('high', 'medium', 'low')),
      notes TEXT,
      sort_order INTEGER DEFAULT 0,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );

    -- Incident Log
    CREATE TABLE IF NOT EXISTS incidents (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      started_at TEXT NOT NULL,
      ended_at TEXT,
      outcome TEXT CHECK (outcome IN ('found', 'found_by_other', '911_called', 'returned_home', 'ongoing')),
      
      -- Location data
      last_seen_lat REAL,
      last_seen_lon REAL,
      last_seen_accuracy REAL,
      found_lat REAL,
      found_lon REAL,
      found_location_name TEXT,
      
      -- Context
      weather TEXT,
      time_of_day TEXT,
      trigger_identified TEXT,
      wearing TEXT,
      
      -- Search details
      areas_checked TEXT,
      people_contacted TEXT,
      
      -- Notes
      notes TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    );

    -- Safety Checklist Items (track completion)
    CREATE TABLE IF NOT EXISTS safety_checks (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      category TEXT CHECK (category IN ('at_home', 'away_from_home', 'foundation')),
      item_key TEXT NOT NULL UNIQUE,
      completed INTEGER DEFAULT 0,
      completed_at TEXT,
      notes TEXT
    );

    -- App Settings
    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );

    -- Onboarding Progress
    CREATE TABLE IF NOT EXISTS onboarding (
      step TEXT PRIMARY KEY,
      completed INTEGER DEFAULT 0,
      completed_at TEXT,
      skipped INTEGER DEFAULT 0
    );
  `,

  // Future migrations go here:
  // 2: `
  //   ALTER TABLE profile ADD COLUMN emergency_notes TEXT;
  //   ALTER TABLE incidents ADD COLUMN duration_minutes INTEGER;
  // `,
};

// Default safety checklist items
export const DEFAULT_SAFETY_CHECKS = [
  // At Home
  { category: 'at_home', item_key: 'visual_supports_doors', completed: 0 },
  { category: 'at_home', item_key: 'locks_high_location', completed: 0 },
  { category: 'at_home', item_key: 'door_chimes', completed: 0 },
  { category: 'at_home', item_key: 'geofence_setup', completed: 0 },
  { category: 'at_home', item_key: 'physical_boundaries', completed: 0 },

  // Away From Home
  { category: 'away_from_home', item_key: 'alert_caregivers_staff', completed: 0 },
  { category: 'away_from_home', item_key: 'safety_plan_locations', completed: 0 },
  { category: 'away_from_home', item_key: 'introduce_first_responders', completed: 0 },
  { category: 'away_from_home', item_key: 'evaluate_locative_tech', completed: 0 },

  // Foundation
  { category: 'foundation', item_key: 'social_stories', completed: 0 },
  { category: 'foundation', item_key: 'water_safety_classes', completed: 0 },
  { category: 'foundation', item_key: 'safety_responsibility', completed: 0 },
];

// Onboarding steps - minimal essential info only
// Additional details can be added later in profile settings
export const ONBOARDING_STEPS = [
  'welcome', // Explain the app purpose
  'profile_name', // Name + nickname (required for 911)
  'profile_photo', // Photo (critical for search)
  'profile_appearance', // Physical description (height, weight, hair, eyes)
  'emergency_contact', // At least one emergency contact
  'complete', // Done - can use app
];
