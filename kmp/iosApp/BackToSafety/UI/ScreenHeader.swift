import Shared
import SwiftUI

/// The screen's navigation bar (inline title, centred, as UINavigationBar draws it): a 48pt
/// back control on the left, the title centred against the full width (optionally with an
/// icon), and an optional action inset from the right edge. IDs: <testID>-back, -title.
struct ScreenHeader<Right: View>: View {
    let title: String
    let testID: String
    let onBack: () -> Void
    var titleIcon: (name: String, color: Color)?
    var titleIconSize: CGFloat = 18
    @ViewBuilder var right: () -> Right
    @Environment(\.appColors) private var colors

    var body: some View {
        // The title is an overlay (absoluteFill in RN): it never sets the header's height, so a
        // large-text title overflows the bar rather than growing it.
        HStack(spacing: 0) {
            // The platform's back control: a 48pt target with a 24pt chevron centred in it.
            Button(action: onBack) {
                SFIcon("chevron.left", 24, colors.tint).frame(width: 48, height: 48).contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityIdentifier("\(testID)-back")
            Spacer()
            right().frame(minWidth: 48, alignment: .trailing).padding(.trailing, Space.sm)
        }
        .frame(maxWidth: .infinity, minHeight: 56)
        .padding(.horizontal, Space.xs)
        .overlay {
            HStack(spacing: Space.xs) {
                if let icon = titleIcon { SFIcon(icon.name, titleIconSize, icon.color) }
                RNText(title, Typography.title.spec, color: colors.text, lines: 1)
                    .accessibilityIdentifier("\(testID)-title")
            }
            .padding(.horizontal, 48 + Space.sm)
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
