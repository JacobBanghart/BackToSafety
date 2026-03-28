/**
 * Onboarding Layout
 * Handles the onboarding flow screens
 */

import { Stack } from 'expo-router';

export default function OnboardingLayout() {
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
        gestureEnabled: false,
      }}
    >
      <Stack.Screen name="index" />
      <Stack.Screen name="name" />
      <Stack.Screen name="photo" />
      <Stack.Screen name="appearance" />
      <Stack.Screen name="contact" />
      <Stack.Screen name="complete" />
    </Stack>
  );
}
