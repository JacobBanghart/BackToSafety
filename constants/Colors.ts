/**
 * Back to Safety App Color Palette
 * Based on Material UI color guidelines
 * Primary: Deep Purple (900: #3f2875)
 * Secondary: Gold/Cream (100: #e3d895)
 */

// Primary palette - Deep Purple
export const primary = {
  50: '#f3f0f9',
  100: '#e1d9f0',
  200: '#cdc0e5',
  300: '#b8a6da',
  400: '#a892d2',
  500: '#987eca',
  600: '#8a6ebf',
  700: '#785ab0',
  800: '#5c4193',
  900: '#3f2875', // Your primary anchor
};

// Secondary palette - Gold/Cream
export const secondary = {
  50: '#fdfcf5',
  100: '#e3d895', // Your secondary anchor
  200: '#d9ca7a',
  300: '#cfbc5f',
  400: '#c5ae44',
  500: '#b89f2e',
  600: '#a08a26',
  700: '#87741f',
  800: '#6e5f19',
  900: '#554912',
};

// Neutral palette
export const neutral = {
  50: '#fafafa',
  100: '#f5f5f5',
  200: '#e5e5e5',
  300: '#d4d4d4',
  400: '#a3a3a3',
  500: '#737373',
  600: '#525252',
  700: '#404040',
  800: '#262626',
  900: '#171717',
};

// Semantic colors
export const semantic = {
  success: '#22c55e',
  warning: '#f59e0b',
  error: '#ef4444',
  info: '#3b82f6',
};

export const Colors = {
  light: {
    // Core text
    text: '#171717',
    textSecondary: neutral[600],
    textDisabled: neutral[400],
    textOnPrimary: '#ffffff',
    textOnSecondary: neutral[900],

    // Backgrounds
    background: '#ffffff',
    card: '#ffffff',
    surface: neutral[50],
    overlay: 'rgba(0,0,0,0.4)',

    // Borders & dividers
    border: neutral[200],
    borderFocused: primary[700],
    divider: neutral[200],

    // Primary & Secondary
    primary: primary[900],
    primaryPressed: primary[800],
    primaryLight: primary[100],
    secondary: secondary[100],
    secondaryDark: secondary[500],

    // Inputs
    inputBackground: '#ffffff',
    inputBorder: neutral[300],
    inputPlaceholder: neutral[400],

    // UI Elements
    tint: primary[700],
    icon: neutral[500],
    tabIconDefault: neutral[400],
    tabIconSelected: primary[700],

    // Semantic
    ...semantic,
  },
  dark: {
    // Core text
    text: '#fafafa',
    textSecondary: neutral[400],
    textDisabled: neutral[600],
    textOnPrimary: '#ffffff',
    textOnSecondary: neutral[900],

    // Backgrounds
    background: '#0a0a0a',
    card: neutral[900],
    surface: neutral[800],
    overlay: 'rgba(0,0,0,0.6)',

    // Borders & dividers
    border: neutral[800],
    borderFocused: primary[300],
    divider: neutral[800],

    // Primary & Secondary
    primary: primary[300],
    primaryPressed: primary[200],
    primaryLight: primary[900],
    secondary: secondary[100],
    secondaryDark: secondary[300],

    // Inputs
    inputBackground: neutral[900],
    inputBorder: neutral[700],
    inputPlaceholder: neutral[500],

    // UI Elements
    tint: primary[300],
    icon: neutral[400],
    tabIconDefault: neutral[500],
    tabIconSelected: primary[300],

    // Semantic
    ...semantic,
  },

  // Export raw palettes for direct access
  primary,
  secondary,
  neutral,
  semantic,
};
