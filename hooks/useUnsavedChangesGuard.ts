import { useEffect, useRef } from 'react';
import { Alert } from 'react-native';

// Narrow structural type covering only what this hook actually calls on the
// navigation object returned by expo-router's useNavigation().
type BeforeRemoveNavigation = {
  addListener: (
    type: 'beforeRemove',
    callback: (event: { preventDefault: () => void; data: { action: unknown } }) => void,
  ) => () => void;
  // `never` here (rather than `unknown`) so real navigation.dispatch's
  // stricter action-union parameter type is still assignable to this
  // structural type; the call site casts the value with `as never` to match.
  dispatch: (action: never) => void;
};

type UseUnsavedChangesGuardOptions = {
  navigation: BeforeRemoveNavigation;
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
