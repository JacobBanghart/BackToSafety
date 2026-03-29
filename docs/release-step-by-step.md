# Release Step-by-Step Guide

Use this as the single runbook for shipping Android releases to Google Play Internal testing.

## 0) Read these docs first

- Android signing and CI build flow: `docs/android-signing-and-release.md`
- Store listing copy and required Play sections: `docs/google-play-listing-template.md`
- Privacy policy source: `docs/privacy-policy.md`
- Privacy policy URL publish checklist: `docs/privacy-policy-url.md`
- Version bump rules: `docs/release-versioning.md`
- Master publish TODOs: `docs/app-store-publish-todo.md`

## 1) Pre-release prep

1. Confirm privacy/support pages are live and correct.
2. Confirm Play listing text is up to date (short and full description).
3. Confirm policy-sensitive behavior is accurate (permissions, data usage, SMS handoff).
4. Bump versions in `app.json`:
   - `expo.version`
   - `expo.android.versionCode`
5. If schema changed, follow migration rules in `docs/release-versioning.md`.

## 2) Verify signing setup

1. Ensure GitHub Actions secrets are set:
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
2. Confirm you still have secure backup access to the release keystore and passwords.

## 3) Build signed Android bundle

1. Open GitHub Actions.
2. Run workflow: `Android Release Build`.
3. Wait for success.
4. Download artifact: `app-release-aab`.

## 4) Complete Play Console compliance

In Play Console, complete or verify:

1. Data safety
2. Ads declaration
3. Content rating
4. Target audience
5. App access
6. Policy declaration acceptance

Keep answers aligned with `docs/privacy-policy.md` and actual app behavior.

## 5) Create internal test release

1. Play Console -> Testing -> Internal testing.
2. Create release.
3. Upload `app-release.aab` from artifact.
4. Add release notes.
5. Roll out to internal testers.

## 6) Smoke test on real Android device

1. Install from internal track.
2. Verify onboarding flow.
3. Verify contacts permission + import.
4. Verify camera/photo permission flows.
5. Verify emergency SMS handoff flow.
6. Verify no unexpected permissions prompt at startup.

## 7) Decide next promotion step

If internal testing looks good:

1. Promote to closed/open testing, or
2. Move to production rollout.

## Quick fail-safe checks

- Do not ship with debug signing.
- Do not commit keystore files.
- Do not publish until Data safety and privacy policy are consistent.
- Do not reuse stale store screenshots or misleading copy.
