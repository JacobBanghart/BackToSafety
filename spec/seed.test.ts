import { expect, it } from 'vitest';

import { DEFAULT_SAFETY_CHECKS, ONBOARDING_STEPS } from '@/database/schema';

// Rows the app seeds on every launch (INSERT OR IGNORE). The Kotlin Store seeds the
// same ones; its test reads this file. Re-bless with `npx vitest run spec -u`.
it('seed rows match spec/seed.json', async () => {
  const seed = { onboardingSteps: ONBOARDING_STEPS, safetyChecks: DEFAULT_SAFETY_CHECKS };
  await expect(`${JSON.stringify(seed, null, 2)}\n`).toMatchFileSnapshot('./seed.json');
});
