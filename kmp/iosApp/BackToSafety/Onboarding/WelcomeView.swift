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
                        ForEach([("timer", "⏱️"), ("readout", "📋"), ("privacy", "🔒")], id: \.0) { key, icon in
                            HStack(alignment: .top, spacing: 16) {
                                RNText(icon, TextSpec(size: 22, lineHeight: 24), color: colors.text)
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
                        RNText(t("welcome.themeLabel").uppercased(), Typography.caption.spec.weight(600).spacing(1), color: colors.textDisabled)
                            .padding(.bottom, Space.md)
                        HStack(spacing: Space.md) {
                            ForEach([("light", "welcome.themeOptions.light", "☀️"), ("dark", "welcome.themeOptions.dark", "🌙"),
                                     ("system", "welcome.themeOptions.auto", "📱")], id: \.0) { value, label, icon in
                                let selected = model.themePreference == value
                                Button {
                                    model.setTheme(value)
                                } label: {
                                    HStack(spacing: 8) {
                                        RNText(icon, TextSpec(size: 20, lineHeight: 24), color: colors.text)
                                        RNText(t(label), Typography.bodyLarge.spec.weight(selected ? 600 : 500),
                                               color: selected ? colors.tint : colors.textSecondary)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(Space.md)
                                    .background(RoundedRectangle(cornerRadius: Radius.lg).fill(selected ? colors.primaryLight : optionBg))
                                    .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(selected ? colors.tint : .clear, lineWidth: 2))
                                }
                                .buttonStyle(.pressable)
                                .accessibilityIdentifier("onboarding-welcome-theme-\(value)")
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

            VStack(spacing: Space.lg) {
                if !scrolled {
                    RNText(t("welcome.scrollHint").uppercased(), Typography.caption.spec.weight(600).spacing(1),
                           color: colors.textDisabled, align: .center)
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
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                }
                .buttonStyle(.pressable)
                .accessibilityIdentifier("onboarding-get-started")
                RNText(t("welcome.privacy"), Typography.caption.spec, color: colors.textDisabled, align: .center)
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
