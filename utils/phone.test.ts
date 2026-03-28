import { describe, expect, it } from 'vitest';

import {
  formatPhoneInput,
  formatPhoneNumber,
  normalizeSmsRecipient,
  normalizeUniqueSmsRecipients,
  stripPhoneFormatting,
} from './phone';

describe('phone utils', () => {
  describe('formatPhoneNumber', () => {
    it('formats a 10-digit US number', () => {
      expect(formatPhoneNumber('5551234567')).toBe('(555) 123-4567');
    });

    it('formats an 11-digit US number with country code', () => {
      expect(formatPhoneNumber('15551234567')).toBe('+1 (555) 123-4567');
    });

    it('returns original value when not enough digits to format', () => {
      expect(formatPhoneNumber('abc')).toBe('abc');
    });
  });

  describe('formatPhoneInput', () => {
    it('formats partial and full values as user types', () => {
      expect(formatPhoneInput('5')).toBe('5');
      expect(formatPhoneInput('5551')).toBe('(555) 1');
      expect(formatPhoneInput('5551234567')).toBe('(555) 123-4567');
    });
  });

  describe('stripPhoneFormatting', () => {
    it('keeps only digits', () => {
      expect(stripPhoneFormatting('(555) 123-4567')).toBe('5551234567');
    });
  });

  describe('normalizeSmsRecipient', () => {
    it('preserves + prefix and strips non-digits', () => {
      expect(normalizeSmsRecipient('+1 (555) 123-4567')).toBe('+15551234567');
      expect(normalizeSmsRecipient('(555) 123-4567')).toBe('5551234567');
    });

    it('returns empty string for blank or non-digit input', () => {
      expect(normalizeSmsRecipient('')).toBe('');
      expect(normalizeSmsRecipient('abc')).toBe('');
    });
  });

  describe('normalizeUniqueSmsRecipients', () => {
    it('deduplicates recipients with and without +', () => {
      const result = normalizeUniqueSmsRecipients([
        '+1 (555) 123-4567',
        '1-555-123-4567',
        '5551234567',
        '',
      ]);

      expect(result).toEqual(['+15551234567', '5551234567']);
    });
  });
});
