import { describe, expect, it, vi } from 'vitest';

// Shadows picks native vs web tokens at load time; the spec records the native
// set, which is what both the Compose and SwiftUI apps implement.
vi.mock('react-native', () => ({ Platform: { OS: 'ios' } }));

const { Colors, primary, secondary, neutral, semantic } = await import('@/constants/Colors');
const { Typography } = await import('@/constants/Typography');
const { Spacing, Radius } = await import('@/constants/Spacing');
const { Shadows } = await import('@/constants/Shadows');

// spec/design-tokens.json is the language-neutral copy of constants/. Kotlin and
// Swift themes are generated from it, so any change here must be deliberate:
// re-bless with `npx vitest run spec -u` in its own commit (PARITY_PLAN rule 1).
describe('design tokens', () => {
  it('match spec/design-tokens.json', async () => {
    const tokens = {
      fontFamily: 'system',
      palette: { primary, secondary, neutral, semantic },
      colors: { light: Colors.light, dark: Colors.dark },
      typography: Typography,
      spacing: Spacing,
      radius: Radius,
      shadows: Shadows,
    };
    await expect(`${JSON.stringify(tokens, null, 2)}\n`).toMatchFileSnapshot('./design-tokens.json');
  });
});
