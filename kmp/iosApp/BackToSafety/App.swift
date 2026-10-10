import Shared
import SwiftUI

/// Looks up a translated string: `t("key")` or `t("key", ["name": value])` (shared/I18n.kt).
struct Translate {
    let lookup: (String, [String: Any]) -> String
    func callAsFunction(_ key: String, _ vars: [String: Any] = [:]) -> String { lookup(key, vars) }
}

/// The screens, as the RN app's routes.
enum Route: Hashable {
    case name, photo, appearance, contact, complete
    case emergency, readout, settings, contacts, destinations, profile
}

/// i18n/index.ts: languages released to users (Spanish awaits a native review).
private let shippedLanguages = ["en"]

@MainActor
final class AppModel: ObservableObject {
    let store: Store
    private let translations = AppModel.loadTranslations()
    @Published var onboarded: Bool?
    @Published var themePreference = "system"
    @Published var language = "en"
    @Published var path: [Route] = []

    init(store: Store = DatabaseBuilder_iosKt.openStore()) {
        self.store = store
    }

    func t(_ namespace: String) -> Translate {
        Translate(lookup: translations.translator(locale: language, namespace: namespace))
    }

    func load() async {
        AppClock.shared.testSeamsEnabled = testSeams
        _ = try? await store.seed()
        // The device is the analytics identity (as on Android).
        if let deviceId = try? await store.deviceId() { identifyAnalytics(deviceId) }
        themePreference = (try? await store.setting(key: "theme_preference")) ?? "system"
        // Spanish only in debug builds until it ships.
        let saved = try? await store.setting(key: "language_preference")
        language = saved == "es" && (isDebug || shippedLanguages.contains("es")) ? "es" : "en"
        onboarded = (try? await store.isOnboarded())?.boolValue ?? false
    }

    func setLanguage(_ value: String) {
        language = value
        Task { try? await store.putSetting(key: "language_preference", value: value) }
    }

    func setTheme(_ value: String) {
        themePreference = value
        Task { try? await store.putSetting(key: "theme_preference", value: value) }
    }

    /// The RN app's locale JSON, bundled as locales/<locale>/<namespace>.json.
    private static func loadTranslations() -> Translations {
        var resources: [String: [String: String]] = [:]
        let root = Bundle.main.bundleURL.appendingPathComponent("locales")
        let fm = FileManager.default
        for locale in (try? fm.contentsOfDirectory(atPath: root.path)) ?? [] {
            var namespaces: [String: String] = [:]
            let dir = root.appendingPathComponent(locale)
            for file in (try? fm.contentsOfDirectory(atPath: dir.path)) ?? [] where file.hasSuffix(".json") {
                namespaces[String(file.dropLast(5))] = try? String(contentsOf: dir.appendingPathComponent(file), encoding: .utf8)
            }
            resources[locale] = namespaces
        }
        return Translations.companion.fromJson(resources: resources)
    }
}

#if TEST_SEAMS
    let testSeams = true
#else
    let testSeams = false
#endif
#if DEBUG
    let isDebug = true
#else
    let isDebug = false
#endif

@main
struct BackToSafetyApp: App {
    @StateObject private var model = AppModel()

    init() {
        AppReady.noteAppInit()
        setUpAnalytics()
    }

    var body: some Scene {
        WindowGroup {
            // Hosting the unit tests, the app stays out of the way: no screens, no database
            // load, no clock changes under the snapshot tests.
            if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil {
                Color.clear
            } else {
                RootView()
                    .environmentObject(model)
                    .onOpenURL(perform: handleTestSeam)
            }
        }
    }

    /// app/debug/clock.tsx: backtosafety://debug/clock?at=<ISO time> freezes the clock,
    /// ?advance=<seconds> moves it forward. Ignored unless built with TEST_SEAMS.
    private func handleTestSeam(_ url: URL) {
        guard url.host == "debug", url.path == "/clock",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else { return }
        if let at = items.first(where: { $0.name == "at" })?.value,
           let date = ISO8601DateFormatter().date(from: at) {
            AppClock.shared.freeze(atMs: Int64(date.timeIntervalSince1970 * 1000))
        }
        if let advance = items.first(where: { $0.name == "advance" })?.value.flatMap(Double.init) {
            AppClock.shared.advance(ms: Int64(advance * 1000))
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) private var systemScheme

    private var scheme: ColorScheme? {
        switch model.themePreference {
        case "dark": .dark
        case "light": .light
        default: nil
        }
    }

    var body: some View {
        let dark = (scheme ?? systemScheme) == .dark
        let colors = AppColors(isDark: dark)
        Group {
            if let onboarded = model.onboarded {
                NavigationStack(path: $model.path) {
                    Group {
                        if onboarded {
                            HomeView(t: model.t("home"), emergencyNumber: model.t("common")("emergencyNumber"))
                        } else {
                            WelcomeView(t: model.t("onboarding"))
                        }
                    }
                    .navigationDestination(for: Route.self) { route in
                        screen(route)
                    }
                }
                // The first screen's first frame (the next turn of the run loop after it
                // appears): report how long the cold start took.
                .onAppear { DispatchQueue.main.async { AppReady.report() } }
            } else {
                colors.background.ignoresSafeArea()
            }
        }
        .environment(\.appColors, colors)
        .preferredColorScheme(scheme)
        .task { await model.load() }
    }
}

extension RootView {
    @ViewBuilder
    func screen(_ route: Route) -> some View {
        let onboarding = model.t("onboarding")
        let common = model.t("common")
        switch route {
        case .name: NameView(t: onboarding, tCommon: common)
        case .photo: PhotoView(t: onboarding, tCommon: common)
        case .appearance: AppearanceView(t: onboarding)
        case .contact: ContactStepView(t: onboarding, tCommon: common)
        case .complete: CompleteView(t: onboarding)
        case .emergency: EmergencyView(t: model.t("emergency"), tCommon: common)
        case .readout: ReadoutView(t: model.t("readout"), tCommon: common)
        case .settings: SettingsView(t: model.t("settings"), tCommon: common)
        case .contacts: ContactsView(t: model.t("contacts"), tCommon: common)
        case .destinations: DestinationsView(t: model.t("destinations"), tCommon: common)
        case .profile: ProfileView(t: model.t("profile"), tCommon: common)
        default: NotPortedView(title: "\(route)")
        }
    }
}

/// Placeholder until the screen is ported.
struct NotPortedView: View {
    let title: String
    @Environment(\.appColors) private var colors
    var body: some View {
        ZStack { colors.background.ignoresSafeArea(); RNText(title, Typography.title.spec, color: colors.text) }
            .toolbar(.hidden, for: .navigationBar)
    }
}
