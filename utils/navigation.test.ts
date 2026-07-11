import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Href } from 'expo-router';

const replace = vi.fn<(href: Href) => void>();
const back = vi.fn<() => void>();

vi.mock('expo-router', () => ({
  router: {
    replace: (href: Href) => replace(href),
    back: () => back(),
  },
}));

import { getPreviousRoute, goBack, setPreviousRoute } from './navigation';

// The module keeps `previousRoute` in memory at module scope, so tests must
// reset it themselves between runs rather than relying on import order.
function clearPreviousRoute() {
  setPreviousRoute(null as unknown as Href);
}

describe('navigation utils', () => {
  beforeEach(() => {
    replace.mockClear();
    back.mockClear();
    clearPreviousRoute();
    replace.mockClear();
  });

  describe('setPreviousRoute + getPreviousRoute', () => {
    it('stores and returns the route that was set', () => {
      setPreviousRoute('/profile');
      expect(getPreviousRoute()).toBe('/profile');
    });

    it('returns null when nothing has been set', () => {
      expect(getPreviousRoute()).toBeNull();
    });
  });

  describe('goBack', () => {
    it('replaces with the stored previous route when one was set', () => {
      setPreviousRoute('/contacts');

      goBack();

      expect(replace).toHaveBeenCalledWith('/contacts');
      expect(replace).toHaveBeenCalledTimes(1);
    });

    it('clears the stored route after use so a second call falls back', () => {
      setPreviousRoute('/contacts');

      goBack();
      expect(getPreviousRoute()).toBeNull();

      replace.mockClear();
      goBack();

      expect(replace).toHaveBeenCalledWith('/');
    });

    it('falls back to "/" by default when nothing was recorded', () => {
      goBack();

      expect(replace).toHaveBeenCalledWith('/');
    });

    it('falls back to a custom fallback route when provided', () => {
      goBack('/settings');

      expect(replace).toHaveBeenCalledWith('/settings');
    });

    it('does not call router.back()', () => {
      setPreviousRoute('/destinations');

      goBack();

      expect(back).not.toHaveBeenCalled();
    });
  });
});
