import { readFileSync } from 'node:fs';
import path from 'node:path';

import { expect, it } from 'vitest';

import contract from './analytics-events.json';

it('analytics event names match spec/analytics-events.json', () => {
  const src = readFileSync(path.join(__dirname, '..', 'utils/analytics.ts'), 'utf8');
  const union = src.match(/type AnalyticsEventName =([^;]+);/)?.[1] ?? '';
  const names = [...union.matchAll(/'([a-z_0-9]+)'/g)].map((m) => m[1]).sort();
  expect(names).toEqual(contract.events);
});
