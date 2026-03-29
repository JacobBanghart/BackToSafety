# Android Signing and Release (No Expo Account)

This project can produce Play Store-ready Android bundles using GitHub Actions + your own keystore.

## 1) Generate a release keystore (one time)

Run locally:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Save the four values somewhere safe:

- Keystore file (`release.keystore`)
- Keystore password
- Key alias (example: `release`)
- Key password

Never commit this file.

## 2) Add GitHub repository secrets

Convert keystore to base64:

```bash
base64 -w 0 release.keystore
```

In GitHub repo settings, add these secrets:

- `ANDROID_KEYSTORE_BASE64` - base64 string output
- `ANDROID_KEYSTORE_PASSWORD` - keystore password
- `ANDROID_KEY_ALIAS` - key alias
- `ANDROID_KEY_PASSWORD` - key password

## 3) Run release workflow

Workflow file: `.github/workflows/android-release.yml`

Trigger options:

- Manual (recommended): Actions -> `Android Release Build` -> Run workflow
- Tag push (optional): `git tag v1.0.1 && git push origin v1.0.1`

No GitHub Release object is required. Running the workflow manually is enough.

Output artifact:

- `app-release-aab` containing `app-release.aab`

## 4) Upload to Google Play

Use Play Console:

1. Open your app -> Testing -> Internal testing
2. Create release
3. Upload `app-release.aab`
4. Add release notes
5. Roll out to internal testers

## 5) First-time Play Console flow (recommended)

Use this order for your first Android app submission:

1. Complete required Play declarations/forms first (or as prompted):
   - Data safety
   - Ads declaration
   - Content rating
   - Target audience
   - App access
2. Accept the policy declaration once the answers are accurate.
3. Run GitHub Action `Android Release Build`.
4. Download artifact `app-release-aab` from the workflow run.
5. Create an Internal testing release in Play Console and upload the AAB.
6. Add release notes and roll out to internal testers.
7. Install from internal track on a real device and verify critical flows.

Suggested smoke test list:

- App opens and onboarding works
- Contacts permission and import work
- Camera/photo permission flows work
- Emergency SMS handoff works
- No unexpected permission prompts on startup

## Notes

- The Android Gradle config reads signing credentials from env vars:
  - `ANDROID_KEYSTORE_FILE`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- Debug signing for release is disabled by default (`android.useDebugSigningInRelease=false`).
- CI build speed optimizations are enabled:
  - Gradle build cache (`org.gradle.caching=true`)
  - GitHub Actions cache for Gradle wrapper/caches and native `.cxx` outputs
  - Build uses `--build-cache --parallel`
- For local test-only signed release builds, you can temporarily pass:

```bash
./gradlew bundleRelease -Pandroid.useDebugSigningInRelease=true
```
