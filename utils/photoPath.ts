/** The file name at the end of a stored photo URI, or null if there isn't one. */
export function photoFileName(uri: string): string | null {
  const path = uri.split(/[?#]/)[0];
  const name = path.slice(path.lastIndexOf('/') + 1);
  return name ? decodeURIComponent(name) : null;
}
