import Shared
import SwiftUI

/// components/AppCard.tsx: card background, 1pt border, radius lg, padding lg, margin below md.
struct AppCard<Content: View>: View {
    var surface = false
    @ViewBuilder let content: () -> Content
    @Environment(\.appColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Space.lg + 1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(surface ? colors.surface : colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
            .padding(.bottom, Space.md)
    }
}

/// components/ListItem.tsx: a caption label over a body value, with a 1pt rule below that
/// takes space. Pressable rows carry testID; the value carries <testID>-value.
///
/// A row with an `action` (an icon and what it does, such as copying) shows the icon at its end,
/// keeps the value to one line, and is a 48pt button announced by that action.
struct ListItem: View {
    let label: String
    let value: String
    var testID: String?
    var ruleColor: Color?
    var action: (icon: String, label: String)?
    var onPress: (() -> Void)?
    @Environment(\.appColors) private var colors

    var body: some View {
        if let onPress {
            Button(action: onPress) { row.frame(minHeight: 48).contentShape(Rectangle()) }
                .buttonStyle(.pressable)
                .accessibilityIdentifier(testID ?? "")
                .accessibilityHint(action?.label ?? "")
        } else {
            row
        }
    }

    private var row: some View {
        HStack(spacing: Space.md) {
            VStack(alignment: .leading, spacing: 0) {
                RNText(label, Typography.caption.spec, color: colors.textSecondary).padding(.bottom, 2)
                RNText(value, Typography.body.spec, color: colors.text, lines: action == nil ? nil : 1)
                    .accessibilityIdentifier(testID.map { "\($0)-value" } ?? "")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let action { SFIcon(action.icon, 20, colors.primaryText) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, Space.sm)
        .padding(.bottom, 1)
        .overlay(alignment: .bottom) { Rectangle().fill(ruleColor ?? colors.border).frame(height: 1) }
    }
}
