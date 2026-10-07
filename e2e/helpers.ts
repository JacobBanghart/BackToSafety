import { expect, type Page } from '@playwright/test';

export type OnboardingData = {
  name: string;
  nickname?: string;
  appearance?: { height?: string; weight?: string; hair?: string; eyes?: string; marks?: string };
  contact?: { name: string; phone: string; relationship?: string };
};

/** Walks onboarding through the real UI, skipping any step without data, and lands on home. */
export async function onboard(page: Page, data: OnboardingData) {
  await page.goto('/');
  await page.getByTestId('onboarding-get-started').click();

  await page.getByTestId('onboarding-name-input').fill(data.name);
  if (data.nickname) await page.getByTestId('onboarding-name-nickname').fill(data.nickname);
  await page.getByTestId('onboarding-name-continue').click();

  await page.getByTestId('onboarding-photo-skip').click();

  const a = data.appearance;
  if (a) {
    if (a.height) await page.getByTestId('onboarding-appearance-height').fill(a.height);
    if (a.weight) await page.getByTestId('onboarding-appearance-weight').fill(a.weight);
    if (a.hair) await page.getByTestId('onboarding-appearance-hair').fill(a.hair);
    if (a.eyes) await page.getByTestId('onboarding-appearance-eyes').fill(a.eyes);
    if (a.marks) await page.getByTestId('onboarding-appearance-marks').fill(a.marks);
    await page.getByTestId('onboarding-appearance-continue').click();
  } else {
    await page.getByTestId('onboarding-appearance-skip').click();
  }

  const c = data.contact;
  if (c) {
    await page.getByTestId('onboarding-contact-name').fill(c.name);
    await page.getByTestId('onboarding-contact-phone').fill(c.phone);
    if (c.relationship)
      await page.getByTestId('onboarding-contact-relationship').fill(c.relationship);
    await page.getByTestId('onboarding-contact-continue').click();
  } else {
    await page.getByTestId('onboarding-contact-skip').click();
  }

  await page.getByTestId('onboarding-complete-home').click();
  await expect(page.getByTestId('home-start-emergency')).toBeVisible();
}
