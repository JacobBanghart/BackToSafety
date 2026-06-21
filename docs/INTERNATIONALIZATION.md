# Internationalization

Back to Safety supports multiple languages through `i18next` / `react-i18next`.

---

## 1. Supported Languages

| Code | Language | Status                                |
| ---- | -------- | ------------------------------------- |
| `en` | English  | Fully supported, fallback             |
| `es` | Spanish  | Available, surfaced in developer mode |

Additional languages can be added by creating a new folder under `i18n/locales/` and registering it in `i18n/index.ts`.

---

## 2. Initialization

`i18n/index.ts` bootstraps `i18next`:

- Detects the device locale via `expo-localization`.
- Loads `en` resources by default.
- Sets `fallbackLng: 'en'`.
- Registers resources by namespace.

```ts
const deviceLocale = Localization.getLocales()[0]?.languageCode ?? 'en';
```

### Saved language

After the database is ready, `loadSavedLanguage()` checks the `language_preference` setting and switches to it if valid.

---

## 3. Namespaces

Strings are organized by namespace to keep files small and domain-specific:

| Namespace      | Used by                                     |
| -------------- | ------------------------------------------- |
| `common`       | Shared labels, buttons, validation messages |
| `home`         | Home dashboard                              |
| `emergency`    | Emergency protocol                          |
| `profile`      | Profile editor                              |
| `contacts`     | Contacts manager                            |
| `destinations` | Destinations manager                        |
| `readout`      | 911 readout                                 |
| `settings`     | Settings                                    |
| `onboarding`   | Onboarding flow                             |

---

## 4. Usage in Components

Use the `useTranslation` hook from `react-i18next`:

```tsx
import { useTranslation } from 'react-i18next';

function MyScreen() {
  const { t } = useTranslation('home');
  return <Text>{t('startEmergency')}</Text>;
}
```

### Interpolation

```ts
t('emergency:callNumber', { emergencyNumber: '911' });
```

---

## 5. Language Switching

A language toggle is exposed on the **Welcome onboarding screen** in `__DEV__` builds and in **Settings** after Dev Mode is unlocked. The selection persists to `language_preference` and calls `i18n.changeLanguage()`.

---

## 6. Adding a Language

1. Create `i18n/locales/<code>/`.
2. Add JSON files matching existing namespaces.
3. Import the resources in `i18n/index.ts`.
4. Add the language code to the supported languages list and to the settings language toggle.
5. Update `expo-localization` plugin config if needed.
