# Design System

Back to Safety uses a centralized design token system in `constants/`. This document describes the available tokens and how to use them.

---

## 1. Entry Point (`constants/index.ts`)

The design tokens are consumed through the `constants/` directory:

```ts
import { Colors, Typography, Spacing, Radius, Shadows, getShadow } from '@/constants';
```

`constants/index.ts` re-exports these from the individual token files.

---

## 2. Colors

File: `constants/Colors.ts`

### Raw palettes

The raw palettes are anchored by a single color and generated outward:

- **Primary** — deep purple (anchor `#3f2875`)
- **Secondary** — gold/cream (anchor `#e3d895`)
- **Neutral** — gray scale
- **Semantic**
  - `success` — `#22c55e`
  - `warning` — `#f59e0b`
  - `error` — `#ef4444`
  - `info` — `#3b82f6`

### Theme tokens

`Colors.light` and `Colors.dark` expose the same semantic keys. Example keys:

| Token              | Use                      |
| ------------------ | ------------------------ |
| `text`             | Default body text        |
| `textSecondary`    | Subtitles, hints         |
| `textDisabled`     | Disabled states          |
| `textOnPrimary`    | Text on primary buttons  |
| `background`       | Screen background        |
| `card`             | Card surfaces            |
| `surface`          | Inline elevated surfaces |
| `overlay`          | Backdrops, scrims        |
| `border`           | Default borders          |
| `borderFocused`    | Focused input borders    |
| `divider`          | Section dividers         |
| `primary`          | Brand primary            |
| `primaryPressed`   | Primary press state      |
| `primaryLight`     | Light primary tint       |
| `secondary`        | Brand secondary          |
| `secondaryDark`    | Secondary press state    |
| `inputBackground`  | Input fill               |
| `inputBorder`      | Input border             |
| `inputPlaceholder` | Placeholder text         |
| `tint`             | Active icon/tab tint     |
| `icon`             | Default icon color       |

### Using colors

```ts
import { useThemeColor } from '@/hooks/useThemeColor';

const backgroundColor = useThemeColor({}, 'background');
const borderColor = useThemeColor({ light: '#ccc', dark: '#555' }, 'border');
```

---

## 3. Typography

File: `constants/Typography.ts`

### Type scale

| Name        | Size | Weight | Line Height | Use                         |
| ----------- | ---- | ------ | ----------- | --------------------------- |
| `display`   | 32   | 700    | 40          | Hero text                   |
| `headline`  | 24   | 700    | 32          | Major section titles        |
| `title`     | 20   | 600    | 28          | Screen titles, card headers |
| `bodyLarge` | 18   | 400    | 28          | Emphasized body             |
| `body`      | 16   | 400    | 24          | Default body                |
| `bodyBold`  | 16   | 600    | 24          | Bold body                   |
| `caption`   | 13   | 400    | 18          | Captions, metadata          |
| `small`     | 11   | 400    | 16          | Fine print                  |

### Applying typography

Use `<ThemedText type="title">` to apply a design-system type token. The component maps token names to the `Typography` definitions.

---

## 4. Spacing

File: `constants/Spacing.ts`

| Token  | Value |
| ------ | ----- |
| `xxs`  | 2     |
| `xs`   | 4     |
| `sm`   | 8     |
| `md`   | 12    |
| `lg`   | 16    |
| `xl`   | 24    |
| `xxl`  | 32    |
| `xxxl` | 48    |

### Border radius

| Token  | Value |
| ------ | ----- |
| `none` | 0     |
| `sm`   | 4     |
| `md`   | 8     |
| `lg`   | 12    |
| `xl`   | 16    |
| `full` | 9999  |

---

## 5. Shadows

File: `constants/Shadows.ts`

Three levels are defined: `sm`, `md`, `lg`.

- **Native**: generates `shadowColor`, `shadowOffset`, `shadowOpacity`, `shadowRadius`, and Android `elevation`.
- **Web**: generates a `boxShadow` string.

### Using shadows

```ts
import { Shadows, getShadow } from '@/constants';

const styles = StyleSheet.create({
  card: {
    ...getShadow('md', colorScheme),
  },
});
```

The shadow opacity adapts to the color scheme because dark-mode shadows use `rgba(0,0,0,...)`, while the active scheme influences the recommended shadow level for elevated surfaces.

---

## 6. Theme Resolution

- `ThemeContext` provides `themePreference` and `colorScheme`.
- `useColorScheme` returns the resolved color scheme.
- `useThemeColor` resolves a named token from `Colors[colorScheme]`.
- Components should prefer `ThemedText`/`ThemedView` and `useThemeColor` instead of hard-coded colors.
