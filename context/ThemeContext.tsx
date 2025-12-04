/**
 * Theme Context
 * Allows users to override system theme preference
 */

import { getSetting, saveSetting } from '@/database/storage';
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { useColorScheme as useSystemColorScheme } from 'react-native';

export type ThemePreference = 'system' | 'light' | 'dark';
export type ColorScheme = 'light' | 'dark';

type ThemeContextType = {
  themePreference: ThemePreference;
  colorScheme: ColorScheme;
  setThemePreference: (pref: ThemePreference) => Promise<void>;
  isLoading: boolean;
};

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const systemColorScheme = useSystemColorScheme();
  const [themePreference, setThemePreferenceState] = useState<ThemePreference>('system');
  const [isLoading, setIsLoading] = useState(true);

  // Load saved preference on mount
  useEffect(() => {
    const load = async () => {
      try {
        const saved = await getSetting('theme_preference');
        if (saved === 'light' || saved === 'dark' || saved === 'system') {
          setThemePreferenceState(saved);
        }
      } catch (err) {
        console.error('Error loading theme preference:', err);
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, []);

  // Calculate effective color scheme
  const colorScheme: ColorScheme =
    themePreference === 'system' ? (systemColorScheme ?? 'light') : themePreference;

  // Save preference
  const setThemePreference = useCallback(async (pref: ThemePreference) => {
    setThemePreferenceState(pref);
    try {
      await saveSetting('theme_preference', pref);
    } catch (err) {
      console.error('Error saving theme preference:', err);
    }
  }, []);

  return (
    <ThemeContext.Provider value={{ themePreference, colorScheme, setThemePreference, isLoading }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
};

/**
 * Hook that returns the effective color scheme (for compatibility)
 */
export const useAppColorScheme = () => {
  const { colorScheme } = useTheme();
  return colorScheme;
};
