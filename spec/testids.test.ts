import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import contract from './testids.json';

// Keeps spec/testids.json and the RN source in step, in both directions. The Kotlin
// apps get the same contract checked against their view hierarchies instead.

const ROOT = path.join(__dirname, '..');

function sourceFiles(dir: string): string[] {
  return readdirSync(path.join(ROOT, dir), { recursive: true, encoding: 'utf8' })
    .filter((f) => f.endsWith('.tsx'))
    .map((f) => path.join(ROOT, dir, f));
}

// `contacts-item-${index}-call` and `contacts-item-{index}-call` both become `contacts-item-*-call`.
const shape = (id: string) =>
  id.replace(/\$\{(?:[^{}]|\{[^}]*\})*\}/g, '*').replace(/\{\w+\}/g, '*');

const sourceIds = new Set<string>();
for (const file of sourceFiles('app')) {
  const src = readFileSync(file, 'utf8');
  for (const m of src.matchAll(/testID=(?:"([^"]+)"|\{`([^`]+)`\})/g))
    sourceIds.add(shape(m[1] ?? m[2]));
}

const declared = Object.values(contract.screens).flat();
const declaredShapes = new Set(declared.map(shape));

describe('testID contract', () => {
  it('declares every testID used in app/', () => {
    expect([...sourceIds].filter((id) => !declaredShapes.has(id)).sort()).toEqual([]);
  });

  it('every declared testID exists in app/', () => {
    expect(declared.filter((id) => !sourceIds.has(shape(id))).sort()).toEqual([]);
  });

  it('declares no ID twice', () => {
    expect(declared.filter((id, i) => declared.indexOf(id) !== i)).toEqual([]);
  });

  it('uses only defined placeholders', () => {
    const known = new Set([...Object.keys(contract.placeholders), 'index']);
    const used = declared.flatMap((id) => [...id.matchAll(/\{(\w+)\}/g)].map((m) => m[1]));
    expect(used.filter((p) => !known.has(p))).toEqual([]);
  });

  it('files every ID under the screen it is prefixed with', () => {
    const misfiled = Object.entries(contract.screens).flatMap(([screen, ids]) =>
      ids.filter((id) => id !== screen && !id.startsWith(`${screen}-`)),
    );
    expect(misfiled).toEqual([]);
  });

  it('shared components emit the derived suffixes', () => {
    const header = readFileSync(path.join(ROOT, 'components/ScreenHeader.tsx'), 'utf8');
    const modal = readFileSync(path.join(ROOT, 'components/AppModal.tsx'), 'utf8');
    for (const suffix of ['back', 'title']) expect(header).toContain(`\${testID}-${suffix}`);
    for (const suffix of ['confirm', 'cancel']) expect(modal).toContain(`\${testID}-${suffix}`);
    for (const id of [...contract.screenHeaders, ...contract.modals])
      expect(declared).toContain(id);
  });
});
