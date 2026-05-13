import { useFocusEffect } from '@react-navigation/native';
import { useRouter } from 'expo-router';
import { useCallback } from 'react';
import { View } from 'react-native';

export default function SettingsTabRedirect() {
  const router = useRouter();

  useFocusEffect(
    useCallback(() => {
      router.replace('/settings');
    }, []),
  );

  return <View style={{ flex: 1 }} />;
}
