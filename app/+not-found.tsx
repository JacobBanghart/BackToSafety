import { Link, Stack } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { StyleSheet } from 'react-native';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';

export default function NotFoundScreen() {
  const { t } = useTranslation('common');
  return (
    <>
      <Stack.Screen options={{ title: t('notFound.screenTitle') }} />
      <ThemedView style={styles.container}>
        <ThemedText type="title">{t('notFound.title')}</ThemedText>
        <Link href="/" style={styles.link}>
          <ThemedText type="link">{t('notFound.link')}</ThemedText>
        </Link>
      </ThemedView>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  link: {
    marginTop: 15,
    paddingVertical: 15,
  },
});
