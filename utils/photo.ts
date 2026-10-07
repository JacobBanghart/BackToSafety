/**
 * Profile photos are copied into the documents directory and their absolute URI is
 * stored. iOS moves the app's container on updates, so that path can go stale and
 * the photo disappears (F-21). Look the file up by name in the current documents
 * directory instead, falling back to the stored URI.
 */

import { File, Paths } from 'expo-file-system';
import { Platform } from 'react-native';

import { photoFileName } from '@/utils/photoPath';

export function resolvePhotoUri(stored: string | undefined): string | undefined {
  if (!stored || Platform.OS === 'web') return stored;
  const name = photoFileName(stored);
  if (!name) return stored;
  try {
    const file = new File(Paths.document, name);
    return file.exists ? file.uri : stored;
  } catch {
    return stored;
  }
}
