import { useBottomTabBarHeight } from '@react-navigation/bottom-tabs';
import { BlurView } from 'expo-blur';
import { StyleSheet } from 'react-native';

import { useTheme } from '@/context/ThemeContext';

export default function BlurTabBarBackground() {
  const { colorScheme } = useTheme();

  return (
    <BlurView
      // Use app-level theme so the blur respects the user's in-app preference,
      // not just the OS system theme.
      tint={colorScheme === 'dark' ? 'systemMaterialDark' : 'systemMaterialLight'}
      intensity={100}
      style={StyleSheet.absoluteFill}
    />
  );
}

export function useBottomTabOverflow() {
  return useBottomTabBarHeight();
}
