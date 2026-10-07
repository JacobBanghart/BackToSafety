import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { ageOn, parseDob } from '@/utils/age';
import * as phone from '@/utils/phone';

// Runs every spec/vectors/*.json file against the TypeScript implementation. The
// Kotlin shared module gets the same runner with its own adapter table, so both apps
// are held to identical input→output pairs.
//
// Vector format: { source, description, functions: { <name>: [{ args, expected, finding? }] } }
// A case tagged `finding` pins current behavior that PARITY_PLAN.md lists as a known
// bug. It gets fixed and re-blessed before the freeze, never copied into Kotlin.

type Case = { args: unknown[]; expected: unknown; finding?: string };
type VectorFile = { source: string; description: string; functions: Record<string, Case[]> };

// Vectors carry calendar dates as YYYY-MM-DD strings, read as local dates.
const toDate = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};
const fromDate = (date: Date | null) =>
  date &&
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

const ADAPTERS: Record<string, (...args: never[]) => unknown> = {
  parseDob: (dob: string) => fromDate(parseDob(dob)),
  ageOn: (dob: string, today: string) => ageOn(dob, toDate(today)),
  formatPhoneNumber: phone.formatPhoneNumber,
  formatPhoneInput: phone.formatPhoneInput,
  stripPhoneFormatting: phone.stripPhoneFormatting,
  normalizeSmsRecipient: phone.normalizeSmsRecipient,
  normalizeUniqueSmsRecipients: phone.normalizeUniqueSmsRecipients,
};

const VECTORS_DIR = path.join(__dirname, 'vectors');
const files = readdirSync(VECTORS_DIR).filter((f) => f.endsWith('.json'));

describe.each(files)('%s', (file) => {
  const vectors: VectorFile = JSON.parse(readFileSync(path.join(VECTORS_DIR, file), 'utf8'));

  describe.each(Object.entries(vectors.functions))('%s', (fn, cases) => {
    it('has an adapter', () => {
      expect(ADAPTERS[fn], `no adapter for ${fn}`).toBeTypeOf('function');
    });

    it.each(cases.map((c, i) => [i, JSON.stringify(c.args), c] as const))(
      'case %i: %s',
      (_i, _label, c) => {
        expect(ADAPTERS[fn](...(c.args as never[]))).toEqual(c.expected);
      },
    );
  });
});
