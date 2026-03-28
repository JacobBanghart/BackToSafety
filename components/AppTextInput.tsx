import React, { useState } from 'react';
import { View, TextInput, StyleSheet, KeyboardTypeOptions, TextInputProps } from 'react-native';
import { useTheme } from '@/context/ThemeContext';
import { Colors } from '@/constants/Colors';
import { ThemedText } from '@/components/ThemedText';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';

interface AppTextInputProps {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  hint?: string;
  multiline?: boolean;
  numberOfLines?: number;
  keyboardType?: KeyboardTypeOptions;
  autoComplete?: TextInputProps['autoComplete'];
  required?: boolean;
}

export function AppTextInput({
  label,
  value,
  onChangeText,
  placeholder,
  hint,
  multiline = false,
  numberOfLines,
  keyboardType = 'default',
  autoComplete,
  required = false,
}: AppTextInputProps) {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];
  const [isFocused, setIsFocused] = useState(false);

  return (
    <View style={styles.formField}>
      <ThemedText style={[styles.fieldLabel, { color: theme.text }]}>
        {label}
        {required ? ' *' : ''}
      </ThemedText>

      {hint != null && hint.length > 0 && (
        <ThemedText style={[styles.hint, { color: theme.textSecondary }]}>{hint}</ThemedText>
      )}

      <TextInput
        style={[
          styles.textInput,
          !multiline && styles.textInputSingleLine,
          multiline && styles.textArea,
          {
            backgroundColor: theme.inputBackground,
            borderColor: isFocused ? theme.borderFocused : theme.inputBorder,
            color: theme.text,
          },
        ]}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={theme.inputPlaceholder}
        multiline={multiline}
        numberOfLines={numberOfLines}
        keyboardType={keyboardType}
        autoComplete={autoComplete}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  formField: {
    marginBottom: Spacing.md,
  },
  fieldLabel: {
    ...Typography.bodyBold,
    marginBottom: Spacing.xs,
  },
  hint: {
    ...Typography.caption,
    marginBottom: Spacing.xs,
  },
  textInput: {
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.md,
    ...Typography.body,
    lineHeight: 20,
    minHeight: 44,
  },
  textInputSingleLine: {
    height: 44,
    paddingVertical: 0,
  },
  textArea: {
    minHeight: 80,
    paddingVertical: Spacing.sm,
    textAlignVertical: 'top',
  },
});
