import Shared
import SwiftUI
import UIKit

/// components/AppTextInput.tsx on iOS: a bold label (with " *" when required), an optional
/// hint, and the field: 48pt single-line (a full touch target) or an 80-120pt text area, 1pt border (focused:
/// borderFocused), radius md, padding md. iOS inputs take the font's own line height.
struct FormTextInput: View {
    let label: String
    @Binding var text: String
    let testID: String
    var placeholder = ""
    var hint: String?
    var required = false
    var multiline = false
    var keyboard: UIKeyboardType = .default
    var capitalization: UITextAutocapitalizationType = .sentences
    var format: ((String) -> String)?
    @State private var focused = false
    @Environment(\.appColors) private var colors
    @Environment(\.dynamicTypeSize) private var dynamicType

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            RNText(required ? "\(label) *" : label, Typography.bodyBold.spec, color: colors.text).padding(.bottom, Space.xs)
            if let hint, !hint.isEmpty {
                RNText(hint, Typography.caption.spec, color: colors.textSecondary).padding(.bottom, Space.xs)
            }
            RNTextInput(
                text: $text, placeholder: placeholder, testID: testID,
                font: .systemFont(ofSize: 16 * rnFontMultiplier(dynamicType)), textColor: UIColor(colors.text),
                placeholderColor: UIColor(colors.inputPlaceholder), tint: UIColor(colors.tint),
                // A UITextView's text sits a point further in than a UITextField's.
                insets: UIEdgeInsets(top: multiline ? Space.sm - 1 : 0, left: multiline ? Space.md : Space.md - 1,
                                     bottom: multiline ? Space.sm - 1 : 0, right: multiline ? Space.md : Space.md - 1),
                multiline: multiline, keyboard: keyboard, capitalization: capitalization, minHeight: 78, maxHeight: 118,
                format: format, onFocus: { focused = $0 }
            )
            .frame(height: multiline ? nil : 46)
            .padding(1)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.inputBackground))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(focused ? colors.borderFocused : colors.inputBorder, lineWidth: 1))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, Space.md)
    }
}

/// The custom on/off switch on the contact form: 50x30, a 24pt knob that slides 20pt. On is the
/// brand primary, like every other selected control.
struct SwitchToggle: View {
    let on: Bool
    @Environment(\.appColors) private var colors

    var body: some View {
        Circle().fill(Color(argb: Light.background)).frame(width: 24, height: 24)
            .offset(x: on ? 20 : 0)
            .frame(width: 44, height: 24, alignment: .leading)
            .padding(3)
            .background(RoundedRectangle(cornerRadius: 15).fill(on ? colors.primary : colors.border))
    }
}

/// The header's action (Add / Update / Save / Saving...): a primary button inside a 48pt-tall
/// target, so it is easy to hit without crowding the bar. It has the body buttons' shape
/// (Radius.lg), not a pill: one shape means one kind of thing, a primary action.
struct HeaderSaveButton: View {
    let label: String
    let testID: String
    let enabled: Bool
    let action: () -> Void
    @Environment(\.appColors) private var colors

    var body: some View {
        Button(action: action) {
            RNText(label, Typography.bodyBold.spec, color: Color(argb: Light.textOnPrimary), lines: 1)
                .padding(.horizontal, Space.lg)
                .frame(minWidth: 72, minHeight: 40)
                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                .frame(minHeight: 48)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .disabled(!enabled)
        .accessibilityIdentifier(testID)
    }
}

/// roleOption / optionButton: an icon and a caption (500) in a bordered pill, 48pt tall to tap.
struct OptionChip: View {
    let label: String
    let icon: String
    let testID: String
    let selected: Bool
    let selectedColor: Color
    let action: () -> Void
    @Environment(\.appColors) private var colors

    var body: some View {
        let white = Color(argb: Light.textOnPrimary)
        Button(action: action) {
            HStack(spacing: 6) {
                SFIcon(icon, 16, selected ? white : colors.icon)
                RNText(label, Typography.caption.spec.weight(500), color: selected ? white : colors.text)
            }
            .padding(.horizontal, Space.md + 1).padding(.vertical, Space.sm + 1)
            // Fills its ChipGrid cell, so a row's two chips are the same width and height.
            .frame(maxWidth: .infinity, minHeight: 48, maxHeight: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(selected ? selectedColor : colors.inputBackground))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(selected ? selectedColor : colors.inputBorder, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Option chips two to a row at equal width, so they line up where a flowing row left one chip
/// alone on the last line. Two to a row only while every label fits on one line in half the
/// width: at large text one long label ("Primary Caregiver") wrapped inside its chip while the
/// others didn't, so then every chip takes the full width, one per row. Mirrors Android's
/// `ChipGrid`.
struct ChipGrid<Item, Content: View>: View {
    let items: [Item]
    @ViewBuilder let chip: (Item) -> Content

    var body: some View {
        ChipGridLayout(gap: Space.sm) {
            ForEach(items.indices, id: \.self) { i in chip(items[i]) }
        }
    }
}

private struct ChipGridLayout: Layout {
    let gap: CGFloat

    private struct Plan {
        var cell: CGFloat
        var columns: Int
        var rowHeights: [CGFloat]
    }

    private func plan(width: CGFloat, _ subviews: Subviews) -> Plan {
        let half = (width - gap) / 2
        // A chip's ideal width is its label on one line.
        let twoUp = subviews.allSatisfy { $0.sizeThatFits(.unspecified).width <= half }
        let cell = twoUp ? half : width
        let columns = twoUp ? 2 : 1
        let heights = subviews.map { $0.sizeThatFits(ProposedViewSize(width: cell, height: nil)).height }
        let rowHeights = stride(from: 0, to: heights.count, by: columns).map { heights[$0 ..< min($0 + columns, heights.count)].max() ?? 0 }
        return Plan(cell: cell, columns: columns, rowHeights: rowHeights)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let rows = plan(width: width, subviews).rowHeights
        return CGSize(width: width, height: rows.reduce(0, +) + gap * CGFloat(max(rows.count - 1, 0)))
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        let p = plan(width: bounds.width, subviews)
        var y = bounds.minY
        for (row, height) in p.rowHeights.enumerated() {
            for column in 0 ..< p.columns {
                let i = row * p.columns + column
                guard i < subviews.count else { break }
                subviews[i].place(at: CGPoint(x: bounds.minX + CGFloat(column) * (p.cell + gap), y: y),
                                  proposal: ProposedViewSize(width: p.cell, height: height))
            }
            y += height + gap
        }
    }
}

/// formDeleteButton: the outlined destructive button at the bottom of an edit form.
struct FormDeleteButton: View {
    let label: String
    let testID: String
    let action: () -> Void

    var body: some View {
        let error = Color(argb: Semantic.error)
        Button(action: action) {
            HStack(spacing: Space.xs) {
                SFIcon("trash", 14, error)
                RNText(label, Typography.bodyBold.spec, color: error)
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .padding(.horizontal, Space.md + 1).padding(.vertical, 1)
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(error, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
        .padding(.top, Space.md)
    }
}

extension View {
    /// The list cards' look: card (or primaryLight while dragged) with a 1pt border, lifted while dragged.
    func listCard(dragging: Bool, colors: AppColors) -> some View {
        frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(dragging ? colors.primaryLight : colors.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(dragging ? colors.primary : colors.border, lineWidth: 1))
            .scaleEffect(dragging ? 1.02 : 1)
            .opacity(dragging ? 0.98 : 1)
            .shadow(color: .black.opacity(dragging ? 0.2 : 0), radius: 4, y: 2)
    }
}

/// RN's Alert.alert on iOS: a UIAlertController with a cancel button and an action.
struct AppAlert: Identifiable {
    let id = UUID()
    let title: String
    var message: String?
    var cancel: String?
    /// Nil is the common "OK", which `appAlert` supplies.
    var confirm: String?
    var destructive = false
    var action: () -> Void = {}
}

extension View {
    func appAlert(_ alert: Binding<AppAlert?>, ok: String) -> some View {
        self.alert(alert.wrappedValue?.title ?? "", isPresented: Binding(get: { alert.wrappedValue != nil }, set: { if !$0 { alert.wrappedValue = nil } }),
                   presenting: alert.wrappedValue) { a in
            if let cancel = a.cancel { Button(cancel, role: .cancel) {} }
            Button(a.confirm ?? ok, role: a.destructive ? .destructive : nil, action: a.action)
        } message: { a in
            if let message = a.message { Text(message) }
        }
    }
}

/// utils/draggable-flatlist: long-press an item, drag it, and it swaps with each neighbour it
/// passes the middle of. [onReorder] gets the new order when the finger lifts. Lists here are a
/// handful of cards, so a plain VStack (inside the screen's scroll) is enough.
struct ReorderableColumn<Item, ID: Hashable, Content: View>: View {
    let items: [Item]
    let id: (Item) -> ID
    var onDragStart: () -> Void = {}
    var onRelease: () -> Void = {}
    let onReorder: ([Item]) -> Void
    @ViewBuilder let content: (Item, Int, Bool) -> Content
    @State private var order: [Item]? = nil
    @State private var draggingID: ID? = nil
    @State private var offset: CGFloat = 0
    @State private var heights: [ID: CGFloat] = [:]

    var body: some View {
        let current = order ?? items
        VStack(spacing: 0) {
            let rows = current.enumerated().map { (index: $0.offset, key: id($0.element), item: $0.element) }
            ForEach(rows, id: \.key) { row in
                let (index, key, item) = (row.index, row.key, row.item)
                let dragging = key == draggingID
                content(item, index, dragging)
                    .background(GeometryReader { g in Color.clear.onAppear { heights[key] = g.size.height }
                            .onChange(of: g.size.height) { _, h in heights[key] = h }
                    })
                    .offset(y: dragging ? offset : 0)
                    .zIndex(dragging ? 1 : 0)
                    .gesture(LongPressGesture(minimumDuration: 0.5).sequenced(before: DragGesture(minimumDistance: 0))
                        .onChanged { value in
                            guard case .second(true, let gesture) = value else { return }
                            if draggingID != key {
                                draggingID = key
                                order = items
                                offset = 0
                                onDragStart()
                            }
                            guard let gesture else { return }
                            move(key, gesture.translation.height)
                        }
                        .onEnded { _ in end() })
            }
        }
        .onChange(of: items.map(id)) { _, _ in if draggingID == nil { order = nil } }
    }

    @State private var consumed: CGFloat = 0

    private func move(_ key: ID, _ translation: CGFloat) {
        guard var list = order, let i = list.firstIndex(where: { id($0) == key }) else { return }
        offset = translation - consumed
        if offset > 0, i < list.count - 1, let next = heights[id(list[i + 1])], offset > next / 2 {
            list.insert(list.remove(at: i), at: i + 1)
            consumed += next
            offset -= next
        } else if offset < 0, i > 0, let prev = heights[id(list[i - 1])], -offset > prev / 2 {
            list.insert(list.remove(at: i), at: i - 1)
            consumed -= prev
            offset += prev
        }
        order = list
    }

    private func end() {
        let final = order ?? items
        let changed = final.map(id) != items.map(id)
        draggingID = nil
        offset = 0
        consumed = 0
        onRelease()
        if changed { onReorder(final) } else { order = nil }
    }
}
