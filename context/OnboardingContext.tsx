/**
 * Onboarding Context
 * Manages onboarding state and navigation
 */

import {
  completeOnboardingStep,
  getCurrentOnboardingStep,
  initializeDatabase,
  isOnboardingComplete,
} from '@/database';
import React, { createContext, useContext, useEffect, useState } from 'react';

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

  const refreshOnboardingState = async () => {
    try {
      const complete = await isOnboardingComplete();
      const step = await getCurrentOnboardingStep();
      setIsOnboarded(complete);
      setCurrentStep(step ?? 'complete');
    } catch (error) {
      console.error('[Onboarding] Error refreshing state:', error);
    }
  };

  const completeStep = async (step: string) => {
    await completeOnboardingStep(step);
    await refreshOnboardingState();
  };

  useEffect(() => {
    async function init() {
      try {
        // Initialize database (runs migrations if needed)
        await initializeDatabase();

        // Check onboarding status
        await refreshOnboardingState();
      } catch (error) {
        console.error('[Onboarding] Initialization error:', error);
      } finally {
        setIsLoading(false);
      }
    }
    init();
  }, []);

  return (
    <OnboardingContext.Provider
      value={{
        isLoading,
        isOnboarded,
        currentStep,
        completeStep,
        refreshOnboardingState,
      }}
    >
      {children}
    </OnboardingContext.Provider>
  );
}

export function useOnboarding() {
  const context = useContext(OnboardingContext);
  if (!context) {
    throw new Error('useOnboarding must be used within OnboardingProvider');
  }
  return context;
}
