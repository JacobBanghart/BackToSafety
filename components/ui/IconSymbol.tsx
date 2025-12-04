// Fallback for using MaterialIcons on Android and web.

import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { SymbolViewProps, SymbolWeight } from 'expo-symbols';
import { ComponentProps } from 'react';
import { OpaqueColorValue, type StyleProp, type TextStyle } from 'react-native';

type IconMapping = Record<SymbolViewProps['name'], ComponentProps<typeof MaterialIcons>['name']>;
type IconSymbolName = keyof typeof MAPPING;

/**
 * Add your SF Symbols to Material Icons mappings here.
 * - see Material Icons in the [Icons Directory](https://icons.expo.fyi).
 * - see SF Symbols in the [SF Symbols](https://developer.apple.com/sf-symbols/) app.
 */
const MAPPING = {
  'house.fill': 'home',
  'paperplane.fill': 'send',
  'chevron.left.forwardslash.chevron.right': 'code',
  'chevron.right': 'chevron-right',
  'chevron.left': 'chevron-left',
  'phone.fill': 'phone',
  pencil: 'edit',
  plus: 'add',
  phone: 'phone',
  person: 'person',
  location: 'location-on',
  'star.fill': 'star',
  'heart.fill': 'favorite',
  'person.2.fill': 'people',
  'briefcase.fill': 'work',
  'bell.fill': 'notifications',
  xmark: 'close',
  gear: 'settings',
  'gearshape.fill': 'settings',
  'arrow.up.right': 'open-in-new',
  'arrow.left.arrow.right': 'swap-horiz',
  'exclamationmark.triangle.fill': 'warning',
  'info.circle.fill': 'info',
  'mappin.and.ellipse': 'place',
  'drop.fill': 'water-drop',
  'building.columns.fill': 'account-balance',
  'cart.fill': 'shopping-cart',
  'fork.knife': 'restaurant',
  'figure.walk': 'directions-walk',
  mappin: 'place',
  ellipsis: 'more-horiz',
  'checkmark.circle.fill': 'check-circle',
  'wrench.fill': 'build',
  'trash.fill': 'delete',
  'paintbrush.fill': 'brush',
} as IconMapping;

/**
 * An icon component that uses native SF Symbols on iOS, and Material Icons on Android and web.
 * This ensures a consistent look across platforms, and optimal resource usage.
 * Icon `name`s are based on SF Symbols and require manual mapping to Material Icons.
 */
export function IconSymbol({
  name,
  size = 24,
  color,
  style,
}: {
  name: IconSymbolName;
  size?: number;
  color: string | OpaqueColorValue;
  style?: StyleProp<TextStyle>;
  weight?: SymbolWeight;
}) {
  return <MaterialIcons color={color} size={size} name={MAPPING[name]} style={style} />;
}
