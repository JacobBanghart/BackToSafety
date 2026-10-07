import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

// Every locale must match English key-for-key, with no empty strings and the same
// {{placeholders}}. An empty string renders as blank text, not as the English fallback.

const LOCALES_DIR = path.join(__dirname, 'locales');
const BASE = 'en';

type Flat = Record<string, string>;

function flatten(obj: Record<string, unknown>, prefix = ''): Flat {
  const out: Flat = {};
  for (const [key, value] of Object.entries(obj)) {
    const id = prefix + key;
    if (value && typeof value === 'object')
      Object.assign(out, flatten(value as Record<string, unknown>, `${id}.`));
    else out[id] = String(value);
  }
  return out;
}

function load(locale: string, file: string): Flat {
  return flatten(JSON.parse(readFileSync(path.join(LOCALES_DIR, locale, file), 'utf8')));
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{\{\s*(\w+)\s*\}\}/g)].map((m) => m[1]).sort();
}

const namespaces = readdirSync(path.join(LOCALES_DIR, BASE)).filter((f) => f.endsWith('.json'));
const locales = readdirSync(LOCALES_DIR).filter((l) => l !== BASE);

describe.each(locales)('%s locale', (locale) => {
  it('has the same namespace files as English', () => {
    expect(readdirSync(path.join(LOCALES_DIR, locale)).sort()).toEqual([...namespaces].sort());
  });

  describe.each(namespaces)('%s', (file) => {
    const base = load(BASE, file);
    const target = load(locale, file);

    it('has exactly the English keys', () => {
      expect(Object.keys(target).sort()).toEqual(Object.keys(base).sort());
    });

    it('has no empty strings', () => {
      expect(Object.keys(target).filter((k) => target[k].trim() === '')).toEqual([]);
    });

    it('keeps every placeholder', () => {
      const mismatched = Object.keys(base).filter(
        (k) => k in target && placeholders(base[k]).join() !== placeholders(target[k]).join(),
      );
      expect(mismatched).toEqual([]);
    });
  });
});

describe(`${BASE} locale`, () => {
  it.each(namespaces)('%s has no empty strings', (file) => {
    const base = load(BASE, file);
    expect(Object.keys(base).filter((k) => base[k].trim() === '')).toEqual([]);
  });
});
