import Shared
import SwiftUI

extension Color {
    /// A DesignTokens color (0xAARRGGBB).
    init(argb: Int64) {
        let v = UInt32(truncatingIfNeeded: argb)
        self.init(
            .sRGB,
            red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255, opacity: Double((v >> 24) & 0xFF) / 255
        )
    }
}

typealias Tokens = DesignTokens
let Light = DesignTokens.Light.shared
let Dark = DesignTokens.Dark.shared
let Primary = DesignTokens.Primary.shared
let Secondary = DesignTokens.Secondary.shared
let Neutral = DesignTokens.Neutral.shared
let Semantic = DesignTokens.Semantic.shared
let Typography = DesignTokens.Typography.shared

/// constants/Spacing.ts
enum Space {
    static let xxs = CGFloat(DesignTokens.Spacing.shared.xxs)
    static let xs = CGFloat(DesignTokens.Spacing.shared.xs)
    static let sm = CGFloat(DesignTokens.Spacing.shared.sm)
    static let md = CGFloat(DesignTokens.Spacing.shared.md)
    static let lg = CGFloat(DesignTokens.Spacing.shared.lg)
    static let xl = CGFloat(DesignTokens.Spacing.shared.xl)
    static let xxl = CGFloat(DesignTokens.Spacing.shared.xxl)
    static let xxxl = CGFloat(DesignTokens.Spacing.shared.xxxl)
}

enum Radius {
    static let sm = CGFloat(DesignTokens.Radius.shared.sm)
    static let md = CGFloat(DesignTokens.Radius.shared.md)
    static let lg = CGFloat(DesignTokens.Radius.shared.lg)
    static let xl = CGFloat(DesignTokens.Radius.shared.xl)
    static let full = CGFloat(DesignTokens.Radius.shared.full)
}

/// The RN app's theme colors (constants/Colors.ts via spec/design-tokens.json).
struct AppColors {
    let isDark: Bool
    private func pick(_ light: Int64, _ dark: Int64) -> Color { Color(argb: isDark ? dark : light) }
    var text: Color { pick(Light.text, Dark.text) }
    var textSecondary: Color { pick(Light.textSecondary, Dark.textSecondary) }
    var textDisabled: Color { pick(Light.textDisabled, Dark.textDisabled) }
    var textOnPrimary: Color { pick(Light.textOnPrimary, Dark.textOnPrimary) }
    var background: Color { pick(Light.background, Dark.background) }
    var card: Color { pick(Light.card, Dark.card) }
    var surface: Color { pick(Light.surface, Dark.surface) }
    var border: Color { pick(Light.border, Dark.border) }
    var borderFocused: Color { pick(Light.borderFocused, Dark.borderFocused) }
    var primary: Color { pick(Light.primary, Dark.primary) }
    var primaryLight: Color { pick(Light.primaryLight, Dark.primaryLight) }
    var tint: Color { pick(Light.tint, Dark.tint) }
    var icon: Color { pick(Light.icon, Dark.icon) }
    var overlay: Color { pick(Light.overlay, Dark.overlay) }
    var inputBackground: Color { pick(Light.inputBackground, Dark.inputBackground) }
    var inputBorder: Color { pick(Light.inputBorder, Dark.inputBorder) }
    var inputPlaceholder: Color { pick(Light.inputPlaceholder, Dark.inputPlaceholder) }
    var error: Color { pick(Light.error, Dark.error) }
    var success: Color { pick(Light.success, Dark.success) }
}

private struct AppColorsKey: EnvironmentKey {
    static let defaultValue = AppColors(isDark: false)
}

extension EnvironmentValues {
    var appColors: AppColors {
        get { self[AppColorsKey.self] }
        set { self[AppColorsKey.self] = newValue }
    }
}
