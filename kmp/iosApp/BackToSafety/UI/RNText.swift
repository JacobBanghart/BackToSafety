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
    case ..<350: .light
    case ..<450: .regular
    case ..<550: .medium
    case ..<650: .semibold
    case ..<750: .bold
    default: .heavy
    }
}

/// Text laid out the way RN's iOS text is: an attributed string as RCTAttributedTextUtils
/// builds it (every line exactly the line height, the glyphs centered in it by a baseline
/// offset of half the spare height, sizes scaled by RN's Dynamic Type multiplier), measured
/// and drawn with TextKit as RCTTextLayoutManager does. SwiftUI's Text breaks lines
/// differently (it pushes a word down rather than leave one alone on the last line).
struct RNText: View {
    let text: String
    let spec: TextSpec
    var color: Color
    var align: TextAlignment = .leading
    var lines: Int?
    @Environment(\.dynamicTypeSize) private var dynamicType

    init(_ text: String, _ spec: TextSpec, color: Color, align: TextAlignment = .leading, lines: Int? = nil) {
        self.text = text
        self.spec = spec
        self.color = color
        self.align = align
        self.lines = lines
    }

    var body: some View {
        TextKitText(string: attributed, lines: lines ?? 0)
            .accessibilityElement()
            .accessibilityLabel(text)
            .accessibilityAddTraits(.isStaticText)
    }

    private var attributed: NSAttributedString {
        let m = rnFontMultiplier(dynamicType)
        var font = UIFont.systemFont(ofSize: spec.size * m, weight: uiWeight(spec.weight))
        if spec.italic, let italic = font.fontDescriptor.withSymbolicTraits(.traitItalic) {
            font = UIFont(descriptor: italic, size: font.pointSize)
        }
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = switch align {
        case .center: .center
        case .trailing: .right
        default: .natural
        }
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor(color)]
        // RN scales font size and line height with Dynamic Type, but not letter spacing.
        if spec.letterSpacing != 0 { attributes[.kern] = spec.letterSpacing }
        let lineHeight = spec.lineHeight * m
        if lineHeight > 0 {
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        attributes[.paragraphStyle] = paragraph
        return NSAttributedString(string: text, attributes: attributes)
    }
}

/// RCTTextLayoutManager: a TextKit stack with no line fragment padding, tail truncation when
/// the lines are limited, and sizes rounded up to whole pixels.
private struct TextKitText: UIViewRepresentable {
    let string: NSAttributedString
    let lines: Int

    func makeUIView(context _: Context) -> TextKitView {
        let view = TextKitView()
        view.backgroundColor = .clear
        view.isOpaque = false
        view.contentMode = .redraw
        view.isAccessibilityElement = false
        // Static text, as RN's Text is: touches go to the views and gestures around it.
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: TextKitView, context _: Context) {
        if view.string != string || view.lines != lines {
            view.string = string
            view.lines = lines
            view.setNeedsDisplay()
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView _: TextKitView, context _: Context) -> CGSize? {
        let size = TextKitView.layout(string, lines: lines, width: proposal.width ?? .greatestFiniteMagnitude).used.size
        let scale = UIScreen.main.scale
        return CGSize(width: ceil(size.width * scale) / scale, height: ceil(size.height * scale) / scale)
    }
}

private final class TextKitView: UIView {
    var string = NSAttributedString()
    var lines = 0

    static func layout(_ string: NSAttributedString, lines: Int, width: CGFloat)
        -> (manager: NSLayoutManager, container: NSTextContainer, storage: NSTextStorage, used: CGRect) {
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = lines
        container.lineBreakMode = lines > 0 ? .byTruncatingTail : .byClipping
        let manager = NSLayoutManager()
        manager.usesFontLeading = false
        manager.addTextContainer(container)
        let storage = NSTextStorage(attributedString: string)
        storage.addLayoutManager(manager)
        manager.ensureLayout(for: container)
        return (manager, container, storage, manager.usedRect(for: container))
    }

    override func draw(_: CGRect) {
        // TextKit can need a fraction of a point more than the width it measured, so text drawn
        // at exactly that width loses its last letter to a hidden second line ("Contacts" drew as
        // "Contact") or to an ellipsis ("Add Conta..."). Text that fits on one line within a
        // couple of points of the bounds is laid out unconstrained; anything else that
        // overflows its bounds gets a hair more width.
        let natural = TextKitView.layout(string, lines: lines, width: .greatestFiniteMagnitude)
        var layout: (manager: NSLayoutManager, container: NSTextContainer, storage: NSTextStorage, used: CGRect)
        if natural.used.width <= bounds.width + 2 {
            layout = TextKitView.layout(string, lines: lines, width: max(bounds.width, natural.used.width + 1))
        } else {
            layout = TextKitView.layout(string, lines: lines, width: bounds.width)
            if layout.used.height > bounds.height + 0.5 {
                layout = TextKitView.layout(string, lines: lines, width: bounds.width + 2)
            }
        }
        // The layout manager holds its storage weakly: keep it alive while drawing.
        let (manager, container, storage, _) = layout
        withExtendedLifetime(storage) {
            let range = manager.glyphRange(for: container)
            manager.drawBackground(forGlyphRange: range, at: .zero)
            manager.drawGlyphs(forGlyphRange: range, at: .zero)
        }
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

extension View {
    /// An RN testID on a whole box: the element (and its bounds) is this view's frame, not
    /// just the glyphs of the text inside it. The overlay only carries the accessibility
    /// element; touches go through to the views and gestures around it.
    func rnID(_ id: String, label: String) -> some View {
        accessibilityHidden(true).overlay {
            Color.clear
                .accessibilityElement()
                .accessibilityLabel(label)
                .accessibilityAddTraits(.isStaticText)
                .accessibilityIdentifier(id)
                .allowsHitTesting(false)
        }
    }
}
