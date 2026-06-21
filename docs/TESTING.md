# Testing Guide

This project uses **Vitest** for unit tests and **Playwright** for end-to-end web tests.

---

## 1. Unit Tests (Vitest)

Configuration: `vitest.config.ts`

### Running tests

```bash
npm test              # run once
npm run test:watch    # watch mode
npm run test:coverage # with coverage report
```

### Writing unit tests

- Place tests next to the file they exercise or in a `__tests__` directory.
- Use the `.test.ts` suffix.
- Existing example: `utils/phone.test.ts` covers phone formatting and normalization utilities.

### Best practices

- Keep tests deterministic and free of native module dependencies.
- Mock `expo-*` modules when testing files that import them.
- Prefer behavioral assertions over snapshot assertions.

---

## 2. End-to-End Tests (Playwright)

Configuration: `playwright.config.ts`

### Running E2E tests

```bash
npm run e2e         # headless
npm run e2e:headed  # visible browser
```

### E2E dev server

Playwright starts the app with:

```bash
npm run web:e2e
```

which runs `CI=1 expo start --web --port 19006`.

### E2E scope

Tests cover critical user paths on web, such as:

- Onboarding flow
- Profile creation and editing
- Starting and resolving an emergency
- Contacts and destinations management

Because the web build uses AsyncStorage instead of SQLite, E2E tests reflect the web data path.

---

## 3. Lint and Type Check

```bash
npm run lint       # ESLint via expo lint
npm run typecheck  # TypeScript --noEmit
npm run format     # Prettier write
npm run format:check # Prettier check
```

CI should run lint, typecheck, and tests before any merge.

---

## 4. Testing Checklist for New Features

Before opening a PR:

- [ ] Unit tests added for new utility logic.
- [ ] Existing unit tests still pass (`npm test`).
- [ ] E2E tests still pass (`npm run e2e`).
- [ ] Type check passes (`npm run typecheck`).
- [ ] Linter passes (`npm run lint`).
- [ ] Changes verified on both native and web if platform split files are involved.
