import { StyleSheet, Text, type TextProps } from 'react-native';

import { Typography, type TypographyVariant } from '@/constants/Typography';
import { useThemeColor } from '@/hooks/useThemeColor';

/** Legacy type names kept for backward compatibility. */
type LegacyVariant = 'default' | 'title' | 'defaultSemiBold' | 'subtitle' | 'link';

export type ThemedTextProps = TextProps & {
  lightColor?: string;
  darkColor?: string;
  /**
   * Typography variant.
   *
   * **New variants** (map to the 8-step design-system scale):
   *   `display` | `headline` | `title` | `bodyLarge` | `body` | `bodyBold` | `caption` | `small`
   *
   * **Legacy variants** (kept for backward compatibility):
   *   `default` | `defaultSemiBold` | `subtitle` | `link`
   *
   * Note: the legacy `title` name now resolves to the design-system `title` token (20px/600).
   */
  type?: TypographyVariant | LegacyVariant;
};

export function ThemedText({
  style,
  lightColor,
  darkColor,
  type = 'default',
  ...rest
}: ThemedTextProps) {
  const color = useThemeColor({ light: lightColor, dark: darkColor }, 'text');
  const linkColor = useThemeColor({}, 'primary');

  // Resolve legacy type aliases to their style objects.
  const legacyStyle = (() => {
    switch (type) {
      case 'default':      return styles.default;
      case 'defaultSemiBold': return styles.defaultSemiBold;
      // 'title' falls through to the Typography token below.
      case 'subtitle':    return styles.subtitle;
      case 'link':        return [styles.link, { color: linkColor }];
      default:            return undefined;
    }
  })();

  // Resolve design-system Typography tokens.
  const typographyStyle = (type as string) in Typography
    ? Typography[type as TypographyVariant]
    : undefined;

  return (
    <Text
      style={[
        { color },
        legacyStyle,
        typographyStyle,
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  /** Legacy: 16px/400 — use `body` for new code. */
  default: {
    fontSize: 16,
    lineHeight: 24,
  },
  /** Legacy: 16px/600 — use `bodyBold` for new code. */
  defaultSemiBold: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '600',
  },
  /** Legacy: 20px/bold — use `title` for new code. */
  subtitle: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  /** Legacy link style — color is overridden inline with theme.primary. */
  link: {
    lineHeight: 30,
    fontSize: 16,
  },
});
