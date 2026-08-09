/**
 * Shared SQL migration engine.
 *
 * Pure module — no expo/platform imports — so it can be unit-tested under
 * Node and reused by any storage backend that speaks SQL (native SQLite
 * today, potentially a SQL-based web backend later).
 *
 * Semantics are ported unchanged from the migration runner that used to
 * live in database/storage.native.ts.
 */

export interface SqlMigrationDb {
  execAsync(sql: string): Promise<unknown>;
  runAsync(sql: string, params?: unknown[]): Promise<unknown>;
}

const MIGRATION_ERROR_PREFIX = '[DB] Migration failed';

/**
 * Throws a descriptive error if any version between fromVersion+1 and
 * toVersion (inclusive) has no entry in `migrations` — i.e. there's a gap
 * in the migration plan that would otherwise leave the schema stuck
 * partway through an upgrade.
 */
export function validateMigrationPlan(
  fromVersion: number,
  toVersion: number,
  migrations: Record<number, unknown>,
): void {
  if (fromVersion >= toVersion) {
    return;
  }

  for (let version = fromVersion + 1; version <= toVersion; version++) {
    if (!migrations[version]) {
      throw new Error(`Missing required migration for version ${version}`);
    }
  }
}

/**
 * Ordered list of versions that still need to run to go from fromVersion
 * to toVersion. Empty when already up to date.
 */
export function listPendingVersions(fromVersion: number, toVersion: number): number[] {
  if (fromVersion >= toVersion) {
    return [];
  }

  const versions: number[] = [];
  for (let v = fromVersion + 1; v <= toVersion; v++) {
    versions.push(v);
  }
  return versions;
}

/**
 * Runs raw-SQL migrations from fromVersion to toVersion, one version at a
 * time, each wrapped in its own transaction. The schema_version row is
 * stamped inside the same transaction as the migration SQL, so a crash
 * mid-run leaves the DB at the last fully-committed version rather than
 * in a partially-migrated state.
 */
export async function runSqlMigrations(
  db: SqlMigrationDb,
  migrations: Record<number, string>,
  fromVersion: number,
  toVersion: number,
): Promise<void> {
  validateMigrationPlan(fromVersion, toVersion, migrations);

  for (const v of listPendingVersions(fromVersion, toVersion)) {
    const migration = migrations[v];
    if (!migration) {
      throw new Error(`Missing migration for version ${v}`);
    }

    try {
      await db.execAsync('BEGIN TRANSACTION');
      await db.execAsync(migration);
      await db.runAsync(
        `INSERT OR REPLACE INTO schema_version (version, migrated_at) VALUES (?, CURRENT_TIMESTAMP)`,
        [v],
      );
      await db.execAsync('COMMIT');
    } catch (error) {
      await db.execAsync('ROLLBACK');
      throw new Error(`${MIGRATION_ERROR_PREFIX} v${v}: ${String(error)}`);
    }
  }
}
