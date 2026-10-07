import { DatabaseSync } from 'node:sqlite';

import { describe, expect, it } from 'vitest';

import { MIGRATIONS, SCHEMA_VERSION } from '@/database/schema';

// The Kotlin app installs over the RN app and opens the same nijii.db, so the schema
// the migrations produce is a contract: spec/db-schema.json. Changing it means a new
// migration on both sides; re-bless with `npx vitest run spec -u` in its own commit.

function migratedSchema() {
  const db = new DatabaseSync(':memory:');
  for (let v = 1; v <= SCHEMA_VERSION; v++) db.exec(MIGRATIONS[v]);

  const tables = db
    .prepare(
      `SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name`,
    )
    .all() as { name: string; sql: string }[];

  return {
    schemaVersion: SCHEMA_VERSION,
    tables: Object.fromEntries(
      tables.map(({ name, sql }) => [
        name,
        {
          columns: (
            db.prepare(`PRAGMA table_info(${name})`).all() as Record<string, unknown>[]
          ).map((c) => ({
            name: c.name,
            type: c.type,
            notNull: c.notnull === 1,
            default: c.dflt_value,
            primaryKey: c.pk,
          })),
          checks: [...sql.matchAll(/CHECK \(([^)]*\([^)]*\)[^)]*|[^)]*)\)/g)].map((m) =>
            m[1].trim(),
          ),
        },
      ]),
    ),
  };
}

describe('database schema', () => {
  it('migrations produce spec/db-schema.json', async () => {
    await expect(`${JSON.stringify(migratedSchema(), null, 2)}\n`).toMatchFileSnapshot(
      './db-schema.json',
    );
  });
});
