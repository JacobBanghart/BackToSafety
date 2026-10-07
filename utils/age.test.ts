import { describe, expect, it } from 'vitest';

import { ageOn, parseDob } from './age';

describe('parseDob', () => {
  it('parses the MM/DD/YYYY format the profile stores', () => {
    expect(parseDob('03/15/1940')).toEqual(new Date(1940, 2, 15));
    expect(parseDob('3/5/1940')).toEqual(new Date(1940, 2, 5));
  });

  it('rejects impossible or malformed dates', () => {
    expect(parseDob('02/30/1940')).toBeNull();
    expect(parseDob('13/01/1940')).toBeNull();
    expect(parseDob('1940-03-15')).toBeNull();
    expect(parseDob('')).toBeNull();
  });
});

describe('ageOn', () => {
  it('does not count the year until the birthday has passed', () => {
    expect(ageOn('03/15/1940', new Date(2026, 2, 14))).toBe(85);
    expect(ageOn('03/15/1940', new Date(2026, 2, 15))).toBe(86);
    expect(ageOn('03/15/1940', new Date(2026, 11, 31))).toBe(86);
  });

  it('handles a Feb 29 birthday in a non-leap year', () => {
    expect(ageOn('02/29/1940', new Date(2026, 1, 28))).toBe(85);
    expect(ageOn('02/29/1940', new Date(2026, 2, 1))).toBe(86);
  });

  it('returns null for unparseable or future dates', () => {
    expect(ageOn('not a date', new Date(2026, 0, 1))).toBeNull();
    expect(ageOn('01/01/2030', new Date(2026, 0, 1))).toBeNull();
  });
});
