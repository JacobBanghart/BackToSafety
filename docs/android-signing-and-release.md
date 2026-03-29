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

- Manual: Actions -> `Android Release Build` -> Run workflow
- Tag push: `git tag v1.0.1 && git push origin v1.0.1`

Output artifact:

- `app-release-aab` containing `app-release.aab`

## 4) Upload to Google Play

Use Play Console:

1. Open your app -> Testing -> Internal testing
2. Create release
3. Upload `app-release.aab`
4. Add release notes
5. Roll out to internal testers

## Notes

- The Android Gradle config reads signing credentials from env vars:
  - `ANDROID_KEYSTORE_FILE`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- Debug signing for release is disabled by default (`android.useDebugSigningInRelease=false`).
- For local test-only signed release builds, you can temporarily pass:

```bash
./gradlew bundleRelease -Pandroid.useDebugSigningInRelease=true
```
