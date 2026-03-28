# App Store Publish TODO

## Must Do Before Submission

- [ ] Add a public privacy policy URL for App Store Connect and Play Console.
- [ ] Add `eas.json` with production build/submission profiles.
- [ ] Fix branding fallback text in home screen (`app/(tabs)/index.tsx`) from `Wandering` to `Back to Safety`.
- [ ] Prepare store metadata package:
  - [ ] App description
  - [ ] Keywords
  - [ ] Support URL
  - [ ] Marketing URL
  - [ ] Screenshots
  - [ ] Promo text

## High-Value Technical Checks

- [ ] Resolve production dependency vulnerabilities from `npm audit --omit=dev`.
- [ ] Run release-build smoke tests on physical iOS and Android devices:
  - [ ] Onboarding flow
  - [ ] Emergency flow
  - [ ] SMS open/send handoff
  - [ ] Contacts import and permissions
  - [ ] Camera/photo permissions
- [ ] Verify icon and splash assets at required densities and store dimensions:
  - [ ] `assets/images/icon.png`
  - [ ] `assets/images/adaptive-icon.png`
  - [ ] `assets/images/splash-icon.png`

## Polish (Recommended)

- [ ] Replace starter README content in `README.md` with app-specific documentation.
- [ ] Update generic not-found copy in `app/+not-found.tsx` to branded messaging.

## Execution Order

1. [x] Create `eas.json` with `preview` and `production` profiles.
2. [x] Publish privacy policy page and capture final URL.
3. [x] Fix remaining branding fallback text.
4. [ ] Build TestFlight/Internal track release and run physical-device QA.
5. [ ] Upload final metadata and screenshots for submission.
