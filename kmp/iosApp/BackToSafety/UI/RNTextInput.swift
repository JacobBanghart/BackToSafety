import SwiftUI
import UIKit

/// An RN TextInput on iOS: a UITextField (a UITextView when multiline) filling its box, with
/// the padding as text insets, so a tap anywhere in the box focuses it and the testID covers
/// the whole box as in the RN app. The caller draws the border around it.
struct RNTextInput: UIViewRepresentable {
    @Binding var text: String
    let placeholder: String
    let testID: String
    var font: UIFont
    var textColor: UIColor
    var placeholderColor: UIColor
    var tint: UIColor
    var insets: UIEdgeInsets
    var multiline = false
    var keyboard: UIKeyboardType = .default
    var capitalization: UITextAutocapitalizationType = .sentences
    var maxLength: Int? = nil
    /// An RN lineHeight on the input (only multiline inputs set one on iOS).
    var lineHeight: CGFloat? = nil
    /// A multiline input's height limits (RN minHeight/maxHeight, inside the border).
    var minHeight: CGFloat = 0
    var maxHeight: CGFloat = .infinity
    var format: ((String) -> String)? = nil
    var onFocus: (Bool) -> Void = { _ in }

    /// The text attributes RN gives an input with a lineHeight (RCTAttributedTextUtils).
    fileprivate var attributes: [NSAttributedString.Key: Any] {
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: textColor]
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            attrs[.paragraphStyle] = paragraph
            attrs[.baselineOffset] = max(0, (lineHeight - font.lineHeight) / 2)
        }
        return attrs
    }

    func makeUIView(context: Context) -> UIView {
        if multiline {
            let view = PlaceholderTextView()
            view.delegate = context.coordinator
            view.backgroundColor = .clear
            view.textContainer.lineFragmentPadding = 0
            view.isScrollEnabled = true
            return view
        }
        let field = InsetTextField()
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return field
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.parent = self
        view.accessibilityIdentifier = testID
        if let field = view as? InsetTextField {
            if field.text != text { field.text = text }
            field.font = font
            field.textColor = textColor
            field.tintColor = tint
            field.insets = insets
            field.keyboardType = keyboard
            field.autocapitalizationType = capitalization
            field.autocorrectionType = keyboard == .default ? .default : .no
            field.attributedPlaceholder = NSAttributedString(string: placeholder, attributes: [.foregroundColor: placeholderColor, .font: font])
        } else if let area = view as? PlaceholderTextView {
            area.typingAttributes = attributes
            if area.text != text { area.attributedText = NSAttributedString(string: text, attributes: attributes) }
            area.tintColor = tint
            area.textContainerInset = insets
            area.keyboardType = keyboard
            area.autocapitalizationType = capitalization
            var placeholderAttributes = attributes
            placeholderAttributes[.foregroundColor] = placeholderColor
            area.placeholder.attributedText = NSAttributedString(string: placeholder, attributes: placeholderAttributes)
            area.setNeedsLayout()
            area.placeholder.isHidden = !text.isEmpty
        }
    }

    /// A multiline input sizes to its content as RN's does: the text, or the placeholder while
    /// empty, plus the insets. Callers clamp it with min and max heights.
    func sizeThatFits(_ proposal: ProposedViewSize, uiView _: UIView, context _: Context) -> CGSize? {
        guard multiline, let width = proposal.width, width.isFinite else { return nil }
        var attrs = attributes
        if text.isEmpty { attrs[.foregroundColor] = placeholderColor }
        let content = NSAttributedString(string: text.isEmpty ? placeholder : text, attributes: attrs)
        let textWidth = max(0, width - insets.left - insets.right)
        let height = content.boundingRect(with: CGSize(width: textWidth, height: .greatestFiniteMagnitude),
                                          options: [.usesLineFragmentOrigin], context: nil).height
        return CGSize(width: width, height: min(maxHeight, max(minHeight, ceil(height) + insets.top + insets.bottom)))
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UITextFieldDelegate, UITextViewDelegate {
        var parent: RNTextInput
        init(_ parent: RNTextInput) { self.parent = parent }

        /// Formatters rewrite the text; the cursor goes to the end, as RN's controlled input does.
        private func apply(_ raw: String) -> String {
            var value = parent.format?(raw) ?? raw
            if let max = parent.maxLength { value = String(value.prefix(max)) }
            parent.text = value
            return value
        }

        @objc func changed(_ field: UITextField) {
            let value = apply(field.text ?? "")
            if field.text != value {
                field.text = value
                let end = field.endOfDocument
                field.selectedTextRange = field.textRange(from: end, to: end)
            }
        }

        func textViewDidChange(_ view: UITextView) {
            let value = apply(view.text)
            if view.text != value { view.text = value }
            (view as? PlaceholderTextView)?.placeholder.isHidden = !value.isEmpty
        }

        // A single-line field gives up the keyboard on Return (RN's default blurOnSubmit).
        func textFieldShouldReturn(_ field: UITextField) -> Bool { field.resignFirstResponder(); return true }
        func textFieldDidBeginEditing(_: UITextField) { parent.onFocus(true) }
        func textFieldDidEndEditing(_: UITextField) { parent.onFocus(false) }
        func textViewDidBeginEditing(_: UITextView) { parent.onFocus(true) }
        func textViewDidEndEditing(_: UITextView) { parent.onFocus(false) }
    }
}

final class InsetTextField: UITextField {
    var insets = UIEdgeInsets.zero
    override func textRect(forBounds bounds: CGRect) -> CGRect { bounds.inset(by: insets) }
    override func editingRect(forBounds bounds: CGRect) -> CGRect { bounds.inset(by: insets) }
    override func placeholderRect(forBounds bounds: CGRect) -> CGRect { bounds.inset(by: insets) }
}

final class PlaceholderTextView: UITextView {
    let placeholder = UILabel()

    override init(frame: CGRect, textContainer: NSTextContainer?) {
        super.init(frame: frame, textContainer: textContainer)
        placeholder.numberOfLines = 0
        addSubview(placeholder)
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        let inset = textContainerInset
        let width = bounds.width - inset.left - inset.right
        placeholder.preferredMaxLayoutWidth = width
        placeholder.frame = CGRect(x: inset.left, y: inset.top, width: width, height: placeholder.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height)
    }
}

extension UIFont.Weight {
    init(rn weight: Int) {
        switch weight {
        case ..<450: self = .regular
        case ..<550: self = .medium
        case ..<650: self = .semibold
        default: self = .bold
        }
    }
}
