/**
 * Database/Storage entry point
 * Exports platform-agnostic storage functions
 * Uses SQLite on native (iOS/Android), AsyncStorage on web
 */

// Re-export storage functions (platform-specific)
export {
  STORAGE_TYPE,
  closeDatabase,
  completeOnboardingStep,
  getCurrentOnboardingStep,
  getDatabase,
  initializeDatabase,
  isOnboardingComplete,
  resetOnboarding,
} from './storage';

// Re-export data modules
export * from './contacts';
export * from './destinations';
export * from './incidents';
export * from './profile';

// Re-export schema constants
export { ONBOARDING_STEPS, SCHEMA_VERSION } from './schema';
