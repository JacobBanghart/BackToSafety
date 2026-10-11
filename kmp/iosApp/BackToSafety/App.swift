import SwiftUI
import UserNotifications

/// The screens, as the RN app's routes.
enum Route: Hashable {
    case name, photo, appearance, contact, complete
    case emergency, readout, settings, contacts, destinations, profile
}

/// i18n/index.ts: languages released to users (Spanish awaits a native review).
private let shippedLanguages = ["en"]

@MainActor
final class AppModel: ObservableObject {
    let store: AppStore
    private let translations = AppModel.loadTranslations()
    @Published var onboarded: Bool?
    @Published var themePreference = "system"
    @Published var language = "en"
    @Published var path: [Route] = []
    /// The countdown alerts on and off the emergency screen (the core's EmergencyAway).
    private(set) lazy var away = EmergencyAway(store: store.awayCore, scheduler: CountdownNotifications(
        emergencyT: { [unowned self] in t("emergency") },
        emergencyNumber: { [unowned self] in t("common")("emergencyNumber") }
    ))

    init(store: AppStore = AppStore.open()) {
        self.store = store
    }

    func t(_ namespace: String, in language: String? = nil) -> Translate {
        Translate(translator: translations.translator(locale: language ?? self.language, namespace: namespace))
    }

    func load() async {
        AppClock.shared.testSeamsEnabled = testSeams
        // Seeds, and reports data_migrated if opening migrated a database the RN app left.
        try? await store.seedAndReportMigration()
        // The device is the analytics identity (as on Android).
        if let deviceId = try? await store.deviceId() { identifyAnalytics(deviceId) }
        themePreference = (try? await store.setting(key: "theme_preference")) ?? "system"
        // Spanish only in debug builds until it ships.
        let saved = try? await store.setting(key: "language_preference")
        language = saved == "es" && (isDebug || shippedLanguages.contains("es")) ? "es" : "en"
        onboarded = (try? await store.isOnboarded()) ?? false
    }

    /// A tapped countdown alert: opens the emergency, if it's still running.
    func openEmergency() {
        Task {
            guard (try? await store.activeEmergency()) != nil, path.last != .emergency else { return }
            path = [.emergency]
        }
    }

    /// Notifications for the countdown alerts: asked for once, on home after onboarding. Not
    /// in test builds, where the system dialog would sit over the flows.
    func askForNotificationsOnce() async {
        guard !testSeams, (try? away.shouldAskForNotifications()) == true else { return }
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        _ = try? away.notificationsAnswered(granted: granted)
    }

    /// app_ready's device properties: whether alerts can be shown (the core adds what's stored).
    func deviceProperties() async -> [String: Any] {
        var properties: [String: Any] = [:]
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        properties["notifications_enabled"] = status == .authorized || status == .provisional || status == .ephemeral
        return properties
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
        return Translations.fromJson(files: resources)
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
        LaunchReport.noteAppInit()
        setUpAnalytics()
        // Before launch finishes, so a tap on an alert that launched the app arrives.
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
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
        AppClock.shared.handleLink(url)
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
                .onAppear {
                    DispatchQueue.main.async { LaunchReport.report(store: model.store, device: model.deviceProperties) }
                    NotificationDelegate.shared.onOpenEmergency = model.openEmergency
                }
                .task(id: onboarded && model.path.isEmpty) {
                    if onboarded, model.path.isEmpty { await model.askForNotificationsOnce() }
                }
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
