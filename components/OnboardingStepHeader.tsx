import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors } from '@/constants/Colors';
import { Radius, Spacing } from '@/constants/Spacing';
import { useTheme } from '@/context/ThemeContext';

interface OnboardingStepHeaderProps {
  activeStep: number;
  totalSteps: number;
}

export function OnboardingStepHeader({ activeStep, totalSteps }: OnboardingStepHeaderProps) {
  const router = useRouter();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const dots = Array.from({ length: totalSteps }, (_, index) => {
    const step = index + 1;
    const isActive = step === activeStep;
    const isComplete = step < activeStep;

    return (
      <View
        key={step}
        style={[
          styles.progressDot,
          isActive && styles.progressActive,
          {
            backgroundColor: isComplete || isActive ? theme.primary : theme.border,
          },
        ]}
      />
    );
  });

  return (
    <View style={styles.headerRow}>
      <Pressable
        style={styles.backButton}
        onPress={() => {
          if (router.canGoBack()) {
            router.back();
            return;
          }

          router.replace('/onboarding');
        }}
        hitSlop={8}
      >
        <IconSymbol name="chevron.left" size={20} color={theme.tint} />
      </Pressable>

      <View style={styles.progress}>{dots}</View>

      <View style={styles.sideSpacer} />
    </View>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: Spacing.xxl,
  },
  backButton: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  progress: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: Spacing.sm,
  },
  progressDot: {
    width: 8,
    height: 8,
    borderRadius: Radius.sm,
  },
  progressActive: {
    width: 24,
  },
  sideSpacer: {
    minWidth: 44,
    minHeight: 44,
  },
});
