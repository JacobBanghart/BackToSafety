/**
 * As-you-type formatters for profile fields. Shared by onboarding and the
 * profile editor; covered by spec/vectors/formatters.json.
 */

/** Feet and inches from up to three digits: 5 → 5, 56 → 5'6", 511 → 5'11". */
export function formatHeightInput(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 3);
  if (!digits) return '';
  if (digits.length === 1) return digits;
  if (digits.length === 2) return `${digits[0]}'${digits[1]}"`;
  return `${digits[0]}'${digits.slice(1)}"`;
}

/** Up to four digits with a thousands separator: 150 → 150, 1500 → 1,500. */
export function formatWeightInput(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  if (!digits) return '';
  return digits.length > 3 ? `${digits.slice(0, -3)},${digits.slice(-3)}` : digits;
}

/** Up to 16 letters/digits, upper-cased, in dash-separated groups of four. */
export function formatMedicAlertIdInput(value: string): string {
  const chars = value
    .replace(/[^a-zA-Z0-9]/g, '')
    .toUpperCase()
    .slice(0, 16);
  if (!chars) return '';

  const chunks = chars.match(/.{1,4}/g);
  return chunks ? chunks.join('-') : chars;
}

/** MM/DD/YYYY from up to eight typed digits. */
export function formatDobInput(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
}
