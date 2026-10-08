import { readFileSync } from 'node:fs';
import path from 'node:path';

import { expect, it } from 'vitest';

import contract from './icons.json';

// IconSymbol.tsx imports native modules, so its MAPPING is read from source.
it('icon mapping matches spec/icons.json', () => {
  const src = readFileSync(path.join(__dirname, '..', 'components/ui/IconSymbol.tsx'), 'utf8');
  const block = src.slice(
    src.indexOf('const MAPPING = {'),
    src.indexOf('} satisfies IconMapping;'),
  );
  const mapping = Object.fromEntries(
    [...block.matchAll(/^\s*'?([\w.]+)'?:\s*'([\w-]+)',/gm)].map((m) => [m[1], m[2]]),
  );
  expect(mapping).toEqual(contract.mapping);
});
