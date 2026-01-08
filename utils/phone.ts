/**
 * Phone number formatting utilities
 */

/**
 * Format a phone number for display
 * Handles US phone numbers: (555) 123-4567
 * @param phone - Raw phone number string
 * @returns Formatted phone number
 */
export function formatPhoneNumber(phone: string): string {
  // Remove all non-digit characters
  const digits = phone.replace(/\D/g, '');

  // Handle US phone numbers (10 or 11 digits)
  if (digits.length === 10) {
    return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`;
  }

  // Handle with country code (11 digits starting with 1)
  if (digits.length === 11 && digits.startsWith('1')) {
    return `+1 (${digits.slice(1, 4)}) ${digits.slice(4, 7)}-${digits.slice(7)}`;
  }

  // For other formats, just return as-is with basic grouping
  if (digits.length > 6) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  }

  // Return original if we can't format it
  return phone;
}

/**
 * Format phone number as user types (for input fields)
 * @param value - Current input value
 * @returns Formatted value for display in input
 */
export function formatPhoneInput(value: string): string {
  // Remove all non-digit characters
  const digits = value.replace(/\D/g, '');

  // Format as user types
  if (digits.length <= 3) {
    return digits;
  }

  if (digits.length <= 6) {
    return `(${digits.slice(0, 3)}) ${digits.slice(3)}`;
  }

  return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6, 10)}`;
}

/**
 * Strip formatting from phone number for storage
 * @param phone - Formatted phone number
 * @returns Raw digits only
 */
export function stripPhoneFormatting(phone: string): string {
  return phone.replace(/\D/g, '');
}
