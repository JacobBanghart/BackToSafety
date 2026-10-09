import {
  DarkTheme,
  DefaultTheme,
  ThemeProvider as NavigationThemeProvider,
} from 'expo-router/react-navigation';
import { Stack, usePathname, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { I18nextProvider } from 'react-i18next';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import 'react-native-reanimated';
import { PostHogProvider } from 'posthog-react-native';

import { Colors, primary } from '@/constants/Colors';
import { OnboardingProvider, useOnboarding } from '@/context/OnboardingContext';
import { ProfileProvider } from '@/context/ProfileContext';
import { ThemeProvider, useTheme } from '@/context/ThemeContext';
import i18n from '@/i18n';
import { loadSavedLanguage } from '@/i18n';
import { getOrCreateDeviceId } from '@/utils/device-id';
import { initAnalytics } from '@/utils/analytics';
import { posthog } from '@/utils/posthog';

function RootLayoutNav() {
  const { colorScheme } = useTheme();
  const { isLoading, isOnboarded } = useOnboarding();
  const segments = useSegments();
  const router = useRouter();
  const pathname = usePathname();

  // PostHog's captureScreens reads @react-navigation/native's context, which expo-router
  // (SDK 56+) no longer provides — it ships its own navigation fork. Track screens by path.
  useEffect(() => {
    posthog.screen(pathname);
  }, [pathname]);

  useEffect(() => {
    if (!isLoading) {
      void loadSavedLanguage();
      void (async () => {
        const deviceId = await getOrCreateDeviceId();
        initAnalytics(deviceId);
      })();
    }
  }, [isLoading]);

  // Navigate based on onboarding state
  useEffect(() => {
    if (isLoading) return;

    const inOnboarding = segments[0] === 'onboarding';

    if (!isOnboarded && !inOnboarding) {
      // User hasn't completed onboarding, redirect to onboarding
      router.replace('/onboarding');
    } else if (isOnboarded && inOnboarding) {
      // User completed onboarding but is still on onboarding screens
      router.replace('/');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading, isOnboarded, segments]);

  // The navigators paint their containers with the navigation theme's background, which shows
  // around screens mid-transition. Use the app's background, not React Navigation's grays.
  const baseTheme = colorScheme === 'dark' ? DarkTheme : DefaultTheme;
  const navigationTheme = {
    ...baseTheme,
    colors: { ...baseTheme.colors, background: Colors[colorScheme].background },
  };

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={primary[700]} />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <NavigationThemeProvider value={navigationTheme}>
          <Stack>
            <Stack.Screen name="index" options={{ headerShown: false }} />
            <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
            <Stack.Screen name="onboarding" options={{ headerShown: false }} />
            <Stack.Screen name="emergency" options={{ headerShown: false }} />
            <Stack.Screen name="readout" options={{ headerShown: false }} />
            <Stack.Screen name="profile" options={{ headerShown: false }} />
            <Stack.Screen name="contacts" options={{ headerShown: false }} />
            <Stack.Screen name="destinations" options={{ headerShown: false }} />
            <Stack.Screen name="settings" options={{ headerShown: false }} />
            <Stack.Screen name="debug/clock" options={{ headerShown: false }} />
            <Stack.Screen name="+not-found" />
          </Stack>
          <StatusBar style={colorScheme === 'dark' ? 'light' : 'dark'} />
        </NavigationThemeProvider>
      </GestureHandlerRootView>
    </SafeAreaProvider>
  );
}

const RootLayout = () => {
  return (
    <I18nextProvider i18n={i18n}>
      <ThemeProvider>
        <OnboardingProvider>
          <ProfileProvider>
            <PostHogProvider
              client={posthog}
              autocapture={{
                captureScreens: false,
                captureTouches: true,
                propsToCapture: ['testID'],
              }}
            >
              <RootLayoutNav />
            </PostHogProvider>
          </ProfileProvider>
        </OnboardingProvider>
      </ThemeProvider>
    </I18nextProvider>
  );
};

export default RootLayout;
