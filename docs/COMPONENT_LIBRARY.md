# Component Library

This document catalogs the shared UI components in `components/`.

All components are written in TypeScript. They are intended to be theme-aware and handle both iOS/Android and web targets unless otherwise noted.

---

## 1. Themed Primitives

### `ThemedText`

Typography primitive that resolves design-system tokens.

**Props:**

- `type?: TypographyVariant` — one of `display`, `headline`, `title`, `bodyLarge`, `body`, `bodyBold`, `caption`, `small`
- `lightColor?: string` — override color in light mode
- `darkColor?: string` — override color in dark mode
- Standard `Text` props

**Notes:**

- Falls back to legacy variant names for backwards compatibility.
- Text color defaults to `Colors[colorScheme].text`.

---

### `ThemedView`

Container primitive that resolves the theme background color.

**Props:**

- `lightColor?: string`
- `darkColor?: string`
- Standard `View` props

**Notes:**

- Default background uses `Colors[colorScheme].background`.

---

## 2. Layout / Structure

### `ScreenHeader`

Consistent header with a centered title and optional back button and right action.

**Props:**

- `title: string`
- `onBack?: () => void`
- `rightElement?: React.ReactNode`
- `titleIcon?: { name: string; color?: string; size?: number }`

**Usage:**
Used on almost every screen instead of the native Stack header.

---

### `AppCard`

Styled card surface.

**Props:**

- `children: React.ReactNode`
- `style?: StyleProp<ViewStyle>`
- `variant?: 'default' | 'elevated' | 'surface'`

**Usage:**
Used for grouped content blocks, summary cards, and modal-style sections.

---

## 3. Inputs

### `AppTextInput`

Labeled text input with focus-aware borders and support for hints and multiline.

**Props:**

- `label: string`
- `value: string`
- `onChangeText: (text: string) => void`
- `placeholder?: string`
- `hint?: string`
- `multiline?: boolean`
- `numberOfLines?: number`
- `keyboardType?: KeyboardTypeOptions`
- `autoComplete?: any`
- `required?: boolean`
- Standard `TextInput` props

**Behavior:**

- Border color changes on focus.
- Hint text appears below the input.
- Required labels show an indicator.

---

## 4. Buttons

### `PrimaryButton`

Filled call-to-action button.

**Props:**

- `label: string`
- `onPress: () => void`
- `disabled?: boolean`
- `loading?: boolean`
- `style?: StyleProp<ViewStyle>`

---

### `SecondaryButton`

Outlined secondary button.

**Props:**

- `label: string`
- `onPress: () => void`
- `style?: StyleProp<ViewStyle>`

---

## 5. Lists

### `ListItem`

Settings-style row with a label, optional value/subtitle, optional right element, and optional press handler.

**Props:**

- `label: string`
- `value?: string`
- `onPress?: () => void`
- `rightElement?: React.ReactNode`
- `style?: StyleProp<ViewStyle>`

**Usage:**
Used heavily in `/settings`, modals, and summary screens.

---

### `OnboardingStepHeader`

Header for onboarding screens with a back button and progress dots.

**Props:**

- `activeStep: number`
- `totalSteps: number`

---

## 6. Icons

### `IconSymbol`

Unified icon component.

**Files:**

- `components/ui/IconSymbol.tsx` — default implementation using `@expo/vector-icons` Material icons fallback
- `components/ui/IconSymbol.ios.tsx` — iOS implementation using `expo-symbols`

**Props:**

- `name: IconSymbolName`
- `size?: number`
- `color?: string`

---

### `TabBarBackground`

Background element for the tab bar.

**Files:**

- `components/ui/TabBarBackground.tsx` — no-op on web/Android
- `components/ui/TabBarBackground.ios.tsx` — iOS `BlurView`

**Usage:**
Registered as the tab bar background in the tabs layout, although the tab bar is hidden.

---

## 7. Feedback / Interactions

### `HapticTab`

Bottom-tab button that adds iOS haptic feedback on press-in.

**Props:**

- `BottomTabBarButtonProps`

**Usage:**
Registered as `tabBarButton` where tabs are used.

---

## 8. Overlays

### `AppModal`

Simple confirmation or alert modal.

**Props:**

- `visible: boolean`
- `onDismiss: () => void`
- `title: string`
- `message?: string`
- `type: 'delete' | 'alert'`
- `confirmLabel?: string`
- `onConfirm?: () => void`

**Behavior:**

- `'delete'` shows destructive confirm/cancel actions.
- `'alert'` shows a single OK action.
- Dismiss backdrop is tap-to-close.
