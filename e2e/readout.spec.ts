import { expect, test } from '@playwright/test';

import { onboard } from './helpers';

test('readout shows the 911 script and copies full details', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await onboard(page, {
    name: 'Margaret Smith',
    nickname: 'Maggie',
    appearance: {
      height: '56',
      weight: '140',
      hair: 'Gray, short',
      eyes: 'Blue',
      marks: 'Hearing aids',
    },
    contact: { name: 'John Smith', phone: '5551234567', relationship: 'Son' },
  });

  await page.getByTestId('home-readout').click();
  await page.getByTestId('readout-script-toggle').click();
  await expect(page.getByTestId('readout-script-text')).toHaveText(
    "I'm reporting a missing vulnerable adult who may be disoriented or at risk. " +
      'Name: Margaret Smith. Last seen: [fill in time]. Last known location: [fill in location]. ' +
      'Appearance: 5\'6", 140, Gray, short hair, Blue eyes, Hearing aids. ' +
      // No photo was added, so no "Photo available." (F-12).
      'Please advise about issuing a local Silver/Purple Alert.',
  );
  await expect(page.getByTestId('readout-script-missing')).toHaveText(
    'Add for stronger script: last seen time, last known location, important details.',
  );

  await page.getByTestId('readout-copy-all').click();
  await expect
    .poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toBe(
      [
        'Name: Margaret Smith (goes by "Maggie")',
        'Appearance: 5\'6", 140, Gray, short hair, Blue eyes, Hearing aids',
        'Last seen: Unknown',
        'Coordinates: Unknown',
        '',
        '⚠️ FILL IN: What were they wearing? (Shirt, jacket, pants, shoes, hat)',
      ].join('\n'),
    );
});
