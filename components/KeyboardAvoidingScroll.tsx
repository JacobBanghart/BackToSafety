import { ReactNode, forwardRef } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, ScrollViewProps, StyleSheet } from 'react-native';

interface KeyboardAvoidingScrollProps extends ScrollViewProps {
  /**
   * Content pinned below the scroll view (e.g. a footer CTA button) that
   * still needs to be lifted above the keyboard. Passing a footer switches
   * the wrapper to 'padding' behavior on iOS so the whole stack — not just
   * the ScrollView's own inset — shrinks to make room for it.
   */
  footer?: ReactNode;
}

/**
 * Wraps scrollable form content so the keyboard never covers focused inputs.
 * Use this instead of hand-rolling KeyboardAvoidingView + ScrollView so every
 * form screen gets the same behavior.
 */
export const KeyboardAvoidingScroll = forwardRef<ScrollView, KeyboardAvoidingScrollProps>(
  function KeyboardAvoidingScroll({ style, children, footer, ...scrollViewProps }, ref) {
    return (
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? (footer ? 'padding' : undefined) : 'height'}
      >
        <ScrollView
          ref={ref}
          style={[styles.flex, style]}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          automaticallyAdjustKeyboardInsets={!footer}
          {...scrollViewProps}
        >
          {children}
        </ScrollView>
        {footer}
      </KeyboardAvoidingView>
    );
  },
);

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
});
