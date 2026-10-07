import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import inventory from './features.json';

// Every feature's coverage references must resolve, and the number of features
// without a Maestro flow (the cross-app gate) may only go down.

const ROOT = path.join(__dirname, '..');
const features = inventory.features;

describe('feature inventory', () => {
  it('has unique IDs', () => {
    const ids = features.map((f) => f.id);
    expect(ids.filter((id, i) => ids.indexOf(id) !== i)).toEqual([]);
  });

  it('references vector functions that exist', () => {
    const broken = features.flatMap((f) =>
      f.coverage.vectors.filter((ref) => {
        const [file, fn] = ref.split('#');
        const vectorPath = path.join(ROOT, 'spec/vectors', file);
        if (!existsSync(vectorPath)) return true;
        return !(fn in JSON.parse(readFileSync(vectorPath, 'utf8')).functions);
      }),
    );
    expect(broken).toEqual([]);
  });

  it('references web tests and flows that exist', () => {
    const missing = features.flatMap((f) =>
      [...f.coverage.web, ...f.coverage.flows].filter((p) => !existsSync(path.join(ROOT, p))),
    );
    expect(missing).toEqual([]);
  });

  it('ratchets features without a Maestro flow', () => {
    const without = features.filter(
      (f) => f.coverage.flows.length === 0 && !('flowExempt' in f),
    ).length;
    expect(without, 'more features lack a flow than maxWithoutFlows allows').toBeLessThanOrEqual(
      inventory.maxWithoutFlows,
    );
    expect(without, `coverage improved: lower maxWithoutFlows to ${without}`).toBe(
      inventory.maxWithoutFlows,
    );
  });
});
