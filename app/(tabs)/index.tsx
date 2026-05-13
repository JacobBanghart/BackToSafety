import { useFocusEffect } from '@react-navigation/native';
import { useRouter } from 'expo-router';
import { useCallback } from 'react';
import { View } from 'react-native';

export default function HomeTabRedirect() {
  const router = useRouter();
  useFocusEffect(
    useCallback(() => {
      router.replace('/');
    }, []),
  );
  return <View style={{ flex: 1 }} />;
}
