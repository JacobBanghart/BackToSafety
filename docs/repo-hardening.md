# Repository Hardening Checklist

This repo includes baseline hardening so it can be safely public.

## What is configured

- CI secret scanning on push/PR: `.github/workflows/secret-scan.yml`
- Local pre-commit secret scan hook:
  - Hook: `.githooks/pre-commit`
  - Scanner script: `scripts/secret-scan.sh`

## One-time local setup

1. Install gitleaks:
   - macOS: `brew install gitleaks`
   - Linux: use package manager or release binary from GitHub
2. Enable repo hooks path:

```bash
git config core.hooksPath .githooks
```

3. Optional manual scan:

```bash
./scripts/secret-scan.sh
```

## Notes

- `android/app/debug.keystore` is present locally for Android debug builds but is not tracked in git.
- Never commit signing keys, `.env` files, API tokens, or credentials.
