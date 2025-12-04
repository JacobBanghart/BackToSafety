/**
 * Native storage implementation using SQLite
 */

import * as SQLite from 'expo-sqlite';
import {
  DATABASE_NAME,
  DEFAULT_SAFETY_CHECKS,
  MIGRATIONS,
  ONBOARDING_STEPS,
  SCHEMA_VERSION,
} from './schema';

let db: SQLite.SQLiteDatabase | null = null;

/**
 * Get or create the database connection
 */
export async function getDatabase(): Promise<SQLite.SQLiteDatabase> {
  if (db) return db;

  db = await SQLite.openDatabaseAsync(DATABASE_NAME);
  return db;
}

/**
 * Get current schema version from database
 */
async function getCurrentVersion(database: SQLite.SQLiteDatabase): Promise<number> {
  try {
    const tableCheck = await database.getFirstAsync<{ name: string }>(
      `SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'`,
    );

    if (!tableCheck) {
      return 0;
    }

    const result = await database.getFirstAsync<{ version: number }>(
      `SELECT MAX(version) as version FROM schema_version`,
    );
    return result?.version ?? 0;
  } catch {
    return 0;
  }
}

/**
 * Run migrations from current version to target version
 */
async function runMigrations(
  database: SQLite.SQLiteDatabase,
  fromVersion: number,
  toVersion: number,
): Promise<void> {
  console.log(`[DB] Running migrations from v${fromVersion} to v${toVersion}`);

  for (let v = fromVersion + 1; v <= toVersion; v++) {
    const migration = MIGRATIONS[v];
    if (!migration) {
      throw new Error(`Missing migration for version ${v}`);
    }

    console.log(`[DB] Applying migration v${v}...`);
    await database.execAsync(migration);

    await database.runAsync(
      `INSERT OR REPLACE INTO schema_version (version, migrated_at) VALUES (?, CURRENT_TIMESTAMP)`,
      [v],
    );

    console.log(`[DB] Migration v${v} complete`);
  }
}

/**
 * Initialize the database with schema and default data
 */
export async function initializeDatabase(): Promise<void> {
  const database = await getDatabase();

  const currentVersion = await getCurrentVersion(database);
  console.log(`[DB] Current schema version: ${currentVersion}, target: ${SCHEMA_VERSION}`);

  if (currentVersion < SCHEMA_VERSION) {
    await runMigrations(database, currentVersion, SCHEMA_VERSION);
  }

  // Insert default safety check items if not exists
  for (const check of DEFAULT_SAFETY_CHECKS) {
    await database.runAsync(
      `INSERT OR IGNORE INTO safety_checks (category, item_key, completed) VALUES (?, ?, ?)`,
      [check.category, check.item_key, check.completed],
    );
  }

  // Insert onboarding steps if not exists
  for (const step of ONBOARDING_STEPS) {
    await database.runAsync(
      `INSERT OR IGNORE INTO onboarding (step, completed, skipped) VALUES (?, 0, 0)`,
      [step],
    );
  }

  console.log('[DB] Database initialized successfully');
}

/**
 * Check if onboarding is complete
 */
export async function isOnboardingComplete(): Promise<boolean> {
  const database = await getDatabase();

  const result = await database.getFirstAsync<{ incomplete: number }>(
    `SELECT COUNT(*) as incomplete FROM onboarding WHERE completed = 0 AND skipped = 0`,
  );

  return (result?.incomplete ?? 1) === 0;
}

/**
 * Mark an onboarding step as complete
 */
export async function completeOnboardingStep(
  step: string,
  skipped: boolean = false,
): Promise<void> {
  const database = await getDatabase();

  if (skipped) {
    await database.runAsync(`UPDATE onboarding SET skipped = 1 WHERE step = ?`, [step]);
  } else {
    await database.runAsync(`UPDATE onboarding SET completed = 1 WHERE step = ?`, [step]);
  }
}

/**
 * Get current onboarding step
 */
export async function getCurrentOnboardingStep(): Promise<string | null> {
  const database = await getDatabase();

  const result = await database.getFirstAsync<{ step: string }>(
    `SELECT step FROM onboarding WHERE completed = 0 AND skipped = 0 ORDER BY id LIMIT 1`,
  );

  return result?.step ?? null;
}

/**
 * Reset onboarding (for testing)
 */
export async function resetOnboarding(): Promise<void> {
  const database = await getDatabase();
  await database.runAsync(`UPDATE onboarding SET completed = 0, skipped = 0`);
}

/**
 * Clear all data and reset to fresh state (for dev/testing)
 */
export async function clearAllData(): Promise<void> {
  const database = await getDatabase();

  // Delete all data from all tables
  await database.runAsync(`DELETE FROM profile`);
  await database.runAsync(`DELETE FROM contacts`);
  await database.runAsync(`DELETE FROM destinations`);
  await database.runAsync(`DELETE FROM incidents`);
  await database.runAsync(`DELETE FROM safety_checks`);
  await database.runAsync(`DELETE FROM settings`);
  await database.runAsync(`DELETE FROM onboarding`);

  // Re-initialize onboarding steps
  for (const step of ONBOARDING_STEPS) {
    await database.runAsync(`INSERT INTO onboarding (step, completed, skipped) VALUES (?, 0, 0)`, [
      step,
    ]);
  }

  console.log('[DB] All data cleared');
}

/**
 * Get a setting value by key
 */
export async function getSetting(key: string): Promise<string | null> {
  const database = await getDatabase();
  const row = await database.getFirstAsync<{ value: string }>(
    `SELECT value FROM settings WHERE key = ?`,
    [key],
  );
  return row?.value ?? null;
}

/**
 * Save a setting value
 */
export async function saveSetting(key: string, value: string): Promise<void> {
  const database = await getDatabase();
  await database.runAsync(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, [
    key,
    value,
  ]);
}

/**
 * Close database connection
 */
export async function closeDatabase(): Promise<void> {
  if (db) {
    await db.closeAsync();
    db = null;
  }
}

// Platform identifier
export const STORAGE_TYPE = 'sqlite' as const;
