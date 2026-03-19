import React from 'react';
import { Modal, View, TouchableOpacity, StyleSheet } from 'react-native';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { semantic } from '@/constants/Colors';

type AppModalType = 'delete' | 'alert';

interface AppModalProps {
  visible: boolean;
  onDismiss: () => void;
  title: string;
  message: string;
  type: AppModalType;
  /** Label for the confirm button. Defaults to 'Delete' for delete type, 'OK' for alert. */
  confirmLabel?: string;
  onConfirm?: () => void;
}

export function AppModal({
  visible,
  onDismiss,
  title,
  message,
  type,
  confirmLabel,
  onConfirm,
}: AppModalProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const resolvedConfirmLabel =
    confirmLabel ?? (type === 'delete' ? 'Delete' : 'OK');

  const confirmBackgroundColor =
    type === 'delete' ? semantic.error : theme.primary;

  return (
    <Modal
      animationType="fade"
      transparent
      visible={visible}
      onRequestClose={onDismiss}
    >
      <View style={styles.overlay}>
        <View style={[styles.content, { backgroundColor: theme.card }]}>
          <ThemedText style={styles.title}>{title}</ThemedText>
          <ThemedText style={styles.message}>{message}</ThemedText>

          <View
            style={[
              styles.buttons,
              type !== 'delete' && styles.buttonsCentered,
            ]}
          >
            {type === 'delete' && (
              <TouchableOpacity
                style={[styles.button, styles.cancelButton, { borderColor: theme.border }]}
                onPress={onDismiss}
              >
                <ThemedText style={[styles.buttonText, { color: theme.text }]}>
                  Cancel
                </ThemedText>
              </TouchableOpacity>
            )}

            <TouchableOpacity
              style={[styles.button, { backgroundColor: confirmBackgroundColor }]}
              onPress={onConfirm ?? onDismiss}
            >
              <ThemedText style={[styles.buttonText, { color: '#fff' }]}>
                {resolvedConfirmLabel}
              </ThemedText>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.lg,
  },
  content: {
    borderRadius: Radius.xl,
    padding: Spacing.xl,
    width: '100%',
    maxWidth: 340,
  },
  title: {
    ...Typography.headline,
    marginBottom: Spacing.sm,
  },
  message: {
    ...Typography.body,
    marginBottom: Spacing.lg,
  },
  buttons: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  buttonsCentered: {
    justifyContent: 'center',
  },
  button: {
    flex: 1,
    padding: Spacing.md,
    borderRadius: Radius.md,
    alignItems: 'center',
  },
  cancelButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
  },
  buttonText: {
    ...Typography.bodyBold,
  },
});
