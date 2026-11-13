import Shared
import SwiftUI
import UIKit

private let tapsToUnlock = 7

/// Port of app/settings.tsx.
struct SettingsView: View {
    let t: Translate
    let tCommon: Translate
    @EnvironmentObject private var model: AppModel
    @Environment(\.appColors) private var colors
    @State private var devMode = isDebug
    @State private var tapCount = 0
    @State private var lastTap: TimeInterval = 0
    @State private var deviceId: String?
    @State private var deleting = false
    @State private var alert: SettingsAlert?

    private enum SettingsAlert: Identifiable {
        case devMode, confirmDelete, deleteError, copied
        var id: Self { self }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: t("screenTitle"), testID: "settings", onBack: { model.path.removeLast() })
            ScrollView {
                VStack(alignment: .leading, spacing: Space.lg) {
                    AppCard {
                        sectionHeader("paintbrush.fill", colors.text, t("sections.appearance.title"))
                        description(t("sections.appearance.description"))
                        HStack(spacing: Space.md) {
                            ForEach([("system", "iphone"), ("light", "sun.max.fill"), ("dark", "moon.fill")], id: \.0) { value, icon in
                                option(t("themeOptions.\(value)"), "settings-theme-\(value)", model.themePreference == value, icon) {
                                    Analytics.shared.track(event: .settingsThemeChanged, properties: ["theme": value])
                                    model.setTheme(value)
                                }
                            }
                        }
                    }

                    if devMode {
                        AppCard {
                            sectionHeader("wrench.fill", colors.text, t("sections.devTools.title"))
                            description(t("sections.devTools.description"))
                            sectionHeader("globe", colors.text, t("languageSection.title"))
                            description(t("languageSection.description"))
                            HStack(spacing: Space.md) {
                                ForEach([("en", "English"), ("es", "Español")], id: \.0) { lang, label in
                                    option(label, "settings-language-\(lang)", model.language == lang, nil) {
                                        Analytics.shared.track(event: .settingsLanguageChanged, properties: ["language": lang])
                                        model.setLanguage(lang)
                                    }
                                }
                            }
                        }
                    }

                    AppCard {
                        RNText(t("sections.about.title"), TextSpec(size: 20, lineHeight: 0, weight: 700), color: colors.text)
                        ListItem(label: t("sections.about.app"), value: appName)
                        ListItem(label: t("sections.about.version"), value: versionLabel + (devMode ? t("sections.about.devSuffix") : ""),
                                 testID: "settings-version", onPress: onVersionTap)
                        // Platform, theme and the device ID are for support, not for the caregiver: set
                        // apart under their own small heading rather than read as ordinary content.
                        RNText(t("sections.about.diagnostics").uppercased(), Typography.caption.spec.weight(600).spacing(0.8), color: colors.textSecondary)
                            .padding(.top, Space.lg).padding(.bottom, Space.xxs)
                            .accessibilityAddTraits(.isHeader)
                        ListItem(label: t("sections.about.platform"), value: "ios")
                        ListItem(label: t("sections.about.theme"), value: colors.isDark ? "dark" : "light")
                        if devMode { ListItem(label: t("sections.about.dbSchema"), value: String(AppDatabaseKt.DATABASE_VERSION)) }
                        ListItem(label: t("sections.about.deviceId"), value: deviceId ?? "—", testID: "settings-device-id",
                                 ruleColor: .clear) {
                            guard let deviceId else { return }
                            UIPasteboard.general.string = deviceId
                            alert = .copied
                        }
                    }

                    // The danger zone sits last, under About, and is outlined rather than a solid red
                    // block: deleting everything is rare and final, so it shouldn't read as prominently
                    // as a routine option. Its confirmation alert is unchanged.
                    AppCard {
                        sectionHeader("trash.fill", colors.error, t("sections.deleteAccount.title"))
                        description(t("sections.deleteAccount.description"))
                        Button { alert = .confirmDelete } label: {
                            HStack(spacing: Space.sm) {
                                SFIcon("trash.fill", 18, colors.error)
                                RNText(t(deleting ? "sections.deleteAccount.deletingButton" : "sections.deleteAccount.button"),
                                       Typography.bodyBold.spec, color: colors.error)
                            }
                            .frame(maxWidth: .infinity, minHeight: 48 - 2 * Space.md)
                            .padding(.vertical, Space.md).padding(.horizontal, 20)
                            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(colors.error, lineWidth: 1))
                            .contentShape(Rectangle())
                            .opacity(deleting ? 0.6 : 1)
                        }
                        .buttonStyle(.pressable)
                        .disabled(deleting)
                        .accessibilityIdentifier("settings-delete-account")
                    }
                }
                .padding(Space.xl)
            }
            .scrollStopsAtSafeArea()
        }
        .background(colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task {
            Analytics.shared.screen(path: "settings", properties: [:])
            deviceId = try? await model.store.deviceId()
        }
        .alert(alertTitle, isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } }), presenting: alert) { kind in
            if kind == .confirmDelete {
                Button(t("deleteAccountModal.cancel"), role: .cancel) {}
                Button(t("deleteAccountModal.confirm"), role: .destructive, action: deleteAccount)
            } else {
                Button(tCommon("ok")) {}
            }
        } message: { kind in
            Text(alertMessage(kind))
        }
    }

    private var alertTitle: String {
        switch alert {
        case .devMode: t("devModeAlert.title")
        case .confirmDelete: t("deleteAccountModal.title")
        case .deleteError: tCommon("error")
        case .copied: tCommon("copied")
        case nil: ""
        }
    }

    private func alertMessage(_ kind: SettingsAlert) -> String {
        switch kind {
        case .devMode: t("devModeAlert.message")
        case .confirmDelete: t("deleteAccountModal.message")
        case .deleteError: t("deleteAccountError")
        case .copied: t("deviceIdCopied")
        }
    }

    private var appName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
            ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String ?? ""
    }

    /// utils/appInfo.ts: "version (build)", or just the version when they're the same.
    private var versionLabel: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        return version.hasPrefix("internal-") || build == version ? version : "\(version) (\(build))"
    }

    private func onVersionTap() {
        let now = Date().timeIntervalSince1970
        if now - lastTap > 1 {
            tapCount = 1
        } else {
            tapCount += 1
            if tapCount >= tapsToUnlock, !devMode {
                devMode = true
                Analytics.shared.track(event: .settingsDevModeUnlocked, properties: [:])
                alert = .devMode
            }
        }
        lastTap = now
    }

    private func deleteAccount() {
        deleting = true
        Task {
            do {
                try await model.store.clearAllData()
                Analytics.shared.track(event: .settingsAccountDeleted, properties: [:])
                Analytics.shared.reset() // the deleted device ID is no longer who this is
                _ = try? await model.away.ended()
                model.onboarded = false
                model.path = []
            } catch {
                reportSaveFailed(screen: "settings", action: "delete_account", error: error)
                alert = .deleteError
            }
            deleting = false
        }
    }

    private func sectionHeader(_ icon: String, _ iconColor: Color, _ title: String) -> some View {
        HStack(spacing: Space.sm) {
            SFIcon(icon, 20, iconColor)
            // ThemedText type="subtitle": 20 bold with no line height of its own.
            RNText(title, TextSpec(size: 20, lineHeight: 0, weight: 700), color: colors.text)
        }
        .padding(.bottom, Space.sm)
    }

    private func description(_ text: String) -> some View {
        RNText(text, TextSpec(size: 16, lineHeight: 24), color: colors.textSecondary).padding(.bottom, Space.lg)
    }

    /// A theme or language choice: 2pt border, tint and primaryLight when selected, the label in primaryText (tint on primaryLight is under 4.5:1).
    private func option(_ label: String, _ testID: String, _ selected: Bool, _ icon: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 0) {
                // A 24pt symbol, tinted like the label.
                if let icon { SFIcon(icon, 24, selected ? colors.primaryText : colors.textSecondary).padding(.bottom, Space.xs) }
                RNText(label, TextSpec(size: 14, lineHeight: 24, weight: selected ? 600 : 400), color: selected ? colors.primaryText : colors.text)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Space.lg + 2)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(selected ? colors.primaryLight : .clear))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(selected ? colors.tint : colors.border, lineWidth: 2))
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier(testID)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
