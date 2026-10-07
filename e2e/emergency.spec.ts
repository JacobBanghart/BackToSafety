import { expect, test } from '@playwright/test';

import { onboard } from './helpers';

test('emergency checklist lists the protocol and tracks progress', async ({ page }) => {
  await onboard(page, { name: 'Margaret Smith' });
  await page.getByTestId('home-start-emergency').click();

  await expect(page.getByTestId('emergency-timer-label')).toHaveText('Time remaining');
  await expect(page.getByTestId('emergency-timer')).toHaveText(/^(15:00|14:5\d)$/);
  await expect(page.getByTestId('emergency-progress')).toHaveText('0/11 steps complete');

  const steps = page.locator('[data-testid^="emergency-step-"]');
  await expect(steps).toHaveCount(11);
  // innerText keeps the line breaks between a step's number, title and description.
  expect(await steps.allInnerTexts()).toEqual([
    '1\nSearch home thoroughly\nCheck every room, closet, under beds, bathrooms, garage, basement, sheds\n💡 People often seek small, quiet spaces',
    '2\nCheck outside areas\nYard, porches, paths, driveways, inside vehicles (locked or unlocked)',
    '3\nAlert neighbors\nShow photo, ask them to call if seen. Check their yards too.',
    '4\nSearch 1-1.5 mile radius\nMost people are found within this distance from home',
    '5\nCheck high-risk areas first\nPRIORITY\nWater (pools, ponds, streams), wooded areas, ditches, busy roads',
    '6\nSearch familiar places\nFormer home, church, old workplace, favorite walking routes',
    '7\nCall 911\nPRIORITY\nIf not found within 15 minutes, call immediately',
    '8\nRequest Silver/Feather Alert\nAsk 911 dispatcher about activating state alert program',
    '9\nShare relevant personal details\nShare their appearance, daily routines, and communication needs with responders.',
    '10\nCoordinate search efforts\nAssign areas to helpers, avoid duplicating coverage',
    '11\nDocument everything\nNote times, areas checked, people contacted for responders',
  ]);

  await page.getByTestId('emergency-step-neighbors').click();
  await expect(page.getByTestId('emergency-progress')).toHaveText('1/11 steps complete');
});

test('countdown follows the clock, not tick count (F-16)', async ({ page }) => {
  await onboard(page, { name: 'Margaret Smith' });
  await page.getByTestId('home-start-emergency').click();
  await expect(page.getByTestId('emergency-timer')).toHaveText(/^(15:00|14:5\d)$/);

  // Jump 14 minutes ahead, as if the app had been in the background.
  const advance = (seconds: number) =>
    page.evaluate(
      (ms) =>
        (window as unknown as { __nijiiAdvanceClock: (ms: number) => void }).__nijiiAdvanceClock(
          ms,
        ),
      seconds * 1000,
    );
  await advance(14 * 60);
  await expect(page.getByTestId('emergency-timer')).toHaveText(/^0[01]:\d\d$/);
  await expect(page.getByTestId('emergency-timer-label')).toHaveText('Time remaining');

  await advance(60);
  await expect(page.getByTestId('emergency-timer')).toHaveText('00:00');
  await expect(page.getByTestId('emergency-timer-label')).toHaveText('TIME TO CALL 911');
});

test('calling 911 twice keeps the step checked (F-17)', async ({ page }) => {
  await onboard(page, { name: 'Margaret Smith' });
  await page.getByTestId('home-start-emergency').click();
  await expect(page.getByTestId('emergency-progress')).toHaveText('0/11 steps complete');

  await page.getByTestId('emergency-call-911').click();
  await expect(page.getByTestId('emergency-progress')).toHaveText('1/11 steps complete');
  await page.getByTestId('emergency-call-911').click();
  await expect(page.getByTestId('emergency-progress')).toHaveText('1/11 steps complete');
});
