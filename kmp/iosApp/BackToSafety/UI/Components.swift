import Shared
import SwiftUI
import UIKit

/// components/OnboardingStepHeader.tsx: back chevron, step dots, and a balancing spacer.
struct OnboardingStepHeader: View {
    let active: Int
    let total: Int
    let onBack: () -> Void
    @Environment(\.appColors) private var colors

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) { SFIcon("chevron.left", 20, colors.tint).frame(width: 44, height: 44) }
                .buttonStyle(.pressable)
            HStack(spacing: Space.sm) {
                ForEach(1 ... total, id: \.self) { step in
                    RoundedRectangle(cornerRadius: Radius.sm)
                        .fill(step <= active ? colors.primary : colors.border)
                        .frame(width: step == active ? 24 : 8, height: 8)
                }
            }
            .frame(maxWidth: .infinity)
            Color.clear.frame(width: 44, height: 44)
        }
        .padding(.bottom, Space.xxl)
    }
}

/// An onboarding step (KeyboardAvoidingScroll with a footer): scrolling content above a footer
/// that rides above the keyboard; dragging the content dismisses the keyboard (F-40).
struct OnboardingScaffold<Content: View, Footer: View>: View {
    var contentTop: CGFloat = 20
    var contentBottom: CGFloat = 0
    var footerGap: CGFloat = Space.md
    var footerPadding: CGFloat = Space.lg
    @ViewBuilder let content: () -> Content
    @ViewBuilder let footer: () -> Footer
    @Environment(\.appColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0, content: content)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Space.xl)
                    .padding(.top, contentTop)
                    .padding(.bottom, contentBottom)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            VStack(spacing: footerGap, content: footer)
                .padding(footerPadding)
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// ThemedText type="title" with marginBottom sm, the heading on every onboarding step.
struct StepTitle: View {
    let text: String
    @Environment(\.appColors) private var colors
    var body: some View {
        RNText(text, Typography.title.spec, color: colors.text).padding(.bottom, Space.sm)
    }
}

/// A labeled input group: bodyBold label, then the field, sm apart.
struct Field<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content
    @Environment(\.appColors) private var colors
    var body: some View {
        VStack(alignment: .leading, spacing: Space.sm) {
            RNText(label, Typography.bodyBold.spec, color: colors.text)
            content()
        }
    }
}

/// The onboarding text input: 48pt tall (or an 80pt-and-up text area), 1pt border, radius lg,
/// padding lg (the border inside it). On iOS RN gives these inputs the font's own line height.
struct AppInput: View {
    @Binding var text: String
    let placeholder: String
    let testID: String
    var size: CGFloat = 16
    var multiline = false
    var keyboard: UIKeyboardType = .default
    var capitalization: UITextAutocapitalizationType = .sentences
    var format: ((String) -> String)?
    @Environment(\.appColors) private var colors
    @Environment(\.dynamicTypeSize) private var dynamicType

    var body: some View {
        let m = rnFontMultiplier(dynamicType)
        RNTextInput(
            text: $text, placeholder: placeholder, testID: testID,
            font: .systemFont(ofSize: size * m), textColor: UIColor(colors.text),
            placeholderColor: UIColor(colors.inputPlaceholder), tint: UIColor(colors.tint),
            insets: UIEdgeInsets(top: multiline ? Space.sm - 1 : 0, left: Space.lg - 1, bottom: multiline ? Space.sm - 1 : 0, right: Space.lg - 1),
            multiline: multiline, keyboard: keyboard, capitalization: capitalization, minHeight: 78, format: format
        )
        .frame(height: multiline ? nil : 46)
        .padding(1)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.inputBackground))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.inputBorder, lineWidth: 1))
    }
}

/// The filled call-to-action button used across onboarding.
struct PrimaryButton: View {
    let label: String
    let testID: String
    var spec: TextSpec = .init(size: 18, lineHeight: 24, weight: 600)
    var enabled = true
    var dimmed: Bool?
    var verticalPadding: CGFloat = Space.md
    let action: () -> Void
    @Environment(\.appColors) private var colors

    var body: some View {
        Button(action: action) {
            RNText(label, spec, color: colors.textOnPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, verticalPadding)
                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(colors.primary))
                .opacity((dimmed ?? !enabled) ? 0.5 : 1)
        }
        .buttonStyle(.pressable)
        .disabled(!enabled)
        .accessibilityIdentifier(testID)
    }
}

/// "Skip for now": a quiet text button.
struct SkipButton: View {
    let label: String
    let testID: String
    let action: () -> Void
    @Environment(\.appColors) private var colors

    var body: some View {
        Button(action: action) {
            RNText(label, Typography.body.spec, color: colors.textDisabled)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Space.sm)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
    }
}

/// onboarding_step_viewed when a step's screen appears; completed/skipped on leaving it.
func trackStep(_ completed: Bool, _ step: String) {
    Analytics.shared.track(event: completed ? .onboardingStepCompleted : .onboardingStepSkipped, properties: ["step": step])
}

extension View {
    func trackStepViewed(_ step: String) -> some View {
        task { Analytics.shared.track(event: .onboardingStepViewed, properties: ["step": step]) }
    }
}

/// RN Pressable: no built-in press or disabled styling (SwiftUI's .plain dims disabled
/// buttons on top of the app's own 0.5).
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label }
}

extension ButtonStyle where Self == PressableStyle {
    static var pressable: PressableStyle { PressableStyle() }
}
