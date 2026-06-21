# Contribution Guide

This guide covers how to set up the project, make changes, and submit them.

---

## 1. Prerequisites

- Node.js LTS
- npm
- Expo CLI / EAS CLI (global or via `npx`)
- For native development:
  - Android Studio or Xcode
  - Valid Android SDK path for local APK/AAB builds

---

## 2. Setup

```bash
npm install
npx expo start
```

Run on a target:

```bash
npm run android   # Android emulator
npm run ios       # iOS simulator
npm run web       # Browser
```

---

## 3. Project Conventions

### File organization

- Screens live in `app/` under the route path that corresponds to the URL.
- Shared components live in `components/`.
- Hooks live in `hooks/`.
- Utilities live in `utils/`.
- Database code is split by platform: `*.native.ts` / `*.web.ts` / base `.ts` re-export.

### Imports

Use the `@/` path alias:

```ts
import { Colors } from '@/constants';
import { useThemeColor } from '@/hooks/useThemeColor';
```

### Naming

- Components: PascalCase.
- Hooks: camelCase, prefixed with `use`.
- Utils: camelCase.
- Database modules: camelCase.

### Styling

- Use the design token system (`Colors`, `Spacing`, `Typography`, `Shadows`).
- Avoid hard-coded colors; resolve through `useThemeColor`.
- Platform-specific styles are acceptable when required.

---

## 4. Making Changes

1. Create a branch for your work.
2. Write or update code, keeping diff minimal.
3. Add or update tests.
4. Run the test/type/lint/format checks.
5. Update relevant documentation in `docs/` if your change affects architecture, public APIs, or release processes.
6. Open a PR with a clear description.

---

## 5. Database Migrations

If you change the local data model:

1. Increment `SCHEMA_VERSION` in `database/schema.ts`.
2. Add a migration at the new version number in `MIGRATIONS`.
3. Verify migrations are sequential and each version exists between current and target.
4. Update [DATABASE.md](./DATABASE.md) and [Release Versioning](./release-versioning.md).
5. Never modify an already-released migration.

---

## 6. Versioning and Releases

See:

- [Release Versioning](./release-versioning.md)
- [Release Step by Step](./release-step-by-step.md)
- [Android Signing and Release](./android-signing-and-release.md)

Every store upload must increment at least one platform build number. Semantic version bumps happen per release train.

---

## 7. Security and Privacy

- All user data stays on-device; do not add cloud sync without explicit review.
- Treat all profile and contact data as sensitive PII.
- Do not commit secrets, signing keystores, or environment files.
- `.env` is ignored by `.gitignore`; use `.env.example` to document required variables.
- Run secret-scanning tools like `gitleaks` before committing.
