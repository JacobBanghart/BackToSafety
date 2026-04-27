/**
 * i18n bootstrap
 * Initialises i18next with namespace-per-screen structure.
 * Language defaults to the device locale (via expo-localization).
 * Spanish is available but hidden behind developer settings until fully translated.
 */

import * as Localization from 'expo-localization';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// ── English translations ──────────────────────────────────────────────────────
import enCommon from './locales/en/common.json';
import enHome from './locales/en/home.json';
import enEmergency from './locales/en/emergency.json';
import enProfile from './locales/en/profile.json';
import enContacts from './locales/en/contacts.json';
import enDestinations from './locales/en/destinations.json';
import enReadout from './locales/en/readout.json';
import enSettings from './locales/en/settings.json';
import enOnboarding from './locales/en/onboarding.json';

// ── Spanish translations ──────────────────────────────────────────────────────
import esCommon from './locales/es/common.json';
import esHome from './locales/es/home.json';
import esEmergency from './locales/es/emergency.json';
import esProfile from './locales/es/profile.json';
import esContacts from './locales/es/contacts.json';
import esDestinations from './locales/es/destinations.json';
import esReadout from './locales/es/readout.json';
import esSettings from './locales/es/settings.json';
import esOnboarding from './locales/es/onboarding.json';

const deviceLocale = Localization.getLocales()[0]?.languageCode ?? 'en';

// Exported so the app can call this once the DB is ready (after onboarding init).
// Falls back silently — if storage is unavailable the device locale is used.
export async function loadSavedLanguage(): Promise<void> {
  try {
    const { getSetting } = await import('@/database/storage');
    const saved = await getSetting('language_preference');
    if (saved === 'en' || saved === 'es') {
      await i18n.changeLanguage(saved);
    }
  } catch {
    // Non-fatal — continue with current language
  }
}

void i18n.use(initReactI18next).init({
  resources: {
    en: {
      common: enCommon,
      home: enHome,
      emergency: enEmergency,
      profile: enProfile,
      contacts: enContacts,
      destinations: enDestinations,
      readout: enReadout,
      settings: enSettings,
      onboarding: enOnboarding,
    },
    es: {
      common: esCommon,
      home: esHome,
      emergency: esEmergency,
      profile: esProfile,
      contacts: esContacts,
      destinations: esDestinations,
      readout: esReadout,
      settings: esSettings,
      onboarding: esOnboarding,
    },
  },
  lng: deviceLocale,
  fallbackLng: 'en',
  defaultNS: 'common',
  ns: [
    'common',
    'home',
    'emergency',
    'profile',
    'contacts',
    'destinations',
    'readout',
    'settings',
    'onboarding',
  ],
  interpolation: {
    escapeValue: false, // React already escapes values
  },
  compatibilityJSON: 'v4',
});

export default i18n;
