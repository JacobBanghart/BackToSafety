BEGIN TRANSACTION;
CREATE TABLE contacts (
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
INSERT INTO "contacts" VALUES(1,'John Smith','(555) 123-4567',NULL,'primary_caregiver',NULL,1,1,NULL,0,'2026-10-07 08:25:43','2026-10-07 08:25:43');
CREATE TABLE destinations (
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
INSERT INTO "destinations" VALUES(1,'Riverside Park',NULL,NULL,NULL,'water',NULL,NULL,'medium',NULL,0,'2026-10-07 08:26:34','2026-10-07 08:26:34');
CREATE TABLE incidents (
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
INSERT INTO "incidents" VALUES(1,'2026-10-07T08:26:47.521Z',NULL,'ongoing',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 08:26:47');
CREATE TABLE onboarding (
      step TEXT PRIMARY KEY,
      completed INTEGER DEFAULT 0,
      completed_at TEXT,
      skipped INTEGER DEFAULT 0
    );
INSERT INTO "onboarding" VALUES('welcome',1,NULL,0);
INSERT INTO "onboarding" VALUES('profile_name',1,NULL,0);
INSERT INTO "onboarding" VALUES('profile_photo',1,NULL,0);
INSERT INTO "onboarding" VALUES('profile_appearance',1,NULL,0);
INSERT INTO "onboarding" VALUES('emergency_contact',1,NULL,0);
INSERT INTO "onboarding" VALUES('complete',1,NULL,0);
CREATE TABLE profile (
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
INSERT INTO "profile" VALUES(1,'Margaret Smith','Maggie','03/15/1940',NULL,'5''6"',NULL,NULL,NULL,NULL,'Moderate Alzheimer''s',NULL,NULL,NULL,'left','Uses cane',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 08:25:24','2026-10-07 08:26:18');
CREATE TABLE safety_checks (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      category TEXT CHECK (category IN ('at_home', 'away_from_home', 'foundation')),
      item_key TEXT NOT NULL UNIQUE,
      completed INTEGER DEFAULT 0,
      completed_at TEXT,
      notes TEXT
    );
INSERT INTO "safety_checks" VALUES(1,'at_home','visual_supports_doors',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(2,'at_home','locks_high_location',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(3,'at_home','door_chimes',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(4,'at_home','geofence_setup',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(5,'at_home','physical_boundaries',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(6,'away_from_home','alert_caregivers_staff',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(7,'away_from_home','safety_plan_locations',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(8,'away_from_home','introduce_first_responders',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(9,'away_from_home','evaluate_locative_tech',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(10,'foundation','social_stories',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(11,'foundation','water_safety_classes',0,NULL,NULL);
INSERT INTO "safety_checks" VALUES(12,'foundation','safety_responsibility',0,NULL,NULL);
CREATE TABLE schema_version (
      version INTEGER PRIMARY KEY,
      migrated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
INSERT INTO "schema_version" VALUES(1,'2026-10-07 08:25:06');
CREATE TABLE settings (
      key TEXT PRIMARY KEY,
      value TEXT,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
INSERT INTO "settings" VALUES('device_id','5955021f-0e85-4e98-9a5e-27578dce9976','2026-10-07 08:25:06');
INSERT INTO "settings" VALUES('theme_preference','dark','2026-10-07 08:26:42');
INSERT INTO "settings" VALUES('active_emergency','{"startedAt":"2026-10-07T08:26:47.521Z","wearing":"Blue jacket","checkedSteps":["home_search","neighbors"],"isActive":true,"incidentId":1}','2026-10-07 08:27:08');
DELETE FROM "sqlite_sequence";
INSERT INTO "sqlite_sequence" VALUES('safety_checks',12);
INSERT INTO "sqlite_sequence" VALUES('contacts',1);
INSERT INTO "sqlite_sequence" VALUES('destinations',1);
INSERT INTO "sqlite_sequence" VALUES('incidents',1);
COMMIT;
