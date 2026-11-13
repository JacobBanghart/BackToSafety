import Shared
import SwiftUI

/// First onboarding step. Port of app/onboarding/index.tsx.
struct WelcomeView: View {
    let t: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors

    var body: some View {
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
                            // In dark mode the logo's cream disc is toned down a little, so it doesn't glare on black.
                            .colorMultiply(colors.isDark ? Color(white: logoDark) : .white)
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
                                    .background(Circle().fill(colors.iconContainer))
                                VStack(alignment: .leading, spacing: Space.xs) {
                                    RNText(t("welcome.features.\(key).title"), Typography.bodyBold.spec, color: colors.text)
                                    RNText(t("welcome.features.\(key).description"), Typography.body.spec, color: colors.textSecondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(Space.lg)
                            // A card like the app's others (card fill, 1pt border): grey on grey
                            // made the feature text look disabled.
                            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.card))
                            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.border, lineWidth: 1))
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
                }
                .scrollIndicators(.hidden)
            }

            // A rule above the footer: the content stops at a visible edge. No "scroll for more" cue:
            // pinned over the content it still made the cards above it look cut off, and the layout
            // already ends the first screen on whole blocks.
            Rectangle().fill(colors.border).frame(height: 1)
            // The privacy line sits a full lg under the button, so it reads as its own promise
            // rather than as the button's caption.
            VStack(spacing: Space.lg) {
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
                // The privacy promise at body size in ink: at caption size it read as fine print.
                RNText(t("welcome.privacy"), Typography.body.spec.weight(500), color: colors.text, align: .center)
                    .frame(maxWidth: .infinity)
            }
            .padding(Space.lg)
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// How much of its brightness the logo keeps in dark mode.
private let logoDark = 0.85

/// The welcome content: logo, title, the three feature cards and the theme picker, stacked with
/// the screen's gaps. A block (card or theme picker) the viewport's bottom edge would cut never
/// shows at rest: the logo shrinks (to `logoMin`) until that block fits with a gap above the
/// footer, and if that isn't enough the block and what follows it move wholly below the fold, so
/// the first screen ends on complete blocks. Only what follows moves: everything above stays
/// where it was (shifting it all down cut the block above instead).
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
        // The blocks below the hero, each with the gap above it.
        let blocks = cards + [heights[heights.count - 1]]
        let gaps = cards.indices.map { $0 == 0 ? 0 : betweenCards } + [afterCards]
        let fold = viewportHeight - foldGap
        var logo = logoMax
        var pushAt = -1
        var pushDown: CGFloat = 0
        var top = topPad + logoMax + afterLogo + hero + afterHero
        for (i, block) in blocks.enumerated() {
            top += gaps[i]
            let bottom = top + block
            if bottom > fold, top < viewportHeight {
                let overflow = bottom - fold
                if overflow <= logoMax - logoMin {
                    logo = logoMax - overflow
                } else {
                    pushAt = i
                    pushDown = viewportHeight - top
                }
                break
            }
            top = bottom
        }
        var frames: [CGRect] = []
        var y = topPad
        frames.append(CGRect(x: (width - logo) / 2, y: y, width: logo, height: logo))
        y += logo + afterLogo
        frames.append(CGRect(x: 0, y: y, width: width, height: hero))
        y += hero + afterHero
        for (i, block) in blocks.enumerated() {
            y += gaps[i] + (i == pushAt ? pushDown : 0)
            frames.append(CGRect(x: 0, y: y, width: width, height: block))
            y += block
        }
        y += bottomPad
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
