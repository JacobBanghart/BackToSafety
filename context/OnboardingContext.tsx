/**
 * Onboarding Context
 * Manages onboarding state and navigation
 */

import { completeOnboardingStep, getCurrentOnboardingStep, isOnboardingComplete } from '@/database';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

type OnboardingState = {
  isLoading: boolean;
  isOnboarded: boolean;
  currentStep: string;
  completeStep: (step: string) => Promise<void>;
  refreshOnboardingState: () => Promise<void>;
};

const OnboardingContext = createContext<OnboardingState | undefined>(undefined);

export function OnboardingProvider({ children }: { children: React.ReactNode }) {
  const [isLoading, setIsLoading] = useState(true);
  const [isOnboarded, setIsOnboarded] = useState(false);
  const [currentStep, setCurrentStep] = useState('welcome');

  const refreshOnboardingState = useCallback(async () => {
    try {
      const complete = await isOnboardingComplete();
      const step = await getCurrentOnboardingStep();
      setIsOnboarded(complete);
      setCurrentStep(step ?? 'complete');
    } catch (error) {
      console.error('[Onboarding] Error refreshing state:', error);
    }
  }, []);

  const completeStep = useCallback(
    async (step: string) => {
      await completeOnboardingStep(step);
      await refreshOnboardingState();
    },
    [refreshOnboardingState],
  );

  // Database initialization is owned by DbGate (app/_layout.tsx), which
  // mounts this provider only after initializeDatabase() has resolved.
  useEffect(() => {
    async function init() {
      try {
        await refreshOnboardingState();
      } finally {
        setIsLoading(false);
      }
    }
    init();
  }, [refreshOnboardingState]);

  const value = useMemo(
    () => ({
      isLoading,
      isOnboarded,
      currentStep,
      completeStep,
      refreshOnboardingState,
    }),
    [isLoading, isOnboarded, currentStep, completeStep, refreshOnboardingState],
  );

  return <OnboardingContext.Provider value={value}>{children}</OnboardingContext.Provider>;
}

export function useOnboarding() {
  const context = useContext(OnboardingContext);
  if (!context) {
    throw new Error('useOnboarding must be used within OnboardingProvider');
  }
  return context;
}
