import { expect, test } from '@playwright/test';

test.describe('onboarding and emergency flow', () => {
  test('user can complete onboarding path and open emergency screen', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByLabel('onboarding-get-started')).toBeVisible();
    await page.getByLabel('onboarding-get-started').click();

    await expect(page.getByText('Who are you caring for?')).toBeVisible();
    await page.getByLabel('onboarding-name-input').fill('Test Person');
    await page.getByLabel('onboarding-name-continue').click();

    await expect(page.getByText('Add a recent photo')).toBeVisible();
    await page.getByLabel('onboarding-photo-skip').click();

    await expect(page.getByText('Physical description')).toBeVisible();
    await page.getByLabel('onboarding-appearance-continue').click();

    await expect(page.getByText('Emergency contact')).toBeVisible();
    await page.getByLabel('onboarding-contact-skip').click();

    await expect(page.getByText("You're ready!")).toBeVisible();
    await page.getByLabel('onboarding-complete-home').click();

    await expect(page.getByText('Start Emergency Search')).toBeVisible();
    await page.getByLabel('home-start-emergency').click();

    await expect(page.getByLabel('emergency-screen')).toBeVisible();
    await expect(page.getByText('Search Protocol')).toBeVisible();
  });
});
