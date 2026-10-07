import { useFocusEffect } from 'expo-router/react-navigation';
import { useRouter } from 'expo-router';
import { useCallback } from 'react';
import { View } from 'react-native';

export default function HomeTabRedirect() {
  const router = useRouter();
  useFocusEffect(
    useCallback(() => {
      router.replace('/');
    }, [router]),
  );
  return <View style={{ flex: 1 }} />;
}
