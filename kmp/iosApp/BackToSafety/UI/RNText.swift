import Shared
import SwiftUI
import UIKit

/// A text style as the RN app gives one: size, line height, weight, letter spacing.
struct TextSpec {
    var size: CGFloat
    var lineHeight: CGFloat
    var weight: Int = 400
    var letterSpacing: CGFloat = 0
    var italic = false

    func weight(_ w: Int) -> TextSpec { var s = self; s.weight = w; return s }
    func spacing(_ l: CGFloat) -> TextSpec { var s = self; s.letterSpacing = l; return s }
    func size(_ size: CGFloat) -> TextSpec { var s = self; s.size = size; return s }
    func lineHeight(_ lh: CGFloat) -> TextSpec { var s = self; s.lineHeight = lh; return s }
    func italic(_ on: Bool = true) -> TextSpec { var s = self; s.italic = on; return s }
}

extension DesignTokens.Type_ {
    /// A constants/Typography.ts style.
    var spec: TextSpec {
        TextSpec(size: CGFloat(fontSize), lineHeight: CGFloat(lineHeight), weight: Int(fontWeight), letterSpacing: CGFloat(letterSpacing))
    }
}

/// RN's iOS Dynamic Type multipliers (RCTAccessibilityManager): font sizes and line heights
/// are scaled by these, not by UIFontMetrics.
func rnFontMultiplier(_ size: DynamicTypeSize) -> CGFloat {
    switch size {
    case .xSmall: return 0.823
    case .small: return 0.882
    case .medium: return 0.941
    case .large: return 1.0
    case .xLarge: return 1.118
    case .xxLarge: return 1.235
    case .xxxLarge: return 1.353
    case .accessibility1: return 1.786
    case .accessibility2: return 2.143
    case .accessibility3: return 2.643
    case .accessibility4: return 3.143
    case .accessibility5: return 3.571
    @unknown default: return 1.0
    }
}

private func uiWeight(_ w: Int) -> UIFont.Weight {
    switch w {
    case ..<350: return .light
    case ..<450: return .regular
    case ..<550: return .medium
    case ..<650: return .semibold
    case ..<750: return .bold
    default: return .heavy
    }
}

private func swiftUIWeight(_ w: Int) -> Font.Weight {
    switch w {
    case ..<350: return .light
    case ..<450: return .regular
    case ..<550: return .medium
    case ..<650: return .semibold
    case ..<750: return .bold
    default: return .heavy
    }
}

/// Text laid out the way RN's iOS text is (RCTAttributedTextUtils): every line exactly the
/// line height, the glyphs centered in it (a baseline offset of half the spare height),
/// sizes scaled by RN's Dynamic Type multiplier.
struct RNText: View {
    let text: String
    let spec: TextSpec
    var color: Color
    var align: TextAlignment = .leading
    var lines: Int? = nil
    @Environment(\.dynamicTypeSize) private var dynamicType

    init(_ text: String, _ spec: TextSpec, color: Color, align: TextAlignment = .leading, lines: Int? = nil) {
        self.text = text
        self.spec = spec
        self.color = color
        self.align = align
        self.lines = lines
    }

    var body: some View {
        let m = rnFontMultiplier(dynamicType)
        let size = spec.size * m
        let lineHeight = spec.lineHeight * m
        let fontLineHeight = UIFont.systemFont(ofSize: size, weight: uiWeight(spec.weight)).lineHeight
        let spare = max(0, lineHeight - fontLineHeight)
        Text(text)
            .font(.system(size: size, weight: swiftUIWeight(spec.weight)))
            .italic(spec.italic)
            .kerning(spec.letterSpacing * m)
            .foregroundStyle(color)
            .multilineTextAlignment(align)
            .lineLimit(lines)
            .lineSpacing(spare)
            .padding(.vertical, spare / 2)
    }
}

/// components/ui/IconSymbol.ios.tsx: an SF Symbol fitted into a size x size box.
struct SFIcon: View {
    let name: String
    let size: CGFloat
    let color: Color

    init(_ name: String, _ size: CGFloat, _ color: Color) {
        self.name = name
        self.size = size
        self.color = color
    }

    var body: some View {
        Image(systemName: name)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(color)
    }
}
