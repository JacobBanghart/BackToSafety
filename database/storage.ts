/**
 * Platform-agnostic storage interface
 * Uses SQLite on native, AsyncStorage on web
 */

// Re-export the appropriate implementation
export * from './storage.native';
