import { expect, test } from '@playwright/test';

test.describe('onboarding and emergency flow', () => {
  test('user can complete onboarding path and open emergency screen', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByTestId('onboarding-get-started')).toBeVisible();
    await page.getByTestId('onboarding-get-started').click();

    await expect(page.getByText('Who are you caring for?')).toBeVisible();
    await page.getByTestId('onboarding-name-input').fill('Test Person');
    await page.getByTestId('onboarding-name-continue').click();

    await expect(page.getByText('Add a recent photo')).toBeVisible();
    await page.getByTestId('onboarding-photo-skip').click();

    await expect(page.getByText('Physical description')).toBeVisible();
    await page.getByTestId('onboarding-appearance-continue').click();

    await expect(page.getByText('Emergency contact')).toBeVisible();
    await page.getByTestId('onboarding-contact-skip').click();

    await expect(page.getByText("You're ready!")).toBeVisible();
    await page.getByTestId('onboarding-complete-home').click();

    await expect(page.getByText('Start Emergency Search')).toBeVisible();
    await page.getByTestId('home-start-emergency').click();

    await expect(page.getByTestId('emergency-screen')).toBeVisible();
    await expect(page.getByText('Search Protocol')).toBeVisible();
  });
});
