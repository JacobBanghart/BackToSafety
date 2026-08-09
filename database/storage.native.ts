/**
 * Native storage implementation using SQLite
 */

import * as SQLite from 'expo-sqlite';
import { runSqlMigrations, type SqlMigrationDb } from './migrations';
import {
  DATABASE_NAME,
  DEFAULT_SAFETY_CHECKS,
  MIGRATIONS,
  ONBOARDING_STEPS,
  SCHEMA_VERSION,
} from './schema';

let db: SQLite.SQLiteDatabase | null = null;

/**
 * Resettable "DB ready" deferred. All DB calls wait on this so that no
 * query runs before tables exist. A resolved deferred stays resolved
 * forever — existing getDatabase() callers must never re-block after a
 * successful init. If it rejects, initializeDatabase() swaps in a fresh
 * deferred on retry; otherwise every future getDatabase() call would throw
 * forever after a single failed attempt.
 */
interface Deferred {
  promise: Promise<void>;
  resolve: () => void;
  reject: (err: unknown) => void;
  status: 'pending' | 'resolved' | 'rejected';
}

function createDeferred(): Deferred {
  const deferred = { status: 'pending' } as Deferred;
  deferred.promise = new Promise<void>((resolve, reject) => {
    deferred.resolve = () => {
      deferred.status = 'resolved';
      resolve();
    };
    deferred.reject = (err: unknown) => {
      deferred.status = 'rejected';
      reject(err);
    };
  });
  return deferred;
}

let dbReadyDeferred = createDeferred();

/**
 * Get the database connection.
 * Always waits for initializeDatabase() to have completed so that
 * no query can run before tables exist.
 */
export async function getDatabase(): Promise<SQLite.SQLiteDatabase> {
  // Block until initializeDatabase() has finished setting up the schema
  await dbReadyDeferred.promise;
  if (!db) throw new Error('[DB] getDatabase called but db is null after ready');
  return db;
}

/**
 * Internal helper used only by initializeDatabase() — opens the connection
 * without waiting for dbReady (which hasn't resolved yet at that point).
 */
async function openDatabase(): Promise<SQLite.SQLiteDatabase> {
  if (!db) {
    db = await SQLite.openDatabaseAsync(DATABASE_NAME);
  }
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
 * Initialize the database with schema and default data.
 * Must be called once at app startup before any other DB operations.
 * Safe to call again after a failed attempt — see dbReadyDeferred above.
 */
export async function initializeDatabase(): Promise<void> {
  if (dbReadyDeferred.status === 'rejected') {
    dbReadyDeferred = createDeferred();
  }

  try {
    const database = await openDatabase();

    const currentVersion = await getCurrentVersion(database);

    if (currentVersion < SCHEMA_VERSION) {
      // expo-sqlite's runAsync() has overloads TS can't structurally match
      // against the generic SqlMigrationDb shape; the runtime behavior
      // (sql, params?) is compatible, so the cast is safe.
      await runSqlMigrations(
        database as unknown as SqlMigrationDb,
        MIGRATIONS,
        currentVersion,
        SCHEMA_VERSION,
      );
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

    dbReadyDeferred.resolve();
  } catch (err) {
    dbReadyDeferred.reject(err);
    throw err;
  }
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
    `SELECT step FROM onboarding WHERE completed = 0 AND skipped = 0 ORDER BY rowid LIMIT 1`,
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

export async function getDatabaseSchemaVersion(): Promise<number> {
  const database = await getDatabase();
  return getCurrentVersion(database);
}

// Platform identifier
export const STORAGE_TYPE = 'sqlite' as const;
