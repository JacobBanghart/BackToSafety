import Shared
import SwiftUI

/// components/ScreenHeader.tsx: back chevron on the left, a title centered against the full
/// width (optionally with an icon), and an optional right element. IDs: <testID>-back, -title.
struct ScreenHeader<Right: View>: View {
    let title: String
    let testID: String
    let onBack: () -> Void
    var titleIcon: (name: String, color: Color)? = nil
    var titleIconSize: CGFloat = 18
    @ViewBuilder var right: () -> Right
    @Environment(\.appColors) private var colors

    var body: some View {
        // The title is an overlay (absoluteFill in RN): it never sets the header's height, so a
        // large-text title overflows the 52pt bar rather than growing it.
        HStack(spacing: 0) {
            Button(action: onBack) {
                SFIcon("chevron.left", 22, colors.tint).frame(minWidth: 44, alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("\(testID)-back")
            Spacer()
            right().frame(minWidth: 44, alignment: .trailing)
        }
        .frame(maxWidth: .infinity, minHeight: 52 - 2 * Space.md)
        .padding(Space.md)
        .overlay {
            HStack(spacing: Space.xs) {
                if let icon = titleIcon { SFIcon(icon.name, titleIconSize, icon.color) }
                RNText(title, Typography.title.spec, color: colors.text, lines: 1)
                    .accessibilityIdentifier("\(testID)-title")
            }
            .padding(.horizontal, 44 + Space.md)
            .fixedSize(horizontal: false, vertical: true)
        }
        .background(colors.background)
    }
}

extension ScreenHeader where Right == EmptyView {
    init(title: String, testID: String, onBack: @escaping () -> Void, titleIcon: (name: String, color: Color)? = nil) {
        self.init(title: title, testID: testID, onBack: onBack, titleIcon: titleIcon, right: { EmptyView() })
    }
}

/// constants/Shadows.ts (iOS takes the shadow* values; Android the elevation).
enum ShadowLevel { case sm, md, lg }

extension View {
    func rnShadow(_ level: ShadowLevel, dark: Bool) -> some View {
        let (y, opacity, radius): (CGFloat, Double, CGFloat) = switch level {
        case .sm: (1, dark ? 0.3 : 0.08, 2)
        case .md: (2, dark ? 0.4 : 0.12, 6)
        case .lg: (4, dark ? 0.5 : 0.18, 12)
        }
        return shadow(color: .black.opacity(opacity), radius: radius / 2, x: 0, y: y)
    }
}
