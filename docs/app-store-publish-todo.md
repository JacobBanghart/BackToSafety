# App Store Publish TODO

## Must Do Before Submission

- [ ] Add a public privacy policy URL for App Store Connect and Play Console.
- [x] Add `eas.json` with production build/submission profiles.
- [x] Fix branding fallback text in home screen (`app/(tabs)/index.tsx`) from `Wandering` to `Back to Safety`.
- [ ] Prepare store metadata package:
  - [x] App description
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

## GitHub Pages + Domain

- [x] Add basic branded GitHub Pages site (`site/index.html`, `site/privacy.html`).
- [x] Add GitHub Actions Pages deployment workflow (`.github/workflows/deploy-pages.yml`).
- [ ] Enable Pages in repository settings (Source: GitHub Actions).
- [ ] Verify live URLs work:
  - [ ] `/`
  - [ ] `/privacy.html`
- [ ] Configure custom domain when ready:
  - [ ] Create `site/CNAME` from `site/CNAME.example`
  - [ ] Add DNS records
  - [ ] Enable HTTPS in GitHub Pages settings

## Public Repo Hardening

- [x] Add CI secret scanning workflow (`.github/workflows/secret-scan.yml`).
- [x] Add local pre-commit secret scan hook (`.githooks/pre-commit`).
- [ ] Enable local hooks path: `git config core.hooksPath .githooks`.
- [ ] Run one manual local secret scan before making repo public.

## Execution Order

1. [x] Create `eas.json` with `preview` and `production` profiles.
2. [x] Publish privacy policy page and capture final URL.
3. [x] Fix remaining branding fallback text.
4. [ ] Build TestFlight/Internal track release and run physical-device QA.
5. [ ] Upload final metadata and screenshots for submission.

## Recent Progress

- [x] Set Android EAS submit track to `internal` for draft rollout (`eas.json`).
- [x] Added Google Play listing copy template (`docs/google-play-listing-template.md`).
