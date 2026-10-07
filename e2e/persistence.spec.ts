import { expect, test } from '@playwright/test';

import { onboard } from './helpers';

test('the last-seen time survives a restart mid-emergency (F-23)', async ({ page }) => {
  await onboard(page, { name: 'Margaret Smith' });
  await page.getByTestId('home-start-emergency').click();
  await expect(page.getByTestId('emergency-timer')).toBeVisible();

  // A reload drops all in-memory state, like the OS killing the app.
  await page.reload();
  await page.goto('/readout');
  await page.getByTestId('readout-script-toggle').click();
  await expect(page.getByTestId('readout-script-text')).toContainText('Last seen: ');
  await expect(page.getByTestId('readout-script-text')).not.toContainText('[fill in time]');
});

test('an emergency is recorded as an incident (F-22)', async ({ page }) => {
  await onboard(page, { name: 'Margaret Smith' });
  await page.getByTestId('home-start-emergency').click();
  await page.getByTestId('emergency-step-neighbors').click();
  await page.getByTestId('emergency-wearing-input').fill('Blue jacket');
  await page.getByTestId('emergency-found').click();
  await expect(page.getByTestId('emergency-modal-found-ok')).toBeVisible();

  const incidents = () =>
    page.evaluate(() => JSON.parse(localStorage.getItem('@nijii/incidents') ?? '[]'));
  await expect.poll(async () => (await incidents())[0]?.outcome).toBe('found');
  const [incident] = await incidents();
  expect(incident.endedAt).toBeTruthy();
  expect(incident.areasChecked).toEqual(['neighbors']);
  expect(incident.wearing).toBe('Blue jacket');
});
