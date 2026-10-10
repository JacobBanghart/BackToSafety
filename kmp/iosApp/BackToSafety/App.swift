import Shared
import SwiftUI
import UserNotifications

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
    /// The countdown alerts on and off the emergency screen (shared EmergencyAway).
    private(set) lazy var away = EmergencyAway(store: store, scheduler: CountdownNotifications(
        emergencyT: { [unowned self] in t("emergency") },
        emergencyNumber: { [unowned self] in t("common")("emergencyNumber") }
    ))

    init(store: Store = DatabaseBuilder_iosKt.openStore()) {
        self.store = store
    }

    func t(_ namespace: String, in language: String? = nil) -> Translate {
        Translate(lookup: translations.translator(locale: language ?? self.language, namespace: namespace))
    }

    func load() async {
        AppClock.shared.testSeamsEnabled = testSeams
        // The first query migrates a database the RN app left (data_migrated).
        let migratingFrom = DatabaseOpen.shared.migratingFrom?.int32Value
        let migrationStart = Date()
        var seedError: Error?
        do { try await store.seed() } catch { seedError = error }
        if let migratingFrom {
            let durationMs = Int64(Date().timeIntervalSince(migrationStart) * 1000)
            let properties = if let seedError {
                AppDatabaseKt.dataMigrationFailedProperties(fromVersion: migratingFrom, durationMs: durationMs, errorType: errorType(seedError))
            } else {
                (try? await store.dataMigratedProperties(fromVersion: migratingFrom, durationMs: durationMs)) ?? [:]
            }
            Analytics.shared.track(event: .dataMigrated, properties: properties)
        }
        // The device is the analytics identity (as on Android).
        if let deviceId = try? await store.deviceId() { identifyAnalytics(deviceId) }
        themePreference = (try? await store.setting(key: "theme_preference")) ?? "system"
        // Spanish only in debug builds until it ships.
        let saved = try? await store.setting(key: "language_preference")
        language = saved == "es" && (isDebug || shippedLanguages.contains("es")) ? "es" : "en"
        onboarded = (try? await store.isOnboarded())?.boolValue ?? false
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
        guard !testSeams, (try? await away.shouldAskForNotifications())?.boolValue == true else { return }
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        _ = try? await away.notificationsAnswered(granted: granted)
    }

    /// app_ready's readiness properties: what's stored, and whether alerts can be shown.
    func readiness() async -> [String: Any] {
        var properties = (try? await store.readinessProperties()) ?? [:]
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
                .onAppear {
                    DispatchQueue.main.async { AppReady.report(readiness: model.readiness) }
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
