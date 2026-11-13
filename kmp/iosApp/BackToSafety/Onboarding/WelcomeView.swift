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
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(spacing: 0) {
                        Image(uiImage: UIImage(named: "logo-full.png") ?? UIImage())
                            .resizable()
                            .scaledToFit()
                            .frame(width: 180, height: 180)
                            .padding(.bottom, Space.xl)
                        RNText(t("welcome.title"), TextSpec(size: 36, lineHeight: 44, weight: 700, letterSpacing: CGFloat(Typography.display.letterSpacing)),
                               color: colors.text, align: .center)
                            .padding(.bottom, Space.lg)
                        RNText(t("welcome.description"), Typography.bodyLarge.spec, color: colors.textSecondary, align: .center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, Space.xxl)

                    VStack(spacing: Space.lg) {
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
                    }
                    .padding(.bottom, Space.xxl)

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
                    .padding(.bottom, Space.xl)
                }
                .padding(.horizontal, Space.xl)
                .padding(.top, Space.xxxl)
                .padding(.bottom, Space.xl)
                .background(GeometryReader { geo in
                    Color.clear.preference(key: ScrollOffsetKey.self, value: -geo.frame(in: .named("welcome")).minY)
                })
            }
            .coordinateSpace(name: "welcome")
            .scrollIndicators(.hidden)
            .onPreferenceChange(ScrollOffsetKey.self) { scrolled = $0 > 10 }

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
