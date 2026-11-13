import Shared
import SwiftUI

/// First onboarding step. Port of app/onboarding/index.tsx.
struct WelcomeView: View {
    let t: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var scrolled = false

    var body: some View {
        let featureBg = colors.isDark ? Color.white.opacity(0.08) : Color(argb: Neutral.c100)
        let optionBg = colors.isDark ? Color.white.opacity(0.1) : Color(argb: Neutral.c100)
        VStack(spacing: 0) {
            // The viewport's height decides how the first screen ends: only whole feature cards show
            // above the footer (a card cut mid-sentence by the rule read as broken at large text).
            GeometryReader { viewport in
                ScrollView {
                    WelcomeLayout(viewportHeight: viewport.size.height) {
                        Image(uiImage: UIImage(named: "logo-full.png") ?? UIImage())
                            .resizable()
                            .scaledToFit()
                        VStack(spacing: 0) {
                            RNText(t("welcome.title"), TextSpec(size: 36, lineHeight: 44, weight: 700, letterSpacing: CGFloat(Typography.display.letterSpacing)),
                                   color: colors.text, align: .center)
                                .padding(.bottom, Space.lg)
                            RNText(t("welcome.description"), Typography.bodyLarge.spec, color: colors.textSecondary, align: .center)
                        }
                        .frame(maxWidth: .infinity)

                        ForEach([("timer", "timer"), ("readout", "doc.text.fill"), ("privacy", "lock.fill")], id: \.0) { key, icon in
                            HStack(alignment: .top, spacing: 16) {
                                SFIcon(icon, 22, colors.primaryText)
                                    .frame(width: 44, height: 44)
                                    .background(Circle().fill(colors.primaryLight))
                                VStack(alignment: .leading, spacing: Space.xs) {
                                    RNText(t("welcome.features.\(key).title"), Typography.bodyBold.spec, color: colors.text)
                                    RNText(t("welcome.features.\(key).description"), Typography.body.spec, color: colors.textSecondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(Space.lg)
                            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(featureBg))
                        }

                        VStack(alignment: .leading, spacing: 0) {
                            RNText(t("welcome.themeLabel").uppercased(), Typography.caption.spec.weight(600).spacing(1), color: colors.textSecondary)
                                .padding(.bottom, Space.md)
                            HStack(spacing: Space.md) {
                                ForEach([("light", "welcome.themeOptions.light", "sun.max.fill"), ("dark", "welcome.themeOptions.dark", "moon.fill"),
                                         ("system", "welcome.themeOptions.auto", "iphone")], id: \.0) { value, label, icon in
                                    let selected = model.themePreference == value
                                    Button {
                                        model.setTheme(value)
                                    } label: {
                                        HStack(spacing: 8) {
                                            SFIcon(icon, 20, selected ? colors.primaryText : colors.textSecondary)
                                            RNText(t(label), Typography.bodyLarge.spec.weight(selected ? 600 : 500),
                                                   color: selected ? colors.primaryText : colors.text)
                                        }
                                        .frame(maxWidth: .infinity)
                                        .padding(Space.md)
                                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(selected ? colors.primaryLight : optionBg))
                                        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(selected ? colors.tint : .clear, lineWidth: 2))
                                    }
                                    .buttonStyle(.pressable)
                                    .accessibilityIdentifier("onboarding-welcome-theme-\(value)")
                                    .accessibilityAddTraits(selected ? .isSelected : [])
                                }
                            }
                        }
                    }
                    .padding(.horizontal, Space.xl)
                    .background(GeometryReader { geo in
                        Color.clear.preference(key: ScrollOffsetKey.self, value: -geo.frame(in: .named("welcome")).minY)
                    })
                }
                .coordinateSpace(name: "welcome")
                .scrollIndicators(.hidden)
                .onPreferenceChange(ScrollOffsetKey.self) { scrolled = $0 > 10 }
            }

            // A rule above the footer: the content stops at a visible edge rather than a card looking
            // cut off by the scroll hint.
            Rectangle().fill(colors.border).frame(height: 1)
            VStack(spacing: Space.md) {
                if !scrolled {
                    RNText(t("welcome.scrollHint").uppercased(), Typography.caption.spec.weight(700).spacing(1),
                           color: colors.primaryText, align: .center)
                        .frame(maxWidth: .infinity)
                }
                Button {
                    Task {
                        _ = try? await model.store.completeStep(step: "welcome")
                        model.path.append(.name)
                    }
                } label: {
                    RNText(t("welcome.getStarted"), Typography.bodyLarge.spec.weight(600), color: colors.textOnPrimary)
                        .padding(.vertical, Space.md)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("onboarding-get-started")
                RNText(t("welcome.privacy"), Typography.caption.spec.weight(500), color: colors.text, align: .center)
                    .frame(maxWidth: .infinity)
            }
            .padding(Space.lg)
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }
}

private struct ScrollOffsetKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// The welcome content: logo, title, the three feature cards and the theme picker, stacked with
/// the screen's gaps. A card the viewport's bottom edge would cut mid-sentence (large text, short
/// screens) never shows at rest: the logo shrinks (to `logoMin`) until that card fits with a gap
/// above the footer, and if that isn't enough the card moves wholly below the fold, so the first
/// screen ends on complete cards and "Scroll for more" says the rest is there.
/// Subviews, in order: logo, hero text, the feature cards, the theme picker. Mirrors Android's
/// `WelcomeLayout`.
private struct WelcomeLayout: Layout {
    let viewportHeight: CGFloat
    private let topPad = Space.xxxl
    private let afterLogo = Space.xl
    private let afterHero = Space.xxl
    private let betweenCards = Space.lg
    private let afterCards = Space.xxl
    private let bottomPad = Space.xl * 2
    private let foldGap = Space.lg
    private let logoMax: CGFloat = 180
    private let logoMin: CGFloat = 96

    private struct Plan {
        var frames: [CGRect]
        var height: CGFloat
    }

    private func plan(width: CGFloat, _ subviews: Subviews) -> Plan {
        guard subviews.count >= 3 else { return Plan(frames: [], height: 0) }
        let full = ProposedViewSize(width: width, height: nil)
        let heights = subviews.map { $0.sizeThatFits(full).height }
        let hero = heights[1]
        let cards = Array(heights[2 ..< heights.count - 1])
        let theme = heights[heights.count - 1]
        let fold = viewportHeight - foldGap
        var logo = logoMax
        var pushDown: CGFloat = 0
        var top = topPad + logoMax + afterLogo + hero + afterHero
        for card in cards {
            let bottom = top + card
            if bottom > fold, top < viewportHeight {
                let overflow = bottom - fold
                if overflow <= logoMax - logoMin { logo = logoMax - overflow } else { pushDown = viewportHeight - top }
                break
            }
            top = bottom + betweenCards
        }
        var frames: [CGRect] = []
        var y = topPad + pushDown
        frames.append(CGRect(x: (width - logo) / 2, y: y, width: logo, height: logo))
        y += logo + afterLogo
        frames.append(CGRect(x: 0, y: y, width: width, height: hero))
        y += hero + afterHero
        for (i, card) in cards.enumerated() {
            frames.append(CGRect(x: 0, y: y, width: width, height: card))
            y += card + (i < cards.count - 1 ? betweenCards : 0)
        }
        y += afterCards
        frames.append(CGRect(x: 0, y: y, width: width, height: theme))
        y += theme + bottomPad
        return Plan(frames: frames, height: y)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        return CGSize(width: width, height: plan(width: width, subviews).height)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        let frames = plan(width: bounds.width, subviews).frames
        for (subview, frame) in zip(subviews, frames) {
            subview.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                          proposal: ProposedViewSize(width: frame.width, height: frame.height))
        }
    }
}
