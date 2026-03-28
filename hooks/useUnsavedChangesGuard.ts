import { useEffect, useRef } from 'react';
import { Alert } from 'react-native';
import { EventArg, NavigationProp, ParamListBase } from '@react-navigation/native';

type BeforeRemoveEvent = EventArg<'beforeRemove', true, { action: unknown }>;

type UseUnsavedChangesGuardOptions = {
  navigation: Pick<NavigationProp<ParamListBase>, 'addListener' | 'dispatch'>;
  hasUnsavedChanges: boolean;
  isSaving?: boolean;
  title: string;
  message: string;
  cancelLabel?: string;
  confirmLabel?: string;
  onDiscard?: () => void;
};

export function useUnsavedChangesGuard({
  navigation,
  hasUnsavedChanges,
  isSaving = false,
  title,
  message,
  cancelLabel = 'Keep Editing',
  confirmLabel = 'Discard',
  onDiscard,
}: UseUnsavedChangesGuardOptions) {
  const skipNextBeforeRemoveRef = useRef(false);

  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', (event) => {
      if (skipNextBeforeRemoveRef.current) {
        skipNextBeforeRemoveRef.current = false;
        return;
      }

      if (!hasUnsavedChanges || isSaving) {
        return;
      }

      event.preventDefault();

      Alert.alert(title, message, [
        { text: cancelLabel, style: 'cancel' },
        {
          text: confirmLabel,
          style: 'destructive',
          onPress: () => {
            skipNextBeforeRemoveRef.current = true;
            onDiscard?.();
            navigation.dispatch(event.data.action as never);
          },
        },
      ]);
    });

    return unsubscribe;
  }, [
    navigation,
    hasUnsavedChanges,
    isSaving,
    title,
    message,
    cancelLabel,
    confirmLabel,
    onDiscard,
  ]);
}
