/**
 * Age from a stored date of birth. Profiles store DOB as MM/DD/YYYY (see
 * app/profile.tsx), which `new Date(string)` isn't guaranteed to parse, so this
 * parses it explicitly.
 */

export function parseDob(value: string): Date | null {
  const match = value.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!match) return null;

  const month = Number(match[1]);
  const day = Number(match[2]);
  const year = Number(match[3]);
  const date = new Date(year, month - 1, day);

  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return date;
}

/** Whole years between `dob` and `today`; null if the DOB is unparseable or in the future. */
export function ageOn(dob: string, today: Date): number | null {
  const birth = parseDob(dob);
  if (!birth) return null;

  let age = today.getFullYear() - birth.getFullYear();
  const hadBirthday =
    today.getMonth() > birth.getMonth() ||
    (today.getMonth() === birth.getMonth() && today.getDate() >= birth.getDate());
  if (!hadBirthday) age -= 1;

  return age >= 0 ? age : null;
}
